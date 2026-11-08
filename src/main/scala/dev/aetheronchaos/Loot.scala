package dev.aetheronchaos

import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
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
  /** A drop template. The base item must be a real vanilla item. `flavor` adds a lore line. */
  final case class Gear(
      item: Item,
      name: String,
      rarity: Rarity,
      enchantments: List[(ResourceKey[Enchantment], Int)],
      flavor: String = ""
  )

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
    val flavor =
      if g.flavor.nonEmpty then List(Component.literal(g.flavor).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW))
      else Nil
    val lore = Component.literal(s"${g.rarity.label} loot - Aetheron Chaos").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)
    s.set(DataComponents.LORE, ItemLore((existing ++ flavor :+ lore).asJava))
    s

  /** The amount is the vanilla stack count - never baked into the name, or it goes stale when
   *  stacks shrink, merge or split. */
  val coinName: Component = Component.literal("Gold Coins").withStyle(ChatFormatting.GOLD)

  def coinStack(count: Int): ItemStack =
    val s = ItemStack(Items.GOLD_NUGGET, count)
    s.set(DataComponents.ITEM_NAME, coinName)
    s.set(
      DataComponents.LORE,
      ItemLore(List(Component.literal("Hard currency of the realm - spend it, or hoard it.").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)).asJava)
    )
    s

  /** Signature drops: iconic gear that ONLY this mob type ever drops (id, percent chance).
   *  Rare (named) elites triple the odds. Boss signatures are the hunt reward. */
  private val signatureDrops: Map[EntityType[?], (Gear, Int)] = Map(
    EntityType.ZOMBIE -> (
      Gear(Items.IRON_SWORD, "Rotten Cleaver", Rarity.Common, List(Enchantments.SHARPNESS -> 1),
        "Still smells of the grave. Only zombies carry it."), 3),
    EntityType.SPIDER -> (
      Gear(Items.BOW, "Silkfang", Rarity.Uncommon, List(Enchantments.POWER -> 2, Enchantments.INFINITY -> 1),
        "Strung with the webbing of a giant spider."), 5),
    EntityType.CREEPER -> (
      Gear(Items.BOW, "Everfuse", Rarity.Uncommon, List(Enchantments.POWER -> 1, Enchantments.FLAME -> 1),
        "It hums faintly, like a fuse about to burn out."), 5),
    EntityType.PILLAGER -> (
      Gear(Items.CROSSBOW, "Oathbreaker", Rarity.Uncommon, List(Enchantments.MULTISHOT -> 1, Enchantments.QUICK_CHARGE -> 1),
        "Taken from a raider who broke the wrong promise."), 5),
    EntityType.WITCH -> (
      Gear(Items.BOOK, "Hexbinder's Grimoire", Rarity.Rare, List(Enchantments.UNBREAKING -> 3),
        "Its pages whisper in a language you almost understand."), 8),
    EntityType.PHANTOM -> (
      Gear(Items.IRON_HELMET, "Nightmare Shroud", Rarity.Rare, List(Enchantments.PROTECTION -> 2, Enchantments.BLAST_PROTECTION -> 2),
        "Woven from the dreams it stole."), 8),
    EntityType.ENDERMAN -> (
      Gear(Items.IRON_BOOTS, "Voidstep Treads", Rarity.Rare, List(Enchantments.PROTECTION -> 2, Enchantments.FEATHER_FALLING -> 3),
        "Your steps feel a handbreadth shorter in them."), 7),
    EntityType.BLAZE -> (
      Gear(Items.BOW, "Cinderheart", Rarity.Rare, List(Enchantments.POWER -> 3, Enchantments.FLAME -> 1),
        "The arrow leaves the string already burning."), 7),
    EntityType.RAVAGER -> (
      Gear(Items.IRON_CHESTPLATE, "Stonefang Warplate", Rarity.Epic, List(Enchantments.PROTECTION -> 3, Enchantments.THORNS -> 2),
        "Hammered from the armored hide of a war-beast."), 15),
    EntityType.ELDER_GUARDIAN -> (
      Gear(Items.TRIDENT, "Heart of the Deep Duke", Rarity.Epic, List(Enchantments.IMPALING -> 3, Enchantments.UNBREAKING -> 3),
        "It pulses like a slow, drowned heart."), 30),
    EntityType.WARDEN -> (
      Gear(Items.IRON_SWORD, "Deepbrand, Bane of the Dark", Rarity.Epic, List(Enchantments.SHARPNESS -> 4, Enchantments.UNBREAKING -> 3),
        "Forged in the dark that answers back. Only wardens carry it."), 30)
  )

  /** On-kill loot roll: drop rates and rarity caps scale with the mob's tier (Enemies.tierOf).
   *  Signature drops roll first and only come from their own mob. Gear for a party kill goes
   *  through the PartyLoot need/greed lock instead of hitting the ground. */
  def onKill(victim: Mob, killer: ServerPlayer, party: List[ServerPlayer]): Unit =
    val registries = victim.level.registryAccess()
    val tier = Enemies.tierOf(victim)
    val rare = victim.hasCustomName
    signatureDrops.get(victim.getType).foreach { (g, chance) =>
      if rng.nextInt(100) < (if rare then chance * 3 else chance) then
        lockedOrDrop(victim, stack(g, registries), killer, party)
    }
    if rare then
      dropNear(victim, coinStack(5 + rng.nextInt(10)))
      lockedOrDrop(victim, stack(rollGear(maxRarity = Rarity.Epic, tier = 5), registries), killer, party)
    else
      if rng.nextInt(100) < 20 + tier * 5 then dropNear(victim, coinStack(1 + rng.nextInt(1 + tier)))
      if rng.nextInt(100) < 3 + tier * 5 then
        lockedOrDrop(victim, stack(rollGear(maxRarity = maxRarityFor(tier), tier = tier), registries), killer, party)

  private def maxRarityFor(tier: Int): Rarity = tier match
    case 1 => Rarity.Common
    case 2 => Rarity.Uncommon
    case 3 | 4 => Rarity.Rare
    case _ => Rarity.Epic

  private def lockedOrDrop(victim: Mob, s: ItemStack, killer: ServerPlayer, party: List[ServerPlayer]): Unit =
    if party.size >= 2 then PartyLoot.offer(killer.server, victim, s, killer :: party)
    else dropNear(victim, s)

  private def rollGear(maxRarity: Rarity, tier: Int): Gear =
    val pool = gearPool.filter(_.rarity.ordinal <= maxRarity.ordinal)
    val base = pool(rng.nextInt(pool.size))
    // higher tiers push the pick toward the best rarity in the band, and bump enchant levels
    val best = pool.maxBy(_.rarity.ordinal)
    val chosen = if rng.nextInt(100) < tier * 8 then best else base
    if tier >= 4 then chosen.copy(enchantments = chosen.enchantments.map(e => (e._1, math.min(5, e._2 + 1))))
    else chosen

  def dropNear(entity: LivingEntity, s: ItemStack): Unit =
    entity.level match
      case serverLevel: ServerLevel =>
        val item = new ItemEntity(serverLevel, entity.getX, entity.getY + 0.2, entity.getZ, s)
        item.setPickUpDelay(20)
        serverLevel.addFreshEntity(item)
      case _ => ()
