package dev.azerothchaos

import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments

/** WoW-style loot rarity colors. */
enum Rarity(val id: String, val label: String, val color: ChatFormatting):
  case Common extends Rarity("common", "Common", ChatFormatting.WHITE)
  case Uncommon extends Rarity("uncommon", "Uncommon", ChatFormatting.GREEN)
  case Rare extends Rarity("rare", "Rare", ChatFormatting.AQUA)
  case Epic extends Rarity("epic", "Epic", ChatFormatting.LIGHT_PURPLE)

/**
 * Loot drops: gold coins and rarity-colored gear with real vanilla enchantments.
 * All items are ordinary vanilla items with custom names/lore, so vanilla clients
 * render them correctly.
 */
object Loot:
  final case class Gear(item: Item, name: String, rarity: Rarity, enchantments: List[(ResourceKey[Enchantment], Int)])

  private val rng = Random()

  /** Gear templates rolled from mob kills; rarity is chosen per roll. */
  private val gearPool: List[Gear] = List(
    Gear(Items.IRON_SWORD, "Blade of the Fang", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 2)),
    Gear(Items.IRON_AXE, "Splithammer", Rarity.Uncommon, List(Enchantments.SHARPNESS -> 2)),
    Gear(Items.BOW, "Whisperstring", Rarity.Uncommon, List(Enchantments.POWER -> 2)),
    Gear(Items.CHAINMAIL_LEGGINGS, "Greaves of the Legion", Rarity.Uncommon, List(Enchantments.PROTECTION -> 1, Enchantments.FIRE_PROTECTION -> 1)),
    Gear(Items.IRON_BOOTS, "Treads of the Outrunner", Rarity.Rare, List(Enchantments.PROTECTION -> 2, Enchantments.UNBREAKING -> 2)),
    Gear(Items.SHIELD, "Bulwark of Storms", Rarity.Rare, List(Enchantments.UNBREAKING -> 3)),
    Gear(Items.IRON_CHESTPLATE, "Cuirass of the Fallen", Rarity.Rare, List(Enchantments.PROTECTION -> 2, Enchantments.THORNS -> 1)),
    Gear(Items.IRON_SWORD, "Duskcleaver", Rarity.Epic, List(Enchantments.SHARPNESS -> 3, Enchantments.KNOCKBACK -> 1, Enchantments.UNBREAKING -> 2)),
    Gear(Items.IRON_HELMET, "Helm of the Black Roar", Rarity.Epic, List(Enchantments.PROTECTION -> 3, Enchantments.UNBREAKING -> 3))
  )

  /** Builds a named, lore-tagged, enchanted item stack from a gear template. */
  def stack(g: Gear, registries: RegistryAccess): ItemStack =
    val s = ItemStack(g.item)
    s.set(DataComponents.ITEM_NAME, Component.literal(g.name).withStyle(g.rarity.color))
    val enchantments = registries.registryOrThrow(Registries.ENCHANTMENT)
    g.enchantments.foreach { (key, level) =>
      try s.enchant(enchantments.wrapAsHolder(enchantments.getOrThrow(key)), level)
      catch case _: Throwable => () // skip any enchantment vanilla rules reject for this item
    }
    val existing = Option(s.get(DataComponents.LORE)).map(_.lines().asScala.toList).getOrElse(Nil)
    val lore = Component.literal(s"${g.rarity.label} loot - Azeroth Chaos").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)
    s.set(DataComponents.LORE, ItemLore((existing :+ lore).asJava))
    s

  def coinStack(count: Int): ItemStack =
    val s = ItemStack(Items.GOLD_NUGGET, count)
    s.set(DataComponents.ITEM_NAME, Component.literal(s"$count Gold Coins").withStyle(ChatFormatting.GOLD))
    s.set(
      DataComponents.LORE,
      ItemLore(List(Component.literal("Hard currency of the realm - spend it, or hoard it.").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)).asJava)
    )
    s

  /** On-kill loot roll: coins most of the time, gear occasionally, guaranteed gear from rare elites. */
  def onKill(victim: Mob): Unit =
    val registries = victim.level.registryAccess()
    val rare = victim.hasCustomName
    if rare then
      dropNear(victim, coinStack(5 + rng.nextInt(10)))
      dropNear(victim, stack(rollGear(minRarity = Rarity.Rare), registries))
    else
      if rng.nextInt(100) < 25 then dropNear(victim, coinStack(1 + rng.nextInt(3)))
      if rng.nextInt(100) < 8 then dropNear(victim, stack(rollGear(minRarity = Rarity.Common), registries))

  private def rollGear(minRarity: Rarity): Gear =
    val pool = gearPool.filter(g => g.rarity.ordinal >= minRarity.ordinal)
    val base = pool(rng.nextInt(pool.size))
    if minRarity == Rarity.Common then
      // de-scale a roll toward common occasionally by swapping the color tag
      if rng.nextInt(100) < 60 then base.copy(rarity = Rarity.Common, enchantments = base.enchantments.map(e => (e._1, 1)))
      else base
    else base

  def dropNear(entity: LivingEntity, s: ItemStack): Unit =
    entity.level match
      case serverLevel: ServerLevel =>
        val item = new ItemEntity(serverLevel, entity.getX, entity.getY + 0.2, entity.getZ, s)
        item.setPickUpDelay(20)
        serverLevel.addFreshEntity(item)
      case _ => ()
