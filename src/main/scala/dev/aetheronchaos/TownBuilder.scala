package dev.aetheronchaos

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.StructureTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerData
import net.minecraft.world.entity.npc.VillagerProfession
import net.minecraft.world.entity.npc.VillagerType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.phys.AABB

/**
 * Builds small quest hubs ("villages"): a quest board (loom + signs) where quests
 * are accepted and turned in, a hearth, a vendor barrel, torch-lit fences and
 * permanent town NPCs (named nitwit villagers: a questmaster, a merchant, guards).
 * Registered boards gate quest turn-ins.
 *
 * The first hero to join a realm gets a starter town built at their spawn point
 * and respawns there from then on. Towns persist to aetheron-chaos-towns.snbt so
 * board registrations survive restarts.
 */
object TownBuilder:
  type Region = (Int, Int, Int, Int, Int, Int)
  /** A built town: anchor block, facing, footprint, whether it is the realm's starter town,
   *  and whether it was adopted from a vanilla village (its plaza stands at the village
   *  meeting point; the surrounding village is the villagers' own business). */
  final case class Town(base: BlockPos, dir: Direction, region: Region, capital: Boolean, adopted: Boolean = false, barracks: Boolean = false)

  // town NPCs (named nitwit villagers). The questmaster and merchant double as
  // clickable quest board / vendor; guards hand out hints.
  val QuestNpcName = "Questmaster Boralus"
  val VendorNpcName = "Merchant Tilli"
  val GuardNpcName = "Guard Captain Mara"
  val HearthNpcName = "Hearthkeeper Finn"

  // towns built or registered this server session (first capital = starter town)
  private val towns = mutable.ListBuffer[Town]()
  // positions of registered quest boards (BlockPos.asLong)
  private val boards = mutable.Set[Long]()
  // positions of registered vendor barrels (BlockPos.asLong)
  private val vendors = mutable.Set[Long]()
  // village centers looked at already (adopted or declined), so the scan never retries
  private val lookedAtVillages = mutable.Set[Long]()

  def boardCount: Int = boards.size

  def isBoard(pos: BlockPos): Boolean = boards.contains(pos.asLong)

  def isVendor(pos: BlockPos): Boolean = vendors.contains(pos.asLong)

  /** True if the player stands within ~12 blocks of any registered quest board. */
  def nearBoard(player: ServerPlayer): Boolean =
    boards.exists { l =>
      val bp = BlockPos.of(l)
      player.blockPosition.distSqr(bp) <= 144
    }

  /** The registered quest board closest to the player, if any exists. */
  def nearestBoard(player: ServerPlayer): Option[BlockPos] =
    if boards.isEmpty then None
    else Some(boards.iterator.map(l => BlockPos.of(l)).minBy(bp => player.blockPosition.distSqr(bp)))

  // --- town guard -----------------------------------------------------------------

  /**
   * The town is a sanctuary: hostile mobs inside town land are escorted back outside
   * the plaza unless a hero is actually fighting them (dealt the last blow). Merely
   * having a target is not enough - a creeper locks onto any hero within ~16 blocks
   * and would otherwise stroll into the plaza and take the fence line with it.
   * A creeper with its fuse lit is always moved out, even one under attack: heroes
   * can still fight it, but it detonates at the walls, not on the lanterns.
   */
  def expelHostileMobs(server: MinecraftServer): Unit =
    val level = server.overworld()
    if level == null || towns.isEmpty then return
    val rng = scala.util.Random()
    towns.foreach { t =>
      val (x1, y1, z1, x2, y2, z2) = t.region
      val box = new AABB(x1 - 4, y1 - 6, z1 - 4, x2 + 4, y2 + 6, z2 + 4)
      val cx = (x1 + x2 + 1) / 2.0
      val cz = (z1 + z2 + 1) / 2.0
      level.getEntitiesOfClass(classOf[Monster], box).asScala.foreach { m =>
        val foughtByHero = m.getLastHurtByMob match
          case _: net.minecraft.world.entity.player.Player => true
          case _ => false
        val midFuse = m match
          case c: Creeper => c.getSwellDir != 0
          case _ => false
        if !foughtByHero || midFuse then
          // escorted out along whichever axis points further out, landing past the edge
          val dx = m.getX - cx
          val dz = m.getZ - cz
          val (px, pz) =
            if math.abs(dx) >= math.abs(dz) then
              (if dx >= 0 then x2 + 8.0 + rng.nextInt(8) else x1 - 8.0 - rng.nextInt(8), m.getZ)
            else
              (m.getX, if dz >= 0 then z2 + 8.0 + rng.nextInt(8) else z1 - 8.0 - rng.nextInt(8))
          m.teleportTo(px, m.getY, pz)
      }
    }

  /** Role of a right-clicked entity for the interaction hook: board/vendor/guard/watch. */
  def npcRole(entity: Entity): Option[String] =
    entity match
      case g: net.minecraft.world.entity.animal.IronGolem if GuardWatch.isGuard(g) => Some("watch")
      case v: Villager if v.getCustomName != null =>
        v.getCustomName.getString match
          case QuestNpcName  => Some("board")
          case VendorNpcName => Some("vendor")
          case HearthNpcName => Some("hearth")
          case GuardNpcName  => Some("guard")
          case _             => None
      case _ => None

  // --- registration / persistence ------------------------------------------------

  private def atOf(base: BlockPos, dir: Direction)(forward: Int, lateral: Int, dy: Int): BlockPos =
    val right = dir.getClockWise
    base.offset(right.getStepX * lateral + dir.getStepX * forward, dy, right.getStepZ * lateral + dir.getStepZ * forward)

  private def regionOf(base: BlockPos, dir: Direction): Region =
    val c1 = atOf(base, dir)(0, -5, 0)
    val c2 = atOf(base, dir)(10, 5, 3)
    (
      math.min(c1.getX, c2.getX), math.min(c1.getY, c2.getY), math.min(c1.getZ, c2.getZ),
      math.max(c1.getX, c2.getX), math.max(c1.getY, c2.getY), math.max(c1.getZ, c2.getZ)
    )

  private def registerBoardAndVendor(t: Town): Unit =
    boards += atOf(t.base, t.dir)(8, 0, 2).asLong
    vendors += atOf(t.base, t.dir)(5, 4, 1).asLong

  def savedTowns: List[Town] = towns.toList

  /** Re-registers towns from disk after a server restart (no blocks are placed). */
  def registerSavedTowns(server: MinecraftServer): Unit =
    if towns.nonEmpty then return
    TownStore.load(server).foreach { t =>
      towns += t
      registerBoardAndVendor(t)
    }

  /** The starter town the newcomers spawn at (first capital, else any town). */
  private def capitalTown: Option[Town] = towns.find(_.capital).orElse(towns.headOption)

  /** Where a warrier or newcomer is placed inside a hub: the plaza stone, one step in. */
  def plazaStand(t: Town): BlockPos = atOf(t.base, t.dir)(2, 0, 1)

  /** Where the market sign stands: just beyond the plaza's edge. */
  def tradePostStand(t: Town): BlockPos = atOf(t.base, t.dir)(12, 6, 0)

  /** Where the barracks rises: the plaza's other flank, mirror of the market. */
  def barracksStand(t: Town): BlockPos = atOf(t.base, t.dir)(12, -6, 0)

  /** Heroes can tell a barracks when they see one; the watch knows too. */
  val BarracksGold = 150

  /** Status of barracks across the realm, read at a hub. */
  def barracksStatus(player: ServerPlayer): String =
    val rows = allTowns.zipWithIndex.map { (t, i) =>
      val note =
        if t.barracks then " - a barracks stands: the watch charter is +1"
        else s" - no barracks; $BarracksGold gold to raise one (/quest barracks build)"
      s"  ${if t.capital then "Hearthhome" else s"the hub at ${t.base.getX}, ${t.base.getZ}"}: $note"
    }
    if rows.isEmpty then "No hubs yet. Build one: /quest town"
    else (List("Barracks of the realm:") ::: rows).mkString("\n")

  /** Raise the barracks at this hub: the gold is the hero's, the stones are the realm's. */
  def buildBarracks(player: ServerPlayer, hubIndex: Int): String =
    val hubOpt = townByIndex(hubIndex + 1)
    hubOpt match
      case None => "There is no such hub."
      case Some(hub) if hub.barracks => "A barracks already stands at this hub."
      case Some(hub) =>
        val gold = Game.countItem(player, net.minecraft.world.item.Items.GOLD_NUGGET)
        if gold < BarracksGold then s"A barracks costs $BarracksGold gold in stone and timber - you carry $gold."
        else
          val level = player.server.overworld()
          if level == null then return "No overworld to build in."
          val placed = placeBarracks(level, hub)
          if placed == 0 then return "There is no clear ground for a barracks here - the plaza's edge is spoken for."
          Game.removeItems(player, net.minecraft.world.item.Items.GOLD_NUGGET, BarracksGold)
          towns(hubIndex) = hub.copy(barracks = true)
          TownStore.save(player.server)
          val raised = GuardWatch.raiseToCharter(player.server, hubIndex)
          Game.broadcast(
            player.server,
            Component
              .literal(s"${player.getName.getString} raises a barracks at ${if hub.capital then "Hearthhome" else s"the hub at ${hub.base.getX}, ${hub.base.getZ}"} - $placed stones set, the watch charter now ${GuardWatch.charterFor(townByIndex(hubIndex + 1).get)}${if raised > 0 then s", and $raised new Guard(s) take the plaza" else ""}.")
              .withStyle(ChatFormatting.GREEN)
          )
          s"The barracks stands: $placed stones set for $BarracksGold gold (${gold - BarracksGold} left). Its weapon rack is visible from the plaza, and the hub's watch charter rises by one."

  /** A small guardhold: floor, arms-rack wall, anvil, lantern, and a sword on the rack. */
  private def placeBarracks(level: ServerLevel, hub: Town): Int =
    val stand = barracksStand(hub)
    var placed = 0
    def set(pos: BlockPos, block: Block): Unit =
      if level.setBlock(pos, block.defaultBlockState, 3) then placed += 1
    val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, stand.getX, stand.getZ)
    val floor = new BlockPos(stand.getX, y - 1, stand.getZ)
    // clear the pad first: a barracks that can't stand is not paid for
    val needed = for dx <- -1 to 1; dz <- -1 to 1 yield floor.offset(dx, 1, dz)
    if needed.exists(p => !level.getBlockState(p).isAir && !level.getBlockState(p).canBeReplaced) then return 0
    for dx <- -1 to 1; dz <- -1 to 1 do
      set(floor.offset(dx, 0, dz), Blocks.SMOOTH_STONE)
      set(floor.offset(dx, 1, dz), Blocks.AIR)
    // the arms rack: a wall of the hub's own stone, an anvil, a light, and the sword itself
    for dx <- -1 to 1 do set(floor.offset(dx, 1, -1), Blocks.STONE_BRICKS)
    set(floor.offset(1, 1, 0), Blocks.ANVIL)
    set(floor.offset(0, 1, 0), Blocks.LANTERN)
    val rack = new net.minecraft.world.entity.decoration.ItemFrame(level, floor.offset(-1, 2, -1), Direction.UP)
    rack.setItem(net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD))
    level.addFreshEntity(rack)
    placed

  /** How the realm labels a hub in warp listings and announcements. */
  def townLabel(t: Town): String =
    if t.capital then "Hearthhome, the first town"
    else if t.adopted then s"Village at ${t.base.getX}, ${t.base.getZ}"
    else s"Outpost at ${t.base.getX}, ${t.base.getZ}"

  def townByIndex(n: Int): Option[Town] = towns.lift(n - 1)

  /** Every hub the realm knows about, in the order they were raised. */
  private[aetheronchaos] def allTowns: List[Town] = towns.toList

  /** The hub whose grounds the player is standing at (nearest registered hub). */
  def townNearest(player: ServerPlayer): Option[Town] =
    towns.minByOption(t => player.blockPosition.distSqr(t.base))

  /** Gold a wayside portal asks: one per 20 blocks folded, minimum five. */
  def warpCost(from: Town, to: Town): Int = Formulas.warpToll(math.sqrt(from.base.distSqr(to.base)))

  /** Other hubs reachable from the board the player stands at: (index, label, gold). */
  def warpOffers(player: ServerPlayer): List[(Int, String, Int)] =
    townNearest(player) match
      case None => Nil
      case Some(here) =>
        towns.zipWithIndex.toList.collect {
          case (t, i) if t != here => (i + 1, townLabel(t), warpCost(here, t))
        }

  // --- building -------------------------------------------------------------------

  /** Manual build: /quest town in front of the player. */
  def build(player: ServerPlayer): String =
    val dir = player.getDirection
    val base = player.blockPosition.offset(dir.getStepX * 5, 0, dir.getStepZ * 5)
    player.level match
      case level: ServerLevel =>
        val placed = buildAt(level, base, dir, capital = towns.isEmpty)
        TownStore.save(player.server)
        s"Quest hub built in front of you ($placed blocks). Right-click the LOOM quest board to take quests. Remove it with /quest clear."
      case _ => "Not on a server level."

  /**
   * First-join welcome: if this realm has no town yet, a starter town rises at the
   * hero's spawn point; then the hero is teleported into its plaza and their
   * respawn point is anchored there.
   */
  def greetNewcomer(player: ServerPlayer): Unit =
    if towns.isEmpty then
      player.level match
        case level: ServerLevel =>
          val dir = player.getDirection
          val base = player.blockPosition.offset(-dir.getStepX * 2, 0, -dir.getStepZ * 2)
          buildAt(level, base, dir, capital = true)
          TownStore.save(player.server)
          System.out.println("[aetheron-chaos] starter town raised for the first hero")
        case _ => ()
    capitalTown.foreach { t =>
      val stand = atOf(t.base, t.dir)(2, 0, 1)
      player.setRespawnPosition(Level.OVERWORLD, stand, 0f, true, false)
      player.teleportTo(stand.getX + 0.5, stand.getY.toDouble, stand.getZ + 0.5)
      Game.message(player, "Welcome to your new town! Talk to Questmaster Boralus or right-click the loom board to take quests.", net.minecraft.ChatFormatting.GOLD)
    }

  // --- adopting vanilla villages --------------------------------------------------

  /**
   * Heroes wandering into a vanilla village bring the realm with them: a quest hub
   * (board, vendor, hearth, guards) is grafted onto the village meeting point, which
   * is already an open plaza. The rest of the village is left exactly as the
   * villagers built it. Each village is considered once per session; adopted hubs
   * persist like built towns (repair, guard and turn-in rules all apply).
   */
  def adoptNearbyVillages(server: MinecraftServer): Unit =
    val level = server.overworld()
    if level == null then return
    server.getPlayerList.getPlayers.asScala.foreach { p =>
      if Game.isJoined(p) then
        val start = level.structureManager().getStructureWithPieceAt(p.blockPosition(), StructureTags.VILLAGE)
        if start.isValid then
          val bb = start.getBoundingBox
          val cx = (bb.minX + bb.maxX) / 2
          val cz = (bb.minZ + bb.maxZ) / 2
          val key = new BlockPos(cx, 0, cz).asLong
          if !lookedAtVillages.contains(key) then
            lookedAtVillages += key
            adoptAtMeetingPoint(level, cx, cz, bb, p)
    }

  /** Grafts a hub at the meeting point of one village, if there is ground and no hub already. */
  private def adoptAtMeetingPoint(level: ServerLevel, cx: Int, cz: Int, bb: BoundingBox, seeker: ServerPlayer): Unit =
    val covered = towns.exists { t =>
      val (x1, _, z1, x2, _, z2) = t.region
      cx >= x1 - 16 && cx <= x2 + 16 && cz >= z1 - 16 && cz <= z2 + 16
    }
    if covered then return
    // find the meeting-point ground: scan down from the village roof
    var y = bb.maxY + 1
    while y > bb.minY - 16 && level.getBlockState(new BlockPos(cx, y, cz)).isAir do y -= 1
    if y <= bb.minY - 16 then return
    val dir = Direction.NORTH
    val base = new BlockPos(cx, y, cz + 5) // plaza spans 0..10 forward, -5..5 lateral -> centered on (cx, cz)
    val placed = buildAt(level, base, dir, capital = false, adopted = true)
    TownStore.save(seeker.server)
    Game.broadcast(
      seeker.server,
      Component
        .literal(s"A vanilla village near ${seeker.getName.getString} has joined the realm! A quest board now stands at its meeting point (${cx}, ${cz}).")
        .withStyle(net.minecraft.ChatFormatting.GOLD)
    )
    System.out.println(s"[aetheron-chaos] adopted village at ($cx, $cz) as a quest hub ($placed blocks)")

  /** Places one quest hub (board, vendor, hearth, fences, signs, NPCs) and registers it. */
  private def buildAt(level: ServerLevel, base: BlockPos, dir: Direction, capital: Boolean, adopted: Boolean = false): Int =
    val placed = layout(level, base, dir, repair = false)
    spawnNpc(level, atOf(base, dir)(7, 0, 1), QuestNpcName)
    spawnNpc(level, atOf(base, dir)(4, 4, 1), VendorNpcName)
    spawnNpc(level, atOf(base, dir)(1, -4, 1), GuardNpcName)
    spawnNpc(level, atOf(base, dir)(4, -3, 1), HearthNpcName)
    towns += Town(base, dir, regionOf(base, dir), capital, adopted)
    registerBoardAndVendor(towns.last)
    // a hub is nothing without its watch
    GuardWatch.foundWatch(level, towns.last, towns.size - 1)
    placed

  /**
   * Replaces missing blocks in every registered town from its stored layout - the
   * daily mason's pass for creeper scars and weather. It only fills holes (air
   * where the plan calls for a block): blocks heroes placed or changed themselves
   * are left alone, and NPCs are never duplicated.
   */
  def repairTowns(server: MinecraftServer): Int =
    val level = server.overworld()
    if level == null then return 0
    var restored = 0
    towns.toList.foreach { t => restored += layout(level, t.base, t.dir, repair = true) }
    if restored > 0 then System.out.println(s"[aetheron-chaos] town repair: restored $restored block(s)")
    restored

  /** Places (or in repair mode, re-fills) the plaza, board, hearth, vendor and fittings. */
  private def layout(level: ServerLevel, base: BlockPos, dir: Direction, repair: Boolean): Int =
    def at(f: Int, l: Int, dy: Int): BlockPos = atOf(base, dir)(f, l, dy)

    var placed = 0
    def set(pos: BlockPos, block: Block): Unit =
      val state = block.defaultBlockState
      if repair then
        // repair mode: refill holes only - never remove foreign blocks, never place "air"
        if !state.isAir && level.getBlockState(pos).isAir then
          if level.setBlock(pos, state, 3) then placed += 1
      else if level.setBlock(pos, state, 3) then placed += 1

    def sign(pos: BlockPos, lines: List[String]): Unit =
      set(pos, Blocks.OAK_SIGN)
      if !repair || Option(level.getBlockEntity(pos)).forall(_.isInstanceOf[SignBlockEntity]) then
        level.getBlockEntity(pos) match
          case s: SignBlockEntity =>
            val front = s.getFrontText
            lines.zipWithIndex.foreach { (line, i) => front.setMessage(i, Component.literal(line)) }
            s.setChanged()
          case _ => ()

    // stone plaza with cleared headroom
    for f <- 0 to 10; l <- -5 to 5 do
      set(at(f, l, 0), if (f + l) % 2 == 0 then Blocks.STONE_BRICKS else Blocks.MOSSY_STONE_BRICKS)
      set(at(f, l, 1), Blocks.AIR)
      set(at(f, l, 2), Blocks.AIR)

    // quest board: loom on a pedestal, flanked by signs, banner overhead
    set(at(8, 0, 1), Blocks.SMOOTH_STONE_SLAB)
    set(at(8, 0, 2), Blocks.LOOM)
    sign(at(8, -1, 1), List("QUEST BOARD", "Right-click me", "to take quests", "and turn them in"))
    sign(at(8, 1, 1), List("HERO HALL", "/quest join", "/quest class", "watch for RARE ELITES"))
    set(at(8, 0, 3), Blocks.WHITE_BANNER)

    // hearth (rest and craft) + general vendor
    set(at(5, -3, 1), Blocks.CAMPFIRE)
    set(at(5, 3, 1), Blocks.BREWING_STAND)
    set(at(5, 4, 1), Blocks.BARREL)
    sign(at(4, 4, 1), List("GENERAL VENDOR", "Right-click me", "to spend your gold", "/quest shop"))

    // perimeter posts with lanterns
    for f <- List(0, 10); l <- -5 to 5 by 5 do
      set(at(f, l, 1), Blocks.OAK_FENCE)
      set(at(f, l, 2), Blocks.LANTERN)
    for f <- List(0, 10); l <- List(-5, 5) do
      set(at(f, l, 1), Blocks.OAK_FENCE)
      set(at(f, l, 2), Blocks.LANTERN)

    // welcome signs at the front
    for l <- List(-5, 5) do
      set(at(0, l, 1), Blocks.OAK_FENCE)
      sign(at(0, l, 2), List("WELCOME, ADVENTURER", "Slay monsters -> XP", "Level up -> loot", "/quest board"))

    placed

  /** Spawns a permanent, invulnerable, named nitwit villager (no AI = stands post-like). */
  private def spawnNpc(level: ServerLevel, pos: BlockPos, name: String): Unit =
    EntityType.VILLAGER.create(level) match
      case v: Villager =>
        v.setPos(pos.getX + 0.5, pos.getY.toDouble, pos.getZ + 0.5)
        v.setCustomName(Component.literal(name))
        v.setCustomNameVisible(true)
        v.setNoAi(true)
        v.setPersistenceRequired()
        v.setInvulnerable(true)
        v.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1))
        level.addFreshEntity(v)
      case _ => ()

  /** Removes the most recently built town (or all of them). */
  def clear(player: ServerPlayer, all: Boolean): String =
    if towns.isEmpty then return "No towns to clear."
    val level = player.level
    val targets = if all then towns.toList else List(towns.last)
    var blocks = 0
    targets.foreach { t =>
      val (x1, y1, z1, x2, y2, z2) = t.region
      for x <- x1 to x2; y <- y1 to y2; z <- z1 to z2 do
        val pos = BlockPos(x, y, z)
        if !level.getBlockState(pos).isAir then
          boards -= pos.asLong
          vendors -= pos.asLong
          level.setBlock(pos, Blocks.AIR.defaultBlockState, 3)
          blocks += 1
      boards -= atOf(t.base, t.dir)(8, 0, 2).asLong
      vendors -= atOf(t.base, t.dir)(5, 4, 1).asLong
      towns -= t
    }
    TownStore.save(player.server)
    s"Cleared ${targets.size} town(s) ($blocks blocks). Note: anything built inside the town area was removed too."
