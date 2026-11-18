package dev.aetheronchaos

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Try

import net.minecraft.ChatFormatting
import net.minecraft.nbt.{CompoundTag, ListTag, Tag, TagParser}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.LevelResource

/**
 * A hero's personal shelf in the world save: a bank for the realm. What is deposited here
 * waits through deaths, logouts and restarts - safe from a full pack and from the dawn
 * merchant - until the hero next withdraws it, at any hub or none. The bank takes a coin
 * per stack stored (withdraw is free); the ledger lives in aetheron-chaos-stash.snbt,
 * keyed by hero, in the same style as the trade post's postings.
 */
object Stash:
  private val FileName = "aetheron-chaos-stash.snbt"
  private def MaxSlots: Int = Tuning.getInt("stash.max_slots", 27)
  private def DepositGold: Int = Tuning.getInt("stash.deposit_gold", 2)

  final class Entry(val stack: ItemStack, val storedDay: String):
    def described: String = describe(stack)

  // hero uuid -> their shelf, oldest deposit first
  private val shelves = mutable.Map[UUID, mutable.ListBuffer[Entry]]()
  private var loaded = false

  // --- hero-facing commands ------------------------------------------------------

  /** `/quest stash` - read the shelf. */
  def list(player: ServerPlayer): String =
    ensureLoaded(player.server)
    val shelf = shelves.getOrElse(player.getUUID, mutable.ListBuffer())
    if shelf.isEmpty then
      s"Your stash is empty - $MaxSlots spaces on the shelf, and it is the one store no death reaches. Hold a stack and /quest stash deposit ($DepositGold gold; you carry ${Game.countItem(player, Items.GOLD_NUGGET)})."
    else
      val rows = shelf.zipWithIndex.toList.map { (e, i) => s"  #${i + 1}. ${e.described} - stored ${e.storedDay}" }
      (List(
        s"--- ${player.getName.getString}'s stash --- ${shelf.size}/$MaxSlots spaces. Deposit: hold a stack, /quest stash deposit ($DepositGold gold a stack). Withdraw: /quest stash withdraw <n>."
      ) ::: rows).mkString("\n")

  /** `/quest stash deposit [all]` - the stack in hand goes on the shelf. `all` takes every
   *  stack of that same item you carry (identical stacks only - named gear stays named). */
  def deposit(player: ServerPlayer, all: Boolean): String =
    ensureLoaded(player.server)
    val held = player.getInventory.getSelected
    if held.isEmpty then "Hold the stack you would store, then /quest stash deposit."
    else
      val shelf = shelves.getOrElseUpdate(player.getUUID, mutable.ListBuffer())
      val matching =
        if all then player.getInventory.items.asScala.filter(s => !s.isEmpty && ItemStack.isSameItemSameComponents(s, held)).toList
        else List(held)
      val fee = DepositGold * matching.size
      if shelf.size + matching.size > MaxSlots then
        s"The shelf holds $MaxSlots stacks and you have ${shelf.size} on it - no room for ${matching.size} more. Withdraw first."
      else if Game.countItem(player, Items.GOLD_NUGGET) < fee then
        s"The bank asks $fee gold to store ${matching.size} stack(s) - you carry ${Game.countItem(player, Items.GOLD_NUGGET)}."
      else
        matching.foreach { s =>
          val snapshot = s.copy()
          s.shrink(snapshot.getCount)
          shelf += new Entry(snapshot, java.time.LocalDate.now().toString)
        }
        player.inventoryMenu.broadcastChanges()
        if fee > 0 then Game.removeItems(player, Items.GOLD_NUGGET, fee)
        save(player.server)
        val what = if all then s"${matching.size} stack(s) of ${describe(held)}" else describe(held)
        s"$what is on your shelf - $fee gold to the bank, ${shelf.size}/$MaxSlots spaces used. It waits for you there, death and all."

  /** `/quest stash withdraw <n>` - take an entry back. Free; the pack just needs room. */
  def withdraw(player: ServerPlayer, index: Int): String =
    ensureLoaded(player.server)
    val shelf = shelves.getOrElse(player.getUUID, mutable.ListBuffer())
    if shelf.isEmpty then "Nothing waits in your stash. /quest stash deposit holds a stack for you."
    else if index < 1 || index > shelf.size then s"The shelf holds ${shelf.size} stack(s): withdraw takes #1..#${shelf.size}."
    else
      val e = shelf(index - 1)
      val snapshot = e.stack.copy()
      if !player.getInventory.add(snapshot) then
        s"Your pack has no room for ${describe(e.stack)} - make the space and ask again. The shelf will keep it."
      else
        shelf.remove(index - 1)
        if shelf.isEmpty then shelves -= player.getUUID
        save(player.server)
        s"${describe(e.stack)} comes off the shelf, free as taking your own coat off its peg. ${shelf.size}/$MaxSlots spaces still in use."

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
      root.getList("shelves", Tag.TAG_COMPOUND).asScala.foreach {
        case e: CompoundTag =>
          val owner = Try(UUID.fromString(e.getString("owner"))).toOption
          val items = e.getList("items", Tag.TAG_COMPOUND).asScala.flatMap {
            case it: CompoundTag =>
              ItemStack.parseOptional(provider, it.getCompound("item")) match
                case s if !s.isEmpty => Some(new Entry(s, if it.contains("day") then it.getString("day") else "?"))
                case _ => None
            case _ => None
          }
          owner.filter(_ => items.nonEmpty).foreach { uuid =>
            shelves += uuid -> mutable.ListBuffer(items.toList*)
          }
        case _ => ()
      }
    }

  private def save(server: MinecraftServer): Unit =
    if !loaded then return // never read the shelves this session: never overwrite what disk holds
    try
      val root = new CompoundTag
      val provider = server.registryAccess()
      val list = new ListTag
      shelves.foreach { (uuid, shelf) =>
        if shelf.nonEmpty then
          val e = new CompoundTag
          e.putString("owner", uuid.toString)
          val items = new ListTag
          shelf.foreach { entry =>
            val it = new CompoundTag
            entry.stack.saveOptional(provider) match
              case null => ()
              case tag =>
                it.put("item", tag)
                it.putString("day", entry.storedDay)
                items.add(it)
          }
          e.put("items", items)
          list.add(e)
      }
      root.put("shelves", list)
      val p = path(server)
      Files.createDirectories(p.getParent)
      Files.writeString(p, root.getAsString)
    catch case e: Throwable => System.err.println(s"[aetheron-chaos] failed to save the stashes: $e")

  private def describe(stack: ItemStack): String =
    val name = stack.getDisplayName.getString
    if stack.getCount > 1 then s"$name x${stack.getCount}" else name
