package dev.azerothchaos

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
    val need = Game.xpToNext(c.level)
    val pct = if c.level >= Game.MaxLevel then 100 else ((c.xp * 100) / math.max(1L, need)).toInt
    val filled = (pct / 10).toInt
    val xpBar = ("█" * filled) + ("░" * (10 - filled))
    val hp = f"${player.getHealth.toFloat}%.0f/${player.getMaxHealth.toFloat}%.0f"

    val spellTag = if c.classId == "mage" then s"  ✦ ${Game.equippedSpell(c).name}" else ""
    val head = Component
      .literal(s"Lv ${c.level} $className  XP $pct%% $xpBar  HP $hp$spellTag")
      .withStyle(ChatFormatting.GOLD)

    // tracked quests with a live compass to the next objective (or the board when done)
    val compass = Game.trackerCompass(player).toMap
    val quests = c.tracked.toList.flatMap(Quests.byId).take(2).map { q =>
      val have = Game.progressOf(player, q)
      val goal = q.objective match
        case Objective.Kill(_, n, _) => n
        case Objective.Collect(_, n) => n
      val done = if have >= goal then " DONE->board" else ""
      val nav = compass.get(q.id).map(" " + _).getOrElse("")
      s"${q.name} $have/$goal$done$nav"
    }
    if quests.nonEmpty then head.append(Component.literal(s"   ${quests.mkString("  |  ")}").withStyle(ChatFormatting.AQUA))
    else head.append(Component.literal("   /quest track <id> for a compass").withStyle(ChatFormatting.DARK_GRAY))

    c.party.foreach { pname =>
      val mates = Game.partiesOf(player, pname)
      if mates.nonEmpty then head.append(Component.literal(s"   Party: ${mates.mkString(", ")}").withStyle(ChatFormatting.LIGHT_PURPLE))
    }
    head
