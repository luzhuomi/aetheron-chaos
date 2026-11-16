package dev.aetheronchaos

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * The vendor side of the realm state: the clickable shop menu, buying and selling.
 * Stock and prices live in Shop.scala; item bookkeeping helpers stay in Game.
 */
object GameShop:
  def shopMenu(player: ServerPlayer): Unit =
    val gold = Game.countItem(player, Items.GOLD_NUGGET)
    Game.message(player, s"--- General Vendor --- you carry $gold gold", ChatFormatting.GOLD)
    Shop.stock.foreach { s =>
      val color = if gold >= s.price then ChatFormatting.AQUA else ChatFormatting.DARK_GRAY
      val text = Component
        .literal(s"[${s.gear.name}] ${s.price} gold")
        .withStyle(st => st.withColor(color).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, s"/quest buy ${s.id}")))
      player.displayClientMessage(text, false)
    }
    val held = player.getInventory.getSelected
    val sellable = if held.isEmpty then None else Shop.sellPrice(held).map(u => u * held.getCount)
    sellable match
      case Some(total) =>
        val sellLine = Component
          .literal(s"[Sell held: ${held.getDisplayName.getString} x${held.getCount} -> $total gold]")
          .withStyle(st => st.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/quest sell")))
        player.displayClientMessage(sellLine, false)
      case _ =>
        Game.message(player, "To sell: hold the item in your main hand, then use /quest sell (or sneak+right-click the merchant).", ChatFormatting.GRAY)
    Game.message(player, "Press T then LEFT-click a line to buy. Gold comes from coin drops, quest rewards and selling.", ChatFormatting.GRAY)
    forgeMenu(player)

  /** The forger's corner of the shop: legendary gear, paid for in gold and proof. */
  private def forgeMenu(player: ServerPlayer): Unit =
    val gold = Game.countItem(player, Items.GOLD_NUGGET)
    val sigils = Game.countSigils(player)
    Game.message(
      player,
      s"--- Legend Forge --- she needs the base gear, gold, and Sigils of the Elite (you carry $sigils). Rare elites, war-band captains and a siege held all leave them:",
      ChatFormatting.GOLD
    )
    Shop.forgeables.foreach { f =>
      val ascended = Game.characterFor(player).paragon >= f.paragon
      val ready = ascended && gold >= f.gold && sigils >= f.sigils && Game.hasItem(player, f.baseItem, isForgeableBase)
      val gate = if f.paragon > 0 then s" [Paragon ${f.paragon}]" else ""
      val text = Component
        .literal(s"[Forge ${f.gear.name}] ${f.gold} gold + ${sigilPhrase(f.sigils)} + ${baseName(f.baseItem)}$gate")
        .withStyle(st =>
          st.withColor(if ready then Rarity.Legendary.color else ChatFormatting.DARK_GRAY)
            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, s"/quest forge ${f.id}")))
      player.displayClientMessage(text, false)
    }
    val rerollReady = gold >= RerollGold && sigils >= RerollSigils
    val rerollLine = Component
      .literal(s"[Re-forge a legendary] ${RerollGold} gold + ${sigilPhrase(RerollSigils)} - hold it and roll its line anew")
      .withStyle(st =>
        st.withColor(if rerollReady then Rarity.Legendary.color else ChatFormatting.DARK_GRAY)
          .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/quest forge reroll")))
    player.displayClientMessage(rerollLine, false)

  private def sigilPhrase(n: Int): String = if n == 1 then "1 sigil" else s"$n sigils"

  /** The forger won't eat a legendary to make another legendary. */
  private def isForgeableBase(stack: ItemStack): Boolean =
    Shop.rarityOf(stack) != Some(Rarity.Legendary)

  private def baseName(item: Item): String = new ItemStack(item).getDisplayName.getString

  /** The forge itself: consume base gear + gold + sigils, hand back a legend. */
  def forge(player: ServerPlayer, id: String): String =
    if id.isEmpty then return forgeList(player)
    Shop.forgeById(id) match
      case None => s"The forge has no pattern '$id'. /quest forge lists what she can strike."
      case Some(f) =>
        val gold = Game.countItem(player, Items.GOLD_NUGGET)
        val sigils = Game.countSigils(player)
        val ascended = Game.characterFor(player).paragon
        val missing = List(
          if ascended < f.paragon then Some(s"Paragon ${f.paragon} - you are Paragon $ascended (/quest prestige)") else None,
          if gold < f.gold then Some(s"${f.gold - gold} more gold") else None,
          if sigils < f.sigils then Some(s"${f.sigils - sigils} more ${if f.sigils == 1 then "sigil" else "sigils"} (rare elites, captains, sieges)") else None,
          if !Game.hasItem(player, f.baseItem, isForgeableBase) then Some(s"a ${baseName(f.baseItem)} to melt down") else None
        ).flatten
        if missing.nonEmpty then s"The ${f.gear.name} needs ${missing.mkString(" and ")}."
        else
          Game.removeItems(player, Items.GOLD_NUGGET, f.gold)
          (1 to f.sigils).foreach(_ => Game.takeSigil(player))
          Game.takeItem(player, f.baseItem, isForgeableBase)
          Game.giveItem(player, Loot.stack(f.gear, player.level.registryAccess()))
          Game.creditLegendForged(player)
          forgeSparks(player)
          s"${f.gear.name} is struck: ${baseName(f.baseItem)} + ${f.gold} gold + ${sigilPhrase(f.sigils)}, ${gold - f.gold} gold left. ${f.flavor}"

  // --- the re-forge: a sink for spent legends ------------------------------------
  // The forger also improves: hand her a legendary and she rolls its line anew.
  // Same name, same metal, new heart. The roll is always legal for the item
  // (vanilla's own canEnchant decides) and never mixes families vanilla keeps apart.

  private val RerollGold = 120
  private val RerollSigils = 1
  private val rerollRng = Random()

  /** Families of enchantments that never share one roll - vanilla's own conflict lines. */
  private val ConflictFamilies: Map[ResourceLocation, String] = Map(
    Enchantments.SHARPNESS.location -> "weapon", Enchantments.SMITE.location -> "weapon",
    Enchantments.BANE_OF_ARTHROPODS.location -> "weapon", Enchantments.IMPALING.location -> "weapon",
    Enchantments.PROTECTION.location -> "protect", Enchantments.FIRE_PROTECTION.location -> "protect",
    Enchantments.BLAST_PROTECTION.location -> "protect", Enchantments.PROJECTILE_PROTECTION.location -> "protect",
    Enchantments.FEATHER_FALLING.location -> "protect",
    Enchantments.SILK_TOUCH.location -> "tool", Enchantments.FORTUNE.location -> "tool",
    Enchantments.MULTISHOT.location -> "crossbow", Enchantments.PIERCING.location -> "crossbow",
    Enchantments.DEPTH_STRIDER.location -> "boots", Enchantments.FROST_WALKER.location -> "boots", Enchantments.RESPIRATION.location -> "boots",
    Enchantments.INFINITY.location -> "bowstring", Enchantments.MENDING.location -> "bowstring"
  )

  /** `/quest forge reroll` - a legendary in hand comes back with its line re-rolled. */
  def rerollLegend(player: ServerPlayer): String =
    val held = player.getInventory.getSelected
    if held.isEmpty then return "Hold the legendary you would have re-forged, then /quest forge reroll."
    if Shop.rarityOf(held) != Some(Rarity.Legendary) then
      return s"The forge does not touch ${held.getDisplayName.getString} - she re-rolls legends, and this is not one."
    val gold = Game.countItem(player, Items.GOLD_NUGGET)
    val sigils = Game.countSigils(player)
    if gold < RerollGold || sigils < RerollSigils then
      return s"A re-forge costs $RerollGold gold and ${sigilPhrase(RerollSigils)} - you carry $gold gold and ${sigilPhrase(sigils)}."
    val registry = player.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
    val curses = Set(Enchantments.BINDING_CURSE.location, Enchantments.VANISHING_CURSE.location)
    val candidates = registry.holders().toList.asScala
      .filter(h => h.value.canEnchant(held) && !curses.contains(registry.getKey(h.value)))
      .toList
    if candidates.isEmpty then return s"She turns it down: there is no line she can roll on ${held.getDisplayName.getString}."
    val wanted = math.max(2, math.min(5, if held.isEnchanted then held.getEnchantments.size else 3))
    val picked = mutable.ListBuffer[net.minecraft.core.Holder[Enchantment]]()
    val families = mutable.Set[String]()
    rerollRng.shuffle(candidates).foreach { h =>
      if picked.size < wanted then
        val family = ConflictFamilies.get(registry.getKey(h.value))
        if family.isEmpty || !families.contains(family.get) then
          picked += h
          family.foreach(families += _)
    }
    held.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
    val lines = picked.toList.map { h =>
      val level = rollLegendLevel(h.value.getMaxLevel)
      held.enchant(h, level)
      describeEnchant(registry.getKey(h.value), level)
    }
    player.inventoryMenu.broadcastChanges()
    Game.removeItems(player, Items.GOLD_NUGGET, RerollGold)
    (1 to RerollSigils).foreach(_ => Game.takeSigil(player))
    forgeSparks(player)
    s"${held.getDisplayName.getString} comes off the anvil with a new heart: ${lines.mkString(", ")}. $RerollGold gold and ${sigilPhrase(RerollSigils)} - the name is the same as it ever was."

  /** Legends roll high: most lines come out at their ceiling, none at the floor. */
  private def rollLegendLevel(maxLevel: Int): Int =
    if maxLevel <= 1 then 1
    else if rerollRng.nextInt(100) < 60 then maxLevel else math.max(1, maxLevel - 1)

  private def describeEnchant(key: ResourceLocation, level: Int): String =
    val name = key.getPath.replace('_', ' ')
    s"${name.take(1).toUpperCase}${name.drop(1)} ${roman(level)}"

  private def roman(n: Int): String = List("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").lift(n).getOrElse(n.toString)

  private def forgeList(player: ServerPlayer): String =
    val sigils = Game.countSigils(player)
    (List(
      s"The village forger strikes ${Shop.forgeables.size} legends. Payment: the base gear, gold, and Sigils of the Elite (you carry $sigils). The two-sigil patterns come out netherite, and patterns marked [Paragon n] she only strikes for heroes who ascended past the cap:"
    ) ++ Shop.forgeables.map(f =>
      s"  /quest forge ${f.id} - ${f.gear.name} (${f.gold} gold + ${sigilPhrase(f.sigils)} + a ${baseName(f.baseItem)}): ${f.flavor}") ::: List(
      s"  /quest forge reroll - hold a legendary and she re-rolls its enchantment line ($RerollGold gold + ${sigilPhrase(RerollSigils)}): same name, new heart"))
      .mkString("\n")

  private def forgeSparks(player: ServerPlayer): Unit =
    player.level match
      case sl: ServerLevel =>
        sl.playSound(null, player, SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.8f)
        sl.sendParticles(ParticleTypes.LAVA, player.getX, player.getY + 1.2, player.getZ, 20, 0.4, 0.6, 0.4, 0.02)
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX, player.getY + 1.0, player.getZ, 25, 0.4, 0.7, 0.4, 0.05)
      case _ => ()

  def buy(player: ServerPlayer, id: String): String =
    Shop.byId(id) match
      case None => s"Unknown item '$id'. Browse with /quest shop."
      case Some(s) =>
        val gold = Game.countItem(player, Items.GOLD_NUGGET)
        if gold < s.price then s"The ${s.gear.name} costs ${s.price} gold - you carry only $gold."
        else
          Game.removeItems(player, Items.GOLD_NUGGET, s.price)
          Game.giveItem(player, Loot.stack(s.gear, player.level.registryAccess()))
          s"Bought ${s.gear.name} for ${s.price} gold (${gold - s.price} left)."

  /** Sells the whole stack in the player's main hand at the merchant's buy price. */
  def sellHeld(player: ServerPlayer): String =
    val inv = player.getInventory
    val held = inv.getSelected
    if held.isEmpty then "Hold the item you want to sell in your main hand, then use /quest sell."
    else
      Shop.sellPrice(held) match
        case None => s"${TownBuilder.VendorNpcName} waves it off - she won't pay coin for ${held.getDisplayName.getString}."
        case Some(unit) =>
          val count = held.getCount
          val total = unit * count
          inv.setItem(inv.selected, ItemStack.EMPTY)
          player.inventoryMenu.broadcastChanges()
          Game.giveItem(player, Loot.coinStack(total))
          s"Sold $count ${held.getDisplayName.getString} for $total gold - you carry ${Game.countItem(player, Items.GOLD_NUGGET)} now."
