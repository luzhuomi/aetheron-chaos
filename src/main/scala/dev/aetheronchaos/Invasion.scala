package dev.aetheronchaos

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.levelgen.Heightmap

/**
 * Invasion nights: now and then a war band marches on one of the realm's hubs and tries
 * it for five minutes. Waves arrive just outside the town's ground; every hostile a hero
 * drives off is counted on a shared siege bar, and the ones who held the line are paid
 * when the horns blow. The scheduler runs from Game.tick; kill crediting rides on
 * Game.awardKill, so spells, shots and swords all count the same.
 */
object Invasion:
  private val rng = Random()

  // --- tuning -----------------------------------------------------------------
  private val FirstSiegeTicks = 12 * 60 * 20L // first siege ~12 minutes into the run
  private val SiegeGapTicks = 30 * 60 * 20L // ~30 minutes between sieges (jittered)
  private val SiegeJitterMinutes = 8
  private val SiegeMinutes = 5 // the siege itself lasts five minutes
  private val SiegeTicks = SiegeMinutes * 60L * 20L
  private val WaveGapTicks = 30 * 20L // a fresh wave every 30 seconds
  private val MaxWaveSize = 8
  private val CrowdCap = 14 // never pile up more than this many on the field
  private val KillBonusGold = 2 // paid out as it happens
  // a named captain leads every third wave: named means the rare rules pay for it
  private val CaptainEveryWaves = 3
  private val CaptainHealth = 2.6
  private val CaptainAttack = 1.5
  private val CaptainBonusGold = 8
  private val CaptainThinsWave = 2 // a captain that falls arrives thin afterwards
  private val RavagerCaptainWave = 6 // a ravager rides with the last wave
  private val warBandCaptains: List[String] = List(
    "Ashra of the Riven Axe",
    "Vesk the Quartermaster",
    "Three-Winter Ost",
    "Shieldbiter",
    "Halberd-Mother Brenna",
    "The Quiet Conductor"
  )
  private val RewardBaseGold = 15
  private val RewardGoldPerKill = 4
  private val RewardBaseXp = 150
  private val RewardXpPerKill = 30
  private val SigilKills = 5 // held the line this well and the forger wants you

  /** War-band roster. Nothing named: a siege captain would read as a rare elite. */
  private val warBand: List[net.minecraft.world.entity.EntityType[?]] = List(
    net.minecraft.world.entity.EntityType.PILLAGER,
    net.minecraft.world.entity.EntityType.ZOMBIE,
    net.minecraft.world.entity.EntityType.SKELETON,
    net.minecraft.world.entity.EntityType.SPIDER,
    net.minecraft.world.entity.EntityType.VINDICATOR,
    net.minecraft.world.entity.EntityType.WITCH
  )

  /** One siege in progress at a time: the realm only fights one war at once. */
  private final class Siege(val town: TownBuilder.Town, val label: String, val level: ServerLevel):
    val endTick = Game.now + SiegeTicks
    val bar = new ServerBossEvent(
      Component.literal(s"Siege of $label").withStyle(ChatFormatting.RED),
      BossEvent.BossBarColor.RED,
      BossEvent.BossBarOverlay.NOTCHED_6
    )
    var nextWaveTick = Game.now
    var wave = 0
    var drivenOff = 0
    var captainsFelled = 0
    val mobs = mutable.LinkedHashSet[Mob]()
    val captains = mutable.LinkedHashSet[Mob]()
    val captainNames = mutable.LinkedHashSet[String]()
    val kills = mutable.LinkedHashMap[UUID, Int]()
    refreshBar()

    def liveCaptain: Option[Mob] = captains.find(m => m.isAlive && !m.isRemoved)

    def refreshBar(): Unit =
      val left = math.max(0L, endTick - Game.now)
      val captainNote = liveCaptain.map(c => s" - ${c.getName.getString} leads them").getOrElse("")
      bar.setName(
        Component
          .literal(s"Siege of $label - $drivenOff driven off, ${mmss(left)} left$captainNote")
          .withStyle(ChatFormatting.RED)
      )
      bar.setProgress(math.max(0.0f, left.toFloat / SiegeTicks.toFloat))

  private var siege: Option[Siege] = None
  private var nextSiegeTick = -1L

  // --- scheduler --------------------------------------------------------------

  /** Called from Game.tick: runs the live siege, then starts the next one when due. */
  private[aetheronchaos] def tickScheduler(server: MinecraftServer): Unit =
    if nextSiegeTick < 0 then nextSiegeTick = Game.now + FirstSiegeTicks
    siege.foreach(s => tickSiege(server, s))
    if siege.isEmpty && Game.now >= nextSiegeTick then
      nextSiegeTick = Game.now + SiegeGapTicks + (rng.nextInt(SiegeJitterMinutes * 2 + 1) - SiegeJitterMinutes) * 60L * 20L
      val online = heroes(server)
      if online.nonEmpty then startSiege(server, nearestHub(online))

  private def tickSiege(server: MinecraftServer, s: Siege): Unit =
    s.mobs.filterInPlace(m => m.isAlive && !m.isRemoved)
    heroes(server).foreach(p => s.bar.addPlayer(p)) // heroes who logged in mid-siege see it too
    if Game.now >= s.endTick then endSiege(server)
    else
      if Game.now >= s.nextWaveTick then
        s.nextWaveTick = Game.now + WaveGapTicks
        if s.mobs.size < CrowdCap then spawnWave(server, s)
      s.refreshBar()

  // --- starting a siege -------------------------------------------------------

  /** Ops: bring the war band to a hub right now (`/quest invade [n]`). */
  def summon(player: ServerPlayer, index: Int): String =
    val towns = TownBuilder.allTowns
    if towns.isEmpty then return "There is no town for a war band to march on. Build one: /quest town"
    val hub =
      if index > 0 then TownBuilder.townByIndex(index).orElse(towns.headOption)
      else nearestHub(heroes(player.server))
    startAt(player.server, hub)

  /** From the server console: no hero to be near, so the war band takes the capital. */
  def summonAt(server: MinecraftServer, index: Int): String =
    val towns = TownBuilder.allTowns
    if towns.isEmpty then return "There is no town for a war band to march on."
    val hub =
      if index > 0 then TownBuilder.townByIndex(index).orElse(towns.headOption)
      else towns.find(_.capital).orElse(towns.headOption)
    startAt(server, hub)

  private def startAt(server: MinecraftServer, hub: Option[TownBuilder.Town]): String =
    if siege.nonEmpty then endSiege(server) // the first war band withdraws properly before the next arrives
    startSiege(server, hub)
    siege match
      case Some(s) => s"The war band is on the road to ${s.label}."
      case None => "The war band could not reach that ground."

  private def startSiege(server: MinecraftServer, hub: Option[TownBuilder.Town]): Unit =
    val level = server.overworld()
    (hub, level) match
      case (Some(t), lvl) if lvl != null =>
        val s = new Siege(t, hubLabel(t), lvl)
        siege = Some(s)
        spawnWave(server, s)
        val (x1, _, z1, x2, _, z2) = t.region
        Game.broadcast(
          server,
          Component
            .literal(
              s"A WAR BAND marches on ${hubLabel(t)}! Hold the ground around ${x1}..${x2}, ${z1}..${z2} for $SiegeMinutes minutes - every hostile you drive off counts, and the ones who stand are paid."
            )
            .withStyle(ChatFormatting.RED)
        )
        lvl.players().asScala.foreach { p =>
          p.playNotifySound(SoundEvents.GOAT_HORN_PLAY, SoundSource.HOSTILE, 1.0f, 0.6f)
        }
      case _ => ()

  /** Which registered hub the online heroes are closest to - a siege nobody can reach is noise. */
  private def nearestHub(online: Seq[ServerPlayer]): Option[TownBuilder.Town] =
    val towns = TownBuilder.allTowns
    if towns.isEmpty || online.isEmpty then None
    else Some(towns.minBy(t => online.map(p => p.blockPosition.distSqr(t.base)).min))

  // --- the waves ------------------------------------------------------------

  private def spawnWave(server: MinecraftServer, s: Siege): Unit =
    s.wave += 1
    val nearby = defenders(s)
    val size = math.max(2, math.min(MaxWaveSize, 2 + s.wave + nearby.size / 2 - CaptainThinsWave * s.captainsFelled))
    (1 to size).foreach(_ => spawnHostile(s, s.wave, captain = false))
    if s.wave % CaptainEveryWaves == 0 then
      val captain = spawnHostile(s, s.wave, captain = true)
      s.captains.filterInPlace(m => m.isAlive && !m.isRemoved)
      captain.foreach { m =>
        Game.broadcast(
          server,
          Component
            .literal(s"${m.getName.getString} rides at the head of wave ${s.wave}. Take the captain down and the war band arrives thin after it.")
            .withStyle(ChatFormatting.LIGHT_PURPLE)
        )
      }
    s.mobs.filterInPlace(m => m.isAlive && !m.isRemoved)
    if s.wave > 1 then
      nearby.foreach { h =>
        Game.message(h, s"Wave ${s.wave} of the war band arrives at ${s.label}.", ChatFormatting.RED)
      }

  /** Spawns one hostile; a captain is named and scaled (and rare rules pay for it). */
  private def spawnHostile(s: Siege, wave: Int, captain: Boolean): Option[Mob] =
    val (x1, _, z1, x2, _, z2) = s.town.region
    val cx = (x1 + x2 + 1) / 2.0
    val cz = (z1 + z2 + 1) / 2.0
    val ang = rng.nextDouble() * 2 * Math.PI
    val radius = 15.0 + rng.nextInt(11)
    val bx = cx + math.cos(ang) * radius
    val bz = cz + math.sin(ang) * radius
    val kind =
      if captain then captainKind(s.wave).asInstanceOf[net.minecraft.world.entity.EntityType[Mob]]
      else warBand(rng.nextInt(warBand.size)).asInstanceOf[net.minecraft.world.entity.EntityType[Mob]]
    val by = s.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx.floor.toInt, bz.floor.toInt) + 1.0
    kind.create(s.level) match
      case null => None
      case m =>
        m.setPos(bx, by, bz)
        m.finalizeSpawn(s.level, s.level.getCurrentDifficultyAt(m.blockPosition), MobSpawnType.EVENT, null)
        // later waves arrive harder - a war band does not send its worst first
        val toughen = (1.0 + 0.15 * (wave - 1)) * (if captain then CaptainHealth else 1.0)
        Option(m.getAttribute(Attributes.MAX_HEALTH)).foreach { a =>
          a.setBaseValue(a.getBaseValue * toughen)
          m.setHealth(a.getValue.toFloat)
        }
        if captain then
          Option(m.getAttribute(Attributes.ATTACK_DAMAGE)).foreach(a => a.setBaseValue(a.getBaseValue * CaptainAttack))
          s.captainNames += nextCaptainName(s)
          m.setCustomName(Component.literal(s.captainNames.last))
          m.setCustomNameVisible(true)
          s.captains += m
        s.level.addFreshEntityWithPassengers(m)
        s.mobs += m
        // they came for the town, and they take the nearest hero who stands in the way
        defenders(s).filter(h => h.distanceToSqr(m) < 625.0) match
          case nearest :: _ => m.setTarget(nearest)
          case Nil => m.getNavigation.moveTo(cx, by, cz, 1.0)
        Some(m)

  private def captainKind(wave: Int): net.minecraft.world.entity.EntityType[?] =
    if wave >= RavagerCaptainWave then net.minecraft.world.entity.EntityType.RAVAGER
    else
      val pool = List(
        net.minecraft.world.entity.EntityType.VINDICATOR,
        net.minecraft.world.entity.EntityType.VINDICATOR,
        net.minecraft.world.entity.EntityType.WITCH,
        net.minecraft.world.entity.EntityType.PILLAGER
      )
      pool(rng.nextInt(pool.size))

  private def nextCaptainName(s: Siege): String =
    val fresh = warBandCaptains.filterNot(s.captainNames.contains)
    val pick = if fresh.isEmpty then warBandCaptains else fresh
    s"Captain ${pick(rng.nextInt(pick.size))}"

  /** Where the war band is, for quest compasses. */
  private[aetheronchaos] def fieldPosition: Option[(net.minecraft.world.phys.Vec3, String)] =
    siege.map { s =>
      val (x1, _, z1, x2, _, z2) = s.town.region
      (net.minecraft.world.phys.Vec3((x1 + x2 + 1) / 2.0, s.town.base.getY.toDouble, (z1 + z2 + 1) / 2.0), "the war band")
    }

  // --- kill crediting (from Game.awardKill) -----------------------------------

  /** Counts a siege kill for the hero who did it and pays the small spot bonus. */
  private[aetheronchaos] def onKill(player: ServerPlayer, victim: Mob): Unit =
    siege.foreach { s =>
      if s.mobs.remove(victim) then
        s.drivenOff += 1
        val scored = s.kills.getOrElse(player.getUUID, 0) + 1
        s.kills(player.getUUID) = scored
        Game.giveCoins(player, KillBonusGold)
        Game.creditWarBandKill(player)
        if s.captains.remove(victim) then
          s.captainsFelled += 1
          Game.creditCaptainFell(player)
          Game.giveCoins(player, CaptainBonusGold)
          defenders(s).foreach { h =>
            Game.message(h, s"${victim.getName.getString} falls. The war band falters - its next wave arrives thin.", ChatFormatting.LIGHT_PURPLE)
          }
        else if scored % 5 == 0 then
          Game.message(player, s"${s.label}: $scored of the war band driven off.", ChatFormatting.GOLD)
        s.refreshBar()
    }

  // --- ending a siege ---------------------------------------------------------

  private def endSiege(server: MinecraftServer): Unit =
    val s = siege.getOrElse {
      siege = None
      return
    }
    siege = None
    s.bar.setProgress(0.0f)
    s.bar.removeAllPlayers()
    val leftfield = s.mobs.toList // still standing when the horns blew: the war band withdraws
    s.mobs.clear()
    leftfield.foreach { m =>
      if m.isAlive && !m.isRemoved then
        m.discard()
        s.level.sendParticles(ParticleTypes.POOF, m.getX, m.getY + 1.0, m.getZ, 12, 0.3, 0.5, 0.3, 0.05)
    }
    if leftfield.nonEmpty then
      val base = s.town.base
      s.level.playSound(null, base.getX.toDouble, base.getY.toDouble, base.getZ.toDouble, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.6f)

    val headline =
      if s.drivenOff == 0 then s"The war band walked up to ${s.label} and nobody stood. The town keeps its sheep, and its grudges."
      else if s.drivenOff < 4 then s"The siege of ${s.label} ends with the fences scorched - only ${s.drivenOff} of the war band driven off."
      else
        val captainNote = if s.captainsFelled > 0 then s" ${s.captainsFelled} captain(s) down." else ""
        s"The siege of ${s.label} is HELD. ${s.drivenOff} of the war band driven off across ${s.wave} waves.$captainNote"
    Game.broadcast(
      server,
      Component.literal(headline).withStyle(if s.drivenOff == 0 then ChatFormatting.GRAY else ChatFormatting.GREEN)
    )
    s.kills.toList.foreach { (uuid, scored) =>
      Option(server.getPlayerList.getPlayer(uuid)).filter(Game.isJoined).foreach { hero =>
        val gold = RewardBaseGold + RewardGoldPerKill * scored
        val xp = RewardBaseXp + RewardXpPerKill * scored
        Game.creditSiegeHeld(hero)
        Game.giveCoins(hero, gold)
        Game.addXp(hero, xp.toLong)
        val proof =
          if scored >= SigilKills then
            Game.giveItem(hero, Loot.sigilStack())
            " A Sigil of the Elite is yours - the forger notices a siege held."
          else ""
        Game.message(hero, s"Held the line at ${s.label}: $scored driven off - $gold gold and $xp XP for it.$proof", ChatFormatting.GOLD)
      }
    }
    if s.drivenOff > 0 then
      val stayed = s.kills.keySet
      val watched = heroes(server).count(p => !stayed.contains(p.getUUID) && p.distanceToSqr(s.town.base.getCenter) < 3600)
      if watched > 0 then
        Game.broadcast(
          server,
          Component.literal(s"$watched stood well away from the walls. The town remembers who came.").withStyle(ChatFormatting.DARK_GRAY)
        )

  // --- helpers ---------------------------------------------------------------

  private def heroes(server: MinecraftServer): List[ServerPlayer] =
    server.getPlayerList.getPlayers.asScala.filter(Game.isJoined).toList

  /** Heroes defending this siege: joined, alive and on the ground. */
  private def defenders(s: Siege): List[ServerPlayer] =
    heroes(s.level.getServer).filter(p => p.isAlive && p.distanceToSqr(s.town.base.getCenter) < 120 * 120)

  private def hubLabel(t: TownBuilder.Town): String =
    if t.capital then "Hearthhome" else TownBuilder.townLabel(t)

  private def mmss(ticks: Long): String =
    val secs = ticks / 20L
    s"${secs / 60}:${"%02d".format(secs % 60)}"
