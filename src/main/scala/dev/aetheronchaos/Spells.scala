package dev.aetheronchaos

import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.entity.projectile.Snowball
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

/** A class spell. Learned automatically at unlockLevel. kind: bolt | heal | purify | protect | blink | burst | steal | buff | arrows | wound | stun | fear | mendself.
 *  Notes: heal/mendself damage = HP restored; buff damage = duration in ticks; wound damage = wither ticks;
 *  aoeRadius doubles as blink/charge distance. */
final case class Spell(
    id: String,
    name: String,
    unlockLevel: Int,
    damage: Float, // bolt: damage; heal: HP restored (beyond that it tunes nothing else)
    cooldownTicks: Int,
    element: String, // fire | frost | storm | holy | shadow | arrow (visuals + riders)
    aoeRadius: Double = 0.0, // >0 = bursts in an area on impact/max range
    kind: String = "bolt",
    school: String = "arcane", // arcane (mage) | holy (priest) | shadow (rogue) | hunt (hunter)
    effect: String = "", // rider descriptor: poison | slow | pierce | speed | invis
    count: Int = 1 // arrows: projectiles per cast (volley)
)

/**
 * The spellbooks: arcane bolts for mages, holy support for priests, martial arts for rogues
 * (channeled through any sword), skill-shots for hunters (any bow), brute force for warriors
 * (any axe) and holy guardianship for paladins (any shield). Arcane/holy bolts are
 * real vanilla projectiles (snowballs retextured per element) with the element applied on hit,
 * so any vanilla client sees the particles and the flying bolt; bolts detonate at max range
 * even without a hit (novas). Support spells act on nearby players; rogues blink/burst/steal;
 * hunters fire real arrows.
 */
