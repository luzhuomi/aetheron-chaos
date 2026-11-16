package dev.aetheronchaos

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/** What an achievement counts. Everything here is a counter the realm already keeps. */
enum Metric:
  case Kills, RareKills, QuestsDone, SiegesHeld, CaptainsFelled, LegendsForged, Level, Paragon, Deaths

/** A title for doing the same thing often. */
final case class Achievement(id: String, title: String, metric: Metric, goal: Int, how: String)

/**
 * Titles and achievements: no new gameplay systems, just a ladder of recognition on top of
 * the counters Game already keeps. Earning one is announced to the hero and broadcast to the
 * realm; earned titles can be carried (shown on the action bar and the leaderboard), and
 * `/quest titles` lists the ladder with live progress.
 */
object Achievements:
  val all: List[Achievement] = List(
    Achievement("first-blood", "First Blood", Metric.Kills, 10, "ten foes in the ground"),
    Achievement("road-worn", "Road-Worn", Metric.Kills, 150, "one hundred and fifty fights, mostly won"),
    Achievement("the-reaper", "the Reaper", Metric.Kills, 600, "six hundred foes - the fields are quiet"),
    Achievement("elite-hunter", "Elite Hunter", Metric.RareKills, 3, "three named elites brought down"),
    Achievement("bane-of-elites", "Bane of Elites", Metric.RareKills, 15, "fifteen rare elites remember your name"),
    Achievement("questbound", "Questbound", Metric.QuestsDone, 5, "five quests turned in at the board"),
    Achievement("storykeeper", "Storykeeper", Metric.QuestsDone, 25, "twenty-five quests - the boards tell your story"),
    Achievement("shield-of-the-hearth", "Shield of the Hearth", Metric.SiegesHeld, 1, "stood at a wall when the horn sounded"),
    Achievement("last-at-the-wall", "Last at the Wall", Metric.SiegesHeld, 8, "eight sieges held to the last horn"),
    Achievement("captain-bane", "Captain Bane", Metric.CaptainsFelled, 5, "five war-band captains taken down"),
    Achievement("legend-keeper", "Legend Keeper", Metric.LegendsForged, 1, "struck a legend at the village forge"),
    Achievement("master-of-the-forge", "Master of the Forge", Metric.LegendsForged, 5, "five legends cooled in the trough"),
    Achievement("veteran", "Veteran", Metric.Level, 10, "level ten - the realm stops being surprised by you"),
    Achievement("warden", "Warden", Metric.Level, 25, "level twenty-five of forty"),
    Achievement("the-ascendant", "the Ascendant", Metric.Level, 40, "the level cap, and everything past the map"),
    Achievement("the-reborn", "the Reborn", Metric.Paragon, 1, "the cap was a door, and you walked through it"),
    Achievement("the-everwalking", "the Everwalking", Metric.Paragon, 3, "three paragon tiers - the road has little left to teach you"),
    Achievement("the-undying", "the Undying", Metric.Paragon, 7, "seven paragon tiers deep"),
    Achievement("un-killable", "the Un-Killable", Metric.Deaths, 20, "twenty deaths and still logging in")
  )

  def byId(id: String): Option[Achievement] = all.find(_.id == id)

  /** Where this hero stands on one achievement right now. */
  def progress(c: Game.Character, a: Achievement): Int =
    a.metric match
      case Metric.Kills => c.kills
      case Metric.RareKills => c.rareKills
      case Metric.QuestsDone => c.done.size
      case Metric.SiegesHeld => c.siegesHeld
      case Metric.CaptainsFelled => c.captainsFelled
      case Metric.LegendsForged => c.legendsForged
      case Metric.Level => c.level
      case Metric.Paragon => c.paragon
      case Metric.Deaths => c.deaths

  def earned(c: Game.Character, a: Achievement): Boolean = progress(c, a) >= a.goal

  def earnedAll(c: Game.Character): List[Achievement] = all.filter(earned(c, _))

  /** Checks every title for this hero and announces whatever is new. Runs on a slow tick,
   *  so it needs no hooks in the systems that move the counters. */
  def check(player: ServerPlayer): Unit =
    val c = Game.ensure(player.getUUID)
    if !c.joined then return
    all.filterNot(a => c.titles.contains(a.id)).filter(a => earned(c, a)).foreach { a =>
      c.titles += a.id
      if c.title.isEmpty then c.title = a.id // the first one is carried free
      player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.2f)
      Game.message(player, s"Title earned: ${a.title} (${a.how}). Carry it with /quest title ${a.id}; /quest titles shows the rest.", ChatFormatting.GOLD)
      Game.broadcast(
        player.server,
        Component
          .literal(s"${player.getName.getString} now carries the title ${a.title} - ${a.how}.")
          .withStyle(ChatFormatting.YELLOW)
      )
    }

  /** The titles still out of reach, nearest first - the ladder, with live progress. */
  def ladder(c: Game.Character): List[(Achievement, Int, Int)] =
    all
      .filterNot(earned(c, _))
      .map(a => (a, math.min(progress(c, a), a.goal), a.goal))
      .sortBy { (a, have, goal) => (-have.toDouble / goal.toDouble, goal, a.id) }

  /** `/quest titles` - what you carry and what you're working toward. */
  def report(player: ServerPlayer): String =
    val c = Game.ensure(player.getUUID)
    val carried = byId(c.title).map(a => s"Carrying: ${a.title}").getOrElse("Carrying: no title (/quest title <id>)")
    val mine = earnedAll(c)
    (List(
      s"Titles - ${mine.size}/${all.size} earned. $carried",
      if mine.isEmpty then "Nothing earned yet. The ladder is below."
      else "Earned: " + mine.map(a => s"${a.title} [${a.id}]").mkString(", ")
    ) ++ List("Working toward:") ++ ladder(c).take(6).map { (a, have, goal) =>
      val pct = if goal == 0 then 0 else (have * 100) / goal
      s"  ${a.title} - $have/$goal ($pct%) - ${a.how}"
    }).mkString("\n")

  /** `/quest title <id>`: carry an earned title, or none to set it down. */
  def carry(player: ServerPlayer, id: String): String =
    val c = Game.ensure(player.getUUID)
    if id.isEmpty || id == "none" || id == "clear" then
      c.title = ""
      "You set the title down. The deeds still count."
    else
      byId(id).orElse(all.find(_.title.equalsIgnoreCase(id))).map(_.id) match
        case None => s"There is no title '$id'. /quest titles lists what there is."
        case Some(real) =>
          if !earned(c, byId(real).get) then
            val a = byId(real).get
            s"'${a.title}' is not yours yet - ${progress(c, a)}/${a.goal} toward it (${a.how})."
          else
            c.title = real
            s"Carrying: ${byId(real).get.title}. It rides with you on the action bar."
