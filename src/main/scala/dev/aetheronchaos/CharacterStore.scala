package dev.aetheronchaos

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Try

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource

/**
 * Persists hero characters to a plain SNBT file in the world folder
 * (aetheron-chaos-characters.snbt), so levels, quests and class survive restarts.
 */
object CharacterStore:
  private val FileName = "aetheron-chaos-characters.snbt"
  // worlds saved back when the mod was named "azeroth-chaos": migrated on first load
  private val LegacyFileName = "azeroth-chaos-characters.snbt"

  private def path(server: MinecraftServer): Path =
    val dir = server.getWorldPath(LevelResource.ROOT)
    val current = dir.resolve(FileName)
    if !Files.exists(current) then
      val legacy = dir.resolve(LegacyFileName)
      if Files.exists(legacy) then Try(Files.move(legacy, current))
    current

  def readAll(server: MinecraftServer): Map[UUID, CompoundTag] =
    val p = path(server)
    if !Files.exists(p) then Map.empty
    else
      Try:
        val root = TagParser.parseTag(Files.readString(p))
        root.getAllKeys.asScala.map(k => UUID.fromString(k) -> root.getCompound(k)).toMap
      .getOrElse(Map.empty)

  /** Loads one character from disk if it is not already held in memory. */
  def loadFor(server: MinecraftServer, uuid: UUID): Unit =
    if !Game.allCharacters.exists(_.uuid == uuid) then
      readAll(server).get(uuid).foreach(tag => Game.mergeFromTag(uuid, tag))

  /** Writes every live character, keeping disk entries for heroes not loaded this session. */
  def save(server: MinecraftServer): Unit =
    try
      val merged = readAll(server).toMap ++ Game.allCharacters.map(c => c.uuid -> Game.tagFor(c)).toMap
      val root = new CompoundTag
      merged.foreach { (uuid, tag) => root.put(uuid.toString, tag) }
      val p = path(server)
      Files.createDirectories(p.getParent)
      Files.writeString(p, root.getAsString)
    catch case e: Throwable => System.err.println(s"[aetheron-chaos] failed to save characters: $e")
