package dev.azerothchaos

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity

/**
 * Kill XP table (the "mob levels" of the realm) and the rare-elite spawn table.
 * Rare elites are WoW-style named mobs: big stat buffs, 10x XP and guaranteed loot.
 */
object Enemies:
  private val DefaultXp = 15

  private val table: Map[EntityType[?], Int] = Map(
    EntityType.SILVERFISH -> 12,
    EntityType.SLIME -> 15,
    EntityType.MAGMA_CUBE -> 25,
    EntityType.SPIDER -> 35,
    EntityType.CAVE_SPIDER -> 45,
    EntityType.ZOMBIE -> 40,
    EntityType.HUSK -> 45,
    EntityType.DROWNED -> 45,
    EntityType.ZOMBIE_VILLAGER -> 45,
    EntityType.SKELETON -> 45,
    EntityType.STRAY -> 50,
    EntityType.CREEPER -> 55,
    EntityType.PILLAGER -> 55,
    EntityType.PHANTOM -> 60,
    EntityType.HOGLIN -> 60,
    EntityType.WITCH -> 80,
    EntityType.VINDICATOR -> 90,
    EntityType.WITHER_SKELETON -> 95,
    EntityType.GUARDIAN -> 120,
    EntityType.ENDERMAN -> 110,
    EntityType.BLAZE -> 120,
    EntityType.ZOGLIN -> 130,
    EntityType.EVOKER -> 150,
    EntityType.GHAST -> 180,
    EntityType.RAVAGER -> 200,
    EntityType.ELDER_GUARDIAN -> 450,
    EntityType.WARDEN -> 1000
  )

  def xpFor(entity: LivingEntity): Int = table.getOrElse(entity.getType, DefaultXp)

  final class Rare(val name: String, val entityType: EntityType[?], val healthMult: Double, val attackMult: Double)

  val rares: List[Rare] = List(
    Rare("Griom the Ironfist", EntityType.VINDICATOR, 8.0, 1.5),
    Rare("Silithis the Broodmother", EntityType.SPIDER, 8.0, 1.5),
    Rare("Hexxia the Cruel", EntityType.WITCH, 6.0, 1.0),
    Rare("Voidcaller Xyr", EntityType.ENDERMAN, 6.0, 1.5),
    Rare("Fathom Lord Muroth", EntityType.DROWNED, 8.0, 1.5),
    Rare("Corebark the Smoldering", EntityType.BLAZE, 6.0, 1.5),
    Rare("Bonewalker Prime", EntityType.SKELETON, 8.0, 1.5),
    Rare("Thundermaw", EntityType.PHANTOM, 6.0, 1.5),
    Rare("Crustfang the Ancient", EntityType.SILVERFISH, 10.0, 1.0)
  )
