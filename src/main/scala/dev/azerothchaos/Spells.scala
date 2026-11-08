package dev.azerothchaos

import scala.util.Random

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

/** A mage ability. Learned automatically at unlockLevel; Nova spells burst on all nearby foes. */
final case class Spell(
    id: String,
    name: String,
    unlockLevel: Int,
    damage: Float,
    cooldownTicks: Int,
    element: String, // fire | frost | storm (visuals + rider effects)
    aoeRadius: Double // 0 = single target, >0 = bursts in an area
)

/**
 * The mage spellbook. Casting is server-side only: a real vanilla projectile (a snowball,
 * retextured per element) with the element applied on hit, so any vanilla client sees the
 * particles and the flying bolt. Bolts detonate at max range even without a hit (novas).
 */
object Spells:
  val all: List[Spell] = List(
    Spell("fire-bolt", "Fire Bolt", 1, 3.5f, 60, "fire", 0.0),
    Spell("frost-bolt", "Frost Bolt", 5, 3.0f, 70, "frost", 0.0),
    Spell("thunder-bolt", "Thunder Bolt", 12, 5.5f, 100, "storm", 0.0),
    Spell("flame-nova", "Flame Nova", 15, 4.0f, 180, "fire", 3.5),
    Spell("storm-nova", "Storm Nova", 25, 5.5f, 220, "storm", 4.5)
  )

  val FocusItem = Items.BLAZE_ROD // any blaze rod works as a spell focus

  def byId(id: String): Option[Spell] = all.find(_.id == id)

  def learnedFor(level: Int): List[Spell] = all.filter(_.unlockLevel <= level)

  /** Spells that just unlocked at this exact level (for level-up announcements). */
  def newlyLearnedAt(level: Int): List[Spell] = all.filter(_.unlockLevel == level)

  private def particleFor(element: String) = element match
    case "fire"  => ParticleTypes.FLAME
    case "frost" => ParticleTypes.SNOWFLAKE
    case _       => ParticleTypes.ELECTRIC_SPARK

  private def boltItemFor(element: String): ItemStack = element match
    case "fire"  => ItemStack(Items.BLAZE_POWDER)
    case "frost" => ItemStack(Items.SNOWBALL)
    case _       => ItemStack(Items.AMETHYST_SHARD)

  private def castPitch(element: String): Float = element match
    case "fire"  => 1.0f
    case "frost" => 1.6f
    case _       => 0.6f

  /** Launches the bolt from the caster's eyes toward the crosshair. */
  def cast(caster: ServerPlayer, spell: Spell): Unit =
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
