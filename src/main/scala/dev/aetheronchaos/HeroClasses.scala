package dev.aetheronchaos

import net.minecraft.ChatFormatting
import net.minecraft.world.item.Items

import net.minecraft.world.item.enchantment.Enchantments

/**
 * Playable hero classes, in the spirit of World of Warcraft.
 *
 * Each class is just data: attribute bonuses (applied by Game.applyBonuses),
 * an optional infinite passive effect, and the starting gear granted when the
 * class is chosen. Everything is vanilla items + effects - no client mod needed.
 */
final case class HeroClass(
    id: String,
    name: String,
    tagline: String,
    healthBonus: Double,
    damageBonus: Double,
    speedBonus: Double,
    passive: scala.Option[net.minecraft.core.Holder[net.minecraft.world.effect.MobEffect]],
    gear: List[Loot.Gear]
)

object HeroClasses:
  import Loot.Gear

  val all: List[HeroClass] = List(
    HeroClass(
      "warrior",
      "Warrior",
      "Stalwart frontline fighter - extra health, hits harder.",
      10.0, 1.5, 0.0, None,
      List(
        Gear(Items.IRON_SWORD, "Warrior's Blade", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 1)),
        Gear(Items.IRON_CHESTPLATE, "Warrior's Plate", Rarity.Uncommon, List(Enchantments.PROTECTION -> 1))
      )
    ),
    HeroClass(
      "paladin",
      "Paladin",
      "Holy knight - the most health and a blessed shield.",
      16.0, 1.0, 0.0, None,
      List(
        Gear(Items.IRON_SWORD, "Lightbringer", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 1)),
        Gear(Items.SHIELD, "Blessed Bulwark", Rarity.Uncommon, List(Enchantments.UNBREAKING -> 2))
      )
    ),
    HeroClass(
      "hunter",
      "Hunter",
      "Ranged beast-stalker - quick feet; any bow fires skill-shots (see spellbooks).",
      2.0, 0.5, 0.05, None,
      List(
        Gear(Items.BOW, "Hunter's Longbow", Rarity.Uncommon, List(Enchantments.POWER -> 2, Enchantments.PUNCH -> 1)),
        Gear(Items.ARROW, "Hunter's Quiver", Rarity.Common, Nil)
      )
    ),
    HeroClass(
      "mage",
      "Mage",
      "Arcane scholar - hurls fire/frost/storm bolts with a wand, and keeps arcane sight.",
      4.0, 0.5, 0.0, Some(net.minecraft.world.effect.MobEffects.NIGHT_VISION),
      List(
        Gear(Items.BLAZE_ROD, "Apprentice's Wand", Rarity.Uncommon, List(Enchantments.UNBREAKING -> 3))
      )
    ),
    HeroClass(
      "rogue",
      "Rogue",
      "Shadow striker - swift, deadly; any sword hides tricks (see spellbooks).",
      2.0, 1.0, 0.15, Some(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
      List(
        Gear(Items.IRON_SWORD, "Shadowfang", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 1, Enchantments.KNOCKBACK -> 1))
      )
    ),
    HeroClass(
      "priest",
      "Priest",
      "Holy healer - regenerates, and smites with a sanctified blade.",
      6.0, 0.5, 0.0, Some(net.minecraft.world.effect.MobEffects.REGENERATION),
      List(
        Gear(Items.IRON_SWORD, "Sanctified Blade", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 1)),
        Gear(Items.CHAINMAIL_CHESTPLATE, "Holy Vestments", Rarity.Uncommon, List(Enchantments.PROJECTILE_PROTECTION -> 2)),
        Gear(Items.BOOK, "Book of Radiance", Rarity.Common, List(),
          "Your prayer book - hold any book to channel holy spells.")
      )
    )
  )

  def byId(id: String): Option[HeroClass] = all.find(_.id == id)

  // attribute modifier ids - namespaced so we can find and remove our own modifiers
  val Namespace = "aetheron-chaos"
  def modifierId(name: String): net.minecraft.resources.ResourceLocation =
    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Namespace, name)
