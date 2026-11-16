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
  private val AssistThreatFactor = 0.3 // a party buff shares the target's notice, not the fight
  private val RetargetRangeSqr = 40.0 * 40.0 // heroes further out don't get the mob's attention
  private val ReadoutRangeSqr = 30.0 * 30.0 // /quest threat: what the hero can actually see
  private def DefenderWindowTicks: Long = (Tuning.getDouble("threat.defender_window_seconds", 5.0) * 20).toLong
  private def DefenderBonus: Double = Tuning.getDouble("threat.defender_bonus", 1.5) // ...and their strikes count half again as loud on the table

  // mob -> (hero it just turned away from, tick the window closes)
  private val justLost = mutable.Map[Mob, (UUID, Long)]()

  // Some mobs keep score differently. A scale of 0 means the mob never does - pure vanilla.
  private val MobThreatScale: Map[net.minecraft.world.entity.EntityType[?], Double] = Map(
    net.minecraft.world.entity.EntityType.RAVAGER -> 0.6, // a ravager keeps the ground it chose
    net.minecraft.world.entity.EntityType.WITCH -> 1.4, // witches remember who annoyed them
    net.minecraft.world.entity.EntityType.PHANTOM -> 1.25, // phantoms nurse grudges
    net.minecraft.world.entity.EntityType.CREEPER -> 0.0 // creepers never keep score at all
  )

  private def scaleFor(mob: Mob): Double = MobThreatScale.getOrElse(mob.getType, 1.0)

  /** Damage from a hero (melee, bow, spell - the damage source's true owner). */
  def onDamage(mob: Mob, hero: ServerPlayer, amount: Float): Unit =
    if amount > 0.0f then
      val s = scaleFor(mob)
      if s > 0.0 then
        // the defender's benefit: a mob that just turned away from you hears you louder, briefly
        val defender = justLost.get(mob) match
          case Some((lost, until)) if lost == hero.getUUID && Game.now < until => DefenderBonus
          case _ => 1.0
        tables.getOrElseUpdate(mob, mutable.Map())(hero.getUUID) += amount.toDouble * s * defender

  /** A hero healed the mob's current target: the mender earns the mob's notice. */
  def onHeal(healed: ServerPlayer, healer: ServerPlayer, amount: Double): Unit =
    if amount <= 0.0 || healer == healed then return
    tables.foreach { (mob, table) =>
      if mob.getTarget == healed then
        table(healer.getUUID) += amount * HealThreatFactor * scaleFor(mob)
    }

  /** A party buff landed on an ally: the one who strengthens them shares the mob's notice. */
  def onAssist(assister: ServerPlayer, ally: ServerPlayer): Unit =
    if assister == ally then return
    tables.foreach { (mob, table) =>
      if mob.getTarget == ally then
        table.get(ally.getUUID).foreach { v =>
          table(assister.getUUID) = table.getOrElse(assister.getUUID, 0.0) + v * AssistThreatFactor
        }
    }

  /** Taunt / Challenge: instant top of the table, and the mob hears it. */
  def onTaunt(mob: Mob, hero: ServerPlayer): Unit =
    if scaleFor(mob) <= 0.0 then return
    tables.getOrElseUpdate(mob, mutable.Map())(hero.getUUID) += TauntThreat
    mob.setTarget(hero)

  /** Fear: the mob stops keeping score entirely. */
  def forget(mob: Mob): Unit =
    tables -= mob
    justLost -= mob

  /** `/quest threat` - every table within a hero's sight, read the way the mob sees it. */
  def readout(player: ServerPlayer): String =
    val engaged = tables.iterator
      .filter { (mob, table) => mob.isAlive && !mob.isRemoved && table.nonEmpty && mob.distanceToSqr(player) <= ReadoutRangeSqr }
      .toList
      .sortBy { (mob, _) => mob.distanceToSqr(player) }
      .take(6)
    if engaged.isEmpty then
      "Nothing within thirty blocks is keeping score. The threat table only fills once steel, spell or mending touches it."
    else
      val rows = engaged.map { (mob, table) =>
        val ranked = table.toList.sortBy(-_._2)
        val watching = Option(mob.getTarget).map(_.getName.getString).getOrElse("nothing in particular"
        )
        val mine = table.get(player.getUUID)
        val top = ranked.head
        val stance = mine match
          case None => "you: not on the table"
          case Some(v) =>
            val rank = ranked.indexWhere(_._1 == player.getUUID) + 1
            val pct = if top._2 > 0.0 then (v / top._2 * 100).toInt else 0
            if rank == 1 then s"you: LEAD at ${fmt(v)} - the mob is yours"
            else if pct >= (100.0 / Hysteresis).toInt then s"you: #$rank at ${fmt(v)} - close enough to take it with a taunt"
            else s"you: #$rank at ${fmt(v)} (${pct}% of the leader)"
        val leader = s"${nameOf(player, top._1)} ${fmt(top._2)}"
        val window = justLost.get(mob).exists { (lost, until) => lost == player.getUUID && Game.now < until }
        val windowNote = if window then " - the defender's window is open: your strikes count half again" else ""
        s"  ${mob.getName.getString} @ ${mob.distanceToSqr(player).toInt}m - watching: $watching; leader: $leader; $stance$windowNote"
      }
      val header = "The realm's grudges, near to far:"
      val footer = "Taunts slam you to the top (and recharge on their own track); healing a hero who holds aggro pulls notice too; a mob that just turned away from you hears you louder for a moment; ravagers dig in and creepers never keep score."
      (List(header) ::: rows ::: List(footer)).mkString("\n")

  /** The on-HUD tell: a mob near this hero that they were holding has turned away.
   *  Not "every mob with a table" (that would be a wall of noise) - only *lost* aggro. */
  def lostAggroTo(player: ServerPlayer): Option[String] =
    tables.iterator
      .filter { (mob, table) => mob.isAlive && !mob.isRemoved && table.contains(player.getUUID) && mob.distanceToSqr(player) <= 20.0 * 20.0 }
      .flatMap { (mob, table) =>
        val holding = heroIdentity(mob.getTarget).contains(player.getUUID)
        val leading = table.maxBy((_, v) => v)._1
        if !holding && leading != player.getUUID then Some(s"${mob.getName.getString}->${nameOf(player, leading)}")
        else None
      }
      .toSeq
      .sortBy(_.length)
      .headOption

  private def fmt(v: Double): String = if v >= 10.0 then v.toInt.toString else f"$v%.1f"

  private def nameOf(player: ServerPlayer, uuid: UUID): String =
    Option(player.server.getPlayerList.getPlayer(uuid)).map(_.getName.getString)
      .orElse(CharacterStore.readAll(player.server).get(uuid).map(_.getString("name")))
      .getOrElse("an absent hero")

  /** Called from Game.tick every couple of seconds: decay, clean up, reassign attention. */
  def tick(server: MinecraftServer): Unit =
    if tables.isEmpty then return
    tables.toList.foreach { (mob, table) =>
      if !mob.isAlive || mob.isRemoved then
        tables -= mob
        justLost -= mob
      else
        justLost.get(mob).foreach { (_, until) => if Game.now >= until then justLost -= mob }
        table.transform((_, v) => v * DecayPerSweep)
        table.filterInPlace((_, v) => v >= MinThreat)
        if table.isEmpty then tables -= mob
        else
          val current = heroIdentity(mob.getTarget)
          current.foreach { held => // whoever took it back closes their own window
            justLost.get(mob).foreach { (lost, _) => if lost == held then justLost -= mob }
          }
          val (topUuid, topScore) = table.maxBy((_, v) => v)
          val currentScore = current.flatMap(table.get).getOrElse(0.0)
          if topScore > currentScore * Hysteresis && !current.contains(topUuid) then
            Option(server.getPlayerList.getPlayer(topUuid)).foreach { hero =>
              if hero.isAlive && hero.distanceToSqr(mob) <= RetargetRangeSqr then
                mob.setTarget(hero)
                current.foreach { lost => justLost(mob) = (lost, Game.now + DefenderWindowTicks) }
            }
    }

  private def heroIdentity(e: LivingEntity): Option[UUID] = e match
    case p: Player => Some(p.getUUID)
    case _ => None
