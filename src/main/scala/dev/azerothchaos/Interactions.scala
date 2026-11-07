package dev.azerothchaos

import scala.util.Random

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks

/**
 * Hooks right-clicks:
 *  - the loom at a built quest hub = quest board (menu with clickable take/turn-in offers)
 *  - the barrel at a built quest hub = general vendor (shop with clickable buys)
 *  - town NPCs: the questmaster opens the board, the merchant the shop, guards give hints
 * Other looms/barrels/villagers in the world are untouched - only registered ones are intercepted.
 */
object Interactions:
  private val rng = Random()
  private val GuardTips = List(
    "Guard Captain Mara: 'Rare elites roam the wilds every 12 minutes. Bring friends.'",
    "Guard Captain Mara: 'The stronger the beast, the finer the loot. Hunt what frightens you.'",
    "Hearthkeeper Finn: 'Party up with /quest party - shared XP, need/greed loot rolls.'",
    "Hearthkeeper Finn: 'Gold spends well at the vendor barrel or /quest shop.'"
  )

  def register(): Unit =
    UseBlockCallback.EVENT.register((player, world, _, hitResult) =>
      player match
        case p: ServerPlayer if TownBuilder.isBoard(hitResult.getBlockPos) && world.getBlockState(hitResult.getBlockPos).is(Blocks.LOOM) =>
          Game.boardMenu(p)
          InteractionResult.SUCCESS
        case p: ServerPlayer if TownBuilder.isVendor(hitResult.getBlockPos) && world.getBlockState(hitResult.getBlockPos).is(Blocks.BARREL) =>
          Game.shopMenu(p)
          InteractionResult.SUCCESS
        case _ => InteractionResult.PASS
    )

    UseEntityCallback.EVENT.register((player, _, hand, entity, _) =>
      if hand != InteractionHand.MAIN_HAND then InteractionResult.PASS
      else
        (player, TownBuilder.npcRole(entity)) match
          case (p: ServerPlayer, Some("board")) =>
            Game.boardMenu(p)
            InteractionResult.SUCCESS
          case (p: ServerPlayer, Some("vendor")) =>
            Game.shopMenu(p)
            InteractionResult.SUCCESS
          case (p: ServerPlayer, Some("guard")) =>
            Game.message(p, GuardTips(rng.nextInt(GuardTips.size)), ChatFormatting.GRAY)
            InteractionResult.SUCCESS
          case _ => InteractionResult.PASS
    )
