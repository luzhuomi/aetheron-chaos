package dev.azerothchaos

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Random

import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.Vec3
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.server.level.ServerLevel

/**
 * Holds the persistent realm state: hero characters (class, level, XP, quest log),
 * parties, quest progress, and the rare-elite spawn scheduler.
 *
 * Gameplay loop (MMORPG style):
 *  - /quest join to create your hero, /quest class to pick one (WoW-style classes).
 *  - Slay mobs for XP, level up (more health/damage), loot rolls on every kill.
 *  - Take quests at the town board (/quest town), turn them in at the board.
 *  - Form parties for XP sharing; rare named elites spawn on a timer for big rewards.
 */
object Game:
  // Tuning knobs
  val MaxLevel = 40
  val PartyMaxSize = 5
  val PartyShareRangeBlocks = 40.0
  val PartySharePercent = 0.5
  val DeathXpPenalty = 0.10
  val RareIntervalTicks = 12L * 60L * 20L
  val RareXpMultiplier = 10
  val PartyQuestXpBonusPercent = 0.5

  // characters autosave to disk while online (crash safety net; normal saves happen on disconnect)
  val AutosaveIntervalTicks = 5L * 60L * 20L

  // quest tracker / navigation
  val MaxTrackedQuests = 4
  private val NavRefreshTicks = 40L
  private val NavScanRadius = 96.0
  private val CompassArrows = Array("↑", "↖", "←", "↙", "↓", "↘", "→", "↗")

  final class Character(val uuid: UUID):
    var displayName: String = "?"
    var classId: String = ""
    var level: Int = 1
    var xp: Long = 0L
    var joined: Boolean = false
    val taken = mutable.LinkedHashSet[String]()
    // which of the taken quests the action-bar tracker follows (max MaxTrackedQuests)
    val tracked = mutable.LinkedHashSet[String]()
    val progress = mutable.Map[String, Int]()
    var party: Option[String] = None
    var kills: Int = 0
    var rareKills: Int = 0
    var deaths: Int = 0

  private val characters = mutable.Map[UUID, Character]()
  private val parties = mutable.Map[String, mutable.LinkedHashSet[UUID]]()
  private val rng = Random()
  private var elapsedTicks = 0L
  private var nextRareTick = 200L // first rare appears 10 seconds into the server run
  private var nextAutosaveTick = AutosaveIntervalTicks
  // quest tracker navigation cache: player uuid -> list of (questId, compass text)
  private val navCache = mutable.Map[UUID, List[(String, String)]]()
  private var rareAnnounced = 0

  def xpToNext(level: Int): Long = 100L + 60L * level + 35L * level * level

  def isJoined(player: ServerPlayer): Boolean = characters.get(player.getUUID).exists(_.joined)

  private def ensure(uuid: UUID): Character = characters.getOrElseUpdate(uuid, new Character(uuid))

  private[azerothchaos] def characterFor(player: ServerPlayer): Character = ensure(player.getUUID)

  private def heroClassOf(c: Character): Option[HeroClass] = HeroClasses.byId(c.classId)

  // --- joining the realm -----------------------------------------------------

  def join(player: ServerPlayer): String =
    CharacterStore.loadFor(player.server, player.getUUID)
    val c = ensure(player.getUUID)
    c.displayName = player.getName.getString
    if c.joined then return "You are already in the realm."
    c.joined = true
    applyBonuses(player)
    val hc = heroClassOf(c)
    if hc.isEmpty then
      s"Welcome, ${player.getName.getString}! Choose a class: /quest class warrior|mage|hunter|priest|rogue|paladin. " +
        "Take quests from the town questmaster (or the loom board). Slay monsters to level up."
    else
      s"Welcome back to the realm, ${c.displayName} - Lv ${c.level} ${hc.get.name}."

  def onJoin(player: ServerPlayer, server: MinecraftServer): Unit =
    CharacterStore.loadFor(server, player.getUUID)
    // a hero with no entry on disk has never played this realm: give them a town and a home
    if !CharacterStore.readAll(server).contains(player.getUUID) then TownBuilder.greetNewcomer(player)
    val c = ensure(player.getUUID)
    if c.classId.nonEmpty || c.level > 1 || c.kills > 0 then
      c.joined = true
      c.displayName = player.getName.getString
      applyBonuses(player)
      broadcast(
        server,
        Component
          .literal(s"${c.displayName} (Lv ${c.level} ${heroClassOf(c).map(_.name).getOrElse("Adventurer")}) has entered the realm.")
          .withStyle(ChatFormatting.LIGHT_PURPLE)
      )

  def leave(player: ServerPlayer): String =
    val c = ensure(player.getUUID)
    if !c.joined then return "You are not in the realm."
    c.joined = false
    leaveParty(player)
    "You left the realm. Rejoin anytime with /quest join."

  def onDisconnect(player: ServerPlayer, server: MinecraftServer): Unit =
    val c = ensure(player.getUUID)
    c.joined = false
    leaveParty(player)
    CharacterStore.save(server)

  /** Server is stopping (clean /stop): persist everyone before the world closes. */
  def onServerStopping(server: MinecraftServer): Unit =
    CharacterStore.save(server)
    TownStore.save(server)

  def afterRespawn(player: ServerPlayer): Unit =
    val c = ensure(player.getUUID)
    if c.joined then applyBonuses(player)

  // --- class & attribute bonuses ---------------------------------------------

  def setClass(player: ServerPlayer, id: String): String =
    val c = ensure(player.getUUID)
    if !c.joined then return "Join the realm first: /quest join"
    HeroClasses.byId(id) match
      case None => s"Unknown class '$id'. Choose: ${HeroClasses.all.map(_.id).mkString(", ")}."
      case Some(hc) =>
        heroClassOf(c).foreach(old => old.passive.foreach(player.removeEffect))
        c.classId = hc.id
        applyBonuses(player)
        hc.gear.foreach(g => giveItem(player, Loot.stack(g, player.level.registryAccess())))
        broadcast(
          player.server,
          Component.literal(s"${c.displayName} has become a Lv ${c.level} ${hc.name}!").withStyle(ChatFormatting.LIGHT_PURPLE)
        )
        s"You are now a ${hc.name}: ${hc.tagline} Starting gear granted."

  private[azerothchaos] def applyBonuses(player: ServerPlayer): Unit =
    val c = ensure(player.getUUID)
    val hc = heroClassOf(c)
    val attributes = Seq(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED)
    attributes.foreach { holder =>
      Option(player.getAttribute(holder)).foreach { instance =>
        instance.getModifiers.asScala.filter(_.id.getNamespace == HeroClasses.Namespace).toList.foreach(m =>
          instance.removeModifier(m)
        )
      }
    }
    val classHealth = hc.map(_.healthBonus).getOrElse(0.0)
    val classDamage = hc.map(_.damageBonus).getOrElse(0.0)
    val classSpeed = hc.map(_.speedBonus).getOrElse(0.0)
    val levelHealth = 2.0 * (c.level - 1)
    val levelDamage = 0.5 * ((c.level - 1) / 4)

    def add(holder: Holder[net.minecraft.world.entity.ai.attributes.Attribute], name: String, amount: Double, op: Operation): Unit =
      if amount != 0.0 then
        Option(player.getAttribute(holder)).foreach(
          _.addTransientModifier(AttributeModifier(HeroClasses.modifierId(name), amount, op))
        )

    add(Attributes.MAX_HEALTH, "health", classHealth + levelHealth, Operation.ADD_VALUE)
    add(Attributes.ATTACK_DAMAGE, "damage", classDamage + levelDamage, Operation.ADD_VALUE)
    add(Attributes.MOVEMENT_SPEED, "speed", classSpeed, Operation.ADD_MULTIPLIED_TOTAL)
    hc.foreach(_.passive.foreach(effect => player.addEffect(MobEffectInstance(effect, -1, 0, false, true))))
    if player.getHealth > player.getMaxHealth then player.setHealth(player.getMaxHealth)

  // --- XP, levels, kills ------------------------------------------------------

  private[azerothchaos] def awardKill(player: ServerPlayer, victim: Mob): Unit =
    val c = ensure(player.getUUID)
    if !c.joined then return
    val rare = victim.hasCustomName
    c.kills += 1
    if rare then c.rareKills += 1
    val mates = partyMates(player)
    advanceQuests(player, victim)
    Loot.onKill(victim, player, mates)
    val xp = Enemies.xpFor(victim) * (if rare then RareXpMultiplier else 1)
    addXp(player, xp)
    // party rules (WoW-style): XP sharing and shared quest progress for members in range
    mates.foreach { mate =>
      addXp(mate, math.max(1L, (xp * PartySharePercent).toLong))
      advanceQuests(mate, victim)
    }

  /** Advances the kill objectives of every quest the member holds. */
  private def advanceQuests(member: ServerPlayer, victim: Mob): Unit =
    val mc = ensure(member.getUUID)
    mc.taken.toList.foreach(qid =>
      Quests.byId(qid).foreach(q =>
        q.objective match
          case Objective.Kill(target, count, _) if victim.getType == target =>
            val now = mc.progress.getOrElse(qid, 0) + 1
            mc.progress(qid) = now
            if now == count then
              message(member, s"Objective complete: ${q.name}! Turn it in at the quest board.", ChatFormatting.GREEN)
            else if now < count then
              message(member, s"${q.name}: $now/$count", ChatFormatting.GRAY)
          case _ => ()
      )
    )

  /** Online party members within XP-share range. */
  private def partyMates(player: ServerPlayer): List[ServerPlayer] =
    val c = ensure(player.getUUID)
    c.party.toList.flatMap { pname =>
      parties.getOrElse(pname, mutable.LinkedHashSet[UUID]()).toList.flatMap { muuid =>
        if muuid == c.uuid then Nil
        else
          Option(player.server.getPlayerList.getPlayer(muuid))
            .filter(m => ensure(muuid).joined && player.distanceToSqr(m) <= PartyShareRangeBlocks * PartyShareRangeBlocks)
      }
    }

  private[azerothchaos] def onPlayerDeath(player: ServerPlayer): Unit =
    val c = ensure(player.getUUID)
    if !c.joined then return
    c.deaths += 1
    if c.xp > 0 then
      val lost = (c.xp * DeathXpPenalty).toLong
      c.xp -= lost
      message(player, s"You died. The effort set you back $lost XP.", ChatFormatting.RED)

  private def addXp(player: ServerPlayer, amount: Long): Unit =
    val c = ensure(player.getUUID)
    c.xp += amount
    var leveled = false
    while c.level < MaxLevel && c.xp >= xpToNext(c.level) do
      c.xp -= xpToNext(c.level)
      c.level += 1
      leveled = true
    if leveled then
      applyBonuses(player)
      player.setHealth(player.getMaxHealth)
      broadcast(
        player.server,
        Component.literal(s"${c.displayName} reached level ${c.level}!").withStyle(ChatFormatting.GOLD)
      )
      player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f)
      player.connection.send(ClientboundSetTitlesAnimationPacket(10, 70, 20))
      player.connection.send(ClientboundSetTitleTextPacket(Component.literal(s"Level ${c.level}!").withStyle(ChatFormatting.GOLD)))
      player.connection.send(ClientboundSetSubtitleTextPacket(Component.literal(heroClassOf(c).map(_.name).getOrElse("Adventurer"))))

  // --- quests ----------------------------------------------------------------

  def takeQuest(player: ServerPlayer, id: String): String =
    val c = ensure(player.getUUID)
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
    val c = ensure(player.getUUID)
    if !c.taken.remove(id) then "That quest is not in your log."
    else
      c.tracked -= id
      c.progress -= id
      navCache -= player.getUUID
      s"Abandoned '$id'."

  // --- quest tracker: the action bar follows these ------------------------------

  def track(player: ServerPlayer, id: String): String =
    val c = ensure(player.getUUID)
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
    val c = ensure(player.getUUID)
    if !c.tracked.contains(id) then s"You are not tracking '$id'."
    else
      c.tracked -= id
      navCache -= player.getUUID
      Quests.byId(id).map(q => s"Stopped tracking '${q.name}'.") getOrElse "Stopped tracking."

  /** Per-quest compass strings for the action bar, refreshed on NavRefreshTicks. */
  private[azerothchaos] def trackerCompass(player: ServerPlayer): List[(String, String)] =
    navCache.getOrElse(player.getUUID, Nil)

  private def refreshNav(player: ServerPlayer): Unit =
    val c = ensure(player.getUUID)
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

  private def scanBox(player: ServerPlayer): net.minecraft.world.phys.AABB =
    val p = player.position()
    new net.minecraft.world.phys.AABB(
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

  private[azerothchaos] def progressOf(player: ServerPlayer, q: Quest): Int =
    val c = ensure(player.getUUID)
    q.objective match
      case Objective.Kill(_, _, _) => c.progress.getOrElse(q.id, 0)
      case Objective.Collect(item, _) => countItem(player, item)

  private def objectiveMet(player: ServerPlayer, q: Quest): Boolean =
    q.objective match
      case Objective.Kill(_, count, _) => ensure(player.getUUID).progress.getOrElse(q.id, 0) >= count
      case Objective.Collect(item, count) => countItem(player, item) >= count

  private def countItem(player: ServerPlayer, item: Item): Int =
    val inv = player.getInventory
    (inv.items.asScala.toSeq ++ inv.offhand.asScala.toSeq).filter(_.getItem == item).map(_.getCount).sum

  private def removeItems(player: ServerPlayer, item: Item, count: Int): Unit =
    var left = count
    player.getInventory.items.asScala.foreach { stack =>
      if left > 0 && stack.getItem == item then
        val take = math.min(left, stack.getCount)
        stack.shrink(take)
        left -= take
    }

  def turnIn(player: ServerPlayer, id: String): String =
    val c = ensure(player.getUUID)
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
        val mates = partyMates(player).filter(m =>
          ensure(m.getUUID).taken.contains(id) && objectiveMet(m, q) && TownBuilder.nearBoard(m)
        )
        val partySize = mates.size + 1
        creditQuest(player, q, partySize)
        mates.foreach(m => creditQuest(m, q, partySize))
        val withParty = if mates.nonEmpty then s" together with ${mates.size} party member(s)" else ""
        val bonus = if q.partyOnly && partySize >= 2 then s" (group quest: +${(PartyQuestXpBonusPercent * 100).toInt}% XP)" else ""
        broadcast(
          player.server,
          Component
            .literal(s"${c.displayName} completed '${q.name}'$withParty! +${q.xp} XP, +${q.coins} gold${if q.reward.nonEmpty then " and a reward" else ""}$bonus")
            .withStyle(ChatFormatting.GREEN)
        )
        val sharedNote = if mates.nonEmpty then s" ${mates.size} party member(s) credited too." else ""
        s"Quest complete: ${q.name} (+${q.xp} XP, +${q.coins} gold${if q.reward.nonEmpty then s", ${q.reward.get.name}" else ""})$bonus. $sharedNote".trim

  /** Credits one hero for a completed quest: consumes the objective, grants rewards. */
  private def creditQuest(member: ServerPlayer, q: Quest, partySize: Int): Unit =
    val mc = ensure(member.getUUID)
    mc.taken -= q.id
    mc.tracked -= q.id
    mc.progress -= q.id
    q.objective match
      case Objective.Collect(item, count) => removeItems(member, item, count)
      case _ => ()
    giveItem(member, Loot.coinStack(q.coins))
    q.reward.foreach(g => giveItem(member, Loot.stack(g, member.level.registryAccess())))
    val bonusXp = if q.partyOnly && partySize >= 2 then (q.xp * PartyQuestXpBonusPercent).toLong else 0L
    addXp(member, q.xp + bonusXp)
    member.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f)

  /** The quest-board menu: turn-in offers on top, then quests you may take. Both are clickable. */
  def boardMenu(player: ServerPlayer): Unit =
    val c = ensure(player.getUUID)
    if !c.joined then
      message(player, join(player), ChatFormatting.GOLD)
      return
    message(player, "--- Quest Board ---", ChatFormatting.GOLD)
    val ready = c.taken.toList.flatMap(Quests.byId).filter(q => objectiveMet(player, q))
    if ready.nonEmpty then
      message(player, "Ready to turn in (left-click):", ChatFormatting.GREEN)
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
      message(player, "LEFT-click a line to accept (press T first to free the mouse). GROUP quests share progress and rewards with your party:", ChatFormatting.AQUA)
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
      message(player, "In your log:", ChatFormatting.YELLOW)
      taken.foreach(q =>
        val have = progressOf(player, q)
        val goal = q.objective match
          case Objective.Kill(_, n, _) => n
          case Objective.Collect(_, n) => n
        message(player, s"${q.name}: $have/$goal - ${Quests.objectiveText(q)}", ChatFormatting.YELLOW)
      )
    if available.isEmpty && taken.isEmpty && ready.isEmpty then message(player, "Nothing for you today, adventurer.", ChatFormatting.GRAY)

  // --- parties ----------------------------------------------------------------

  def partyJoin(player: ServerPlayer, name: String): String =
    val c = ensure(player.getUUID)
    if !c.joined then return "Join the realm first: /quest join"
    c.party.foreach(p => parties.get(p).foreach(_ -= c.uuid))
    val members = parties.getOrElseUpdate(name.toLowerCase, mutable.LinkedHashSet[UUID]())
    if members.size >= PartyMaxSize && !members.contains(c.uuid) then
      c.party = None
      return s"The party '$name' is full ($PartyMaxSize maximum)."
    members += c.uuid
    c.party = Some(name.toLowerCase)
    s"Joined party '$name' (${members.size}/$PartyMaxSize). Party members within $PartyShareRangeBlocks blocks share 50% of kill XP."

  def leaveParty(player: ServerPlayer): String =
    val c = ensure(player.getUUID)
    c.party match
      case None => "You are not in a party."
      case Some(p) =>
        parties.get(p).foreach { m =>
          m -= c.uuid
          if m.isEmpty then parties -= p
        }
        c.party = None
        "Left the party."

  private[azerothchaos] def partiesOf(player: ServerPlayer, pname: String): List[String] =
    parties
      .getOrElse(pname, mutable.LinkedHashSet[UUID]())
      .toList
      .flatMap(u => Option(player.server.getPlayerList.getPlayer(u)))
      .map(_.getName.getString)
      .filterNot(_ == player.getName.getString)

  def partyList(player: ServerPlayer): String =
    val c = ensure(player.getUUID)
    c.party match
      case None => "You are not in a party. Join one: /quest party <name>"
      case Some(p) =>
        val names = parties.getOrElse(p, mutable.LinkedHashSet[UUID]()).flatMap(u => Option(player.server.getPlayerList.getPlayer(u)).map(_.getName.getString))
        s"Party '$p': ${names.mkString(", ")}"

  // --- vendor / shop ------------------------------------------------------------

  def shopMenu(player: ServerPlayer): Unit =
    val gold = countItem(player, Items.GOLD_NUGGET)
    message(player, s"--- General Vendor --- you carry $gold gold", ChatFormatting.GOLD)
    Shop.stock.foreach { s =>
      val color = if gold >= s.price then ChatFormatting.AQUA else ChatFormatting.DARK_GRAY
      val text = Component
        .literal(s"[${s.gear.name}] ${s.price} gold")
        .withStyle(st => st.withColor(color).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, s"/quest buy ${s.id}")))
      player.displayClientMessage(text, false)
    }
    message(player, "Press T then LEFT-click a line to buy. Gold comes from coin drops and quest rewards.", ChatFormatting.GRAY)

  def buy(player: ServerPlayer, id: String): String =
    Shop.byId(id) match
      case None => s"Unknown item '$id'. Browse with /quest shop."
      case Some(s) =>
        val gold = countItem(player, Items.GOLD_NUGGET)
        if gold < s.price then s"The ${s.gear.name} costs ${s.price} gold - you carry only $gold."
        else
          removeItems(player, Items.GOLD_NUGGET, s.price)
          giveItem(player, Loot.stack(s.gear, player.level.registryAccess()))
          s"Bought ${s.gear.name} for ${s.price} gold (${gold - s.price} left)."

  // --- stats & leaderboard ----------------------------------------------------

  def statsReport(player: ServerPlayer): String =
    val c = ensure(player.getUUID)
    val hc = heroClassOf(c).map(_.name).getOrElse("Adventurer")
    val need = xpToNext(c.level)
    s"Lv ${c.level} $hc | XP ${c.xp}/$need | kills ${c.kills} (${c.rareKills} rare) | deaths ${c.deaths} | quests in log ${c.taken.size}"

  def leaderboard(server: MinecraftServer): List[(String, Int, String)] =
    val merged = mutable.Map[UUID, (Int, String, String)]() // level, classId, saved name
    CharacterStore.readAll(server).foreach { (uuid, tag) =>
      merged(uuid) = (tag.getInt("level"), tag.getString("class"), tag.getString("name"))
    }
    characters.foreach { (uuid, c) =>
      if c.level > merged.get(uuid).map(_._1).getOrElse(0) then merged(uuid) = (c.level, c.classId, c.displayName)
    }
    merged.toList
      .map { entry =>
        val uuid = entry._1
        val level = entry._2._1
        val classId = entry._2._2
        val savedName = entry._2._3
        val name = Option(server.getPlayerList.getPlayer(uuid)).map(_.getName.getString).getOrElse(savedName)
        (name, level, HeroClasses.byId(classId).map(_.name).getOrElse("Adventurer"))
      }
      .sortBy(row => -row._2)
      .take(10)

  def reset(player: ServerPlayer): String =
    val c = ensure(player.getUUID)
    c.classId = ""
    c.level = 1
    c.xp = 0L
    c.taken.clear()
    c.progress.clear()
    c.kills = 0
    c.rareKills = 0
    c.deaths = 0
    leaveParty(player)
    applyBonuses(player)
    "Your hero was reset to a level 1 Adventurer."

  def resetOther(server: MinecraftServer, target: String): String =
    Option(server.getPlayerList.getPlayerByName(target)) match
      case Some(p) => reset(p)
      case None => s"No online player named '$target'."

  // --- rare elites & ticking --------------------------------------------------

  def spawnRare(player: ServerPlayer, nameOpt: Option[String]): String =
    val chosen = nameOpt.flatMap(n => Enemies.rares.find(_.name.equalsIgnoreCase(n)))
      .getOrElse(Enemies.rares(rng.nextInt(Enemies.rares.size)))
    player.level match
      case serverLevel: ServerLevel =>
        val factory = chosen.entityType.asInstanceOf[EntityType[Mob]]
        Option(factory.create(serverLevel)) match
          case None => "The rare refused to appear here."
          case Some(m) =>
            val ang = rng.nextDouble() * 2 * Math.PI
            val dist = 14 + rng.nextInt(10)
            m.setPos(player.getX + math.cos(ang) * dist, player.getY + 2, player.getZ + math.sin(ang) * dist)
            m.setCustomName(Component.literal(chosen.name))
            m.setCustomNameVisible(true)
            Option(m.getAttribute(Attributes.MAX_HEALTH)).foreach { a =>
              a.setBaseValue(a.getBaseValue * chosen.healthMult)
              m.setHealth(a.getValue.toFloat)
            }
            Option(m.getAttribute(Attributes.ATTACK_DAMAGE)).foreach(a => a.setBaseValue(a.getBaseValue * chosen.attackMult))
            serverLevel.addFreshEntity(m)
            rareAnnounced += 1
            broadcast(
              player.server,
              Component
                .literal(s"A RARE ELITE has appeared: ${chosen.name}, near ${player.getName.getString}! Slay it for 10x XP and guaranteed loot.")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
            )
            player.playNotifySound(SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.9f)
            s"Spawned rare elite: ${chosen.name}"
      case _ => "Not on a server level."

  def tick(server: MinecraftServer): Unit =
    elapsedTicks += 1
    PartyLoot.tick(server)
    if elapsedTicks >= nextAutosaveTick && server.getPlayerList.getPlayerCount > 0 then
      nextAutosaveTick = elapsedTicks + AutosaveIntervalTicks
      CharacterStore.save(server) // crash safety net between disconnect saves
    if elapsedTicks >= nextRareTick then
      nextRareTick = elapsedTicks + RareIntervalTicks
      val online = server.getPlayerList.getPlayers.asScala.filter(isJoined)
      if online.nonEmpty then
        val target = online.toSeq(rng.nextInt(online.size))
        spawnRare(target, None)
    server.getPlayerList.getPlayers.asScala.filter(isJoined).foreach { p =>
      if elapsedTicks % NavRefreshTicks == 0 then refreshNav(p)
      Hud.update(p)
    }

  // --- persistence views -------------------------------------------------------

  private[azerothchaos] def allCharacters: Iterable[Character] = characters.values

  private[azerothchaos] def tagFor(c: Character): CompoundTag =
    val tag = new CompoundTag
    tag.putString("name", c.displayName)
    tag.putString("class", c.classId)
    tag.putInt("level", c.level)
    tag.putLong("xp", c.xp)
    tag.putString("taken", c.taken.mkString(","))
    tag.putString("tracked", c.tracked.mkString(","))
    val progressTag = new CompoundTag
    c.progress.foreach { (qid, n) => progressTag.putInt(qid, n) }
    tag.put("progress", progressTag)
    tag.putInt("kills", c.kills)
    tag.putInt("rareKills", c.rareKills)
    tag.putInt("deaths", c.deaths)
    tag

  private[azerothchaos] def mergeFromTag(uuid: UUID, tag: CompoundTag): Unit =
    val c = ensure(uuid)
    c.displayName = tag.getString("name")
    c.classId = tag.getString("class")
    c.level = math.max(1, tag.getInt("level"))
    c.xp = tag.getLong("xp")
    if tag.getString("taken").nonEmpty then c.taken ++= tag.getString("taken").split(",").toSeq
    if tag.contains("tracked") && tag.getString("tracked").nonEmpty then
      tag.getString("tracked").split(",").toSeq.filter(c.taken.contains).foreach(c.tracked += _)
    Option(tag.getCompound("progress")).foreach { p =>
      p.getAllKeys.asScala.foreach(k => c.progress(k) = p.getInt(k))
    }
    c.kills = tag.getInt("kills")
    c.rareKills = tag.getInt("rareKills")
    c.deaths = tag.getInt("deaths")

  // --- helpers -----------------------------------------------------------------

  /** Typo help: the closest quest id within edit distance 3, e.g. bone-copllection -> bone-collection. */
  private def didYouMean(input: String): Option[String] =
    Quests.all
      .map(_.id)
      .map(id => (editDistance(id.toLowerCase, input.toLowerCase), id))
      .filter(_._1 <= 3)
      .sortBy(_._1)
      .headOption
      .map(_._2)

  private def editDistance(a: String, b: String): Int =
    var prev = Array.tabulate(b.length + 1)(identity)
    for i <- 1 to a.length do
      val cur = Array.ofDim[Int](b.length + 1)
      cur(0) = i
      for j <- 1 to b.length do
        val sub = prev(j - 1) + (if a(i - 1) == b(j - 1) then 0 else 1)
        cur(j) = math.min(math.min(prev(j) + 1, cur(j - 1) + 1), sub)
      prev = cur
    prev(b.length)

  private def giveItem(player: ServerPlayer, stack: net.minecraft.world.item.ItemStack): Unit =
    if !player.getInventory.add(stack) then player.drop(stack, false)

  private[azerothchaos] def message(player: ServerPlayer, text: String, color: ChatFormatting): Unit =
    player.displayClientMessage(Component.literal(text).withStyle(color), false)

  private[azerothchaos] def broadcast(server: MinecraftServer, msg: Component): Unit =
    server.getPlayerList().broadcastSystemMessage(msg, false)
