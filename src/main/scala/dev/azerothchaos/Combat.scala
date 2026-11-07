package dev.azerothchaos

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob

/**
 * Credits kills: the last hitter (melee, bow, potion, trident - the damage source's
 * "true" owner) gets XP credit, quest progress and loot for their party.
 */
object Combat:
  def register(): Unit =
    ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) =>
      entity match
        case player: ServerPlayer => Game.onPlayerDeath(player)
        case mob: Mob =>
          source.getEntity match
            case killer: ServerPlayer => Game.awardKill(killer, mob)
            case _ => ()
        case _ => ()
    )
