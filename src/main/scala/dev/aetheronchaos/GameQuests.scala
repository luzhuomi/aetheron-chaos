package dev.aetheronchaos

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * The quest half of the realm state: the log (take/abandon), the action-bar tracker
 * with its compass navigation, turn-in crediting and the clickable board menu.
 * Quest definitions live in Quests.scala; XP awarding stays in Game.addXp.
 */
object GameQuests:
  // quest tracker / navigation
  val MaxTrackedQuests = 4
  private val NavRefreshTicks = 40L
  private val NavScanRadius = 96.0
  private val CompassArrows = Array("↑", "↖", "←", "↙", "↓", "↘", "→", "↗")
  // quest tracker navigation cache: player uuid -> list of (questId, compass text)
  private val navCache = mutable.Map[UUID, List[(String, String)]]()

  /** Called from Game.tick: refresh the compass cache on its own interval. */
  private[aetheronchaos] def tickNavigation(server: MinecraftServer): Unit =
    if Game.now % NavRefreshTicks == 0 then
      server.getPlayerList.getPlayers.asScala.filter(Game.isJoined).foreach(refreshNav)

  def takeQuest(player: ServerPlayer, id: String): String =
    val c = Game.ensure(player.getUUID)
    if !c.joined then return "Join the realm first: /quest join"
    Quests.byId(id) match
      case None =>
        val hint = didYouMean(id).map(s => s" Did you mean /quest take $s?").getOrElse("")
        s"Unknown quest '$id'.$hint See the board: /quest board"
      case Some(q) =>
        if c.taken.contains(q.id) then "That quest is already in your log."
        else if c.level < q.minLevel then s"You must be level ${q.minLevel} for '${q.name}'. Current level: ${c.level}."
        else
          c.taken += q.id
          if c.tracked.size < MaxTrackedQuests then c.tracked += q.id
          refreshNav(player)
          val trackNote = if c.tracked.contains(q.id) then " (tracked)" else ""
          s"Quest accepted: ${q.name}$trackNote - ${Quests.objectiveText(q)}. Hint: ${q.hint}"

  def abandonQuest(player: ServerPlayer, id: String): String =
    val c = Game.ensure(player.getUUID)
    if !c.taken.remove(id) then "That quest is not in your log."
    else
      c.tracked -= id
      c.progress -= id
      navCache -= player.getUUID
      s"Abandoned '$id'."

  // --- quest tracker: the action bar follows these ------------------------------

  def track(player: ServerPlayer, id: String): String =
    val c = Game.ensure(player.getUUID)
    Quests.byId(id) match
      case None =>
        val hint = didYouMean(id).map(s => s" Did you mean /quest track $s?").getOrElse("")
        s"No such quest: $id.$hint"
      case Some(q) =>
        if !c.taken.contains(id) then s"Accept '${q.name}' first: /quest take ${q.id}"
        else if c.tracked.contains(id) then s"'${q.name}' is already tracked."
        else if c.tracked.size >= MaxTrackedQuests then
          s"Tracker is full ($MaxTrackedQuests). Untrack one first: /quest untrack <id>"
        else
          c.tracked += id
          refreshNav(player)
          s"Tracking '${q.name}' - the action bar now points the way."

  def untrack(player: ServerPlayer, id: String): String =
    val c = Game.ensure(player.getUUID)
    if !c.tracked.contains(id) then s"You are not tracking '$id'."
    else
      c.tracked -= id
      navCache -= player.getUUID
      Quests.byId(id).map(q => s"Stopped tracking '${q.name}'.") getOrElse "Stopped tracking."

  /** Per-quest compass strings for the action bar, refreshed on NavRefreshTicks. */
  private[aetheronchaos] def trackerCompass(player: ServerPlayer): List[(String, String)] =
    navCache.getOrElse(player.getUUID, Nil)

  private[aetheronchaos] def refreshNav(player: ServerPlayer): Unit =
    val c = Game.ensure(player.getUUID)
    navCache(c.uuid) = c.tracked.toList.flatMap { qid =>
      Quests.byId(qid).flatMap(q => navTarget(player, q).map(t => (qid, compassText(player, t))))
    }

  /** Where a quest's next step physically is: the objective target, or the board once it's done. */
  private def navTarget(player: ServerPlayer, q: Quest): Option[(Vec3, String)] =
    val level = player.level
    if objectiveMet(player, q) then
      TownBuilder.nearestBoard(player).map(bp => (Vec3.atBottomCenterOf(bp), "quest board"))
    else
      q.objective match
        case Objective.Kill(target, _, label) =>
          val found = level
            .getEntitiesOfClass(classOf[Entity], scanBox(player), e => e.isAlive && e.getType == target)
            .asScala
            .minByOption(e => player.blockPosition.distSqr(e.blockPosition()))
          found.map(e => (e.position(), label))
        case Objective.Collect(item, _) =>
          val name = ItemStack(item).getHoverName.getString
          val found = level
            .getEntitiesOfClass(classOf[ItemEntity], scanBox(player), ei => ei.getItem.is(item))
            .asScala
            .minByOption(e => player.blockPosition.distSqr(e.blockPosition()))
          found.map(e => (e.position(), s"$name (dropped)"))

  private def scanBox(player: ServerPlayer): AABB =
    val p = player.position()
    new AABB(
      p.x - NavScanRadius, p.y - NavScanRadius, p.z - NavScanRadius,
      p.x + NavScanRadius, p.y + NavScanRadius, p.z + NavScanRadius
    )

  /** "↗ 43m zombies" - 8-way arrow relative to where the player is facing. */
  private def compassText(player: ServerPlayer, t: (Vec3, String)): String =
    val d = t._1.subtract(player.position())
    val dist = math.sqrt(d.x * d.x + d.z * d.z).toInt
    val look = player.getLookAngle
    val rel = math.atan2(d.z, d.x) - math.atan2(look.z, look.x)
    val idx = ((math.round(math.toDegrees(rel) / 45.0).toInt % 8) + 8) % 8
    s"${CompassArrows(idx)} ${dist}m ${t._2}"

  private[aetheronchaos] def progressOf(player: ServerPlayer, q: Quest): Int =
    val c = Game.ensure(player.getUUID)
    q.objective match
      case Objective.Kill(_, _, _) => c.progress.getOrElse(q.id, 0)
      case Objective.Collect(item, _) => Game.countItem(player, item)

  private def objectiveMet(player: ServerPlayer, q: Quest): Boolean =
    q.objective match
      case Objective.Kill(_, count, _) => Game.ensure(player.getUUID).progress.getOrElse(q.id, 0) >= count
      case Objective.Collect(item, count) => Game.countItem(player, item) >= count

  def turnIn(player: ServerPlayer, id: String): String =
    val c = Game.ensure(player.getUUID)
    if !c.taken.contains(id) then
      return s"'$id' is not in your log, so there is nothing to turn in. To ACCEPT a quest: /quest board then LEFT-click its blue line, or type /quest take $id (Tab completes ids)."
    Quests.byId(id) match
      case None => "Unknown quest."
      case Some(q) =>
        if TownBuilder.boardCount > 0 && !TownBuilder.nearBoard(player) then
          return "Return to the quest board in town to turn quests in."
        if !objectiveMet(player, q) then
          val have = progressOf(player, q)
          val why = q.objective match
            case Objective.Kill(_, count, _) => s"Not yet: $have/$count. Keep going."
            case Objective.Collect(item, count) => s"You need $count ${ItemStack(item).getHoverName.getString} in your inventory (you have $have)."
          return why
        // shared turn-in: party members holding this quest and ready at the board turn in too
        val mates = Game.partyMates(player).filter(m =>
          Game.ensure(m.getUUID).taken.contains(id) && objectiveMet(m, q) && TownBuilder.nearBoard(m)
        )
        val partySize = mates.size + 1
        creditQuest(player, q, partySize)
        mates.foreach(m => creditQuest(m, q, partySize))
        val withParty = if mates.nonEmpty then s" together with ${mates.size} party member(s)" else ""
        val bonus = if q.partyOnly && partySize >= 2 then s" (group quest: +${(Game.PartyQuestXpBonusPercent * 100).toInt}% XP)" else ""
        Game.broadcast(
          player.server,
          Component
            .literal(s"${c.displayName} completed '${q.name}'$withParty! +${q.xp} XP, +${q.coins} gold${if q.reward.nonEmpty then " and a reward" else ""}$bonus")
            .withStyle(ChatFormatting.GREEN)
        )
        val sharedNote = if mates.nonEmpty then s" ${mates.size} party member(s) credited too." else ""
        s"Quest complete: ${q.name} (+${q.xp} XP, +${q.coins} gold${if q.reward.nonEmpty then s", ${q.reward.get.name}" else ""})$bonus. $sharedNote".trim

  /** Credits one hero for a completed quest: consumes the objective, grants rewards. */
  private def creditQuest(member: ServerPlayer, q: Quest, partySize: Int): Unit =
    val mc = Game.ensure(member.getUUID)
    mc.taken -= q.id
    mc.tracked -= q.id
    mc.progress -= q.id
    q.objective match
      case Objective.Collect(item, count) => Game.removeItems(member, item, count)
      case _ => ()
    Game.giveItem(member, Loot.coinStack(q.coins))
    q.reward.foreach(g => Game.giveItem(member, Loot.stack(g, member.level.registryAccess())))
    val bonusXp = if q.partyOnly && partySize >= 2 then (q.xp * Game.PartyQuestXpBonusPercent).toLong else 0L
    Game.addXp(member, q.xp + bonusXp)
    member.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f)

  /** The quest-board menu: turn-in offers on top, then quests you may take. Both are clickable. */
  def boardMenu(player: ServerPlayer): Unit =
    val c = Game.ensure(player.getUUID)
    if !c.joined then
      Game.message(player, Game.join(player), ChatFormatting.GOLD)
      return
    Game.message(player, "--- Quest Board ---", ChatFormatting.GOLD)
    val ready = c.taken.toList.flatMap(Quests.byId).filter(q => objectiveMet(player, q))
    if ready.nonEmpty then
      Game.message(player, "Ready to turn in (left-click):", ChatFormatting.GREEN)
      ready.foreach(q =>
        player.displayClientMessage(
          Component
            .literal(s"[TURN IN] ${q.name}")
            .withStyle(s => s.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, s"/quest turnin ${q.id}"))),
          false
        )
      )
    val available = Quests.all.filter(q => !c.taken.contains(q.id) && q.minLevel <= c.level)
    if available.nonEmpty then
      Game.message(player, "LEFT-click a line to accept (press T first to free the mouse). GROUP quests share progress and rewards with your party:", ChatFormatting.AQUA)
      available.foreach(q =>
        val tag = if q.partyOnly then "[GROUP] " else ""
        player.displayClientMessage(
          Component
            .literal(s"$tag[${q.name}] ${q.minLevel}o - ${Quests.objectiveText(q)} -> ${q.xp} XP, ${q.coins} gold")
            .withStyle(s => s.withColor(ChatFormatting.AQUA).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, s"/quest take ${q.id}"))),
          false
        )
      )
    val taken = c.taken.toList.flatMap(Quests.byId)
    if taken.nonEmpty then
      Game.message(player, "In your log:", ChatFormatting.YELLOW)
      taken.foreach(q =>
        val have = progressOf(player, q)
        val goal = q.objective match
          case Objective.Kill(_, n, _) => n
          case Objective.Collect(_, n) => n
        Game.message(player, s"${q.name}: $have/$goal - ${Quests.objectiveText(q)}", ChatFormatting.YELLOW)
      )
    if available.isEmpty && taken.isEmpty && ready.isEmpty then Game.message(player, "Nothing for you today, adventurer.", ChatFormatting.GRAY)

  /** Typo help: the closest quest id within edit distance 3, e.g. bone-copllection -> bone-collection. */
  private def didYouMean(input: String): Option[String] =
    Quests.all
      .map(_.id)
      .map(id => (Game.editDistance(id.toLowerCase, input.toLowerCase), id))
      .filter(_._1 <= 3)
      .sortBy(_._1)
      .headOption
      .map(_._2)
