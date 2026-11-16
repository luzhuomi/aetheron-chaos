package dev.aetheronchaos

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.item.Items
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB

/**
 * The realm's watch. Every hub keeps a small company of named iron golems: they stand on the
 * plaza, answer the horn when a war band marches on their town, and can fall doing it. A hub
 * that loses guards stays undermanned - the company is not re-raised for free until the mason's
 * daily round, or until a hero pays to re-man it.
 *
 * The company is the world, not a table: the golems are ordinary entities, so they survive a
 * restart and are re-adopted onto the roll by name at the next muster. Session membership is a
 * uuid -> hub map and every strength number is derived from it, so there is no counter to corrupt.
 */
object GuardWatch:
  val GuardName = "Watch-Guard"
  val RemanGold = 40

  private def GuardHealthScale = Tuning.getDouble("guard.health_scale", 0.6) // the town's watch is sturdy, not a raid boss
  private def GuardAttackScale = Tuning.getDouble("guard.attack_scale", 0.7)
  // charter sizes live in Formulas.guardCharter (capital 3 / hub 2 / +1 with a barracks)
  private val LeashBlocks = 55.0 // a guard who wanders this far is walked home
  private val MusterIntervalTicks = 200L

  // live members this session: entity uuid -> hub index
  private val members = mutable.LinkedHashMap[UUID, Int]()
  private var nextMusterTick = -1L
  // the roll is only made up once the world is actually loaded around a hero
  private var rollPending = true

  /** How many guards a hub is entitled to keep. A barracks raises the charter by one. */
  def charterFor(hub: TownBuilder.Town): Int = Formulas.guardCharter(hub.capital, hub.barracks)

  /** Fill a hub's watch up to its charter at no cost (a barracks raise, a mason's gift). */
  private[aetheronchaos] def raiseToCharter(server: MinecraftServer, hubIndex: Int): Int =
    val level = server.overworld()
    TownBuilder.townByIndex(hubIndex + 1) match
      case None | Some(_) if level == null => 0
      case Some(hub) =>
        val missing = charterFor(hub) - company(level, hubIndex).size
        if missing <= 0 then 0 else (1 to missing).count(_ => raiseGuard(level, hub, hubIndex) != null)
      case None => 0

  /** Founding a hub raises its watch. */
  private[aetheronchaos] def foundWatch(level: ServerLevel, hub: TownBuilder.Town, hubIndex: Int): Int =
    (1 to charterFor(hub)).count(_ => raiseGuard(level, hub, hubIndex) != null)

  /** Re-manning: a hero pays for the missing guards and they take the plaza now. */
  def reman(player: ServerPlayer, hubIndex: Int): String =
    val server = player.server
    val level = server.overworld()
    TownBuilder.townByIndex(hubIndex + 1) match
      case None => "There is no such hub on the network."
      case Some(hub) if level == null => "No overworld to keep a watch in."
      case Some(hub) =>
        val charter = charterFor(hub)
        val missing = charter - company(level, hubIndex).size
        if missing <= 0 then s"${shortName(hub)}'s watch is at its full strength ($charter). Nothing to re-man."
        else
          val cost = RemanGold * missing
          val gold = Game.countItem(player, Items.GOLD_NUGGET)
          if gold < cost then s"${shortName(hub)} needs $missing more guard(s) at $RemanGold gold each - $cost gold, and you carry only $gold."
          else
            Game.removeItems(player, Items.GOLD_NUGGET, cost)
            val raised = (1 to missing).count(_ => raiseGuard(level, hub, hubIndex) != null)
            Game.broadcast(
              server,
              Component
                .literal(s"${player.getName.getString} pays $cost gold: $raised Watch-Guard(s) take the plaza at ${shortName(hub)}.")
                .withStyle(ChatFormatting.GREEN)
            )
            s"Re-manned $raised of $charter at ${shortName(hub)} for $cost gold (${gold - cost} left)."

  /** The mason's daily round: every hub's watch is re-formed at no cost. */
  private[aetheronchaos] def reform(server: MinecraftServer): Unit =
    if rollPending then makeRoll(server) // adopt what the world still has before topping up
    val level = server.overworld()
    if level == null then return
    TownBuilder.allTowns.zipWithIndex.foreach { (hub, i) =>
      val missing = charterFor(hub) - company(level, i).size
      if missing > 0 then
        val raised = (1 to missing).count(_ => raiseGuard(level, hub, i) != null)
        if raised > 0 then
          Game.broadcast(
            server,
            Component
              .literal(s"The mason's round re-forms the watch at ${shortName(hub)}: $raised Guard(s) back on the plaza.")
              .withStyle(ChatFormatting.GRAY)
          )
    }

  /** The hub this hero is standing at (or the nearest one), as an index into the network. */
  private[aetheronchaos] def hubNear(player: ServerPlayer): Option[Int] =
    val hubs = TownBuilder.allTowns
    hubs.zipWithIndex.minByOption { (t, _) => player.blockPosition.distSqr(t.base) }.filter { (t, _) =>
      player.blockPosition.distSqr(t.base) < 90 * 90
    }.map(_._2)

  /** What one hub's watch looks like, read out to one hero. */
  def statusOf(player: ServerPlayer, hubIndex: Int): String =
    val level = player.server.overworld()
    TownBuilder.townByIndex(hubIndex + 1) match
      case None => "There is no such hub on the network."
      case Some(hub) if level == null => "No overworld to keep a watch in."
      case Some(hub) =>
        val live = company(level, hubIndex).size
        val charter = charterFor(hub)
        val where = if live == 0 then "No guard stands at this hub."
          else if live < charter then s"$live of $charter stand to it - the hub is undermanned by ${charter - live}."
          else s"$live of $charter stand to it - the full watch."
        val price = (charter - live).max(0) * RemanGold
        s"${shortName(hub)}: $where" + (if live < charter then s" To re-man now costs $price gold: /quest guard eman (the mason's round re-forms the watch for free once a day)." else "")

  /** The status a hero reads at the board, from a guard, or with /quest guard. */
  def status(player: ServerPlayer): String =
    val level = player.server.overworld()
    if level == null then return "No overworld to keep a watch in."
    val ranked = TownBuilder.allTowns.zipWithIndex.map { (hub, i) =>
      val live = company(level, i).size
      val charter = charterFor(hub)
      val note =
        if live < charter then s" - short by ${charter - live}, re-man for ${(charter - live) * RemanGold} gold: /quest guard eman"
        else " - at its full strength"
      s"  ${shortName(hub)}: $live of $charter at their posts$note"
    }
    if ranked.isEmpty then "No hub keeps a watch yet. Build one: /quest town"
    else (List("The realm's watch:") ++ ranked).mkString("\n")

  /** The horn: the watch turns toward the fight. Returns how many answered. */
  private[aetheronchaos] def answerTheHorn(level: ServerLevel, hubIndex: Int): Int = company(level, hubIndex).size

  /** A wave arrives: every guard is pointed at the nearest of them. Golems do the rest. */
  private[aetheronchaos] def takeTheField(level: ServerLevel, hubIndex: Int, foes: Iterable[Mob]): Unit =
    val live = company(level, hubIndex)
    if live.isEmpty then return
    val targets = foes.toList.filter(m => m.isAlive && !m.isRemoved)
    if targets.isEmpty then return
    live.foreach { g =>
      targets.minByOption(m => g.distanceToSqr(m)) match
        case Some(foe) if g.distanceToSqr(foe) < 80 * 80 =>
          g.setTarget(foe)
          g.getNavigation.moveTo(foe.getX, foe.getY, foe.getZ, 1.0)
        case _ => ()
    }

  private[aetheronchaos] def isGuard(entity: LivingEntity): Boolean = members.contains(entity.getUUID)

  /** Called from Combat when anything dies: a fallen guard comes off the roll. */
  private[aetheronchaos] def onDeath(victim: LivingEntity): Unit =
    members.remove(victim.getUUID) match
      case None => ()
      case Some(hubIndex) =>
        TownBuilder.townByIndex(hubIndex + 1).foreach { hub =>
          val charter = charterFor(hub)
          val live = victim.level match
            case sl: ServerLevel => company(sl, hubIndex).size
            case _ => 0
          Game.broadcast(
            victim.getServer,
            Component
              .literal(s"A Watch-Guard of ${shortName(hub)} has fallen - the watch stands $live of $charter.")
              .withStyle(ChatFormatting.GRAY)
          )
          victim.level match
            case sl: ServerLevel => sl.playSound(null, victim, SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 0.9f, 0.8f)
            case _ => ()
          // a war band at the walls presses while the town is short
          Invasion.watchFell(victim.getServer, hubIndex)
        }

  /** Muster: strays are walked home, and after a restart the golems are re-adopted. */
  private[aetheronchaos] def tick(server: MinecraftServer): Unit =
    if rollPending && server.getPlayerList.getPlayerCount > 0 then makeRoll(server)
    if nextMusterTick < 0 then nextMusterTick = Game.now + MusterIntervalTicks
    if Game.now < nextMusterTick then return
    nextMusterTick = Game.now + MusterIntervalTicks
    val level = server.overworld()
    if level == null then return
    TownBuilder.allTowns.zipWithIndex.foreach { (hub, i) =>
      val post = TownBuilder.plazaStand(hub)
      company(level, i).foreach { g =>
        val far = g.distanceToSqr(post.getX.toDouble, post.getY.toDouble, post.getZ.toDouble) > LeashBlocks * LeashBlocks
        if far && g.getTarget == null then g.teleportTo(post.getX + 0.5, post.getY.toDouble, post.getZ + 0.5)
      }
    }

  // --- internals --------------------------------------------------------------

  private def raiseGuard(level: ServerLevel, hub: TownBuilder.Town, hubIndex: Int): IronGolem =
    val post = TownBuilder.plazaStand(hub)
    val ang = members.size * 1.9
    val px = post.getX + 0.5 + math.cos(ang) * 1.8
    val pz = post.getZ + 0.5 + math.sin(ang) * 1.8
    val py = level.getHeight(Heightmap.Types.MOTION_BLOCKING, post.getX, post.getZ) + 1.0
    EntityType.IRON_GOLEM.create(level) match
      case g: IronGolem =>
        g.setPos(px, py, pz)
        g.setCustomName(Component.literal(s"$GuardName of ${shortName(hub)}"))
        g.setPersistenceRequired()
        // a hub with a barracks fields better-kept guards
        val healthScale = GuardHealthScale * (if hub.barracks then Tuning.getDouble("barracks.guard_health_bonus", 1.15) else 1.0)
        Option(g.getAttribute(Attributes.MAX_HEALTH)).foreach { a =>
          a.setBaseValue(a.getBaseValue * healthScale)
          g.setHealth(a.getValue.toFloat)
        }
        Option(g.getAttribute(Attributes.ATTACK_DAMAGE)).foreach(a => a.setBaseValue(a.getBaseValue * GuardAttackScale))
        level.addFreshEntity(g)
        members += g.getUUID -> hubIndex
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py + 1.0, pz, 18, 0.4, 0.6, 0.4, 0.01)
        level.playSound(null, px, py, pz, SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 0.7f, 1.1f)
        g
      case _ => null

  /** Every live member of a hub's watch. */
  private def company(level: ServerLevel, hubIndex: Int): List[IronGolem] =
    members.toList.filter(_._2 == hubIndex).flatMap { (uuid, _) =>
      level.getEntity(uuid) match
        case g: IronGolem if g.isAlive && !g.isRemoved => Some(g)
        case _ =>
          members -= uuid
          None
    }

  /** First muster after the world loads: every hub is ensured its company. Hubs raised before
   *  the watch existed get one now, and a hub whose whole company fell is re-formed by the realm. */
  private def enrolLegacyWatches(server: MinecraftServer): Unit =
    val level = server.overworld()
    if level == null then return
    TownBuilder.allTowns.zipWithIndex.foreach { (hub, i) =>
      if company(level, i).isEmpty then
        val raised = foundWatch(level, hub, i)
        if raised > 0 then
          Game.broadcast(
            server,
            Component
              .literal(s"The watch is raised at ${shortName(hub)}: $raised Watch-Guard(s) take the plaza.")
              .withStyle(ChatFormatting.GRAY)
          )
    }

  /** The first time a hero is online, the roll is made up: adopt whatever watch the world still
   *  has, and raise one where a hub has none. Hubs are loaded first - an unloaded plaza reads as
   *  empty, and that would double the company on every restart. */
  private def makeRoll(server: MinecraftServer): Unit =
    rollPending = false
    val level = server.overworld()
    if level == null then return
    TownBuilder.allTowns.foreach(t => level.getChunk(t.base.getX >> 4, t.base.getZ >> 4))
    adoptExisting(server)
    enrolLegacyWatches(server)

  /** After a restart the golems are still in the world: adopt them back onto the roll - up to
   *  the hub's charter. Anything beyond that is just a village golem, and may be adopted later. */
  private def adoptExisting(server: MinecraftServer): Unit =
    val level = server.overworld()
    val hubs = TownBuilder.allTowns
    if level == null || hubs.isEmpty then return
    val regions = hubs.map(_.region)
    val box = new AABB(
      regions.map(_._1).min - 12, regions.map(_._2).min - 24, regions.map(_._3).min - 12,
      regions.map(_._4).max + 12, regions.map(_._5).max + 24, regions.map(_._6).max + 12
    )
    val ours = level.getEntitiesOfClass(classOf[IronGolem], box).asScala
      .filter(g => !members.contains(g.getUUID) && namedOurWatch(g) && g.isAlive && !g.isRemoved)
      .toList
    hubs.zipWithIndex.foreach { (hub, i) =>
      val room = charterFor(hub) - company(level, i).size
      ours.filter(g => nearestHub(hubs, g.blockPosition).contains(i)).take(math.max(0, room))
        .foreach(g => members += g.getUUID -> i)
    }

  private def namedOurWatch(g: IronGolem): Boolean =
    Option(g.getCustomName).exists(_.getString.startsWith(GuardName))

  private def nearestHub(hubs: List[TownBuilder.Town], at: BlockPos): Option[Int] =
    hubs.zipWithIndex.minByOption { (t, _) => at.distSqr(t.base) }.map(_._2)

  private def shortName(hub: TownBuilder.Town): String =
    if hub.capital then "Hearthhome"
    else if hub.adopted then s"the Village at ${hub.base.getX}, ${hub.base.getZ}"
    else s"the Outpost at ${hub.base.getX}, ${hub.base.getZ}"