object Spells:
  private val rng = Random()

  val all: List[Spell] = List(
    // mage - arcane
    Spell("fire-bolt", "Fire Bolt", 1, 3.5f, 60, "fire"),
    Spell("frost-bolt", "Frost Bolt", 5, 3.0f, 70, "frost"),
    Spell("thunder-bolt", "Thunder Bolt", 12, 5.5f, 100, "storm"),
    Spell("flame-nova", "Flame Nova", 15, 4.0f, 180, "fire", 3.5),
    Spell("storm-nova", "Storm Nova", 25, 5.5f, 220, "storm", 4.5),
    // priest - holy
    Spell("holy-smite", "Holy Smite", 1, 2.5f, 70, "holy", kind = "bolt", school = "holy"),
    Spell("heal", "Heal", 3, 6.0f, 110, "holy", kind = "heal", school = "holy"),
    Spell("purify", "Purify", 6, 0f, 160, "holy", kind = "purify", school = "holy"),
    Spell("protection", "Protection", 15, 0f, 400, "holy", kind = "protect", school = "holy"),
    Spell("judgment", "Judgment", 25, 4.0f, 190, "holy", 4.0, kind = "bolt", school = "holy"),
    // rogue - shadow (focus: any sword)
    Spell("shadowstep", "Shadowstep", 1, 0f, 150, "shadow", 8.0, kind = "blink", school = "shadow"),
    Spell("fan-of-knives", "Fan of Knives", 3, 3.5f, 130, "shadow", 3.5, kind = "burst", school = "shadow"),
    Spell("pickpocket", "Pickpocket", 8, 0f, 200, "shadow", 5.0, kind = "steal", school = "shadow"),
    Spell("evasion", "Evasion", 14, 160f, 300, "shadow", kind = "buff", effect = "speed", school = "shadow"),
    Spell("vanish", "Vanish", 22, 130f, 420, "shadow", kind = "buff", effect = "invis", school = "shadow"),
    // hunter - skill shots (focus: any bow)
    Spell("quick-shot", "Quick Shot", 1, 3.0f, 25, "arrow", kind = "arrows", school = "hunt"),
    Spell("serpent-shot", "Serpent Shot", 5, 2.5f, 90, "arrow", kind = "arrows", effect = "poison", school = "hunt"),
    Spell("piercing-shot", "Piercing Shot", 12, 3.0f, 130, "arrow", kind = "arrows", effect = "pierce", school = "hunt"),
    Spell("volley", "Arrow Volley", 18, 2.5f, 220, "arrow", kind = "arrows", count = 5, school = "hunt"),
    Spell("crippling-shot", "Crippling Shot", 25, 4.5f, 160, "arrow", kind = "arrows", effect = "slow", school = "hunt"),
    // warrior - might (focus: any axe; wound damage = wither ticks)
    Spell("charge", "Charge", 1, 1.5f, 140, "might", 6.0, kind = "blink", effect = "charge", school = "might"),
    Spell("deep-wound", "Deep Wound", 3, 100f, 120, "might", kind = "wound", school = "might"),
    Spell("whirlwind", "Whirlwind", 12, 4.0f, 170, "might", 4.0, kind = "burst", school = "might"),
    Spell("intimidating-shout", "Intimidating Shout", 18, 0f, 300, "might", 5.0, kind = "fear", school = "might"),
    Spell("avatar", "Avatar", 25, 200f, 600, "might", kind = "buff", effect = "rage", school = "might"),
    // paladin - crusade (focus: any shield)
    Spell("consecrate", "Consecrate", 1, 2.5f, 120, "holy", 3.0, kind = "burst", school = "crusade"),
    Spell("lay-on-hands", "Lay on Hands", 5, 10.0f, 200, "holy", kind = "mendself", school = "crusade"),
    Spell("hammer-of-justice", "Hammer of Justice", 12, 1.5f, 200, "holy", kind = "stun", school = "crusade"),
    Spell("guardians-blessing", "Guardian's Blessing", 15, 120f, 400, "holy", kind = "buff", effect = "guard", school = "crusade"),
    Spell("divine-storm", "Divine Storm", 25, 5.0f, 240, "holy", 4.5, kind = "burst", school = "crusade")
  )

  /** Spell focus per school: mages a blaze rod, priests a book, rogues any sword, hunters any
   *  bow, warriors any axe, paladins a shield. */
  def isFocus(school: String, stack: ItemStack): Boolean =
    !stack.isEmpty && (school match
      case "arcane"  => stack.is(Items.BLAZE_ROD)
      case "holy"    => stack.is(Items.BOOK)
      case "shadow"  => stack.is(ItemTags.SWORDS)
      case "hunt"    => stack.is(ItemTags.BOW_ENCHANTABLE)
      case "might"   => stack.is(ItemTags.AXES)
      case "crusade" => stack.is(Items.SHIELD)
      case _         => false
    )

  val NegativeEffects = List(
    MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.BLINDNESS,
    MobEffects.CONFUSION, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER,
    MobEffects.HUNGER, MobEffects.LEVITATION, MobEffects.DARKNESS, MobEffects.GLOWING
  )

  def schoolFor(classId: String): String = classId match
    case "mage"    => "arcane"
    case "priest"  => "holy"
    case "rogue"   => "shadow"
    case "hunter"  => "hunt"
    case "warrior" => "might"
    case "paladin" => "crusade"
    case _         => ""

  def byId(id: String): Option[Spell] = all.find(_.id == id)

  def learnedFor(school: String, level: Int): List[Spell] =
    all.filter(s => s.school == school && s.unlockLevel <= level)

  /** Spells that just unlocked at this exact level (for level-up announcements). */
  def newlyLearnedAt(school: String, level: Int): List[Spell] =
    all.filter(s => s.school == school && s.unlockLevel == level)

  private def particleFor(element: String) = element match
    case "fire"   => ParticleTypes.FLAME
    case "frost"  => ParticleTypes.SNOWFLAKE
    case "holy"   => ParticleTypes.END_ROD
    case "shadow" => ParticleTypes.PORTAL
    case _        => ParticleTypes.ELECTRIC_SPARK

  /** Extra rider applied after a hit lands, per spell descriptor. */
  private def riderFor(spec: Spell, t: net.minecraft.world.entity.LivingEntity): Unit =
    spec.effect match
      case "poison" => t.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0))
      case "slow"   => t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 1))
      case _        => ()

  private def boltItemFor(element: String): ItemStack = element match
    case "fire"  => ItemStack(Items.BLAZE_POWDER)
    case "frost" => ItemStack(Items.SNOWBALL)
    case "holy"  => ItemStack(Items.GLOWSTONE_DUST)
    case _       => ItemStack(Items.AMETHYST_SHARD)

  private def castPitch(element: String): Float = element match
    case "fire"  => 1.0f
    case "frost" => 1.6f
    case "holy"  => 1.3f
    case _       => 0.6f

  /** Entry point: bolts fly, support spells act on the caster's surroundings. */
  def cast(caster: ServerPlayer, spell: Spell): Unit =
    spell.kind match
      case "heal"   => castHeal(caster, spell)
      case "purify" => castPurify(caster, spell)
      case "protect" => castProtect(caster, spell)
      case "blink"    => castBlink(caster, spell)
      case "burst"    => castBurst(caster, spell)
      case "steal"    => castSteal(caster, spell)
      case "buff"     => castBuff(caster, spell)
      case "arrows"   => castArrows(caster, spell)
      case "wound"    => castWound(caster, spell)
      case "stun"     => castStun(caster, spell)
      case "fear"     => castFear(caster, spell)
      case "mendself" => castMendSelf(caster, spell)
      case _          => castBolt(caster, spell)

  /** Launches the bolt from the caster's eyes toward the crosshair. */
  private def castBolt(caster: ServerPlayer, spell: Spell): Unit =
    caster.level match
      case sl: ServerLevel =>
        val look = caster.getLookAngle
        val eye = caster.getEyePosition()
        val bolt = new Bolt(caster, spell, sl)
        bolt.setPos(eye.x + look.x * 0.6, eye.y - 0.1 + look.y * 0.6, eye.z + look.z * 0.6)
        bolt.setDeltaMovement(look.x * 1.4, look.y * 1.4 + 0.04, look.z * 1.4)
        sl.addFreshEntity(bolt)
        sl.sendParticles(particleFor(spell.element), eye.x + look.x, eye.y + look.y, eye.z + look.z, 6, 0.05, 0.05, 0.05, 0.01)
        sl.playSound(null, caster, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5f, castPitch(spell.element))
      case _ => ()

  /** Heal: mends the ally closest to the crosshair; falls back to the caster themselves. */
  private def castHeal(caster: ServerPlayer, spell: Spell): Unit =
    val target = aimAlly(caster).getOrElse(caster)
    val healed = target.getMaxHealth - target.getHealth
    target.setHealth(math.min(target.getMaxHealth, target.getHealth + spell.damage))
    caster.level match
      case sl: ServerLevel =>
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX, target.getY + 1.0, target.getZ, 20, 0.4, 0.6, 0.4, 0.1)
        sl.playSound(null, target, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f)
        Game.message(caster, s"You heal ${target.getDisplayName.getString} (+${math.min(healed, spell.damage).toInt} HP).", ChatFormatting.GREEN)
      case _ => ()

  /** Purify: cleanses negative effects from the caster and nearby allies. */
  private def castPurify(caster: ServerPlayer, spell: Spell): Unit =
    forEachAlly(caster, 6.0) { ally =>
      NegativeEffects.foreach(ally.removeEffect)
      ally.level match
        case sl: ServerLevel =>
          sl.sendParticles(ParticleTypes.WITCH, ally.getX, ally.getY + 1.2, ally.getZ, 24, 0.5, 0.8, 0.5, 0.0)
        case _ => ()
    }
    caster.level match
      case sl: ServerLevel =>
        sl.playSound(null, caster, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.8f)
        Game.message(caster, "A warm light sweeps over you and your allies - ailments dispelled.", ChatFormatting.AQUA)
      case _ => ()

  /** Protection: Damage Resistance on the caster and nearby allies. */
  private def castProtect(caster: ServerPlayer, spell: Spell): Unit =
    forEachAlly(caster, 6.0) { ally =>
      ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 240, 0, true, true))
      ally.level match
        case sl: ServerLevel =>
          sl.sendParticles(ParticleTypes.END_ROD, ally.getX, ally.getY + 1.0, ally.getZ, 16, 0.4, 0.8, 0.4, 0.02)
        case _ => ()
    }
    caster.level match
      case sl: ServerLevel =>
        sl.playSound(null, caster, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.4f)
        Game.message(caster, "A shield of light settles over your group (Damage Resistance 12s).", ChatFormatting.AQUA)
      case _ => ()

  /** Runs f for the caster plus every nearby player. */
  private def forEachAlly(caster: ServerPlayer, radius: Double)(f: ServerPlayer => Unit): Unit =
    caster.level match
      case sl: ServerLevel =>
        val box = new AABB(caster.position(), caster.position()).inflate(radius)
        f(caster)
        val nearby = sl.getEntitiesOfClass(classOf[ServerPlayer], box, (p: ServerPlayer) => p != caster && p.isAlive)
        nearby.forEach((p: ServerPlayer) => f(p))
      case _ => ()

  /** The living ally whose body sits closest to where the caster is aiming. */
  private def aimAlly(caster: ServerPlayer): Option[ServerPlayer] =
    caster.level match
      case sl: ServerLevel =>
        val eye = caster.getEyePosition()
        val look = caster.getLookAngle
        val box = new AABB(eye, eye).inflate(14)
        val candidates = sl.getEntitiesOfClass(classOf[ServerPlayer], box, (p: ServerPlayer) => p != caster && p.isAlive).asScala.toList
        candidates
          .map { p =>
            val v = p.position().subtract(eye)
            val along = v.dot(look)
            val perp = v.subtract(look.scale(along)).length()
            (if along >= 0 then perp else 100.0 + p.distanceToSqr(caster), p)
          }
          .sortBy(_._1)
          .headOption
          .map(_._2)
      case _ => None

  // --- rogue martial arts & hunter skill shots ---------------------------------------

  /** Shadowstep: blink toward the crosshair, landing on the furthest clear spot. */
  private def castBlink(caster: ServerPlayer, spell: Spell): Unit =
    caster.level match
      case sl: ServerLevel =>
        val look = caster.getLookAngle
        val from = caster.position()
        var landing: Option[Vec3] = None
        var d = spell.aoeRadius.toInt
        while landing.isEmpty && d >= 1 do
          val cand = new Vec3(from.x + look.x * d, from.y + math.max(-1.0, math.min(1.5, look.y * d)), from.z + look.z * d)
          if standingRoom(sl, cand) then landing = Some(cand)
          else d -= 1
        landing match
          case Some(p) =>
            sl.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 20, 0.3, 0.8, 0.3, 0.01)
            caster.teleportTo(p.x, p.y, p.z)
            val landingFx = if spell.effect == "charge" then ParticleTypes.CRIT else ParticleTypes.PORTAL
            sl.sendParticles(landingFx, p.x, p.y + 1.0, p.z, 20, 0.3, 0.8, 0.3, 0.01)
            if spell.effect == "charge" then
              // slam: everyone close to the landing point gets shoved and tagged
              val box = new AABB(p, p).inflate(2.2)
              val foes = sl.getEntitiesOfClass(classOf[Monster], box, (e: Monster) => e.isAlive)
              foes.forEach((e: Monster) =>
                val push = e.position().subtract(p).normalize()
                e.push(push.x * 0.9, 0.35, push.z * 0.9)
                applyHit(caster, spell, caster, e)
              )
              sl.playSound(null, p.x, p.y, p.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5f, 1.3f)
            else sl.playSound(null, caster, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.6f)
          case None =>
            Game.message(caster, s"${spell.name} finds no clear landing - aim at open ground.", ChatFormatting.GRAY)
      case _ => ()

  /** True when the 1.8-tall box above this spot is all air. */
  private def standingRoom(sl: ServerLevel, at: Vec3): Boolean =
    val it = BlockPos
      .betweenClosed(
        Math.floor(at.x - 0.3).toInt, Math.floor(at.y).toInt, Math.floor(at.z - 0.3).toInt,
        Math.floor(at.x + 0.3).toInt, Math.floor(at.y + 1.8).toInt, Math.floor(at.z + 0.3).toInt
      )
      .iterator()
    var clear = true
    while clear && it.hasNext do
      if !sl.getBlockState(it.next()).isAir then clear = false
    clear

  /** Fan of Knives: an instant burst of blade-work around the caster. */
  private def castBurst(caster: ServerPlayer, spell: Spell): Unit =
    caster.level match
      case sl: ServerLevel =>
        val c = caster.position()
        val box = new AABB(c, c).inflate(spell.aoeRadius)
        val foes = sl.getEntitiesOfClass(classOf[Monster], box, (e: Monster) => e.isAlive)
        foes.forEach((e: Monster) => applyHit(caster, spell, caster, e))
        val fx = if spell.element == "holy" then ParticleTypes.END_ROD else ParticleTypes.CRIT
        sl.sendParticles(fx, c.x, c.y + 1.2, c.z, 30, spell.aoeRadius * 0.7, 0.5, spell.aoeRadius * 0.7, 0.1)
        sl.playSound(null, caster, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 1.5f)
      case _ => ()

  /** Pickpocket: coin from the pockets of nearby foes. */
  private def castSteal(caster: ServerPlayer, spell: Spell): Unit =
    caster.level match
      case sl: ServerLevel =>
        val c = caster.position()
        val box = new AABB(c, c).inflate(spell.aoeRadius)
        val near = sl.getEntitiesOfClass(classOf[Monster], box, (e: Monster) => e.isAlive).asScala.toList
        var coins = 0
        var picked = 0
        near.foreach { m =>
          if rng.nextInt(100) < 60 then
            coins += 1 + rng.nextInt(3)
            picked += 1
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX, m.getY + 1.0, m.getZ, 6, 0.2, 0.3, 0.2, 0.0)
        }
        if coins > 0 then
          Game.giveCoins(caster, coins)
          Game.message(caster, s"Quick hands: $picked pockets rifled, $coins gold pocketed.", ChatFormatting.GOLD)
        else
          Game.message(caster, "Empty pockets all around - get closer to more foes.", ChatFormatting.GRAY)
      case _ => ()

  /** Evasion / Vanish / Guardian's Blessing / Avatar: short tricks on the caster themselves. */
  private def castBuff(caster: ServerPlayer, spell: Spell): Unit =
    val ticks = spell.damage.toInt
    spell.effect match
      case "speed" =>
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, true, true))
        Game.message(caster, "Evasion - your feet blur.", ChatFormatting.AQUA)
      case "invis" =>
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, true, true))
        caster.setHealth(math.min(caster.getMaxHealth, caster.getHealth + 2))
        Game.message(caster, "Vanish - you slip out of every eye's reach.", ChatFormatting.AQUA)
      case "guard" =>
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 2, true, true))
        Game.message(caster, "A guardian's light hardens around you.", ChatFormatting.AQUA)
      case "rage" =>
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1, true, true))
        Game.message(caster, "Your blows turn savage.", ChatFormatting.RED)
      case _ => ()
    val fx = spell.effect match
      case "guard" => ParticleTypes.END_ROD
      case "rage"  => ParticleTypes.CRIT
      case _       => ParticleTypes.PORTAL
    caster.level match
      case sl: ServerLevel =>
        sl.sendParticles(fx, caster.getX, caster.getY + 1.0, caster.getZ, 24, 0.4, 0.9, 0.4, 0.01)
        if spell.effect == "invis" then
          sl.playSound(null, caster, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 1.8f)
      case _ => ()

  /** Deep Wound: the foe nearest the crosshair withers, bleeding freely. */
  private def castWound(caster: ServerPlayer, spell: Spell): Unit =
    aimFoe(caster, 7.0) match
      case Some(t) =>
        t.addEffect(new MobEffectInstance(MobEffects.WITHER, spell.damage.toInt, 0, true, true))
        t.level match
          case sl: ServerLevel =>
            sl.sendParticles(ParticleTypes.CRIT, t.getX, t.getY + 1.2, t.getZ, 14, 0.3, 0.4, 0.3, 0.1)
          case _ => ()
        Game.message(caster, s"${t.getDisplayName.getString} bleeds from a deep wound.", ChatFormatting.YELLOW)
      case None =>
        Game.message(caster, "No foe within reach of your blade.", ChatFormatting.GRAY)

  /** Hammer of Justice: the aimed foe is stunned in place. */
  private def castStun(caster: ServerPlayer, spell: Spell): Unit =
    aimFoe(caster, 8.0) match
      case Some(t) =>
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 5, true, true))
        applyHit(caster, spell, caster, t)
        t.level match
          case sl: ServerLevel =>
            sl.sendParticles(ParticleTypes.END_ROD, t.getX, t.getY + 1.6, t.getZ, 10, 0.2, 0.4, 0.2, 0.02)
          case _ => ()
        Game.message(caster, s"${t.getDisplayName.getString} is struck down - stopped in its tracks.", ChatFormatting.AQUA)
      case None =>
        Game.message(caster, "Aim the hammer at a foe in reach.", ChatFormatting.GRAY)

  /** Intimidating Shout: nearby foes break and flee. */
  private def castFear(caster: ServerPlayer, spell: Spell): Unit =
    caster.level match
      case sl: ServerLevel =>
        val c = caster.position()
        val box = new AABB(c, c).inflate(spell.aoeRadius)
        val foes = sl.getEntitiesOfClass(classOf[Monster], box, (m: Monster) => m.isAlive)
        foes.forEach((m: Monster) =>
          val push = m.position().subtract(c).normalize()
          m.push(push.x * 1.1, 0.3, push.z * 1.1)
          m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0, true, true))
          if m.getTarget == caster then m.setTarget(null)
        )
        sl.sendParticles(ParticleTypes.CRIT, c.x, c.y + 1.2, c.z, 20, 1.2, 0.6, 1.2, 0.15)
        sl.playSound(null, caster, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7f, 1.2f)
        if !foes.isEmpty then
          Game.message(caster, s"${foes.size} foes break and flee before you!", ChatFormatting.GOLD)
        else
          Game.message(caster, "Only the wind heeds your shout.", ChatFormatting.GRAY)
      case _ => ()

  /** Lay on Hands: mend yourself. */
  private def castMendSelf(caster: ServerPlayer, spell: Spell): Unit =
    val healed = math.min(spell.damage.toDouble, caster.getMaxHealth - caster.getHealth)
    caster.setHealth(math.min(caster.getMaxHealth, caster.getHealth + spell.damage))
    caster.level match
      case sl: ServerLevel =>
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, caster.getX, caster.getY + 1.0, caster.getZ, 20, 0.4, 0.6, 0.4, 0.1)
        sl.playSound(null, caster, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 0.9f)
        Game.message(caster, s"You lay hands on yourself (+${healed.toInt} HP).", ChatFormatting.GREEN)
      case _ => ()

  /** The hostile closest to where the caster is aiming, within range along the sight line. */
  private def aimFoe(caster: ServerPlayer, range: Double): Option[Monster] =
    caster.level match
      case sl: ServerLevel =>
        val eye = caster.getEyePosition()
        val look = caster.getLookAngle
        val box = new AABB(eye, eye).inflate(range)
        sl.getEntitiesOfClass(classOf[Monster], box, (m: Monster) => m.isAlive).asScala.toList
          .map { m =>
            val v = m.position().subtract(eye)
            val along = v.dot(look)
            val perp = v.subtract(look.scale(along)).length()
            (if along >= 0 && along <= range then perp else 1000.0 + m.distanceToSqr(caster), m)
          }
          .sortBy(_._1)
          .headOption
          .map(_._2)
      case _ => None

  /** Skill-shot: one real arrow, or a fanned volley. */
  private def castArrows(caster: ServerPlayer, spell: Spell): Unit =
    caster.level match
      case sl: ServerLevel =>
        val look = caster.getLookAngle
        val eye = caster.getEyePosition()
        val n = spell.count
        for k <- 0 until n do
          val dir = rotateY(look, (k - (n - 1) / 2.0) * 0.13)
          val arrow = new BoltArrow(caster, spell, sl)
          arrow.setPos(eye.x + dir.x * 0.5, eye.y - 0.1, eye.z + dir.z * 0.5)
          arrow.shoot(dir.x, dir.y, dir.z, 3.0f, if n > 1 then 2.0f else 0.0f)
          sl.addFreshEntity(arrow)
        sl.playSound(null, caster, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.6f, 1.0f)
      case _ => ()

  private def rotateY(v: Vec3, a: Double): Vec3 =
    val cos = math.cos(a)
    val sin = math.sin(a)
    new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos)

  /** Shared hit application for every damaging spell (bolts, novas, bursts, arrows). */
  private def applyHit(caster: ServerPlayer, spec: Spell, source: net.minecraft.world.entity.Entity, target: net.minecraft.world.entity.Entity): Unit =
    target match
      case t: Monster if t.isAlive && t != source =>
        val src = caster.damageSources.indirectMagic(source, caster)
        if t.hurt(src, spec.damage) then
          spec.element match
            case "fire" =>
              t.setRemainingFireTicks(100)
            case "frost" =>
              t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1))
            case "storm" =>
              val push = t.position().subtract(caster.position()).normalize()
              t.push(push.x * 0.5, 0.35, push.z * 0.5)
            case _ => ()
          riderFor(spec, t)
      case _ => ()

  /** The hunter's skill-shot: a real arrow; Piercing Shot keeps flying through foes. */
  final class BoltArrow(val caster: ServerPlayer, val spec: Spell, val sl: ServerLevel)
      extends Arrow(EntityType.ARROW, sl):
    private var age = 0
    private var hits = 0
    private val maxHits = if spec.effect == "pierce" then 6 else 1

    setOwner(caster)
    pickup = AbstractArrow.Pickup.CREATIVE_ONLY

    override def tick(): Unit =
      super.tick()
      age += 1
      if age > 160 then discard()

    override protected def onHitEntity(hit: EntityHitResult): Unit =
      if hits < maxHits then
        hits += 1
        applyHit(caster, spec, this, hit.getEntity)
        if hits >= maxHits then discard()

  /** The flying bolt: a vanilla snowball with our element applied on impact or at max range. */
  final class Bolt(val caster: ServerPlayer, val spec: Spell, val sl: ServerLevel)
      extends Snowball(sl, caster):
    private var age = 0

    setItem(boltItemFor(spec.element))

    override def tick(): Unit =
      super.tick()
      age += 1
      val p = position()
      sl.sendParticles(particleFor(spec.element), p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.0)
      if age >= MaxAge then
        if spec.aoeRadius > 0 then burst(p)
        else fizz()
        discard()

    override protected def onHitEntity(hit: EntityHitResult): Unit =
      val p = hit.getEntity.position()
      if spec.aoeRadius > 0 then burst(p)
      else applyHit(caster, spec, Bolt.this, hit.getEntity)
      super.onHitEntity(hit) // lets vanilla discard the projectile

    /** Everything hostile within the nova radius takes the spell. */
    private def burst(center: Vec3): Unit =
      val box = new AABB(center, center).inflate(spec.aoeRadius)
      sl.getEntitiesOfClass(classOf[Monster], box, (e: Monster) => e.isAlive).forEach(e => applyHit(caster, spec, Bolt.this, e))
      sl.sendParticles(particleFor(spec.element), center.x, center.y + 0.3, center.z, 24, spec.aoeRadius * 0.6, 0.6, spec.aoeRadius * 0.6, 0.04)
      sl.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, castPitch(spec.element) * 0.8f)

    private def fizz(): Unit =
      val p = position()
      sl.sendParticles(particleFor(spec.element), p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.02)

    private def MaxAge = 28 // ~40 blocks at bolt speed
