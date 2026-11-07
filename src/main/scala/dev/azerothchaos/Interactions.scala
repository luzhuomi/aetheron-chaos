package dev.azerothchaos

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks

/**
 * Hooks block right-clicks:
 *  - the loom at a built quest hub = quest board (menu with clickable take/turn-in offers)
 *  - the barrel at a built quest hub = general vendor (shop with clickable buys)
 * Other looms/barrels in the world are untouched - only registered blocks are intercepted.
 */
object Interactions:
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
