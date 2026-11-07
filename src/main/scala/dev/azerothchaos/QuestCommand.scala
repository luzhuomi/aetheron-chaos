package dev.azerothchaos

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import scala.jdk.CollectionConverters.*

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * /quest help | join | leave | class | board | take | abandon | turnin | log
 *      | party | stats | top | town | clear | rare | reset | roles
 */
object QuestCommand:
  def register(): Unit =
    CommandRegistrationCallback.EVENT.register((dispatcher, _, _) =>
      dispatcher.register(
        Commands.literal("quest")
          .`then`(Commands.literal("help").executes(ctx => help(ctx)))
          .`then`(Commands.literal("join").executes(ctx => withPlayer(ctx, Game.join)))
          .`then`(Commands.literal("leave").executes(ctx => withPlayer(ctx, Game.leave)))
          .`then`(
            Commands.literal("class")
              .`then`(
                Commands.argument("class", StringArgumentType.word())
                  .suggests((_, builder) => SharedSuggestionProvider.suggest(HeroClasses.all.map(_.id).asJava, builder))
                  .executes(ctx => withPlayer(ctx, p => Game.setClass(p, StringArgumentType.getString(ctx, "class"))))
              )
          )
          .`then`(Commands.literal("roles").executes(ctx => roles(ctx)))
          .`then`(Commands.literal("board").executes(ctx => withPlayerSilent(ctx, Game.boardMenu)))
          .`then`(questArgument("take", ctx => withPlayer(ctx, p => Game.takeQuest(p, StringArgumentType.getString(ctx, "quest")))))
          .`then`(questArgument("abandon", ctx => withPlayer(ctx, p => Game.abandonQuest(p, StringArgumentType.getString(ctx, "quest")))))
          .`then`(questArgument("turnin", ctx => withPlayer(ctx, p => Game.turnIn(p, StringArgumentType.getString(ctx, "quest")))))
          .`then`(Commands.literal("log").executes(ctx => log(ctx)))
          .`then`(
            Commands.literal("party")
              .executes(ctx => withPlayer(ctx, Game.partyList))
              .`then`(Commands.literal("leave").executes(ctx => withPlayer(ctx, Game.leaveParty)))
              .`then`(
                Commands.argument("name", StringArgumentType.word())
                  .executes(ctx => withPlayer(ctx, p => Game.partyJoin(p, StringArgumentType.getString(ctx, "name"))))
              )
          )
          .`then`(Commands.literal("stats").executes(ctx => withPlayer(ctx, Game.statsReport)))
          .`then`(Commands.literal("top").executes(ctx => top(ctx)))
          .`then`(
            Commands.literal("town")
              .requires(src => src.hasPermission(2))
              .executes(ctx => withPlayer(ctx, TownBuilder.build))
          )
          .`then`(
            Commands.literal("clear")
              .requires(src => src.hasPermission(2))
              .executes(ctx => withPlayer(ctx, p => TownBuilder.clear(p, all = false)))
              .`then`(Commands.literal("all").executes(ctx => withPlayer(ctx, p => TownBuilder.clear(p, all = true))))
          )
          .`then`(
            Commands.literal("rare")
              .requires(src => src.hasPermission(2))
              .executes(ctx => withPlayer(ctx, p => Game.spawnRare(p, None)))
          )
          .`then`(
            Commands.literal("reset")
              .executes(ctx => withPlayer(ctx, Game.reset))
              .`then`(
                Commands.argument("player", StringArgumentType.word())
                  .requires(src => src.hasPermission(2))
                  .executes(ctx => withServerReset(ctx))
              )
          )
      )
    )

  private def questArgument(name: String, body: CommandContext[CommandSourceStack] => Int) =
    Commands.argument("quest", StringArgumentType.word())
      .suggests((_, builder) => SharedSuggestionProvider.suggest(Quests.all.map(_.id).asJava, builder))
      .executes(ctx => body(ctx))

  private def withServerReset(ctx: CommandContext[CommandSourceStack]): Int =
    val msg = Game.resetOther(ctx.getSource.getServer, StringArgumentType.getString(ctx, "player"))
    ctx.getSource.sendSuccess(() => Component.literal(msg), false)
    1

  private def roles(ctx: CommandContext[CommandSourceStack]): Int =
    ctx.getSource.sendSuccess(() => Component.literal("Hero classes:").withStyle(ChatFormatting.GOLD), false)
    HeroClasses.all.foreach { hc =>
      ctx.getSource.sendSuccess(
        () =>
          Component
            .literal(s"/quest class ${hc.id} - ${hc.name}: ${hc.tagline} (+${hc.healthBonus.toInt} health, +${hc.damageBonus} attack${if hc.speedBonus > 0 then ", faster" else ""})")
            .withStyle(ChatFormatting.AQUA),
        false
      )
    }
    1

  private def log(ctx: CommandContext[CommandSourceStack]): Int =
    ctx.getSource.getEntity match
      case p: ServerPlayer =>
        val c = Game.characterFor(p)
        if c.taken.isEmpty then
          ctx.getSource.sendSuccess(() => Component.literal("Quest log is empty. Visit the board: /quest board").withStyle(ChatFormatting.GRAY), false)
        else
          ctx.getSource.sendSuccess(() => Component.literal("Quest log:").withStyle(ChatFormatting.GOLD), false)
          c.taken.toList.flatMap(Quests.byId).foreach { q =>
            val have = Game.progressOf(p, q)
            val goal = q.objective match
              case Objective.Kill(_, n, _) => n
              case Objective.Collect(_, n) => n
            val tag = if q.partyOnly then "[GROUP] " else ""
            ctx.getSource.sendSuccess(
              () =>
                Component
                  .literal(s"$tag${q.name} ($have/$goal): ${Quests.objectiveText(q)} | reward: ${q.xp} XP + ${q.coins} gold | hint: ${q.hint}")
                  .withStyle(ChatFormatting.AQUA),
              false
            )
          }
          ctx.getSource.sendSuccess(() => Component.literal("Turn completed quests in at the quest board: /quest turnin <id>").withStyle(ChatFormatting.YELLOW), false)
        1
      case _ =>
        ctx.getSource.sendFailure(Component.literal("This command needs a player."))
        0

  private def top(ctx: CommandContext[CommandSourceStack]): Int =
    val rows = Game.leaderboard(ctx.getSource.getServer)
    if rows.isEmpty then
      ctx.getSource.sendSuccess(() => Component.literal("No heroes yet. Be the first: /quest join").withStyle(ChatFormatting.GRAY), false)
    else
      ctx.getSource.sendSuccess(() => Component.literal("Heroes of the realm:").withStyle(ChatFormatting.GOLD), false)
      rows.zipWithIndex.foreach { case ((name, level, cls), i) =>
        ctx.getSource.sendSuccess(
          () => Component.literal(s"${i + 1}. Lv $level $cls - $name").withStyle(ChatFormatting.AQUA),
          false
        )
      }
    1

  private def help(ctx: CommandContext[CommandSourceStack]): Int =
    ctx.getSource.sendSuccess(
      () =>
        Component
          .literal(
            "Azeroth Chaos MMORPG: /quest join, /quest class <warrior|paladin|hunter|mage|rogue|priest> (see /quest roles), " +
              "/quest board or right-click the town loom, /quest take <id>, /quest log, /quest turnin <id> (at the board), " +
              "/quest party <name>, /quest stats, /quest top. Ops: /quest town, /quest clear, /quest rare. " +
              "Slay monsters for XP and loot - named rare elites spawn on a timer!"
          )
          .withStyle(ChatFormatting.YELLOW),
      false
    )
    1

  private def withPlayer(ctx: CommandContext[CommandSourceStack], f: ServerPlayer => String): Int =
    ctx.getSource.getEntity match
      case p: ServerPlayer =>
        val msg = f(p)
        ctx.getSource.sendSuccess(() => Component.literal(msg), false)
        1
      case _ =>
        ctx.getSource.sendFailure(Component.literal("This command needs a player."))
        0

  private def withPlayerSilent(ctx: CommandContext[CommandSourceStack], f: ServerPlayer => Unit): Int =
    ctx.getSource.getEntity match
      case p: ServerPlayer =>
        f(p)
        1
      case _ =>
        ctx.getSource.sendFailure(Component.literal("This command needs a player."))
        0
