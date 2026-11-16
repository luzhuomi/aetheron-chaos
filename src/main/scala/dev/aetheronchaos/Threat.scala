package dev.aetheronchaos

import java.util.UUID
import scala.collection.mutable

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player

/**
 * The threat table: who a mob ought to be angry at, kept server-side.
 * Damage builds threat, healing the mob's target pulls threat on the mender,
 * taunts slam the hero to the top, fear wipes the slate. Every couple of seconds
 * each engaged mob turns to whoever leads its table - but only when that hero is
 * clearly ahead (hysteresis), so targets never flicker. Mobs with no threat data
 * are left entirely to vanilla AI.
 */
object Threat:
  // mob -> (hero uuid -> threat score)
  private val tables = mutable.Map[Mob, mutable.Map[UUID, Double]]()

  // Tuning knobs
  private val DecayPerSweep = 0.8 // threat softens every sweep (~2s)
  private val MinThreat = 2.0 // below this, nobody is making them angry
  private val Hysteresis = 1.3 // a challenger must lead by 30% to steal the mob
  private val TauntThreat = 60.0 // a taunt is a shout, not a suggestion
  private val HealThreatFactor = 0.8 // healing pulls aggro a touch less than damage per HP
  private val RetargetRangeSqr = 40.0 * 40.0 // heroes further out don't get the mob's attention

  /** Damage from a hero (melee, bow, spell - the damage source's true owner). */
  def onDamage(mob: Mob, hero: ServerPlayer, amount: Float): Unit =
    if amount > 0.0f then
      tables.getOrElseUpdate(mob, mutable.Map())(hero.getUUID) += amount.toDouble

  /** A hero healed the mob's current target: the mender earns the mob's notice. */
  def onHeal(healed: ServerPlayer, healer: ServerPlayer, amount: Double): Unit =
    if amount <= 0.0 || healer == healed then return
    tables.foreach { (mob, table) =>
      if mob.getTarget == healed then
        table(healer.getUUID) += amount * HealThreatFactor
    }

  /** Taunt / Challenge: instant top of the table, and the mob hears it. */
  def onTaunt(mob: Mob, hero: ServerPlayer): Unit =
    tables.getOrElseUpdate(mob, mutable.Map())(hero.getUUID) += TauntThreat
    mob.setTarget(hero)

  /** Fear: the mob stops keeping score entirely. */
  def forget(mob: Mob): Unit = tables -= mob

  /** Called from Game.tick every couple of seconds: decay, clean up, reassign attention. */
  def tick(server: MinecraftServer): Unit =
    if tables.isEmpty then return
    tables.toList.foreach { (mob, table) =>
      if !mob.isAlive || mob.isRemoved then tables -= mob
      else
        table.transform((_, v) => v * DecayPerSweep)
        table.filterInPlace((_, v) => v >= MinThreat)
        if table.isEmpty then tables -= mob
        else
          val current = heroIdentity(mob.getTarget)
          val (topUuid, topScore) = table.maxBy((_, v) => v)
          val currentScore = current.flatMap(table.get).getOrElse(0.0)
          if topScore > currentScore * Hysteresis && !current.contains(topUuid) then
            Option(server.getPlayerList.getPlayer(topUuid)).foreach { hero =>
              if hero.isAlive && hero.distanceToSqr(mob) <= RetargetRangeSqr then mob.setTarget(hero)
            }
    }

  private def heroIdentity(e: LivingEntity): Option[UUID] = e match
    case p: Player => Some(p.getUUID)
    case _ => None
