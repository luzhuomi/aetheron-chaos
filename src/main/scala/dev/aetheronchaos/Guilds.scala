package dev.aetheronchaos

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Try

import net.minecraft.ChatFormatting
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, ListTag, Tag, TagParser}
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelResource

/**
 * Guilds, lightly: a name, a roster you can read in chat, and one hunt the whole guild
 * shares each day. No new entities, no GUI - the guild hall is a chat channel and a
 * scoreboard that resets at dawn. Membership persists in aetheron-chaos-guilds.snbt
 * alongside the other realm ledgers; the daily bookkeeping rides the same real-world
 * day the quest board's dailies do.
 *
 * A guild costs 100 gold to found (the charter and a banner at the plaza - a deliberate
 * gold sink), any member may invite, and the shared daily pays every member who claims
 * it, whether or not they were online when the hunt finished.
 */
object Guilds:
  private val MaxMembers = 8
  private def CreateGold: Int = Tuning.getInt("guild.create_gold", 100)
  private val MinNameLen = 2
  private val MaxNameLen = 16
  private def DailyGoal: Int = Tuning.getInt("guild.daily_goal", 20)
  private def DailyRewardGold: Int = Tuning.getInt("guild.daily_reward_gold", 20)
  private val FileName = "aetheron-chaos-guilds.snbt"

  final class Guild(val name: String):
    var leader: UUID = null
    val members = mutable.LinkedHashSet[UUID]()
    val names = mutable.Map[UUID, String]() // last known names, so the roster reads offline
    var dailyDay: String = ""
    var dailyProgress: Int = 0
    val dailyPaid = mutable.Set[UUID]() // who has already taken today's reward
    var bannerAt: Option[(String, BlockPos)] = None // hub key -> the banner's block, if raised

    def isComplete: Boolean = dailyProgress >= DailyGoal

  private val guilds = mutable.LinkedHashMap[String, Guild]() // lowercased name -> guild
  private val invites = mutable.Map[UUID, String]() // invited uuid -> guild key
  private var loaded = false

  // --- reading -------------------------------------------------------------------

  /** `/quest guild` - your guild's charter, roster and today's hunt; or who exists. */
  def status(player: ServerPlayer): String =
    ensureLoaded(player.server)
    guildOf(player.getUUID) match
      case None =>
        if invites.contains(player.getUUID) then
          val invitedTo = guilds.get(invites(player.getUUID)).map(_.name).getOrElse("a guild that has since folded")
          s"You hold an invitation to $invitedTo: /quest guild accept (or decline)."
        else if guilds.isEmpty then
          s"No guilds hold a charter here yet. Founding one costs $CreateGold gold: /quest guild create <name>"
        else
          val rows = guilds.values.toList.map { g =>
            s"  ${g.name} - ${g.members.size}/$MaxMembers members, led by ${g.names.getOrElse(g.leader, "?")}"
          }
          (List(s"No guild holds your name. You could found one for $CreateGold gold (/quest guild create <name>), or ask a member to invite you. Charters on record:") ::: rows)
            .mkString("\n")
      case Some(g) =>
        rollover(g)
        val roster = g.members.toList.map { u =>
          val who = g.names.getOrElse(u, "?")
          val online = Option(player.server.getPlayerList.getPlayer(u)).isDefined
          val mark = if online then "online" else "away"
          val role = if u == g.leader then " (founder)" else ""
          s"  $who - $mark$role"
        }
        val hunt =
          if g.isComplete then
            if g.dailyPaid.contains(player.getUUID) then "Today's hunt: complete, and you have taken your share."
            else s"Today's hunt: complete! Your $DailyRewardGold gold is unclaimed: /quest guild claim"
          else s"Today's hunt: ${g.dailyProgress}/$DailyGoal monsters felled by the guild"
        val bannerLine = g.bannerAt.flatMap { (key, _) => hubByServerKey(player.server, key).map(hub => s"Banner: flying at ${hubLabel(hub)}.") }
          .orElse(if g.bannerAt.isDefined then Some("Banner: its hub is gone now.") else None)
        val claimNote = if g.isComplete then " /quest guild claim" else ""
        (List(
          s"--- Guild: ${g.name} --- ${g.members.size}/$MaxMembers. Founder: ${g.names.getOrElse(g.leader, "?")}.",
          hunt,
          bannerLine.getOrElse(s"Banner: none raised. Any member may raise one at a hub for $BannerGold gold (/quest guild banner).")
        ) ::: "Roster:" :: roster ::: List(s"Ops: /quest guild invite <hero> | leave | claim | banner$claimNote"))
          .mkString("\n")

  // --- founding & membership -------------------------------------------------------

  /** `/quest guild create <name>` - the charter is bought, the banner is implied. */
  def create(player: ServerPlayer, name: String): String =
    ensureLoaded(player.server)
    if !Game.isJoined(player) then return "Join the realm first: /quest join"
    if guildOf(player.getUUID).nonEmpty then return "You already hold a charter. /quest guild leave first."
    val key = name.toLowerCase
    if name.length < MinNameLen || name.length > MaxNameLen then return s"Guild names run $MinNameLen to $MaxNameLen characters."
    if guilds.contains(key) then return s"A guild named '${guilds(key).name}' already holds a charter here."
    val purse = Game.countItem(player, Items.GOLD_NUGGET)
    if purse < CreateGold then return s"A charter costs $CreateGold gold - you carry $purse."
    Game.removeItems(player, Items.GOLD_NUGGET, CreateGold)
    val g = new Guild(name)
    g.leader = player.getUUID
    g.members += player.getUUID
    g.names(player.getUUID) = player.getName.getString
    guilds += key -> g
    save(player.server)
    Game.broadcast(
      player.server,
      Component
        .literal(s"${player.getName.getString} founds the guild '${g.name}' - a charter is signed for $CreateGold gold. /quest guild ${g.name.toLowerCase} to see it.")
        .withStyle(ChatFormatting.LIGHT_PURPLE)
    )
    s"The charter is signed: '${g.name}' stands with one member and a hunt of $DailyGoal monsters to share each day. Invite with /quest guild invite <hero>."

  /** `/quest guild invite <hero>` - any member may bring someone in. */
  def invite(player: ServerPlayer, hero: String): String =
    ensureLoaded(player.server)
    guildOf(player.getUUID) match
      case None => "You are not in a guild to invite anyone from. /quest guild lists the charters."
      case Some(g) =>
        Option(player.server.getPlayerList.getPlayerByName(hero)) match
          case None => s"No one online by that name."
          case Some(target) =>
            if !Game.isJoined(target) then s"${target.getName.getString} has not joined the realm yet (/quest join)."
            else if guildOf(target.getUUID).nonEmpty then s"${target.getName.getString} already holds a charter elsewhere."
            else if g.members.size >= MaxMembers then s"'${g.name}' is full ($MaxMembers is the table)."
            else
              invites(target.getUUID) = keyOf(g)
              Game.message(
                target,
                s"${player.getName.getString} invites you to join the guild '${g.name}'. /quest guild accept - or decline.",
                ChatFormatting.LIGHT_PURPLE
              )
              s"${target.getName.getString} is invited. The invitation waits until they answer."

  /** `/quest guild accept` */
  def accept(player: ServerPlayer): String =
    ensureLoaded(player.server)
    invites.remove(player.getUUID) match
      case None => "No invitation waits for you."
      case Some(key) =>
        guilds.get(key) match
          case None => "The guild that invited you is gone now."
          case Some(g) =>
            if guildOf(player.getUUID).nonEmpty then "You joined another charter in the meantime."
            else if g.members.size >= MaxMembers then s"'${g.name}' filled its table while you thought it over."
            else
              g.members += player.getUUID
              g.names(player.getUUID) = player.getName.getString
              save(player.server)
              Game.broadcast(
                player.server,
                Component.literal(s"${player.getName.getString} joins the guild '${g.name}'.")
                  .withStyle(ChatFormatting.LIGHT_PURPLE)
              )
              announceDaily(player, g)
              s"You are of '${g.name}' (${g.members.size}/$MaxMembers). Today's hunt: ${g.dailyProgress}/$DailyGoal - /quest guild reads the roster."

  /** `/quest guild decline` */
  def decline(player: ServerPlayer): String =
    invites.remove(player.getUUID) match
      case None => "No invitation waits for you."
      case Some(key) =>
        val name = guilds.get(key).map(_.name).getOrElse("the guild")
        s"You let the invitation to '$name' pass. It is gone; they will know."

  /** `/quest guild leave` - the banner goes back on its wall. */
  def leave(player: ServerPlayer): String =
    ensureLoaded(player.server)
    guildOf(player.getUUID) match
      case None => "You are not in a guild."
      case Some(g) =>
        val name = g.name
        g.members -= player.getUUID
        invites -= player.getUUID
        if g.members.isEmpty then
          removeBanner(player.server, g) // the banner comes down with the charter
          guilds -= keyOf(g)
          save(player.server)
          Game.broadcast(
            player.server,
            Component.literal(s"The guild '$name' folds its banner - its last member has left.")
              .withStyle(ChatFormatting.GRAY)
          )
          s"You leave '$name'. With no one left, the charter is retired."
        else
          val handed = g.leader == player.getUUID
          if handed then g.leader = g.members.head
          save(player.server)
          if handed then
            Game.message(player, s"Its charter passes to ${g.names.getOrElse(g.leader, "?")}.", ChatFormatting.YELLOW)
            s"You leave '$name'; the founder's seat is filled."
          else s"You leave '$name'."

  /** `/quest guild claim` - the shared daily pays every member who asks for it. */
  def claim(player: ServerPlayer): String =
    ensureLoaded(player.server)
    guildOf(player.getUUID) match
      case None => "You are not in a guild to claim with."
      case Some(g) =>
        rollover(g)
        if !g.isComplete then s"Today's hunt is not over: ${g.dailyProgress}/$DailyGoal felled by '${g.name}'. Every kill by any member counts."
        else if g.dailyPaid.contains(player.getUUID) then "You have already taken today's share. The hunt starts again at dawn."
        else
          g.dailyPaid += player.getUUID
          Game.giveCoins(player, DailyRewardGold)
          save(player.server)
          s"'${g.name}' completed today's hunt: $DailyRewardGold gold, and the guild's thanks. Tomorrow the count starts at zero."

  // --- the banner ------------------------------------------------------------------
  // A guild's name in the world: a real banner on a pole at a hub plaza, paid for in
  // gold like the barracks. One per guild; raising it elsewhere moves it. The mason's
  // daily round re-raises it if someone knocks it down.

  private def BannerGold: Int = Tuning.getInt("guild.banner_gold", 200)

  /** The guild's colours are its name's: the same guild always flies the same banner. */
  private val BannerPalette: List[Block] = List(
    Blocks.WHITE_BANNER, Blocks.ORANGE_BANNER, Blocks.MAGENTA_BANNER, Blocks.LIGHT_BLUE_BANNER,
    Blocks.YELLOW_BANNER, Blocks.LIME_BANNER, Blocks.PINK_BANNER, Blocks.GRAY_BANNER,
    Blocks.LIGHT_GRAY_BANNER, Blocks.CYAN_BANNER, Blocks.PURPLE_BANNER, Blocks.BLUE_BANNER,
    Blocks.BROWN_BANNER, Blocks.GREEN_BANNER, Blocks.RED_BANNER, Blocks.BLACK_BANNER
  )

  /** `/quest guild banner` - raise the guild's banner at the hub you stand at. */
  def banner(player: ServerPlayer): String =
    ensureLoaded(player.server)
    guildOf(player.getUUID) match
      case None => "You are not in a guild to raise a banner."
      case Some(g) =>
        val hubIndex = GuardWatch.hubNear(player).getOrElse(-1)
        TownBuilder.townByIndex(hubIndex + 1) match
          case None => "Stand at a hub plaza to raise the guild's banner there."
          case Some(hub) =>
            val level = player.server.overworld()
            if level == null then return "No overworld to raise it in."
            val gold = Game.countItem(player, Items.GOLD_NUGGET)
            if gold < BannerGold then return s"A banner costs $BannerGold gold for pole and cloth - you carry $gold."
            removeBanner(player.server, g) // a guild flies one banner: raising it here moves it
            placeBanner(level, hub, g) match
              case None => "There is no clear ground for a banner here - the plaza's edge is spoken for."
              case Some(pos) =>
                Game.removeItems(player, Items.GOLD_NUGGET, BannerGold)
                g.bannerAt = Some((hubKey(hub), pos))
                save(player.server)
                Game.broadcast(
                  player.server,
                  Component
                    .literal(s"The guild '${g.name}' raises its banner at ${hubLabel(hub)} - it flies from the plaza's edge now.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                )
                s"The guild's banner stands at ${hubLabel(hub)} ($BannerGold gold, ${gold - BannerGold} left). Its colours are your guild's own."

  private def hubKey(hub: TownBuilder.Town): String = s"${hub.base.getX},${hub.base.getZ}"

  private def hubByServerKey(server: MinecraftServer, key: String): Option[TownBuilder.Town] =
    TownBuilder.allTowns.find(t => hubKey(t) == key)

  private def hubLabel(hub: TownBuilder.Town): String =
    if hub.capital then "Hearthhome" else s"the hub at ${hub.base.getX}, ${hub.base.getZ}"

  private def bannerBlockFor(g: Guild): Block = BannerPalette(math.abs(g.name.hashCode) % BannerPalette.size)

  private def isAnyBanner(state: net.minecraft.world.level.block.state.BlockState): Boolean =
    BannerPalette.exists(b => state.is(b))

  private def placeBanner(level: ServerLevel, hub: TownBuilder.Town, g: Guild): Option[BlockPos] =
    val stand = TownBuilder.guildBannerStand(hub)
    val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, stand.getX, stand.getZ)
    val bannerPos = new BlockPos(stand.getX, y, stand.getZ)
    val pedestal = bannerPos.below()
    if !level.getBlockState(bannerPos).isAir && !isAnyBanner(level.getBlockState(bannerPos)) then None
    else
      if level.getBlockState(pedestal).isAir then level.setBlock(pedestal, Blocks.SMOOTH_STONE.defaultBlockState, 3)
      level.setBlock(bannerPos, bannerBlockFor(g).defaultBlockState.setValue(BlockStateProperties.HORIZONTAL_FACING, facingToward(bannerPos, hub.base)), 3)
      Some(bannerPos)

  private def removeBanner(server: MinecraftServer, g: Guild): Unit =
    val level = server.overworld()
    if level == null then return
    g.bannerAt.foreach { (_, pos) =>
      if isAnyBanner(level.getBlockState(pos)) then level.setBlock(pos, Blocks.AIR.defaultBlockState, 3)
    }
    g.bannerAt = None

  /** The mason's daily round: a knocked-down guild banner goes back up for free. */
  private[aetheronchaos] def ensureBanners(server: MinecraftServer): Unit =
    if !loaded then return
    val level = server.overworld()
    if level == null then return
    guilds.values.foreach { g =>
      g.bannerAt.foreach { (key, pos) =>
        if !isAnyBanner(level.getBlockState(pos)) then
          hubByServerKey(server, key).foreach(hub => placeBanner(level, hub, g).foreach(p => g.bannerAt = Some((key, p))))
      }
    }

  private def facingToward(from: BlockPos, to: BlockPos): Direction =
    val dx = to.getX - from.getX
    val dz = to.getZ - from.getZ
    if math.abs(dx) >= math.abs(dz) then (if dx >= 0 then Direction.EAST else Direction.WEST)
    else if dz >= 0 then Direction.SOUTH else Direction.NORTH

  /** The guilds whose banners fly above this hub - their members hold it for their own name. */
  private[aetheronchaos] def bannerPride(server: MinecraftServer, hub: TownBuilder.Town): List[String] =
    ensureLoaded(server)
    banneredAt(hub).map(_.name).sorted

  /** Tell a besieged hub's bannered guilds, member by member, what holding it means. */
  private[aetheronchaos] def noticePride(server: MinecraftServer, hub: TownBuilder.Town): Unit =
    banneredAt(hub).foreach { g =>
      g.members.toList.flatMap(u => Option(server.getPlayerList.getPlayer(u))).foreach { m =>
        Game.message(
          m,
          s"Your guild's banner flies at ${hubLabel(hub)}. Hold this ground: every siege kill counts twice toward the guild's hunt, and your pay will carry a pride bonus.",
          ChatFormatting.LIGHT_PURPLE
        )
      }
    }

  /** One more hunt credit for a kill fought under the guild's own banner. */
  private[aetheronchaos] def prideCredit(player: ServerPlayer, hub: TownBuilder.Town): Option[String] =
    guildOf(player.getUUID) match
      case Some(g) if g.bannerAt.exists { (key, _) => key == hubKey(hub) } =>
        creditHunt(g, 1, player.server)
        Some(g.name)
      case None => None

  private def banneredAt(hub: TownBuilder.Town): List[Guild] =
    guilds.values.toList.filter(g => g.bannerAt.exists { (key, _) => key == hubKey(hub) }).sortBy(_.name)

  // --- the shared daily ------------------------------------------------------------

  /** Every kill by a member feeds the guild's hunt. Called from Game.awardKill. */
  private[aetheronchaos] def onKill(player: ServerPlayer): Unit =
    guildOf(player.getUUID).foreach(g => creditHunt(g, 1, player.server))

  private def creditHunt(g: Guild, amount: Int, server: MinecraftServer): Unit =
    rollover(g)
    if !g.isComplete then
      g.dailyProgress += amount
      if g.isComplete then
        val guildLine = Component
          .literal(s"The guild '${g.name}' has completed today's hunt (${DailyGoal} felled). Members: /quest guild claim for your $DailyRewardGold gold.")
          .withStyle(ChatFormatting.GOLD)
        g.members.toList.flatMap(u => Option(server.getPlayerList.getPlayer(u))).foreach { m =>
          Game.message(m, s"'${g.name}': the hunt is done - ${DailyGoal} felled. Your share: /quest guild claim.", ChatFormatting.GOLD)
        }
        Game.broadcast(server, guildLine)
        save(server)

  /** A member joining mid-hunt should know where it stands. */
  private def announceDaily(player: ServerPlayer, g: Guild): Unit =
    rollover(g)
    if !g.isComplete then
      Game.message(player, s"Today's hunt for '${g.name}': ${g.dailyProgress}/$DailyGoal. Every kill by any member counts.", ChatFormatting.AQUA)

  /** The hunt belongs to one day: on first contact with a new day, the count resets. */
  private def rollover(g: Guild): Unit =
    val today = java.time.LocalDate.now().toString
    if g.dailyDay != today then
      g.dailyDay = today
      g.dailyProgress = 0
      g.dailyPaid.clear()

  // --- lookups used by other systems -------------------------------------------------

  private def keyOf(g: Guild): String = g.name.toLowerCase

  private[aetheronchaos] def guildOf(uuid: UUID): Option[Guild] =
    guilds.values.find(_.members.contains(uuid))

  /** The guild tag for join broadcasts: "Name [Guild] has entered the realm." */
  private[aetheronchaos] def tagOf(player: ServerPlayer): String =
    ensureLoaded(player.server)
    guildOf(player.getUUID).map(g => s" [${g.name}]").getOrElse("")

  /** The guild name behind a hero, for the leaderboard. */
  private[aetheronchaos] def tagName(server: MinecraftServer, uuid: UUID): Option[String] =
    ensureLoaded(server)
    guildOf(uuid).map(_.name)

  // --- persistence ---------------------------------------------------------------------

  private def path(server: MinecraftServer): Path =
    server.getWorldPath(LevelResource.ROOT).resolve(FileName)

  private def ensureLoaded(server: MinecraftServer): Unit =
    if loaded then return
    loaded = true
    val p = path(server)
    if !Files.exists(p) then return
    Try {
      val root = TagParser.parseTag(Files.readString(p))
      root.getList("guilds", Tag.TAG_COMPOUND).asScala.foreach {
        case e: CompoundTag =>
          val g = new Guild(e.getString("name"))
          g.leader = UUID.fromString(e.getString("leader"))
          e.getList("members", Tag.TAG_COMPOUND).asScala.foreach {
            case m: CompoundTag =>
              val u = UUID.fromString(m.getString("uuid"))
              g.members += u
              g.names(u) = m.getString("name")
            case _ => ()
          }
          if g.members.nonEmpty then
            if e.contains("bannerHub") then
              g.bannerAt = Some((e.getString("bannerHub"), new BlockPos(e.getInt("bx"), e.getInt("by"), e.getInt("bz"))))
            guilds += g.name.toLowerCase -> g
        case _ => ()
      }
    }

  /** Membership changes are rare; save them straight away. Daily counts save on completion. */
  private def save(server: MinecraftServer): Unit =
    try
      val list = new ListTag
      guilds.values.foreach { g =>
        val e = new CompoundTag
        e.putString("name", g.name)
        e.putString("leader", g.leader.toString)
        val members = new ListTag
        g.members.foreach { u =>
          val m = new CompoundTag
          m.putString("uuid", u.toString)
          m.putString("name", g.names.getOrElse(u, "?"))
          members.add(m)
        }
        e.put("members", members)
        g.bannerAt.foreach { (key, pos) =>
          e.putString("bannerHub", key)
          e.putInt("bx", pos.getX)
          e.putInt("by", pos.getY)
          e.putInt("bz", pos.getZ)
        }
        list.add(e)
      }
      val root = new CompoundTag
      root.put("guilds", list)
      val p = path(server)
      Files.createDirectories(p.getParent)
      Files.writeString(p, root.getAsString)
    catch case e: Throwable => System.err.println(s"[aetheron-chaos] failed to save the guilds: $e")
