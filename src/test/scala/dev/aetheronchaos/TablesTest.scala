package dev.aetheronchaos

class TablesTest extends munit.FunSuite:
  TestRealm.boot() // the quest and spell tables are plain Scala objects; only their Minecraft
  // constants (entity types, items) need the vanilla registries - the server's own bootstrap covers it

  test("quest ids are unique"):
    val ids = Quests.all.map(_.id)
    assertEquals(ids.distinct.size, ids.size, "two quests share an id")

  test("no quest chain points at a quest that does not exist"):
    val ids = Quests.all.map(_.id).toSet
    Quests.all.filter(_.requires.nonEmpty).foreach { q =>
      assert(ids.contains(q.requires), s"${q.id} requires '${q.requires}' - no such quest")
    }

  test("chains only ever ask more of a hero, never less"):
    val byId = Quests.all.map(q => q.id -> q).toMap
    Quests.all.filter(_.requires.nonEmpty).foreach { q =>
      byId.get(q.requires).foreach { parent =>
        assert(q.minLevel >= parent.minLevel, s"${q.id} (Lv ${q.minLevel}) hangs off the easier ${parent.id} (Lv ${parent.minLevel})")
      }
    }

  test("every quest is playable within the level cap and asks for real work"):
    Quests.all.foreach { q =>
      assert(q.minLevel >= 1 && q.minLevel <= Game.MaxLevel, s"${q.id} asks for Lv ${q.minLevel}; the realm plays 1..${Game.MaxLevel}")
      assert(q.xp > 0, s"${q.id} pays no XP")
      assert(q.coins >= 0, s"${q.id} has a negative purse")
      q.objective match
        case Objective.Kill(_, n, _) => assert(n >= 1, s"${q.id} asks for $n kills")
        case Objective.Collect(_, n) => assert(n >= 1, s"${q.id} asks for $n items")
        case Objective.WarBand(n) => assert(n >= 1, s"${q.id} asks for $n war-band kills")
    }

  test("quest rewards stay below legendary - legends are forged, not handed out"):
    val rewards = Quests.all.flatMap(_.reward)
    assert(rewards.nonEmpty, "the board stopped rewarding anything")
    rewards.foreach { g =>
      assert(g.rarity != Rarity.Legendary, s"${g.name} is Legendary - a quest just did the forger's work")
      assert(g.enchantments.forall(_._2 >= 1), s"${g.name} has a broken enchant line")
    }

  test("class trials belong to real classes, and each teaches exactly one spell"):
    val trials = Quests.all.filter(_.forClass.nonEmpty)
    assert(trials.nonEmpty, "the class trials vanished from the board")
    trials.foreach { q =>
      assert(HeroClasses.byId(q.forClass).nonEmpty, s"${q.id} is written for '${q.forClass}' - no such class")
      val taught = Quests.trialSpells(q.id)
      assertEquals(taught.size, 1, s"${q.id} should teach exactly one spell (teaches ${taught.map(_.id).mkString(", ")})")
    }

  test("every spell gate names a real quest"):
    val ids = Quests.all.map(_.id).toSet
    Spells.all.filter(_.gate.nonEmpty).foreach { s =>
      assert(ids.contains(s.gate), s"spell '${s.id}' is taught by quest '${s.gate}' - no such quest")
    }

  test("spell ids are unique, cooldowns real, unlock levels inside the cap"):
    val ids = Spells.all.map(_.id)
    assertEquals(ids.distinct.size, ids.size, "two spells share an id")
    Spells.all.foreach { s =>
      assert(s.unlockLevel >= 1 && s.unlockLevel <= Game.MaxLevel, s"${s.id} unlocks at Lv ${s.unlockLevel}")
      assert(s.cooldownTicks > 0, s"${s.id} has no cooldown - it would be a button masher")
    }

  test("the daily pool can fill the board, and every day posts a legal rotation"):
    assert(Quests.dailyPool.size >= Quests.DailySlots, "fewer dailies in the pool than the board posts")
    for day <- List(0L, 1L, 2L, 13L, 999L) do
      val picks = Quests.dailiesOfTheDay(day)
      assertEquals(picks.size, Quests.DailySlots, s"day $day posts ${picks.size} dailies")
      assertEquals(picks.map(_.id).distinct.size, Quests.DailySlots, s"day $day repeats a daily")
      assert(picks.forall(_.daily), s"day $day posts a non-daily")
    if Quests.dailyPool.size > Quests.DailySlots then
      assert(
        Quests.dailiesOfTheDay(0L).map(_.id) != Quests.dailiesOfTheDay(1L).map(_.id),
        "with more dailies than slots, two consecutive days must not post the same board"
      )
