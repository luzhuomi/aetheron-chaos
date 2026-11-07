package dev.azerothchaos

import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments

/** Quest objectives: slay N mobs of a type, or deliver N of an item. */
enum Objective:
  case Kill(target: EntityType[?], count: Int, label: String)
  case Collect(item: Item, count: Int)

/** A quest posted on the town quest board. */
final case class Quest(
    id: String,
    name: String,
    minLevel: Int,
    hint: String,
    objective: Objective,
    xp: Int,
    coins: Int,
    reward: scala.Option[Loot.Gear],
    partyOnly: Boolean = false
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
      "warband", "Warband of the Pillager", 3,
      "Raid a pillager outpost or intercept their patrols.",
      Objective.Kill(EntityType.PILLAGER, 10, "pillagers"), 450, 8, None
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
      partyOnly = true
    ),
    Quest(
      "void-walkers", "Void Walkers", 5,
      "Endermen roam at night - do not look them in the eye.",
      Objective.Kill(EntityType.ENDERMAN, 4, "endermen"), 600, 10, None
    ),
    Quest(
      "pearl-hunt", "Pocketful of Pearls", 6,
      "Endermen leave behind ender pearls when slain.",
      Objective.Collect(Items.ENDER_PEARL, 3), 520, 10, None
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
    )
  )

  def byId(id: String): Option[Quest] = all.find(_.id == id)

  def objectiveText(q: Quest): String =
    q.objective match
      case Objective.Kill(_, count, label) => s"Defeat $count $label"
      case Objective.Collect(item, count) => s"Deliver $count x ${ItemStack(item).getHoverName.getString}"
