package dev.azerothchaos

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Try

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.{CompoundTag, ListTag, TagParser, Tag}
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource

/**
 * Persists built towns to a plain SNBT file in the world folder
 * (azeroth-chaos-towns.snbt): just the anchor block + facing of each hub, which
 * is enough to re-register quest boards and vendor barrels after a restart
 * (the blocks themselves are already in the world).
 */
object TownStore:
  private val FileName = "azeroth-chaos-towns.snbt"

  private def path(server: MinecraftServer): Path = server.getWorldPath(LevelResource.ROOT).resolve(FileName)

  def load(server: MinecraftServer): List[TownBuilder.Town] =
    val p = path(server)
    if !Files.exists(p) then Nil
    else
      Try:
        val root = TagParser.parseTag(Files.readString(p))
        root.getList("towns", Tag.TAG_COMPOUND).asScala.toList.flatMap {
          case e: CompoundTag =>
            Some(
              TownBuilder.Town(
                new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")),
                Direction.from3DDataValue(e.getInt("dir")),
                (e.getInt("x1"), e.getInt("y1"), e.getInt("z1"), e.getInt("x2"), e.getInt("y2"), e.getInt("z2")),
                e.getBoolean("capital")
              )
            )
          case _ => None
        }
      .getOrElse(Nil)

  def save(server: MinecraftServer): Unit =
    try
      val list = new ListTag
      TownBuilder.savedTowns.foreach { t =>
        val e = new CompoundTag
        e.putInt("x", t.base.getX)
        e.putInt("y", t.base.getY)
        e.putInt("z", t.base.getZ)
        e.putInt("dir", t.dir.get3DDataValue)
        val (x1, y1, z1, x2, y2, z2) = t.region
        e.putInt("x1", x1); e.putInt("y1", y1); e.putInt("z1", z1)
        e.putInt("x2", x2); e.putInt("y2", y2); e.putInt("z2", z2)
        e.putBoolean("capital", t.capital)
        list.add(e)
      }
      val root = new CompoundTag
      root.put("towns", list)
      val p = path(server)
      Files.createDirectories(p.getParent)
      Files.writeString(p, root.getAsString)
    catch case e: Throwable => System.err.println(s"[azeroth-chaos] failed to save towns: $e")
