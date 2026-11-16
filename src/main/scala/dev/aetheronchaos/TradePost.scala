package dev.aetheronchaos

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Try

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.nbt.{CompoundTag, ListTag, Tag, TagParser}
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelResource

/**
 * The trade post: every hub keeps a market sign by the plaza where a hero can leave a
 * stack and a price, and any other hero who walks up can buy it. This is the async half
 * of the economy face-to-face trading cannot serve - the seller is gone when the coin
 * changes hands, so sale proceeds wait at the post until the seller next passes through
 * (or arrive the moment they log back in).
 *
 * Listings are real items (named legendary gear included) and persist in
 * aetheron-chaos-postings.snbt keyed by hub anchor, so a post outlives restarts.
 * The sign itself is part of the world and re-written whenever the board changes.
 */
object TradePost:
  private val MaxPerHub = 10
  private val MaxPerHero = 3
  private val MaxPrice = 5000
  private val MaxWatches = 3
  private val SoldHistoryPerItem = 5
  private val FileName = "aetheron-chaos-postings.snbt"

  final class Posting(val seller: UUID, val sellerName: String, val stack: ItemStack, val gold: Int):
    def described: String =
      val name = stack.getDisplayName.getString
      if stack.getCount > 1 then s"$name x${stack.getCount}" else name

  // hub key ("x,z") -> the postings on that board, plus sale proceeds waiting for sellers
  private val boards = mutable.Map[String, mutable.ListBuffer[Posting]]()
  private val owed = mutable.Map[UUID, (String, Int)]() // seller uuid -> (name, gold waiting at the post)
  // what heroes are watching for: uuid -> phrases matched against posted item names
  private val watches = mutable.Map[UUID, mutable.LinkedHashSet[String]]()
  // what the board has actually paid: item key -> recent sale prices (price guidance for sellers)
  private val sold = mutable.Map[String, List[Int]]()
  private var loaded = false
  private var postsPending = true // the first time a hero is online, the realm's posts are built

  // --- hero-facing commands ------------------------------------------------------

  /** `/quest post` - read the market at the hub you stand at. */
  def list(player: ServerPlayer): String =
    ensureLoaded(player.server)
    hubFor(player) match
      case None => "No hub within reach to trade at. /quest post works standing at a plaza - the board's compass points at one."
      case Some((hubKey, hub)) =>
        val board = boards.getOrElseUpdate(hubKey, mutable.ListBuffer())
        val mine = board.filter(_.seller == player.getUUID)
        val wallet = owed.get(player.getUUID).map(_._2)
        val rows =
          if board.isEmpty then List("  The board is empty. Hold a stack and use /quest post sell <gold> to post it.")
          else board.zipWithIndex.toList.map { (p, i) =>
            val yours = if p.seller == player.getUUID then s" (yours - /quest post cancel ${i + 1})" else ""
            s"  #${i + 1}. ${p.described} - ${p.gold} gold, posted by ${p.sellerName}$yours"
          }
        val waiting = wallet match
          case Some(g) if g > 0 => s"\n  The post owes you $g gold: /quest post coin takes it."
          case _ => ""
        val mineWatch = watches.get(player.getUUID).filter(_.nonEmpty).map(w => s"\n  You watch for: ${w.mkString(", ")} (/quest post unwatch <phrase>)").getOrElse("")
        (List(
          s"--- Trade Post at ${hubLabel(hub)} --- ${board.size}/$MaxPerHub posted. To sell: hold a stack, /quest post sell <gold> (no price asks the market what it paid). To buy: /quest post buy <n>. To be told when something lands: /quest post watch <phrase>."
        ) ::: rows ::: List(waiting, mineWatch)).filter(_.nonEmpty).mkString("\n")

  /** `/quest post sell <gold>` - leave the stack in hand on the board for a price.
   *  No price (`gold` 0) is the market's turn to speak: what the board recently paid. */
  def sell(player: ServerPlayer, gold: Int): String =
    ensureLoaded(player.server)
    hubFor(player) match
      case None => "Stand at a hub plaza to post goods."
      case Some((hubKey, hub)) =>
        val held = player.getInventory.getSelected
        if held.isEmpty then "Hold the stack you would sell, then /quest post sell <gold>."
        else if gold == 0 then priceGuidance(held)
        else if gold < 1 || gold > MaxPrice then s"Prices run from 1 to $MaxPrice gold."
        else
          val board = boards.getOrElseUpdate(hubKey, mutable.ListBuffer())
          if board.size >= MaxPerHub then "This board is full - ten postings is a market."
          else if countEverywhere(player.getUUID) >= MaxPerHero then
            s"A hero may keep $MaxPerHero postings out at a time. Sell one first (/quest post cancel)."
          else
            val snapshot = held.copy()
            held.shrink(snapshot.getCount)
            player.inventoryMenu.broadcastChanges()
            board += new Posting(player.getUUID, player.getName.getString, snapshot, gold)
            notifyWatchers(player.server, snapshot, gold, hub)
            save(player.server)
            rewriteSigns(player.server)
            val guidance = sold.get(itemKey(snapshot)).flatMap(recentGuidance)
            val guidanceNote = guidance.map(g => s" (the board has paid $g for these recently)").getOrElse("")
            s"${describe(snapshot)} is posted at ${hubLabel(hub)} for $gold gold$guidanceNote. The board sees you now; other heroes will."

  /** What the board has actually paid for this item - the only price guide there is. */
  private def priceGuidance(held: ItemStack): String =
    sold.get(itemKey(held)) match
      case None => s"No copy of ${describe(held)} has sold at the posts yet - the board is your only price guide. Post it: /quest post sell <gold>."
      case Some(prices) =>
        val recent = prices.takeRight(SoldHistoryPerItem)
        val avg = recent.sum / recent.size
        s"The posts have paid ${recent.mkString(", ")} gold for ${describe(held)} recently - that averages $avg. Price it: /quest post sell <gold>."

  /** A price far off the recent sales is worth one honest sentence, no more. */
  private def recentGuidance(prices: List[Int]): Option[String] =
    if prices.isEmpty then None
    else
      val avg = prices.sum / prices.size
      if avg > 0 then Some(avg.toString) else None

  /** `/quest post watch <phrase>` - be told when something matching lands on any board. */
  def watch(player: ServerPlayer, phrase: String): String =
    ensureLoaded(player.server)
    val p = phrase.trim
    if p.length < 3 then "Watch phrases are at least 3 characters - 'Dawn', 'Sigil', 'iron boots'."
    else if p.length > 30 then "Keep watch phrases under 30 characters."
    else
      val mine = watches.getOrElseUpdate(player.getUUID, mutable.LinkedHashSet())
      if mine.contains(p.toLowerCase) then s"You already watch for '$p'."
      else if mine.size >= MaxWatches then s"A hero may watch for $MaxWatches things at a time (/quest post unwatch <phrase> to free one)."
      else
        mine += p.toLowerCase
        save(player.server)
        s"You will hear when anything matching '$p' is posted at any hub."

  /** `/quest post unwatch <phrase>` */
  def unwatch(player: ServerPlayer, phrase: String): String =
    ensureLoaded(player.server)
    val p = phrase.trim.toLowerCase
    watches.get(player.getUUID) match
      case None => s"You never watched for '$phrase'."
      case Some(mine) if !mine.contains(p) => s"You never watched for '$phrase'."
      case Some(mine) =>
        mine -= p
        if mine.isEmpty then watches -= player.getUUID
        save(player.server)
        s"You stop watching for '$phrase'."

  private def notifyWatchers(server: MinecraftServer, stack: ItemStack, gold: Int, hub: TownBuilder.Town): Unit =
    val name = stack.getDisplayName.getString.toLowerCase
    val described = describe(stack)
    watches.foreach { (uuid, phrases) =>
      phrases.find(p => name.contains(p)).foreach { p =>
        Option(server.getPlayerList.getPlayer(uuid)).foreach { hero =>
          Game.message(hero, s"What you watch for is posted at ${hubLabel(hub)}: $described for $gold gold (matches '$p'). /quest post lists the board.", ChatFormatting.GOLD)
        }
      }
    }

  /** `/quest post buy <n>` - take posting n down off the board. */
  def buy(player: ServerPlayer, n: Int): String =
    ensureLoaded(player.server)
    hubFor(player) match
      case None => "Stand at a hub plaza to buy."
      case Some((hubKey, hub)) =>
        val board = boards.getOrElseUpdate(hubKey, mutable.ListBuffer())
        if n < 1 || n > board.size then s"There is no posting #$n on this board (${board.size} posted)."
        else
          val p = board(n - 1)
          if p.seller == player.getUUID then s"That is your own posting. /quest post cancel $n to take it back."
          else
            val purse = Game.countItem(player, Items.GOLD_NUGGET)
            if purse < p.gold then s"${p.described} costs ${p.gold} gold and you carry $purse."
            else
              Game.removeItems(player, Items.GOLD_NUGGET, p.gold)
              board -= p
              owe(p.seller, p.sellerName, p.gold)
              recordSale(itemKey(p.stack), p.gold)
              Game.giveItem(player, p.stack.copy())
              save(player.server)
              rewriteSigns(player.server)
              Option(player.server.getPlayerList.getPlayer(p.seller)).foreach { hero =>
                Game.message(hero, s"${player.getName.getString} bought your ${describe(p.stack)} at ${hubLabel(hub)}. Your ${p.gold} gold waits at the post (/quest post coin).", ChatFormatting.GOLD)
              }
              Game.broadcast(
                player.server,
                Component
                  .literal(s"${player.getName.getString} bought ${p.described} from ${p.sellerName}'s posting at the ${hubLabel(hub)} post for ${p.gold} gold.")
                  .withStyle(ChatFormatting.GOLD)
              )
              s"You bought ${describe(p.stack)} for ${p.gold} gold. ${p.sellerName} is owed it at the post."

  /** `/quest post cancel <n>` - take your own posting back. */
  def cancel(player: ServerPlayer, n: Int): String =
    ensureLoaded(player.server)
    hubFor(player) match
      case None => "Stand at a hub plaza to unpost."
      case Some((hubKey, hub)) =>
        val board = boards.getOrElseUpdate(hubKey, mutable.ListBuffer())
        if n < 1 || n > board.size then s"There is no posting #$n here."
        else
          val p = board(n - 1)
          if p.seller != player.getUUID then s"${p.described} is ${p.sellerName}'s, not yours."
          else
            board -= p
            Game.giveItem(player, p.stack.copy())
            save(player.server)
            rewriteSigns(player.server)
            s"${describe(p.stack)} is off the board and back in your pack."

  /** `/quest post coin` - collect what past sales have earned. */
  def collect(player: ServerPlayer): String =
    ensureLoaded(player.server)
    owed.get(player.getUUID) match
      case None | Some((_, 0)) => "No gold is waiting for you at any post."
      case Some((_, gold)) =>
        owed -= player.getUUID
        Game.giveCoins(player, gold)
        save(player.server)
        s"The post pays you $gold gold from past sales."

  /** A hero comes back to the realm: their sales pay out on the spot. */
  def settleOnJoin(player: ServerPlayer): Unit =
    ensureLoaded(player.server)
    owed.get(player.getUUID).foreach { (_, gold) =>
      if gold > 0 then
        owed -= player.getUUID
        Game.giveCoins(player, gold)
        save(player.server)
        Game.message(player, s"Word reached you: your postings sold. The post sends $gold gold.", ChatFormatting.GOLD)
    }

  /** The realm is closing: make sure the ledgers on disk are current. */
  private[aetheronchaos] def settleForShutdown(server: MinecraftServer): Unit =
    if loaded then save(server)

  // --- the posts themselves --------------------------------------------------------

  /** First time a hero is online: load the ledgers and make sure every hub has its sign. */
  private[aetheronchaos] def tick(server: MinecraftServer): Unit =
    if postsPending && server.getPlayerList.getPlayerCount > 0 then
      postsPending = false
      ensureLoaded(server)
      raisePosts(server)

  private def hubFor(player: ServerPlayer): Option[(String, TownBuilder.Town)] =
    GuardWatch.hubNear(player).flatMap(i => TownBuilder.townByIndex(i + 1).map(h => s"${h.base.getX},${h.base.getZ}" -> h))

  private def countEverywhere(uuid: UUID): Int =
    boards.values.map(_.count(_.seller == uuid)).sum

  private def owe(uuid: UUID, name: String, gold: Int): Unit =
    val (_, had) = owed.getOrElse(uuid, ("", 0))
    owed += uuid -> (name, had + gold)

  /** The sale ledger: what the board actually paid, per item, for price guidance. */
  private def recordSale(key: String, gold: Int): Unit =
    sold(key) = (sold.getOrElse(key, Nil) :+ gold).takeRight(SoldHistoryPerItem)

  /** Item type + custom name: a named legendary is its own market, separate from plain gear. */
  private def itemKey(stack: ItemStack): String =
    val base = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem).getPath
    val named = Option(stack.get(net.minecraft.core.component.DataComponents.ITEM_NAME)).map(c => c.getString).getOrElse("")
    if named.nonEmpty then s"$base:$named" else base

  /** Build (or keep) the market sign at every hub, with today's board written on it. */
  private def raisePosts(server: MinecraftServer): Unit =
    val level = server.overworld()
    if level == null then return
    TownBuilder.allTowns.foreach(hub => raisePostAt(level, hub))

  private def raisePostAt(level: ServerLevel, hub: TownBuilder.Town): Unit =
    val stand = TownBuilder.tradePostStand(hub)
    val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, stand.getX, stand.getZ)
    val pedestal = new BlockPos(stand.getX, y - 1, stand.getZ)
    val signPos = new BlockPos(stand.getX, y, stand.getZ)
    if !level.getBlockState(signPos).isAir then return // something stands here; the market yields
    if level.getBlockState(pedestal).isAir then level.setBlock(pedestal, Blocks.SMOOTH_STONE.defaultBlockState, 3)
    level.setBlock(signPos, Blocks.OAK_SIGN.defaultBlockState, 3)
    writeSign(level, signPos, hub)

  private def rewriteSigns(server: MinecraftServer): Unit =
    val level = server.overworld()
    if level == null then return
    TownBuilder.allTowns.foreach { hub =>
      val stand = TownBuilder.tradePostStand(hub)
      val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, stand.getX, stand.getZ)
      writeSign(level, new BlockPos(stand.getX, y, stand.getZ), hub)
    }

  private def writeSign(level: ServerLevel, pos: BlockPos, hub: TownBuilder.Town): Unit =
    level.getBlockEntity(pos) match
      case s: SignBlockEntity =>
        val front = s.getFrontText
        val key = s"${hub.base.getX},${hub.base.getZ}"
        val n = boards.get(key).map(_.size).getOrElse(0)
        front.setMessage(0, Component.literal("TRADE POST"))
        front.setMessage(1, Component.literal(if n == 0 then "board is empty" else s"$n posted here"))
        front.setMessage(2, Component.literal("/quest post sell"))
        front.setMessage(3, Component.literal("/quest post buy <n>"))
        s.setChanged()
      case _ => ()

  // --- persistence ---------------------------------------------------------------

  private def path(server: MinecraftServer): Path =
    server.getWorldPath(LevelResource.ROOT).resolve(FileName)

  private def ensureLoaded(server: MinecraftServer): Unit =
    if loaded then return
    loaded = true
    val p = path(server)
    if !Files.exists(p) then return
    Try {
      val root = TagParser.parseTag(Files.readString(p))
      val provider = server.registryAccess()
      root.getAllKeys.asScala.filter(k => k != "owed" && k != "watches" && k != "sold").foreach { hubKey =>
        val board = mutable.ListBuffer[Posting]()
        root.getList(hubKey, Tag.TAG_COMPOUND).asScala.foreach {
          case e: CompoundTag =>
            val stack = ItemStack.parseOptional(provider, e.getCompound("item"))
            if !stack.isEmpty && e.contains("seller") then
              board += new Posting(UUID.fromString(e.getString("seller")), e.getString("sellerName"), stack, e.getInt("gold"))
          case _ => ()
        }
        boards += hubKey -> board
      }
      if root.contains("owed") then
        val owedTag = root.getCompound("owed")
        owedTag.getAllKeys.asScala.foreach { k =>
          Try {
            val e = owedTag.getCompound(k)
            owed += UUID.fromString(k) -> (e.getString("name"), e.getInt("gold"))
          }
        }
      if root.contains("watches") then
        val watchTag = root.getCompound("watches")
        watchTag.getAllKeys.asScala.foreach { k =>
          Try {
            val phrases = watchTag.getString(k).split(",").toSeq.filter(_.nonEmpty)
            if phrases.nonEmpty then watches += UUID.fromString(k) -> mutable.LinkedHashSet(phrases*)
          }
        }
      if root.contains("sold") then
        val soldTag = root.getCompound("sold")
        soldTag.getAllKeys.asScala.foreach { k =>
          val prices = soldTag.getString(k).split(",").toSeq.flatMap(s => Try(s.trim.toInt).toOption).toList
          if prices.nonEmpty then sold += k -> prices
        }
    }

  private def save(server: MinecraftServer): Unit =
    if !loaded then return // never read the ledgers this session: never overwrite what disk holds
    try
      val root = new CompoundTag
      val provider = server.registryAccess()
      boards.foreach { (hubKey, board) =>
        val list = new ListTag
        board.foreach { p =>
          val e = new CompoundTag
          e.putString("seller", p.seller.toString)
          e.putString("sellerName", p.sellerName)
          e.putInt("gold", p.gold)
          p.stack.saveOptional(provider) match
            case null => ()
            case tag => e.put("item", tag)
          list.add(e)
        }
        root.put(hubKey, list)
      }
      val owedTag = new CompoundTag
      owed.foreach { case (uuid, (name, gold)) =>
        val e = new CompoundTag
        e.putString("name", name)
        e.putInt("gold", gold)
        owedTag.put(uuid.toString, e)
      }
      root.put("owed", owedTag)
      val watchTag = new CompoundTag
      watches.foreach { (uuid, phrases) => watchTag.putString(uuid.toString, phrases.mkString(",")) }
      root.put("watches", watchTag)
      val soldTag = new CompoundTag
      sold.foreach { (key, prices) => soldTag.putString(key, prices.mkString(",")) }
      root.put("sold", soldTag)
      val p = path(server)
      Files.createDirectories(p.getParent)
      Files.writeString(p, root.getAsString)
    catch case e: Throwable => System.err.println(s"[aetheron-chaos] failed to save the trade posts: $e")

  private def describe(stack: ItemStack): String =
    val name = stack.getDisplayName.getString
    if stack.getCount > 1 then s"$name x${stack.getCount}" else name

  private def hubLabel(hub: TownBuilder.Town): String =
    if hub.capital then "Hearthhome"
    else s"the hub at ${hub.base.getX}, ${hub.base.getZ}"
