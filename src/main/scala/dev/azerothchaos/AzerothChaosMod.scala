package dev.azerothchaos

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

class AzerothChaosMod extends ModInitializer:
  override def onInitialize(): Unit =
    QuestCommand.register()
    Interactions.register()
    Combat.register()
    ServerWorldEvents.LOAD.register((server, level) =>
      if level == server.overworld() then TownBuilder.registerSavedTowns(server)
    )
    ServerLifecycleEvents.SERVER_STOPPING.register(server => Game.onServerStopping(server))
    ServerTickEvents.END_SERVER_TICK.register(server => Game.tick(server))
    ServerPlayConnectionEvents.JOIN.register((handler, _, server) => Game.onJoin(handler.getPlayer, server))
    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) => Game.onDisconnect(handler.getPlayer, server))
    ServerPlayerEvents.AFTER_RESPAWN.register((_, newPlayer, _) => Game.afterRespawn(newPlayer))
