package dev.aetheronchaos

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack

/**
 * Hero-to-hero trading, chat-only: a vanilla client gives us no trade window, so the
 * exchange is a pair of offers read out in chat and a double-confirm by clicking. A
 * trade is an invitation (both heroes must be in sight of each other), an offer from
 * each side built from the stack in hand, and two confirmations. Nothing moves until
 * both say yes, and every offered stack is re-checked against the giver's inventory at
 * the moment of the swap - if anything changed in between, the trade calls itself off
 * rather than lying about what changed hands.
 */
object Trading:
  private val RangeBlocks = 40.0 // a trade is a handshake; you have to be able to see each other

  private final class Session(val a: UUID, val b: UUID):
    val key = Session.keyOf(a, b)
    val offers = Map(a -> mutable.ListBuffer[ItemStack](), b -> mutable.ListBuffer[ItemStack]())
    val confirmed = mutable.Set[UUID]()
    var accepted = false

  private object Session:
    def keyOf(x: UUID, y: UUID): String = if x.toString.compareTo(y.toString) <= 0 then s"$x|$y" else s"$y|$x"

  private val sessions = mutable.Map[String, Session]()
  private val byPlayer = mutable.Map[UUID, String]()

  // --- the invitation -----------------------------------------------------------

  /** `/quest trade <hero>` - open a trade with a hero standing in front of you. */
  def invite(player: ServerPlayer, name: String): String =
    val target = player.server.getPlayerList.getPlayerByName(name)
    if target == null then s"No hero named '$name' is online."
    else if target.getUUID == player.getUUID then "You cannot trade with yourself, however quiet the road gets."
    else if !inRange(player, target) then
      s"${target.getName.getString} is too far away to shake hands on anything - trade needs both heroes within ${RangeBlocks.toInt} blocks."
    else
      closeFor(player.getUUID)
      closeFor(target.getUUID) // one trade per hero: an old one is called off, not queued
      val s = new Session(player.getUUID, target.getUUID)
      sessions += s.key -> s
      byPlayer += player.getUUID -> s.key
      byPlayer += target.getUUID -> s.key
      Game.message(
        target,
        s"${player.getName.getString} offers to trade goods. " +
          "Type /quest trade yes to open it, /quest trade no to decline. (Or click: )",
        ChatFormatting.GOLD
      )
      target.displayClientMessage(clickableLine(" ", ChatFormatting.GRAY,
        List(("[Accept]", ChatFormatting.GREEN, "/quest trade yes"), ("[Decline]", ChatFormatting.RED, "/quest trade no"))),
        false)
      s"A trade invitation goes out to ${target.getName.getString}."

  /** The invited answer. */
  def respond(player: ServerPlayer, accept: Boolean): String =
    sessionFor(player) match
      case None => "No trade invitation is waiting for you."
      case Some(s) if s.accepted => "Your trade is already open - /quest trade shows both offers."
      case Some(s) if !accept =>
        close(s, "declined")
        "You decline the trade. Nothing changes hands."
      case Some(s) =>
        s.accepted = true
        val (me, other) = pair(player, s)
        List(me, other).foreach { p =>
          Game.message(p, s"Trade opened with ${otherOf(p, s).getName.getString}. Hold a stack and use /quest trade give to offer it; /quest trade confirm when you both agree.", ChatFormatting.GOLD)
          showOffers(p, s)
        }
        "The trade is open."

  // --- the offers ---------------------------------------------------------------

  /** Offer the stack currently in hand (the whole stack, named gear and all). */
  def offerHeld(player: ServerPlayer): String =
    sessionFor(player) match
      case None => "You are not in a trade. /quest trade <hero> opens one."
      case Some(s) if !s.accepted => "The trade is not open yet - wait for the invitation to be accepted."
      case Some(s) =>
        val held = player.getInventory.getSelected
        if held.isEmpty then "Hold the stack you would trade, then /quest trade give."
        else
          val mine = s.offers(player.getUUID)
          val twin = mine.find(o => ItemStack.isSameItemSameComponents(o, held) && o.getCount + held.getCount <= o.getMaxStackSize)
          twin match
            case Some(o) => o.grow(held.getCount)
            case None => mine += held.copy()
          s.confirmed -= player.getUUID // the offer changed; the yes goes with it
          showOffers(player, s)
          s"${describe(held)} is offered. ${otherOf(player, s).getName.getString} can see it."

  /** Take the offer back. */
  def clearOffer(player: ServerPlayer): String =
    sessionFor(player) match
      case None => "You are not in a trade."
      case Some(s) =>
        s.offers(player.getUUID).clear()
        s.confirmed -= player.getUUID
        showOffers(player, s)
        "Your offer is taken back."

  /** `/quest trade` - read the trade as it stands. */
  def show(player: ServerPlayer): String =
    sessionFor(player) match
      case None => "You are not in a trade. /quest trade <hero> opens one with a hero standing near you."
      case Some(s) if !s.accepted => "The invitation is still waiting to be answered: /quest trade yes or /quest trade no."
      case Some(s) =>
        showOffers(player, s)
        val other = otherOf(player, s)
        if s.confirmed.contains(other.getUUID) then "They have confirmed. /quest trade confirm to close it."
        else "You are waiting on their confirmation."

  /** Confirm this side. When both have confirmed, the trade commits. */
  def confirm(player: ServerPlayer): String =
    sessionFor(player) match
      case None => "You are not in a trade."
      case Some(s) if !s.accepted => "The trade is not open yet."
      case Some(s) =>
        if s.offers(player.getUUID).isEmpty && s.offers(otherOf(player, s).getUUID).isEmpty then
          "Neither side has offered anything. There is nothing to confirm."
        else
          s.confirmed += player.getUUID
          if bothConfirmed(s) then commit(player.server, s)
          else
            showOffers(player, s)
            Game.message(otherOf(player, s), s"${player.getName.getString} has confirmed the trade. /quest trade confirm to close it.", ChatFormatting.GOLD)
            "You confirm. Waiting on the other side."

  private def bothConfirmed(s: Session): Boolean = s.confirmed.contains(s.a) && s.confirmed.contains(s.b)

  /** Cancel this side's trade. */
  def cancelBy(player: ServerPlayer): String =
    sessionFor(player) match
      case None => "You are not in a trade."
      case Some(s) =>
        close(s, "called off")
        "You call the trade off. Nothing changes hands."

  /** A hero left the realm: any trade they were in is over. */
  def closeFor(uuid: UUID): Unit =
    byPlayer.get(uuid).flatMap(sessions.get).foreach { s =>
      close(s, "ended")
    }

  // --- the swap -----------------------------------------------------------------

  private def commit(server: MinecraftServerLike, s: Session): String =
    val (pa, pb) = (online(server, s.a), online(server, s.b))
    (pa, pb) match
      case (Some(x), Some(y)) if inRange(x, y) =>
        // every promise is checked against the pack as it is right now, not as it was
        val broken = List(
          missing(x, s.offers(s.a)),
          missing(y, s.offers(s.b))
        ).flatten
        if broken.nonEmpty then
          close(s, "called off")
          List(x, y).foreach(p =>
            Game.message(p, s"The trade falls apart: ${broken.mkString(", ")} was not there when the hand reached for it.", ChatFormatting.RED))
          "The trade fell apart - what was offered was no longer in the pack."
        else
          val gaveA = s.offers(s.a).toList
          val gaveB = s.offers(s.b).toList
          gaveA.foreach(takeExactly(x, _))
          gaveB.foreach(takeExactly(y, _))
          gaveA.foreach(st => Game.giveItem(y, st.copy()))
          gaveB.foreach(st => Game.giveItem(x, st.copy()))
          close(s, "closed")
          val summaryA = if gaveA.isEmpty then "nothing" else gaveA.map(describe).mkString(", ")
          val summaryB = if gaveB.isEmpty then "nothing" else gaveB.map(describe).mkString(", ")
          Game.message(x, s"Trade closed: you gave $summaryA and received $summaryB.", ChatFormatting.GREEN)
          Game.message(y, s"Trade closed: you gave $summaryB and received $summaryA.", ChatFormatting.GREEN)
          x.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f)
          y.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f)
          "Both sides confirmed, and the goods changed hands."
      case (Some(x), Some(y)) =>
        close(s, "called off")
        List(x, y).foreach(p => Game.message(p, "The trade ends - you are no longer close enough to shake hands on it.", ChatFormatting.RED))
        "The trade ends - you drifted too far apart."
      case _ =>
        close(s, "ended")
        "The trade ends - one side has left the realm."

  // a type alias would be cleaner, but this keeps the import list honest
  private type MinecraftServerLike = net.minecraft.server.MinecraftServer

  private def online(server: MinecraftServerLike, uuid: UUID): Option[ServerPlayer] =
    Option(server.getPlayerList.getPlayer(uuid)).filter(_.isAlive)

  /** The offered stacks this hero no longer actually carries, described for the failure message. */
  private def missing(player: ServerPlayer, offer: Iterable[ItemStack]): List[String] =
    offer.filterNot { want =>
      val inv = (player.getInventory.items.asScala.toSeq ++ player.getInventory.offhand.asScala.toSeq)
      inv.exists(st => !st.isEmpty && ItemStack.isSameItemSameComponents(st, want) && st.getCount >= want.getCount)
    }.map(describe).toList

  private def takeExactly(player: ServerPlayer, want: ItemStack): Unit =
    val inv = player.getInventory
    val main = inv.items.asScala.toSeq
    main.find(st => !st.isEmpty && ItemStack.isSameItemSameComponents(st, want) && st.getCount >= want.getCount) match
      case Some(st) =>
        if st.getCount > want.getCount then st.shrink(want.getCount)
        else inv.items.set(main.indexWhere(_ eq st), ItemStack.EMPTY)
        player.inventoryMenu.broadcastChanges()
      case None =>
        inv.offhand.asScala.zipWithIndex.find { (st, _) =>
          !st.isEmpty && ItemStack.isSameItemSameComponents(st, want) && st.getCount >= want.getCount
        }.foreach { (_, slot) =>
          inv.offhand.set(slot, ItemStack.EMPTY)
          player.inventoryMenu.broadcastChanges()
        }

  // --- the readout ---------------------------------------------------------------

  private def showOffers(player: ServerPlayer, s: Session): Unit =
    val other = otherOf(player, s)
    Game.message(player, s"--- Trade with ${other.getName.getString} ---", ChatFormatting.GOLD)
    val mine = s.offers(player.getUUID)
    if mine.isEmpty then Game.message(player, "  You offer: nothing yet. Hold a stack, then /quest trade give.", ChatFormatting.GRAY)
    else
      player.displayClientMessage(
        Component.literal("  You offer: ").withStyle(ChatFormatting.GRAY)
          .append(Component.literal(mine.map(describe).mkString(", ")).withStyle(ChatFormatting.AQUA)),
        false)
    val theirs = s.offers(other.getUUID)
    if theirs.isEmpty then Game.message(player, s"  ${other.getName.getString} offers: nothing yet.", ChatFormatting.GRAY)
    else
      player.displayClientMessage(
        Component.literal(s"  ${other.getName.getString} offers: ").withStyle(ChatFormatting.GRAY)
          .append(Component.literal(theirs.map(describe).mkString(", ")).withStyle(ChatFormatting.LIGHT_PURPLE)),
        false)
    val you = if s.confirmed.contains(player.getUUID) then "You have confirmed (an offer change takes that back)." else "You have not confirmed."
    val them = if s.confirmed.contains(other.getUUID) then "They have confirmed." else "They have not confirmed."
    Game.message(player, s"  $you $them", ChatFormatting.GRAY)
    player.displayClientMessage(clickableLine("", ChatFormatting.GRAY, List(
      ("[Confirm]", ChatFormatting.GREEN, "/quest trade confirm"),
      ("[Offer the stack in hand]", ChatFormatting.AQUA, "/quest trade give"),
      ("[Clear my offer]", ChatFormatting.YELLOW, "/quest trade clear"),
      ("[Call it off]", ChatFormatting.RED, "/quest trade cancel")
    )), false)

  private def clickableLine(prefix: String, base: ChatFormatting, parts: List[(String, ChatFormatting, String)]): Component =
    parts.foldLeft(Component.literal(prefix).withStyle(base)) { (acc, part) =>
      val (label, color, cmd) = part
      if acc.getSiblings.isEmpty then acc.append(Component.literal(label).withStyle(st => st.withColor(color).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))))
      else acc.append(Component.literal(" ")).append(Component.literal(label).withStyle(st => st.withColor(color).withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))))
      acc
    }

  private def describe(stack: ItemStack): String =
    val name = stack.getDisplayName.getString
    if stack.getCount > 1 then s"$name x${stack.getCount}" else name

  // --- plumbing -------------------------------------------------------------------

  private def sessionFor(player: ServerPlayer): Option[Session] =
    byPlayer.get(player.getUUID).flatMap(sessions.get)

  private def otherOf(player: ServerPlayer, s: Session): ServerPlayer =
    val uuid = if player.getUUID == s.a then s.b else s.a
    online(player.server, uuid).getOrElse(player) // callers only run this while both are online

  private def pair(player: ServerPlayer, s: Session): (ServerPlayer, ServerPlayer) =
    (online(player.server, s.a).get, online(player.server, s.b).get)

  private def inRange(a: ServerPlayer, b: ServerPlayer): Boolean =
    a.distanceToSqr(b.getX, b.getY, b.getZ) <= RangeBlocks * RangeBlocks

  private def close(s: Session, how: String): Unit =
    sessions -= s.key
    byPlayer -= s.a
    byPlayer -= s.b
    // "ended" means a side left the realm: there is no one left to tell
    if how == "ended" then ()
