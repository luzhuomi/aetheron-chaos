package dev.aetheronchaos

import java.util.UUID
import scala.collection.mutable

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer

/**
 * The spellbook half of the realm state: equipping, casting, cooldowns and the
 * spell menu. Spell definitions and the casting effects live in Spells.scala;
 * this object owns the per-hero casting state (which spell is equipped, when the
 * focus is ready again) and the command-facing text.
 */
object GameSpells:
  // spell cooldowns: uuid -> tick the focus is ready again
  private val spellCooldowns = mutable.Map[UUID, Long]()
  // taunts recharge on their own track (see tryCast): a tank's rhythm, not the spellbook's
  private val tauntCooldowns = mutable.Map[UUID, Long]()
  // throttle for "hold your focus" hints: uuid -> tick of the last hint
  private val spellNags = mutable.Map[UUID, Long]()

  /** Spells that just unlocked on a level-up - or the class trial that stands between
   *  the hero and the spell (WoW Classic style: earn the big spells). */
  private[aetheronchaos] def announceNewSpells(player: ServerPlayer, c: Game.Character, fromLevel: Int): Unit =
    val school = Spells.schoolFor(c.classId)
    if school.isEmpty then return
    val done = c.done.toSet
    Quests.all.filter(q => q.forClass == c.classId && q.minLevel == c.level).foreach { q =>
      val spellNote = Spells.all.find(_.gate == q.id).map(s => s" Complete it to learn ${s.name}.").getOrElse("")
      Game.message(player, s"A trial awaits: '${q.name}' now waits on the quest board.$spellNote", ChatFormatting.GOLD)
    }
    Spells.all
      .filter(s =>
        s.school == school && s.unlockLevel > fromLevel && s.unlockLevel <= c.level && (s.gate.isEmpty || done.contains(s.gate))
      )
      .foreach { s =>
        Game.message(player, s"New spell learned: ${s.name}! Right-click with your focus to cast it, /quest spell to equip.", ChatFormatting.LIGHT_PURPLE)
      }

  /** The spell a hero currently has equipped (falls back to the first known one). */
  private[aetheronchaos] def equippedSpell(c: Game.Character): Spell =
    val school = Spells.schoolFor(c.classId)
    Spells.byId(c.spell).filter(s => s.school == school && s.unlockLevel <= c.level && (s.gate.isEmpty || c.done.contains(s.gate)))
      .orElse(Spells.learnedFor(school, c.level, c.done.toSet).headOption)
      .getOrElse(Spells.all.head)

  /** Right-click cast / sneak+right-click cycle. Returns true if the click was consumed. */
  private[aetheronchaos] def castOrCycle(player: ServerPlayer, hand: net.minecraft.world.InteractionHand): Boolean =
    val c = Game.ensure(player.getUUID)
    val school = Spells.schoolFor(c.classId)
    if !c.joined || school.isEmpty then return false
    // only the hand being used matters: carrying a wand in the bag must not block eating,
    // placing or using anything else. Mirror vanilla's choice: an empty main hand uses the off-hand.
    val primary = player.getItemInHand(hand)
    val used =
      if primary.isEmpty && hand == net.minecraft.world.InteractionHand.MAIN_HAND then player.getOffhandItem
      else primary
    if !Spells.isFocus(school, used) then
      if used.isEmpty then
        // empty-hand right-click: a nudge about the focus, at most once per 10s
        if Game.now >= spellNags.getOrElse(c.uuid, 0L) then
          spellNags(c.uuid) = Game.now + 200
          val hint = school match
            case "holy"    => "A prayer book is your holy focus - hold any book (your Book of Radiance works) to cast."
            case "shadow"  => "Your blades are your focus - hold any sword to call on your martial arts."
            case "hunt"    => "Your bow is your focus - hold a bow to fire skill-shots."
            case "might"   => "An axe focuses your might - hold any axe (the vendor sells one)."
            case "crusade" => "Your shield is your prayer focus - hold any shield to call down blessings."
            case _         => "A wand is your spell focus - hold any blaze rod to cast. Your other items stay usable."
          Game.message(player, hint, ChatFormatting.GRAY)
      return false
    if player.isCrouching then
      val known = Spells.learnedFor(school, c.level, c.done.toSet)
      if known.size < 2 then
        Game.message(player, s"Casting ${equippedSpell(c).name}. Learn more spells by leveling up.", ChatFormatting.GRAY)
        return true
      val current = known.indexWhere(_.id == c.spell)
      val next = known((current + 1) % known.size)
      c.spell = next.id
      Game.message(player, s"Equipped: ${next.name} - right-click to cast.", ChatFormatting.LIGHT_PURPLE)
      return true
    val spell = equippedSpell(c)
    // taunts keep a tank's own rhythm: they recharge on their own track, never locking
    // the rest of the spellbook, and the spellbook never locks them (per-class values
    // live in Spells.scala - the warrior's taunt comes back faster than the paladin's challenge)
    val tauntTrack = spell.kind == "taunt"
    val track = if tauntTrack then tauntCooldowns else spellCooldowns
    val cdTicks = Tuning.getInt(s"spell.cooldown.${spell.id}", spell.cooldownTicks)
    val readyAt = track.getOrElse(c.uuid, 0L)
    if Game.now < readyAt then
      Game.message(player, s"${spell.name} is still recharging... ${(readyAt - Game.now + 19) / 20}s", ChatFormatting.GRAY)
      return true
    track(c.uuid) = Game.now + cdTicks
    if !tauntTrack then player.getCooldowns.addCooldown(used.getItem, cdTicks) // the focus visibly greys out
    Spells.cast(player, spell)
    true

  private[aetheronchaos] def spellList(player: ServerPlayer): String =
    val c = Game.ensure(player.getUUID)
    val school = Spells.schoolFor(c.classId)
    if school.isEmpty then return "That class has no spellbook. /quest roles to see classes."
    val known = Spells.learnedFor(school, c.level, c.done.toSet)
    val focusName = school match
      case "holy"    => "book focus"
      case "shadow"  => "sword focus (any blade)"
      case "hunt"    => "bow focus (any bow)"
      case "might"   => "axe focus (any axe)"
      case "crusade" => "shield focus (any shield)"
      case _         => "wand (blaze rod) focus"
    val lines = known.map { s =>
      val mark = if s.id == equippedSpell(c).id then "*" else " "
      val effect = s.kind match
        case "heal"    => s"mend up to ${s.damage.toInt} HP (aim at an ally - healing allies grants you XP)"
        case "purify"  => "cleanse ailments from nearby allies"
        case "protect" => "Damage Resistance 12s for nearby allies"
        case "blink"   =>
          if s.effect == "charge" then s"charge up to ${s.aoeRadius.toInt} blocks, slamming foes on landing"
          else s"blink up to ${s.aoeRadius.toInt} blocks toward your aim"
        case "burst"   => s"${s.damage} dmg to all foes around you (${s.aoeRadius} blocks)"
        case "steal"   => s"steal coin from foes within ${s.aoeRadius.toInt} blocks"
        case "buff"    =>
          if s.effect == "speed" then s"Speed II for ${s.damage.toInt / 20}s"
          else if s.effect == "invis" then s"invisibility ${s.damage.toInt / 20}s + a little mending"
          else if s.effect == "guard" then s"Damage Resistance (hard to hurt) for ${s.damage.toInt / 20}s"
          else s"harder hits (+damage) for ${s.damage.toInt / 20}s"
        case "wound"    => s"open a bleeding wound: withers the aimed foe for ${s.damage.toInt / 20}s"
        case "stun"     => s"${s.damage} dmg + stuns the aimed foe in place ~3s"
        case "fear"     => s"scare foes within ${s.aoeRadius.toInt} blocks into fleeing"
        case "mendself" => s"mend up to ${s.damage.toInt} HP on yourself"
        case "taunt"    =>
          if s.effect == "single" then "taunt the aimed foe - it must come at you"
          else s"challenge all foes within ${s.aoeRadius.toInt} blocks to battle you"
        case "arrows"  =>
          val rider = s.effect match
            case "poison" => ", poisons"
            case "slow"   => ", hobbles the target"
            case "pierce" => ", flies through up to 6 foes"
            case _        => ""
          val shots = if s.count > 1 then s"fires ${s.count} arrows" else "one arrow"
          s"${s.damage} dmg - $shots$rider"
        case _ if s.aoeRadius > 0 => s"${s.damage} dmg to all foes within ${s.aoeRadius.toInt} blocks"
        case _         => s"${s.damage} dmg bolt"
      s"$mark ${s.name} (Lv${s.unlockLevel}) - $effect"
    }
    val locked = Spells.all.filter(s => s.school == school && s.unlockLevel > c.level)
    val trialsPending = Spells.sealedFor(school, c.level, c.done.toSet)
    (List(s"Spellbook - right-click with your $focusName to cast, sneak+right-click to cycle:") ++ lines ++
      (if locked.nonEmpty then List("Locked: " + locked.map(s => s"${s.name} (Lv${s.unlockLevel})").mkString(", ")) else Nil) ++
      (if trialsPending.nonEmpty then
        List("Trials pending: " + trialsPending
          .map(s => s"${s.name} - complete '${Quests.byId(s.gate).map(_.name).getOrElse("your class trial")}'")
          .mkString("; "))
      else Nil))
      .mkString("\n")

  private[aetheronchaos] def equipSpell(player: ServerPlayer, id: String): String =
    val c = Game.ensure(player.getUUID)
    val school = Spells.schoolFor(c.classId)
    if school.isEmpty then return "That class has no spellbook."
    Spells.byId(id) match
      case None =>
        val hint = Spells.all.find(s => s.school == school && Game.editDistance(s.id.toLowerCase, id.toLowerCase) <= 3)
          .map(s => s" Did you mean ${s.id}?").getOrElse("")
        s"Unknown spell '$id'.$hint See /quest spell."
      case Some(s) if s.school != school => s"${s.name} is a ${s.school} spell - not in your book."
      case Some(s) if s.unlockLevel > c.level => s"${s.name} unlocks at level ${s.unlockLevel} (you are ${c.level})."
      case Some(s) if s.gate.nonEmpty && !c.done.contains(s.gate) =>
        s"${s.name} is still sealed - complete '${Quests.byId(s.gate).map(_.name).getOrElse("your class trial")}' at the quest board first."
      case Some(s) =>
        c.spell = s.id
        s"Equipped: ${s.name}."
