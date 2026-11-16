package dev.aetheronchaos

import scala.jdk.CollectionConverters.*

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/** Per-tick action bar: class, level, XP bar, HP, quest tracker and party. */
object Hud:
  def update(player: ServerPlayer): Unit =
    player.displayClientMessage(bar(player), true)

  private def bar(player: ServerPlayer): Component =
    val c = Game.characterFor(player)
    val className = HeroClasses.byId(c.classId).map(_.name).getOrElse("Adventurer")
    val atCap = c.level >= Game.MaxLevel
    val need = Game.xpNeed(c)
    val pct = ((c.xp * 100) / math.max(1L, need)).toInt
    val filled = (pct / 10).toInt
    val xpBar = ("█" * filled) + ("░" * (10 - filled))
    val hp = f"${player.getHealth.toFloat}%.0f/${player.getMaxHealth.toFloat}%.0f"

    val spellTag = if Spells.schoolFor(c.classId).nonEmpty then s"  ✦ ${GameSpells.equippedSpell(c).name}" else ""
    val paragonTag = if c.paragon > 0 then s" ★${c.paragon}" else ""
    val titleTag = Achievements.byId(c.title).map(a => s" \"${a.title}\"").getOrElse("")
    val pool = if atCap then s"PAR $pct%" else s"XP $pct%"
    val head = Component
      .literal(s"Lv ${c.level}$paragonTag $className$titleTag  $pool $xpBar  HP $hp$spellTag")
      .withStyle(if atCap then ChatFormatting.AQUA else ChatFormatting.GOLD)

    // tracked quests with a live compass to the next objective (or the board when done)
    val compass = GameQuests.trackerCompass(player).toMap
    val quests = c.tracked.toList.flatMap(Quests.byId).take(2).map { q =>
      val have = GameQuests.progressOf(player, q)
      val goal = Quests.goalOf(q)
      val done = if have >= goal then " DONE->board" else ""
      val nav = compass.get(q.id).map(" " + _).getOrElse("")
      s"${q.name} $have/$goal$done$nav"
    }
    if quests.nonEmpty then head.append(Component.literal(s"   ${quests.mkString("  |  ")}").withStyle(ChatFormatting.AQUA))
    else head.append(Component.literal("   /quest track <id> for a compass").withStyle(ChatFormatting.DARK_GRAY))

    // the trinity's tell: only when you *lost* something, never a wall of noise
    Threat.lostAggroTo(player).foreach { gone =>
      head.append(Component.literal(s"  \u26a0 aggro: $gone (/quest threat)").withStyle(ChatFormatting.RED))
    }

    c.party.foreach { pname =>
      val mates = Game.partiesOf(player, pname)
      if mates.nonEmpty then head.append(Component.literal(s"   Party: ${mates.mkString(", ")}").withStyle(ChatFormatting.LIGHT_PURPLE))
    }
    head
