package dev.aetheronchaos

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
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
