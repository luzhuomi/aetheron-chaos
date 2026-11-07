package dev.azerothchaos

import scala.collection.mutable

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerData
import net.minecraft.world.entity.npc.VillagerProfession
import net.minecraft.world.entity.npc.VillagerType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity

/**
 * Builds small quest hubs ("villages"): a quest board (loom + signs) where quests
 * are accepted and turned in, a hearth, a vendor barrel, torch-lit fences and
 * permanent town NPCs (named nitwit villagers: a questmaster, a merchant, guards).
 * Registered boards gate quest turn-ins.
 *
 * The first hero to join a realm gets a starter town built at their spawn point
 * and respawns there from then on. Towns persist to azeroth-chaos-towns.snbt so
 * board registrations survive restarts.
 */
object TownBuilder:
  type Region = (Int, Int, Int, Int, Int, Int)
  /** A built town: anchor block, facing, footprint and whether it is the realm's starter town. */
  final case class Town(base: BlockPos, dir: Direction, region: Region, capital: Boolean)

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

  /** Role of a right-clicked entity for the interaction hook: board/vendor/guard. */
  def npcRole(entity: Entity): Option[String] =
    entity match
      case v: Villager if v.getCustomName != null =>
        v.getCustomName.getString match
          case QuestNpcName  => Some("board")
          case VendorNpcName => Some("vendor")
          case GuardNpcName | HearthNpcName => Some("guard")
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
          System.out.println("[azeroth-chaos] starter town raised for the first hero")
        case _ => ()
    capitalTown.foreach { t =>
      val stand = atOf(t.base, t.dir)(2, 0, 1)
      player.setRespawnPosition(Level.OVERWORLD, stand, 0f, true, false)
      player.teleportTo(stand.getX + 0.5, stand.getY.toDouble, stand.getZ + 0.5)
      Game.message(player, "Welcome to your new town! Talk to Questmaster Boralus or right-click the loom board to take quests.", net.minecraft.ChatFormatting.GOLD)
    }

  /** Places one quest hub (board, vendor, hearth, fences, signs, NPCs) and registers it. */
  private def buildAt(level: ServerLevel, base: BlockPos, dir: Direction, capital: Boolean): Int =
    def at(f: Int, l: Int, dy: Int): BlockPos = atOf(base, dir)(f, l, dy)

    var placed = 0
    def set(pos: BlockPos, block: Block): Unit =
      if level.setBlock(pos, block.defaultBlockState, 3) then placed += 1

    def sign(pos: BlockPos, lines: List[String]): Unit =
      set(pos, Blocks.OAK_SIGN)
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

    // permanent town NPCs
    spawnNpc(level, at(7, 0, 1), QuestNpcName)
    spawnNpc(level, at(4, 4, 1), VendorNpcName)
    spawnNpc(level, at(1, -4, 1), GuardNpcName)
    spawnNpc(level, at(4, -3, 1), HearthNpcName)

    towns += Town(base, dir, regionOf(base, dir), capital)
    registerBoardAndVendor(towns.last)
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
