package dev.aetheronchaos

import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments

/** Quest objectives: slay N mobs of a type, or deliver N of an item. */
enum Objective:
  case Kill(target: EntityType[?], count: Int, label: String)
  case Collect(item: Item, count: Int)

/** A quest posted on the town quest board. requires: id of a quest that must be
 *  completed first - that's what makes chains ("finish A and B appears on the board").
 *  daily: part of the rotating pool - three of them are posted realm-wide each day.
 *  forClass: only this class is ever offered the quest (class trials). */
final case class Quest(
    id: String,
    name: String,
    minLevel: Int,
    hint: String,
    objective: Objective,
    xp: Int,
    coins: Int,
    reward: scala.Option[Loot.Gear],
    partyOnly: Boolean = false,
    requires: String = "",
    daily: Boolean = false,
    forClass: String = ""
)

object Quests:
  import Loot.Gear

  val all: List[Quest] = List(
    Quest(
      "first-rites", "Rookie Rites", 1,
      "Zombies shamble around at night - any graveyard-sized field will do.",
      Objective.Kill(EntityType.ZOMBIE, 4, "zombies"), 120, 3,
      Some(Gear(Items.IRON_SWORD, "Recruit's Sidearm", Rarity.Common, List(Enchantments.SHARPNESS -> 1)))
    ),
    Quest(
      "scourge-sweep", "Plague of the Undead", 2,
      "Zombies and husks roam deserts and dark places alike.",
      Objective.Kill(EntityType.ZOMBIE, 12, "zombies"), 320, 6, None
    ),
    Quest(
      "bone-collection", "The Bone Collector", 2,
      "Skeletons drop bones - collect them from the battlefield.",
      Objective.Collect(Items.BONE, 8), 260, 5,
      Some(Gear(Items.IRON_BOOTS, "Boots of the Courier", Rarity.Uncommon, List(Enchantments.PROTECTION -> 1)))
    ),
    Quest(
      "silithid-brood", "Silithid Brood", 2,
      "Spiders den in caves, forests and abandoned mineshafts.",
      Objective.Kill(EntityType.SPIDER, 8, "spiders"), 300, 5, None
    ),
    Quest(
      "hissing-doom", "Hissing Doom", 3,
      "Creepers want to meet you. From a safe distance, preferably.",
      Objective.Kill(EntityType.CREEPER, 6, "creepers"), 380, 6,
      Some(Gear(Items.CROSSBOW, "Steady Strings", Rarity.Uncommon, List(Enchantments.UNBREAKING -> 2)))
    ),
    Quest(
      "road-patrol", "Road Patrol", 3,
      "Pillager scouts prowl the trade roads. Thin them out before the warband follows.",
      Objective.Kill(EntityType.PILLAGER, 5, "pillager scouts"), 400, 7, None
    ),
    Quest(
      "warband", "Warband of the Pillager", 3,
      "Raid a pillager outpost or intercept their patrols.",
      Objective.Kill(EntityType.PILLAGER, 10, "pillagers"), 450, 8, None,
      requires = "road-patrol"
    ),
    Quest(
      "nightbane", "Nightbane Patrol", 4,
      "Phantoms stalk those who skip sleep - hunt them at dusk.",
      Objective.Kill(EntityType.PHANTOM, 5, "phantoms"), 480, 8,
      Some(Gear(Items.IRON_HELMET, "Dreamwreath Circlet", Rarity.Rare, List(Enchantments.PROTECTION -> 2)))
    ),
    Quest(
      "hexbreaker", "Hexbreaker", 4,
      "Witches brew their wicked potions near swamps and raids.",
      Objective.Kill(EntityType.WITCH, 3, "witches"), 460, 8, None
    ),
    Quest(
      "ravager-rampage", "Ravager Rampage", 5,
      "One ravager stands between the village and peace.",
      Objective.Kill(EntityType.RAVAGER, 1, "a ravager"), 550, 10,
      Some(Gear(Items.IRON_CHESTPLATE, "Girdle of Stone", Rarity.Rare, List(Enchantments.PROTECTION -> 2))),
      partyOnly = true,
      requires = "warband"
    ),
    Quest(
      "void-walkers", "Void Walkers", 5,
      "Endermen roam at night - do not look them in the eye.",
      Objective.Kill(EntityType.ENDERMAN, 4, "endermen"), 600, 10, None
    ),
    Quest(
      "pearl-hunt", "Pocketful of Pearls", 6,
      "Endermen leave behind ender pearls when slain.",
      Objective.Collect(Items.ENDER_PEARL, 3), 520, 10, None,
      requires = "void-walkers"
    ),
    Quest(
      "core-hounds", "Core Hound Hunt", 6,
      "Blazes guard the nether fortresses - bring water buckets.",
      Objective.Kill(EntityType.BLAZE, 5, "blazes"), 700, 12,
      Some(Gear(Items.BOW, "Windshot", Rarity.Rare, List(Enchantments.POWER -> 3, Enchantments.PUNCH -> 1)))
    ),
    Quest(
      "deep-duke", "The Deep Duke", 8,
      "The elder guardian rules an ocean monument, deep below.",
      Objective.Kill(EntityType.ELDER_GUARDIAN, 1, "the elder guardian"), 1200, 20,
      Some(Gear(Items.IRON_SWORD, "Tidesplitter", Rarity.Epic, List(Enchantments.SHARPNESS -> 3, Enchantments.UNBREAKING -> 3))),
      partyOnly = true
    ),
    Quest(
      "warden-below", "The Warden Below", 12,
      "Deep in the deep dark, something ancient stirs. Go quietly.",
      Objective.Kill(EntityType.WARDEN, 1, "the warden"), 3000, 50,
      Some(Gear(Items.IRON_SWORD, "Deepbrand, Bane of the Dark", Rarity.Epic, List(Enchantments.SHARPNESS -> 4, Enchantments.UNBREAKING -> 3))),
      partyOnly = true
    ),
    // --- class trials: complete yours to earn its signature spell (see Spell.gate) ---
    Quest(
      "trial-warrior-battalion", "Hollow Battalion", 12,
      "A warrior's answer to a raid is geometry: make eight pillagers into fewer.",
      Objective.Kill(EntityType.PILLAGER, 8, "pillagers"), 700, 12, None,
      forClass = "warrior"
    ),
    Quest(
      "trial-paladin-circle", "The Unbroken Circle", 12,
      "Four creepers, four walls, zero detonations. The hammer learns who swings it.",
      Objective.Kill(EntityType.CREEPER, 4, "creepers"), 680, 12, None,
      forClass = "paladin"
    ),
    Quest(
      "trial-hunter-longshot", "The Long Shot", 12,
      "Phantoms are the only targets that mock a bow from above. Four clean kills.",
      Objective.Kill(EntityType.PHANTOM, 4, "phantoms"), 650, 12, None,
      forClass = "hunter"
    ),
    Quest(
      "trial-rogue-silence", "Guild Trials: Silence", 14,
      "The Guild counts cellars cleared, not swords drawn. Six spiders, quietly.",
      Objective.Kill(EntityType.SPIDER, 6, "spiders"), 750, 14, None,
      forClass = "rogue"
    ),
    Quest(
      "trial-mage-embers", "The Crucible of Embers", 15,
      "A nova is a circle of fire you stand in the middle of. Prove you survive one: four blazes.",
      Objective.Kill(EntityType.BLAZE, 4, "blazes"), 900, 15, None,
      forClass = "mage"
    ),
    Quest(
      "trial-priest-vigil", "The Shepherd's Vigil", 15,
      "Six altars dark, six lamps to carry. Protection is learned by staying awake.",
      Objective.Collect(Items.GLOWSTONE_DUST, 6), 850, 15, None,
      forClass = "priest"
    ),
    // --- the daily rotation pool: three of these are posted realm-wide each day -----
    Quest(
      "daily-undead-cull", "Dawn Watch: Undead Cull", 1,
      "Six shamblers short of a quiet road. Any graveyard field will do.",
      Objective.Kill(EntityType.ZOMBIE, 6, "zombies"), 150, 4, None, daily = true
    ),
    Quest(
      "daily-bone-drive", "The Bone Drive", 1,
      "The hearth kiln needs bones - six will earn your keep today.",
      Objective.Collect(Items.BONE, 6), 140, 4, None, daily = true
    ),
    Quest(
      "daily-flesh-rations", "Rations for the Hearth", 1,
      "Hearthkeeper Finn cooks, you gather. Rotten flesh, surprisingly edible.",
      Objective.Collect(Items.ROTTEN_FLESH, 8), 130, 5, None, daily = true
    ),
    Quest(
      "daily-spider-sweep", "Cellar Sweep", 2,
      "Spiders in the cellars and mines again - clear six of them.",
      Objective.Kill(EntityType.SPIDER, 6, "spiders"), 220, 5, None, daily = true
    ),
    Quest(
      "daily-skeleton-drill", "Bone Drill", 2,
      "The watch trains on skeletons: steady aim, eight down.",
      Objective.Kill(EntityType.SKELETON, 8, "skeletons"), 260, 5, None, daily = true
    ),
    Quest(
      "daily-creeper-warden", "Creeper Warden", 3,
      "Four creepers away from the palisade - hear them hiss, find them first.",
      Objective.Kill(EntityType.CREEPER, 4, "creepers"), 280, 6, None, daily = true
    ),
    Quest(
      "daily-pillager-scouts", "Count the Scouts", 3,
      "Six pillager scouts report to their captain. Make one of the reports final.",
      Objective.Kill(EntityType.PILLAGER, 6, "pillager scouts"), 300, 6, None, daily = true
    ),
    Quest(
      "daily-witch-watch", "Hex on the Watch", 4,
      "Three witches circling the outskirts - their potions are running out.",
      Objective.Kill(EntityType.WITCH, 3, "witches"), 320, 7, None, daily = true
    ),
    Quest(
      "daily-phantom-dusk", "Dusk Patrol", 4,
      "Phantoms gather where the sleepless walk. Four fewer by morning.",
      Objective.Kill(EntityType.PHANTOM, 4, "phantoms"), 340, 7, None, daily = true
    )
  )

  /** The daily rotation pool. */
  val dailyPool: List[Quest] = all.filter(_.daily)

  /** How many dailies are posted per day. */
  val DailySlots = 3

  /** The realm's dailies for one real-world day - same three for every hero,
   *  decided by the date alone (no state to corrupt, no sync to do). */
  def dailiesOfTheDay(epochDay: Long): List[Quest] =
    scala.util.Random(epochDay * 7919L).shuffle(dailyPool).take(DailySlots)

  def byId(id: String): Option[Quest] = all.find(_.id == id)

  /** The quests that a completed quest unlocks - the next chapter of its chain. */
  def nextInChain(id: String): List[Quest] = all.filter(_.requires == id)

  /** Spells earned by completing this class trial (see Spell.gate). */
  def trialSpells(questId: String): List[Spell] = Spells.all.filter(_.gate == questId)

  /** May this hero see/take this quest yet? (level gate + chain prerequisite) */
  def isUnlocked(q: Quest, done: Set[String]): Boolean =
    q.requires.isEmpty || done.contains(q.requires)

  def prereqName(q: Quest): String =
    byId(q.requires).map(_.name).getOrElse(q.requires)

  def objectiveText(q: Quest): String =
    q.objective match
      case Objective.Kill(_, count, label) => s"Defeat $count $label"
      case Objective.Collect(item, count) => s"Deliver $count x ${ItemStack(item).getHoverName.getString}"
