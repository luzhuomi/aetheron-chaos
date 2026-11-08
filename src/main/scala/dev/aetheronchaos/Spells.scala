package dev.aetheronchaos

import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Snowball
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

/** A class spell. Learned automatically at unlockLevel. kind: bolt | heal | purify | protect. */
final case class Spell(
    id: String,
    name: String,
    unlockLevel: Int,
    damage: Float, // bolt: damage; heal: HP restored (beyond that it tunes nothing else)
    cooldownTicks: Int,
    element: String, // fire | frost | storm | holy (visuals + riders)
    aoeRadius: Double = 0.0, // >0 = bursts in an area on impact/max range
    kind: String = "bolt",
    school: String = "arcane" // arcane (mage) | holy (priest)
)

/**
 * The spellbooks: arcane bolts for mages, holy spells for priests. Casting is server-side only:
 * bolts are real vanilla projectiles (snowballs retextured per element) with the element applied
 * on hit, so any vanilla client sees the particles and the flying bolt. Bolts detonate at max
 * range even without a hit (novas). holy spells (heal/purify/protect) act on nearby players.
 */
object Spells:
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
    Spell("judgment", "Judgment", 25, 4.0f, 190, "holy", 4.0, kind = "bolt", school = "holy")
  )

  /** Spell focus per school: mages channel through a blaze rod, priests through a book. */
  def focusFor(school: String) = school match
    case "arcane" => Some(Items.BLAZE_ROD)
    case "holy"   => Some(Items.BOOK)
    case _        => None

  val NegativeEffects = List(
    MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.BLINDNESS,
    MobEffects.CONFUSION, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER,
    MobEffects.HUNGER, MobEffects.LEVITATION, MobEffects.DARKNESS, MobEffects.GLOWING
  )

  def schoolFor(classId: String): String = classId match
    case "mage"   => "arcane"
    case "priest" => "holy"
    case _        => ""

  def byId(id: String): Option[Spell] = all.find(_.id == id)

  def learnedFor(school: String, level: Int): List[Spell] =
    all.filter(s => s.school == school && s.unlockLevel <= level)

  /** Spells that just unlocked at this exact level (for level-up announcements). */
  def newlyLearnedAt(school: String, level: Int): List[Spell] =
    all.filter(s => s.school == school && s.unlockLevel == level)

  private def particleFor(element: String) = element match
    case "fire"  => ParticleTypes.FLAME
    case "frost" => ParticleTypes.SNOWFLAKE
    case "holy"  => ParticleTypes.END_ROD
    case _       => ParticleTypes.ELECTRIC_SPARK

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
      case "heal"    => castHeal(caster, spell)
      case "purify"  => castPurify(caster, spell)
      case "protect" => castProtect(caster, spell)
      case _         => castBolt(caster, spell)

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

  /** The flying bolt: a vanilla snowball with our element applied on impact or at max range. */
  final class Bolt(val caster: ServerPlayer, val spec: Spell, val sl: ServerLevel)
      extends Snowball(sl, caster):
    private var age = 0
    private val rng = Random()

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
      else applyTo(hit.getEntity)
      super.onHitEntity(hit) // lets vanilla discard the projectile

    /** Everything hostile within the nova radius takes the spell. */
    private def burst(center: Vec3): Unit =
      val box = new AABB(center, center).inflate(spec.aoeRadius)
      sl.getEntitiesOfClass(classOf[Monster], box, (e: Monster) => e.isAlive).forEach(e => applyTo(e))
      sl.sendParticles(particleFor(spec.element), center.x, center.y + 0.3, center.z, 24, spec.aoeRadius * 0.6, 0.6, spec.aoeRadius * 0.6, 0.04)
      sl.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, castPitch(spec.element) * 0.8f)

    private def fizz(): Unit =
      val p = position()
      sl.sendParticles(particleFor(spec.element), p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.02)

    private def applyTo(target: net.minecraft.world.entity.Entity): Unit =
      target match
        case t: Monster if t.isAlive && t != Bolt.this =>
          val src = caster.damageSources.indirectMagic(Bolt.this, caster)
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

    private def MaxAge = 28 // ~40 blocks at bolt speed
