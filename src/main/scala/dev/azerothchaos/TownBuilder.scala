package dev.azerothchaos

import scala.collection.mutable

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity

/**
 * Builds a small quest hub ("village") in front of the player: a quest board
 * (loom + signs) where quests are accepted and turned in, a hearth to rest at,
 * and torch-lit fences. Registered boards gate quest turn-ins.
 */
object TownBuilder:
  // bounding boxes (minX,minY,minZ,maxX,maxY,maxZ) of towns built this server session
  private val built = mutable.Stack[(Int, Int, Int, Int, Int, Int)]()
  // positions of registered quest boards (BlockPos.asLong)
  private val boards = mutable.Set[Long]()
  // positions of registered vendor barrels (BlockPos.asLong)
  private val vendors = mutable.Set[Long]()

  def boardCount: Int = boards.size

  def isBoard(pos: BlockPos): Boolean = boards.contains(pos.asLong)

  def isVendor(pos: BlockPos): Boolean = vendors.contains(pos.asLong)

  /** True if the player stands within ~12 blocks of any registered quest board. */
  def nearBoard(player: ServerPlayer): Boolean =
    boards.exists { l =>
      val bp = BlockPos.of(l)
      player.blockPosition.distSqr(bp) <= 144
    }

  def build(player: ServerPlayer): String =
    val level = player.level
    val dir = player.getDirection
    val right = dir.getClockWise
    val base = player.blockPosition.offset(dir.getStepX * 5, 0, dir.getStepZ * 5)

    def at(forward: Int, lateral: Int, dy: Int): BlockPos =
      base.offset(right.getStepX * lateral + dir.getStepX * forward, dy, right.getStepZ * lateral + dir.getStepZ * forward)

    var placed = 0
    def set(pos: BlockPos, block: Block): Unit =
      if level.setBlock(pos, block.defaultBlockState, 3) then placed += 1

    def sign(pos: BlockPos, lines: List[String]): Unit =
      set(pos, Blocks.OAK_SIGN)
      level.getBlockEntity(pos) match
        case s: SignBlockEntity =>
          val front = s.getFrontText
          lines.zipWithIndex.foreach { (line, i) => front.setMessage(i, Component.literal(line)) }
          s.setChanged()
        case _ => ()

    // stone plaza with cleared headroom
    for f <- 0 to 10; l <- -5 to 5 do
      set(at(f, l, 0), if (f + l) % 2 == 0 then Blocks.STONE_BRICKS else Blocks.MOSSY_STONE_BRICKS)
      set(at(f, l, 1), Blocks.AIR)
      set(at(f, l, 2), Blocks.AIR)

    // quest board: loom on a pedestal, flanked by signs, banner overhead
    set(at(8, 0, 1), Blocks.SMOOTH_STONE_SLAB)
    set(at(8, 0, 2), Blocks.LOOM)
    boards += at(8, 0, 2).asLong
    sign(at(8, -1, 1), List("QUEST BOARD", "Right-click me", "to take quests", "and turn them in"))
    sign(at(8, 1, 1), List("HERO HALL", "/quest join", "/quest class", "watch for RARE ELITES"))
    set(at(8, 0, 3), Blocks.WHITE_BANNER)

    // hearth (rest and craft) + general vendor
    set(at(5, -3, 1), Blocks.CAMPFIRE)
    set(at(5, 3, 1), Blocks.BREWING_STAND)
    set(at(5, 4, 1), Blocks.BARREL)
    vendors += at(5, 4, 1).asLong
    sign(at(4, 4, 1), List("GENERAL VENDOR", "Right-click me", "to spend your gold", "/quest shop"))

    // perimeter posts with lanterns
    for f <- List(0, 10); l <- -5 to 5 by 5 do
      set(at(f, l, 1), Blocks.OAK_FENCE)
      set(at(f, l, 2), Blocks.LANTERN)
    for f <- List(0, 10); l <- List(-5, 5) do
      set(at(f, l, 1), Blocks.OAK_FENCE)
      set(at(f, l, 2), Blocks.LANTERN)

    // welcome signs at the front
    for l <- List(-5, 5) do
      set(at(0, l, 1), Blocks.OAK_FENCE)
      sign(at(0, l, 2), List("WELCOME, ADVENTURER", "Slay monsters -> XP", "Level up -> loot", "/quest board"))

    val c1 = at(0, -5, 0)
    val c2 = at(10, 5, 3)
    built.push((
      math.min(c1.getX, c2.getX), math.min(c1.getY, c2.getY), math.min(c1.getZ, c2.getZ),
      math.max(c1.getX, c2.getX), math.max(c1.getY, c2.getY), math.max(c1.getZ, c2.getZ)
    ))

    s"Quest hub built in front of you ($placed blocks). Right-click the LOOM quest board to take quests. Remove it with /quest clear."

  /** Removes the most recently built town (or all of them). */
  def clear(player: ServerPlayer, all: Boolean): String =
    if built.isEmpty then return "No towns to clear."
    val level = player.level
    val regions =
      if all then
        val r = built.toList
        built.clear()
        boards.clear()
        vendors.clear()
        r
      else List(built.pop())
    var blocks = 0
    regions.foreach { case (x1, y1, z1, x2, y2, z2) =>
      for x <- x1 to x2; y <- y1 to y2; z <- z1 to z2 do
        val pos = BlockPos(x, y, z)
        if !level.getBlockState(pos).isAir then
          boards -= pos.asLong
          vendors -= pos.asLong
          level.setBlock(pos, Blocks.AIR.defaultBlockState, 3)
          blocks += 1
    }
    s"Cleared ${regions.size} town(s) ($blocks blocks). Note: anything built inside the town area was removed too."
