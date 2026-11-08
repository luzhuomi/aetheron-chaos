package dev.aetheronchaos

import scala.jdk.CollectionConverters.*

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments

/**
 * The town general vendor: spend gold coins (the currency quest rewards and mob
 * kills hand you) on guaranteed gear. Browse with /quest shop or right-click the
 * vendor barrel in town; buy with a click or /quest buy <id>.
 */
object Shop:
  final case class ShopItem(id: String, gear: Loot.Gear, price: Int)

  val stock: List[ShopItem] = List(
    ShopItem("drill-sword", Loot.Gear(Items.IRON_SWORD, "Drill Ground Blade", Rarity.Common, List(Enchantments.SHARPNESS -> 1)), 10),
    ShopItem("militia-bow", Loot.Gear(Items.BOW, "Militia Longbow", Rarity.Common, List(Enchantments.POWER -> 1)), 12),
    ShopItem("woodcutter-axe", Loot.Gear(Items.IRON_AXE, "Woodcutter's Axe", Rarity.Common, List(Enchantments.SHARPNESS -> 1)), 12),
    ShopItem("guard-shirt", Loot.Gear(Items.CHAINMAIL_CHESTPLATE, "Militia Shirt", Rarity.Common, List(Enchantments.PROTECTION -> 1)), 12),
    ShopItem("travel-boots", Loot.Gear(Items.CHAINMAIL_BOOTS, "Postboy Boots", Rarity.Common, List(Enchantments.PROTECTION -> 1)), 10),
    ShopItem("kite-shield", Loot.Gear(Items.SHIELD, "Kite Shield", Rarity.Uncommon, List(Enchantments.UNBREAKING -> 2)), 18),
    ShopItem("fang-sword", Loot.Gear(Items.IRON_SWORD, "Blade of the Fang", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 2)), 25),
    ShopItem("ranger-bow", Loot.Gear(Items.BOW, "Whisperstring", Rarity.Uncommon, List(Enchantments.POWER -> 2, Enchantments.PUNCH -> 1)), 30),
    ShopItem("knight-plate", Loot.Gear(Items.IRON_CHESTPLATE, "Cuirass of the Fallen", Rarity.Rare, List(Enchantments.PROTECTION -> 2, Enchantments.THORNS -> 1)), 60),
    ShopItem("black-roar", Loot.Gear(Items.IRON_HELMET, "Helm of the Black Roar", Rarity.Epic, List(Enchantments.PROTECTION -> 3, Enchantments.UNBREAKING -> 3)), 120)
  )

  def byId(id: String): Option[ShopItem] = stock.find(_.id == id)

  // --- selling -----------------------------------------------------------------

  /** What the merchant pays for gear, by rarity (well below the selling price). */
  private val RarityBuyPrice: Map[Rarity, Int] = Map(
    Rarity.Common -> 5, Rarity.Uncommon -> 12, Rarity.Rare -> 30, Rarity.Epic -> 65
  )

  /** Everyday farm and dungeon junk a village shop will happily take. */
  private val JunkBuyPrice: Map[Item, Int] = Map(
    Items.ROTTEN_FLESH -> 1, Items.BONE -> 1, Items.STRING -> 1, Items.SPIDER_EYE -> 1,
    Items.GUNPOWDER -> 2, Items.SLIME_BALL -> 2, Items.MAGMA_CREAM -> 2, Items.BLAZE_ROD -> 3,
    Items.LEATHER -> 2, Items.FEATHER -> 1, Items.INK_SAC -> 1, Items.GLOW_INK_SAC -> 2,
    Items.ENDER_PEARL -> 5, Items.GHAST_TEAR -> 6, Items.PRISMARINE_SHARD -> 2,
    Items.COAL -> 1, Items.COPPER_INGOT -> 1, Items.RAW_IRON -> 2, Items.RAW_GOLD -> 3,
    Items.IRON_INGOT -> 3, Items.GOLD_INGOT -> 4, Items.REDSTONE -> 1, Items.LAPIS_LAZULI -> 1,
    Items.QUARTZ -> 1, Items.AMETHYST_SHARD -> 2, Items.DIAMOND -> 12, Items.EMERALD -> 10,
    Items.WHEAT -> 2, Items.POTATO -> 1, Items.CARROT -> 1, Items.BEETROOT -> 1, Items.EGG -> 1,
    Items.SUGAR_CANE -> 1, Items.CACTUS -> 1, Items.PUMPKIN -> 1, Items.COCOA_BEANS -> 1,
    Items.NETHER_WART -> 2
  )

  /** Gold the merchant pays per item of this stack; None = she won't buy it. */
  def sellPrice(stack: ItemStack): Option[Int] =
    val item = stack.getItem
    if item == Items.GOLD_NUGGET then None // that's already money
    else JunkBuyPrice.get(item)
      .orElse(rarityOf(stack).map(RarityBuyPrice))

  /** Recognizes our own loot by its "X loot - Aetheron Chaos" lore line. */
  private def rarityOf(stack: ItemStack): Option[Rarity] =
    Option(stack.get(DataComponents.LORE)).flatMap { lore =>
      Rarity.values.find { r =>
        lore.lines().asScala.exists(_.getString == s"${r.label} loot - Aetheron Chaos")
      }
    }
