package dev.aetheronchaos

import net.minecraft.world.item.Items
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
    ShopItem("guard-shirt", Loot.Gear(Items.CHAINMAIL_CHESTPLATE, "Militia Shirt", Rarity.Common, List(Enchantments.PROTECTION -> 1)), 12),
    ShopItem("travel-boots", Loot.Gear(Items.CHAINMAIL_BOOTS, "Postboy Boots", Rarity.Common, List(Enchantments.PROTECTION -> 1)), 10),
    ShopItem("kite-shield", Loot.Gear(Items.SHIELD, "Kite Shield", Rarity.Uncommon, List(Enchantments.UNBREAKING -> 2)), 18),
    ShopItem("fang-sword", Loot.Gear(Items.IRON_SWORD, "Blade of the Fang", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 2)), 25),
    ShopItem("ranger-bow", Loot.Gear(Items.BOW, "Whisperstring", Rarity.Uncommon, List(Enchantments.POWER -> 2, Enchantments.PUNCH -> 1)), 30),
    ShopItem("knight-plate", Loot.Gear(Items.IRON_CHESTPLATE, "Cuirass of the Fallen", Rarity.Rare, List(Enchantments.PROTECTION -> 2, Enchantments.THORNS -> 1)), 60),
    ShopItem("black-roar", Loot.Gear(Items.IRON_HELMET, "Helm of the Black Roar", Rarity.Epic, List(Enchantments.PROTECTION -> 3, Enchantments.UNBREAKING -> 3)), 120)
  )

  def byId(id: String): Option[ShopItem] = stock.find(_.id == id)
