package dev.aetheronchaos

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * Rare-elite lifecycle: the spawn scheduler, the live registry (so a party wipe can
 * end a fight instead of letting players grind it down), and the spawn itself.
 * The rare table lives in Enemies.scala; XP crediting stays in Game.awardKill.
 */
object GameRares:
  private val rng = Random()
  private var nextRareTick = 200L // first rare appears 10 seconds into the server run
  // live rare elites, so a party wipe can end the fight instead of letting players grind it down
  private val activeRares = mutable.LinkedHashSet[Mob]()

  /** Called from Game.tick: spawns the next scheduled rare near a random online hero. */
  private[aetheronchaos] def tickScheduler(server: MinecraftServer): Unit =
    if Game.now >= nextRareTick then
      nextRareTick = Game.now + Game.RareIntervalTicks
      val online = server.getPlayerList.getPlayers.asScala.filter(Game.isJoined)
      if online.nonEmpty then
        val target = online.toSeq(rng.nextInt(online.size))
        spawnRare(target, None)

  /** Called from Game.awardKill when a rare dies by player hands. */
  private[aetheronchaos] def forget(victim: Mob): Unit = activeRares -= victim

  def spawnRare(player: ServerPlayer, nameOpt: Option[String]): String =
    val chosen = nameOpt.flatMap(n => Enemies.rares.find(_.name.equalsIgnoreCase(n)))
      .getOrElse(Enemies.rares(rng.nextInt(Enemies.rares.size)))
    player.level match
      case serverLevel: ServerLevel =>
        val factory = chosen.entityType.asInstanceOf[net.minecraft.world.entity.EntityType[Mob]]
        Option(factory.create(serverLevel)) match
          case None => "The rare refused to appear here."
          case Some(m) =>
            val ang = rng.nextDouble() * 2 * Math.PI
            val dist = 14 + rng.nextInt(10)
            m.setPos(player.getX + math.cos(ang) * dist, player.getY + 2, player.getZ + math.sin(ang) * dist)
            m.setCustomName(Component.literal(chosen.name))
            m.setCustomNameVisible(true)
            Option(m.getAttribute(Attributes.MAX_HEALTH)).foreach { a =>
              a.setBaseValue(a.getBaseValue * chosen.healthMult)
              m.setHealth(a.getValue.toFloat)
            }
            Option(m.getAttribute(Attributes.ATTACK_DAMAGE)).foreach(a => a.setBaseValue(a.getBaseValue * chosen.attackMult))
            serverLevel.addFreshEntity(m)
            activeRares += m
            Game.broadcast(
              player.server,
              Component
                .literal(s"A RARE ELITE has appeared: ${chosen.name}, near ${player.getName.getString}! Slay it for 10x XP and guaranteed loot.")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
            )
            player.playNotifySound(SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.9f)
            s"Spawned rare elite: ${chosen.name}"
      case _ => "Not on a server level."

  /** A wipe is not a grind: any rare the group was actually fighting fades away when the
   *  whole group is down (solo death counts as a wipe of a party of one). */
  private[aetheronchaos] def dropRaressOnWipe(player: ServerPlayer): Unit =
    activeRares.filterInPlace(r => r.isAlive && !r.isRemoved)
    if activeRares.isEmpty then return
    val c = Game.ensure(player.getUUID)
    val group: List[ServerPlayer] = c.party match
      case Some(p) =>
        Game.partyMembers(p).flatMap(u => Option(player.server.getPlayerList.getPlayer(u)))
      case None => List(player)
    if group.exists(_.isAlive) then return // someone is still standing to fight it
    activeRares.toList.foreach { r =>
      val engaged = group.exists(g => r.getTarget == g || r.distanceToSqr(g) < 900) // targeting or within 30 blocks
      if engaged then
        activeRares -= r
        r.discard()
        r.level match
          case sl: ServerLevel =>
            sl.sendParticles(ParticleTypes.POOF, r.getX, r.getY + 1.0, r.getZ, 25, 0.4, 0.6, 0.4, 0.05)
            sl.playSound(null, r, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.7f)
          case _ => ()
        Game.broadcast(
          player.server,
          Component
            .literal(s"${r.getName.getString}, sensing the party's fall, fades back into the wilds.")
            .withStyle(ChatFormatting.DARK_GRAY)
        )
    }
