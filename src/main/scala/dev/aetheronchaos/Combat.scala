package dev.aetheronchaos

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster

/**
 * Credits kills: the last hitter (melee, bow, potion, trident - the damage source's
 * "true" owner) gets XP credit, quest progress and loot for their party.
 * Also feeds the threat table: every hit a hero lands builds threat on that mob.
 */
object Combat:
  def register(): Unit =
    ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) =>
      entity match
        case m: Monster =>
          source.getEntity match
            case hero: ServerPlayer => Threat.onDamage(m, hero, amount)
            case _ => ()
        case _ => ()
      true // we only observe; the damage itself is vanilla
    )
    ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) =>
      entity match
        case player: ServerPlayer => Game.onPlayerDeath(player)
        case mob: Mob =>
          source.getEntity match
            case killer: ServerPlayer => Game.awardKill(killer, mob)
            case _ => ()
        case _ => ()
    )
