package dev.aetheronchaos

import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

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
      s"--- Legend Forge --- she needs the base gear, ${Shop.ForgeGold} gold and one Sigil of the Elite (you carry $sigils). Rare elites drop sigils:",
      ChatFormatting.GOLD
    )
    Shop.forgeables.foreach { f =>
      val ready = gold >= f.gold && sigils >= 1 && Game.hasItem(player, f.baseItem, isForgeableBase)
      val text = Component
        .literal(s"[Forge ${f.gear.name}] ${f.gold} gold + 1 sigil + ${baseName(f.baseItem)}")
        .withStyle(st =>
          st.withColor(if ready then Rarity.Legendary.color else ChatFormatting.DARK_GRAY)
            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, s"/quest forge ${f.id}")))
      player.displayClientMessage(text, false)
    }

  /** The forger won't eat a legendary to make another legendary. */
  private def isForgeableBase(stack: ItemStack): Boolean =
    Shop.rarityOf(stack) != Some(Rarity.Legendary)

  private def baseName(item: Item): String = new ItemStack(item).getDisplayName.getString

  /** The forge itself: consume base gear + gold + sigil, hand back a legend. */
  def forge(player: ServerPlayer, id: String): String =
    if id.isEmpty then return forgeList(player)
    Shop.forgeById(id) match
      case None => s"The forge has no pattern '$id'. /quest forge lists what she can strike."
      case Some(f) =>
        val gold = Game.countItem(player, Items.GOLD_NUGGET)
        val sigils = Game.countSigils(player)
        val missing = List(
          if gold < f.gold then Some(s"${f.gold - gold} more gold") else None,
          if sigils < 1 then Some("a Sigil of the Elite (rare elites drop them)") else None,
          if !Game.hasItem(player, f.baseItem, isForgeableBase) then Some(s"a ${baseName(f.baseItem)} to melt down") else None
        ).flatten
        if missing.nonEmpty then s"The ${f.gear.name} needs ${missing.mkString(" and ")}."
        else
          Game.removeItems(player, Items.GOLD_NUGGET, f.gold)
          Game.takeSigil(player)
          Game.takeItem(player, f.baseItem, isForgeableBase)
          Game.giveItem(player, Loot.stack(f.gear, player.level.registryAccess()))
          Game.creditLegendForged(player)
          forgeSparks(player)
          s"${f.gear.name} is struck: ${baseName(f.baseItem)} + ${f.gold} gold + one sigil, ${gold - f.gold} gold left. ${f.flavor}"

  private def forgeList(player: ServerPlayer): String =
    val sigils = Game.countSigils(player)
    (List(
      s"The village forger strikes ${Shop.forgeables.size} legends. Payment: the base gear, ${Shop.ForgeGold} gold and one Sigil of the Elite (you carry $sigils):"
    ) ++ Shop.forgeables.map(f => s"  /quest forge ${f.id} - ${f.gear.name} (needs a ${baseName(f.baseItem)}): ${f.flavor}"))
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
