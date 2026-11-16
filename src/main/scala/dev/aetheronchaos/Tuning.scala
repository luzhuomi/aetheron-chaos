package dev.aetheronchaos

import java.nio.file.Files
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Try

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource

/**
 * Runtime tuning: an optional `aetheron-chaos-tuning.conf` in the world folder lets a
 * server host move the realm's numbers without recompiling the mod. The format is
 * `key = number` per line, `#` starts a comment; the file is read once when the
 * overworld loads.
 *
 * Every value keeps its named default in the system that owns it - this file overrides,
 * it never replaces. Unknown keys are ignored (loudly, in the log, so typos surface);
 * aetheron-chaos-tuning.example.conf documents the keys that mean something today.
 */
object Tuning:
  private val FileName = "aetheron-chaos-tuning.conf"
  private val overrides = mutable.Map[String, Double]()
  private val consulted = mutable.LinkedHashMap[String, (Double, Boolean)]() // key -> (default, from file)
  private var loaded = false

  /** Read the world folder's tuning file, if there is one. Called on world load. */
  private[aetheronchaos] def load(server: MinecraftServer): Unit =
    loaded = true
    overrides.clear()
    val p = server.getWorldPath(LevelResource.ROOT).resolve(FileName)
    if !Files.exists(p) then return
    Try {
      Files.readAllLines(p).asScala.foreach { raw =>
        val line = raw.takeWhile(_ != '#').trim
        if line.nonEmpty then
          val parts = line.split("=", 2)
          if parts.length == 2 then
            val key = parts(0).trim.toLowerCase
            Try(parts(1).trim.toDouble) match
              case scala.util.Success(v) =>
                overrides(key) = v
                System.out.println(s"[aetheron-chaos] tuning: $key = $v")
              case scala.util.Failure(_) =>
                System.out.println(s"[aetheron-chaos] tuning: ignoring '$line' (not a number)")
          else System.out.println(s"[aetheron-chaos] tuning: ignoring '$line' (expected key = number)")
      }
      System.out.println(s"[aetheron-chaos] applied ${overrides.size} tuning overrides from $FileName")
    }

  private def get(key: String, default: Double): Option[Double] =
    val fromFile = overrides.contains(key)
    consulted(key) = (default, fromFile) // so /quest tuning can show what the realm actually uses
    if fromFile then Some(overrides(key)) else None

  /** Every key consulted this session, with its live value and where it came from. */
  private[aetheronchaos] def report: String =
    if consulted.isEmpty then
      "Nothing has consulted the tuning tables yet - the realm runs on defaults until systems wake up. Play a little, then ask again."
    else
      val rows = consulted.toList.sortBy(_._1).map { (key, info) =>
        val (default, fromFile) = info
        val effective = overrides.getOrElse(key, default)
        val src = if fromFile then "from file" else "default"
        s"  $key = ${fmtNum(effective)} ($src, built-in default ${fmtNum(default)})"
      }
      (List(s"Tuning in effect this session - ${overrides.size} override(s) on file, ${consulted.size} knob(s) consulted:") ::: rows :::
        List("Edit aetheron-chaos-tuning.conf in the world folder; values apply at next world load."))
        .mkString("\n")

  private def fmtNum(v: Double): String =
    if v == v.round.toDouble then v.round.toString else f"$v%.3f"

  private[aetheronchaos] def getDouble(key: String, default: Double): Double =
    get(key, default).getOrElse(default)

  private[aetheronchaos] def getInt(key: String, default: Int): Int =
    get(key, default.toDouble).map(v => math.round(v).toInt).getOrElse(default)

  private[aetheronchaos] def getLong(key: String, default: Long): Long =
    get(key, default.toDouble).map(v => math.round(v)).getOrElse(default)
