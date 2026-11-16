package dev.aetheronchaos

import com.mojang.brigadier.arguments.IntegerArgumentType
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
          .`then`(
            Commands.literal("spell")
              .executes(ctx => withPlayer(ctx, GameSpells.spellList))
              .`then`(
                Commands.argument("spell", StringArgumentType.word())
                  .suggests((_, builder) => SharedSuggestionProvider.suggest(Spells.all.map(_.id).asJava, builder))
                  .executes(ctx => withPlayer(ctx, p => GameSpells.equipSpell(p, StringArgumentType.getString(ctx, "spell"))))
              )
          )
          .`then`(Commands.literal("board").executes(ctx => withPlayerSilent(ctx, GameQuests.boardMenu)))
          .`then`(questArgument("take", ctx => withPlayer(ctx, p => GameQuests.takeQuest(p, StringArgumentType.getString(ctx, "quest")))))
          .`then`(questArgument("abandon", ctx => withPlayer(ctx, p => GameQuests.abandonQuest(p, StringArgumentType.getString(ctx, "quest")))))
          .`then`(questArgument("track", ctx => withPlayer(ctx, p => GameQuests.track(p, StringArgumentType.getString(ctx, "quest")))))
          .`then`(questArgument("untrack", ctx => withPlayer(ctx, p => GameQuests.untrack(p, StringArgumentType.getString(ctx, "quest")))))
          .`then`(questArgument("turnin", ctx => withPlayer(ctx, p => GameQuests.turnIn(p, StringArgumentType.getString(ctx, "quest")))))
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
          .`then`(
            Commands.literal("guild")
              .executes(ctx => withPlayer(ctx, p => Guilds.status(p)))
              .`then`(Commands.literal("create").`then`(Commands.argument("name", StringArgumentType.word())
                .executes(ctx => withPlayer(ctx, p => Guilds.create(p, StringArgumentType.getString(ctx, "name"))))))
              .`then`(Commands.literal("invite").`then`(Commands.argument("hero", StringArgumentType.word())
                .executes(ctx => withPlayer(ctx, p => Guilds.invite(p, StringArgumentType.getString(ctx, "hero"))))))
              .`then`(Commands.literal("accept").executes(ctx => withPlayer(ctx, p => Guilds.accept(p))))
              .`then`(Commands.literal("decline").executes(ctx => withPlayer(ctx, p => Guilds.decline(p))))
              .`then`(Commands.literal("leave").executes(ctx => withPlayer(ctx, p => Guilds.leave(p))))
              .`then`(Commands.literal("claim").executes(ctx => withPlayer(ctx, p => Guilds.claim(p))))
              .`then`(Commands.literal("banner").executes(ctx => withPlayer(ctx, p => Guilds.banner(p))))
          )
          .`then`(
            Commands.literal("warp")
              .`then`(
                Commands.argument("hub", IntegerArgumentType.integer())
                  .executes(ctx => withPlayer(ctx, p => GameQuests.warp(p, IntegerArgumentType.getInteger(ctx, "hub"))))
              )
          )
          .`then`(Commands.literal("stats").executes(ctx => withPlayer(ctx, Game.statsReport)))
          .`then`(Commands.literal("titles").executes(ctx => withPlayer(ctx, Achievements.report)))
          .`then`(
            Commands.literal("title")
              .executes(ctx => withPlayer(ctx, p => Achievements.report(p)))
              .`then`(
                Commands.argument("title", StringArgumentType.word())
                  .suggests((ctx, builder) =>
                    val earned = ctx.getSource.getEntity match
                      case p: ServerPlayer => Game.ensure(p.getUUID).titles.toList
                      case _ => Nil
                    SharedSuggestionProvider.suggest(earned.asJava, builder)
                  )
                  .executes(ctx => withPlayer(ctx, p => Achievements.carry(p, StringArgumentType.getString(ctx, "title"))))
              )
          )
          .`then`(Commands.literal("top").executes(ctx => top(ctx)))
          .`then`(Commands.literal("prestige").executes(ctx => withPlayer(ctx, p => Game.prestigeReport(p))))
          .`then`(Commands.literal("threat").executes(ctx => withPlayer(ctx, p => Threat.readout(p))))
          .`then`(
            Commands.literal("trade")
              .executes(ctx => withPlayer(ctx, p => Trading.show(p)))
              .`then`(Commands.literal("yes").executes(ctx => withPlayer(ctx, p => Trading.respond(p, true))))
              .`then`(Commands.literal("no").executes(ctx => withPlayer(ctx, p => Trading.respond(p, false))))
              .`then`(Commands.literal("give").executes(ctx => withPlayer(ctx, p => Trading.offerHeld(p))))
              .`then`(Commands.literal("clear").executes(ctx => withPlayer(ctx, p => Trading.clearOffer(p))))
              .`then`(Commands.literal("confirm").executes(ctx => withPlayer(ctx, p => Trading.confirm(p))))
              .`then`(Commands.literal("cancel").executes(ctx => withPlayer(ctx, p => Trading.cancelBy(p))))
              .`then`(Commands.argument("hero", StringArgumentType.word())
                .executes(ctx => withPlayer(ctx, p => Trading.invite(p, StringArgumentType.getString(ctx, "hero")))))
          )
          .`then`(
            Commands.literal("barracks")
              .executes(ctx => withPlayer(ctx, p => TownBuilder.barracksStatus(p)))
              .`then`(Commands.literal("build").executes(ctx => withPlayer(ctx, p => {
                val hubIndex = GuardWatch.hubNear(p).getOrElse(-1)
                if hubIndex < 0 then "Stand at a hub plaza to raise a barracks there."
                else TownBuilder.buildBarracks(p, hubIndex)
              })))
          )
          .`then`(
            Commands.literal("post")
              .executes(ctx => withPlayer(ctx, p => TradePost.list(p)))
              .`then`(
                Commands.literal("sell")
                  .executes(ctx => withPlayer(ctx, p => TradePost.sell(p, 0)))
                  .`then`(Commands.argument("gold", IntegerArgumentType.integer(1, 5000))
                    .executes(ctx => withPlayer(ctx, p => TradePost.sell(p, IntegerArgumentType.getInteger(ctx, "gold")))))
              )
              .`then`(Commands.literal("watch").`then`(Commands.argument("phrase", StringArgumentType.greedyString())
                .executes(ctx => withPlayer(ctx, p => TradePost.watch(p, StringArgumentType.getString(ctx, "phrase"))))))
              .`then`(Commands.literal("unwatch").`then`(Commands.argument("phrase", StringArgumentType.greedyString())
                .executes(ctx => withPlayer(ctx, p => TradePost.unwatch(p, StringArgumentType.getString(ctx, "phrase"))))))
              .`then`(Commands.literal("buy").`then`(Commands.argument("n", IntegerArgumentType.integer(1, 10))
                .executes(ctx => withPlayer(ctx, p => TradePost.buy(p, IntegerArgumentType.getInteger(ctx, "n"))))))
              .`then`(Commands.literal("cancel").`then`(Commands.argument("n", IntegerArgumentType.integer(1, 10))
                .executes(ctx => withPlayer(ctx, p => TradePost.cancel(p, IntegerArgumentType.getInteger(ctx, "n"))))))
              .`then`(Commands.literal("coin").executes(ctx => withPlayer(ctx, p => TradePost.collect(p))))
          )
          .`then`(Commands.literal("shop").executes(ctx => withPlayerSilent(ctx, GameShop.shopMenu)))
          .`then`(
            Commands.literal("sell")
              .executes(ctx => withPlayer(ctx, p => GameShop.sellHeld(p)))
          )
          .`then`(
            Commands.literal("buy")
              .`then`(
                Commands.argument("item", StringArgumentType.greedyString())
                  .suggests((_, builder) => SharedSuggestionProvider.suggest(Shop.stock.map(_.id).asJava, builder))
                  .executes(ctx => withPlayer(ctx, p => GameShop.buy(p, StringArgumentType.getString(ctx, "item"))))
              )
          )
          .`then`(
            Commands.literal("forge")
              .executes(ctx => withPlayer(ctx, p => GameShop.forge(p, "")))
              .`then`(Commands.literal("reroll").executes(ctx => withPlayer(ctx, p => GameShop.rerollLegend(p))))
              .`then`(
                Commands.argument("pattern", StringArgumentType.greedyString())
                  .suggests((_, builder) => SharedSuggestionProvider.suggest(Shop.forgeables.map(_.id).asJava, builder))
                  .executes(ctx => withPlayer(ctx, p => GameShop.forge(p, StringArgumentType.getString(ctx, "pattern"))))
              )
          )
          .`then`(
            Commands.literal("loot")
              .`then`(Commands.literal("need").`then`(offerArgument(need = true)))
              .`then`(Commands.literal("greed").`then`(offerArgument(need = false)))
          )
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
              .executes(ctx => withPlayer(ctx, p => GameRares.spawnRare(p, None)))
          )
          .`then`(
            Commands.literal("guard")
              .executes(ctx => withPlayer(ctx, p => GuardWatch.status(p)))
              .`then`(Commands.literal("eman").executes(ctx => withPlayer(ctx, p => {
                val hubIndex = GuardWatch.hubNear(p).getOrElse(-1)
                if hubIndex < 0 then "Stand at a hub to re-man its watch (nearest first: /quest guard to see them all)."
                else GuardWatch.reman(p, hubIndex)
              })))
          )
          .`then`(
            Commands.literal("invade")
              .requires(src => src.hasPermission(2))
              .executes(ctx => invade(ctx, 0))
              .`then`(
                Commands.argument("hub", IntegerArgumentType.integer())
                  .executes(ctx => invade(ctx, IntegerArgumentType.getInteger(ctx, "hub")))
              )
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
    // literal subcommand (/quest <name> ...) + greedy id: quest ids contain hyphens, which word() would reject
    Commands.literal(name)
      .`then`(
        Commands.argument("quest", StringArgumentType.greedyString())
          .suggests((_, builder) => SharedSuggestionProvider.suggest(Quests.all.map(_.id).asJava, builder))
          .executes(ctx => body(ctx))
      )

  private def offerArgument(need: Boolean) =
    Commands.argument("offer", IntegerArgumentType.integer())
      .executes(ctx => withPlayer(ctx, p => PartyLoot.roll(p, IntegerArgumentType.getInteger(ctx, "offer"), need)))

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
          ctx.getSource.sendSuccess(() => Component.literal(s"Quest log (${c.tracked.size}/${GameQuests.MaxTrackedQuests} tracked):").withStyle(ChatFormatting.GOLD), false)
          c.taken.toList.flatMap(Quests.byId).foreach { q =>
            val have = GameQuests.progressOf(p, q)
            val goal = Quests.goalOf(q)
            val tag = if q.partyOnly then "[GROUP] " else ""
            val mark = if c.tracked.contains(q.id) then "★ " else "- "
            ctx.getSource.sendSuccess(
              () =>
                Component
                  .literal(s"$mark$tag${q.name} ($have/$goal): ${Quests.objectiveText(q)} | reward: ${q.xp} XP + ${q.coins} gold | hint: ${q.hint}")
                  .withStyle(ChatFormatting.AQUA),
              false
            )
          }
          ctx.getSource.sendSuccess(
            () =>
              Component
                .literal("★ = tracked (shows with a compass in your action bar): /quest track <id> | /quest untrack <id>")
                .withStyle(ChatFormatting.YELLOW),
            false
          )
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
      rows.zipWithIndex.foreach { case ((name, level, paragon, cls, titleId, uuid), i) =>
        val titleTag = Achievements.byId(titleId).map(t => s" \"${t.title}\"").getOrElse("")
        val guildTag = Guilds.tagName(ctx.getSource.getServer, uuid).map(g => s" [$g]").getOrElse("")
        val line = Component.literal(s"${i + 1}. Lv $level").withStyle(ChatFormatting.AQUA)
        if paragon > 0 then line.append(Component.literal(s" ★$paragon").withStyle(Game.paragonColor(paragon)))
        line.append(Component.literal(s" $cls").withStyle(ChatFormatting.AQUA))
        if titleTag.nonEmpty then line.append(Component.literal(titleTag).withStyle(if paragon > 0 then Game.paragonColor(paragon) else ChatFormatting.AQUA))
        if guildTag.nonEmpty then line.append(Component.literal(guildTag).withStyle(ChatFormatting.LIGHT_PURPLE))
        line.append(Component.literal(s" - $name").withStyle(ChatFormatting.AQUA))
        ctx.getSource.sendSuccess(() => line, false)
      }
    1

  private def help(ctx: CommandContext[CommandSourceStack]): Int =
    ctx.getSource.sendSuccess(
      () =>
        Component
          .literal(
            "Aetheron Chaos MMORPG: /quest join, /quest class <warrior|paladin|hunter|mage|rogue|priest> (see /quest roles), " +
              "/quest board or right-click the town loom, /quest take <id>, /quest log, /quest track <id> (quest compass), /quest turnin <id> (at the board), " +
              "/quest warp <n> (wayside portal at any board), " +
              "/quest party <name>, /quest guild (charters: create/invite/accept/claim - one shared hunt a day), /quest trade <hero> (swap goods face to face), /quest post (the hub market: sell/buy/coin), /quest threat (who the nearby mobs are angry at), /quest shop (spend gold), /quest forge (strike legends), /quest guard (the realm's watch; 'guard eman' to re-man), /quest stats, /quest prestige (paragon tiers past the cap), /quest titles (carry one with /quest title), /quest top. Ops: /quest town, /quest clear, /quest rare, /quest invade. " +
              "Slay monsters for tiered XP and loot - party gear drops are need/greed locked! Named rare elites spawn on a timer."
          )
          .withStyle(ChatFormatting.YELLOW),
      false
    )
    1

  /** `/quest invade` answers from the console too - a server host can call their own siege. */
  private def invade(ctx: CommandContext[CommandSourceStack], hub: Int): Int =
    val msg = ctx.getSource.getEntity match
      case p: ServerPlayer => Invasion.summon(p, hub)
      case _ => Invasion.summonAt(ctx.getSource.getServer, hub)
    ctx.getSource.sendSuccess(() => Component.literal(msg), false)
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
