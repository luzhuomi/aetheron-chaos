package dev.aetheronchaos

import scala.jdk.CollectionConverters.*

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
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

  // --- the legend forge ---------------------------------------------------------

  /** A pattern the forger will strike: the base gear she must see (any copy of that
   *  item, even a named one), the gold, and the Sigils of the Elite proof that rare elites
   *  fell by this hero's hand. The base gear is consumed. The two-sigil patterns come out
   *  netherite: another tier to work toward, not a bigger version of the same one.
   *  Paragon-gated patterns additionally require the hero to have ascended - the cap's
   *  second ladder, given a gear goal. */
  final case class Forgeable(id: String, gear: Loot.Gear, baseItem: Item, gold: Int, sigils: Int, flavor: String, paragon: Int = 0)

  val ForgeGold = 100

  val forgeables: List[Forgeable] = List(
    Forgeable("sunforged-greatblade",
      Loot.Gear(Items.IRON_SWORD, "Sunforged Greatblade", Rarity.Legendary,
        List(Enchantments.SHARPNESS -> 5, Enchantments.UNBREAKING -> 3)),
      Items.IRON_SWORD, ForgeGold, 1, "Struck from a blade that survived an elite. It remembers."),
    Forgeable("dawnsong",
      Loot.Gear(Items.BOW, "Dawnsong", Rarity.Legendary,
        List(Enchantments.POWER -> 5, Enchantments.FLAME -> 1, Enchantments.UNBREAKING -> 3)),
      Items.BOW, ForgeGold, 1, "Every arrow it looses arrives already burning."),
    Forgeable("aegis-of-the-dawn",
      Loot.Gear(Items.IRON_CHESTPLATE, "Aegis of the Dawn", Rarity.Legendary,
        List(Enchantments.PROTECTION -> 4, Enchantments.THORNS -> 3, Enchantments.UNBREAKING -> 3)),
      Items.IRON_CHESTPLATE, ForgeGold, 1, "Warm to wear. The blows come back to the striker."),
    Forgeable("crown-of-the-steady-light",
      Loot.Gear(Items.IRON_HELMET, "Crown of the Steady Light", Rarity.Legendary,
        List(Enchantments.PROTECTION -> 4, Enchantments.PROJECTILE_PROTECTION -> 4, Enchantments.UNBREAKING -> 3)),
      Items.IRON_HELMET, ForgeGold, 1, "Steady in the eye of any storm of arrows."),
    Forgeable("bulwark-of-the-quiet-dawn",
      Loot.Gear(Items.SHIELD, "Bulwark of the Quiet Dawn", Rarity.Legendary,
        List(Enchantments.UNBREAKING -> 3, Enchantments.MENDING -> 1)),
      Items.SHIELD, 90, 1, "It does not ring. It just stops things, and mends itself after."),
    Forgeable("greaves-of-the-long-road",
      Loot.Gear(Items.IRON_BOOTS, "Greaves of the Long Road", Rarity.Legendary,
        List(Enchantments.PROTECTION -> 4, Enchantments.FEATHER_FALLING -> 4, Enchantments.UNBREAKING -> 3)),
      Items.IRON_BOOTS, 110, 1, "Built for the road between towns, and for the fall off it."),
    Forgeable("dawnbreaker",
      Loot.Gear(Items.NETHERITE_SWORD, "Dawnbreaker", Rarity.Legendary,
        List(Enchantments.SHARPNESS -> 5, Enchantments.FIRE_ASPECT -> 2, Enchantments.MENDING -> 1, Enchantments.UNBREAKING -> 3)),
      Items.DIAMOND_SWORD, 240, 2, "Two proofs, one blade. What it cuts, it also lights."),
    Forgeable("widowhollow",
      Loot.Gear(Items.NETHERITE_AXE, "Widowhollow", Rarity.Legendary,
        List(Enchantments.SHARPNESS -> 5, Enchantments.EFFICIENCY -> 4, Enchantments.MENDING -> 1, Enchantments.UNBREAKING -> 3)),
      Items.DIAMOND_AXE, 220, 2, "It opens doors, shields and war bands with the same swing."),
    Forgeable("hauberk-of-the-last-watch",
      Loot.Gear(Items.NETHERITE_CHESTPLATE, "Hauberk of the Last Watch", Rarity.Legendary,
        List(Enchantments.PROTECTION -> 4, Enchantments.THORNS -> 3, Enchantments.MENDING -> 1, Enchantments.UNBREAKING -> 3)),
      Items.DIAMOND_CHESTPLATE, 260, 2, "Struck for whoever was left standing at the wall. That is you."),
    // --- paragon-gated: the forger only strikes these for heroes who ascended past the cap ---
    Forgeable("crown-of-the-first-light",
      Loot.Gear(Items.NETHERITE_HELMET, "Crown of the First Light", Rarity.Legendary,
        List(Enchantments.PROTECTION -> 4, Enchantments.PROJECTILE_PROTECTION -> 4, Enchantments.RESPIRATION -> 3, Enchantments.MENDING -> 1, Enchantments.UNBREAKING -> 3)),
      Items.DIAMOND_HELMET, 320, 3, "Struck for a hero the level cap could not hold. It is heavy with more than iron.",
      paragon = 2),
    Forgeable("earthgrip-greaves",
      Loot.Gear(Items.NETHERITE_LEGGINGS, "Earthgrip Greaves", Rarity.Legendary,
        List(Enchantments.PROTECTION -> 4, Enchantments.THORNS -> 3, Enchantments.MENDING -> 1, Enchantments.UNBREAKING -> 3)),
      Items.DIAMOND_LEGGINGS, 300, 3, "After a paragon falls, the ground is the one thing that still holds them up.",
      paragon = 3)
  )

  def forgeById(id: String): Option[Forgeable] = forgeables.find(_.id == id)

  // --- selling -----------------------------------------------------------------

  /** What the merchant pays for gear, by rarity (the table lives in Formulas). */
  private val RarityBuyPrice: Map[Rarity, Int] = Rarity.values.map(r => r -> Formulas.rarityBuyPrice(r.ordinal)).toMap

  /** The forger buys back proofs too - she is always hiring reputation. */
  val SigilBuyPrice: Int = Formulas.sigilBuyPrice

  /** Everyday farm and dungeon junk a village shop will happily take (ids -> gold in Formulas). */
  private val JunkBuyPrice: Map[Item, Int] =
    Formulas.junkBuyPrices.iterator
      .map { (path, price) => (BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(path)), price) }
      .filter(_._1 != null)
      .toMap

  /** Gold the merchant pays per item of this stack; None = she won't buy it. */
  def sellPrice(stack: ItemStack): Option[Int] =
    val item = stack.getItem
    if item == Items.GOLD_NUGGET then None // that's already money
    else if Loot.isSigil(stack) then Some(SigilBuyPrice) // before the raw-amethyst price
    else JunkBuyPrice.get(item)
      .orElse(rarityOf(stack).map(RarityBuyPrice))

  /** Recognizes our own loot by its "X loot - Aetheron Chaos" lore line. */
  private[aetheronchaos] def rarityOf(stack: ItemStack): Option[Rarity] =
    Option(stack.get(DataComponents.LORE)).flatMap { lore =>
      Rarity.values.find { r =>
        lore.lines().asScala.exists(_.getString == s"${r.label} loot - Aetheron Chaos")
      }
    }
