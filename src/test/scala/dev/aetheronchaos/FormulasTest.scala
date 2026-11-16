package dev.aetheronchaos

/**
 * Unit tests for the pure realm arithmetic (Formulas.scala) - the curves and costs the
 * whole mod is tuned on. No Minecraft runtime: these run in plain `./gradlew test`.
 */
class FormulasTest extends munit.FunSuite:

  test("xpToNext matches the designed table at low levels"):
    assertEquals(Formulas.xpToNext(1), 195L)
    assertEquals(Formulas.xpToNext(2), 360L)
    assertEquals(Formulas.xpToNext(10), 4200L)

  test("the xp curve rises monotonically up to the cap"):
    for level <- 1 until Game0.MaxLevel do
      assert(Formulas.xpToNext(level + 1) > Formulas.xpToNext(level), s"level $level did not cost more than ${level - 1}")

  test("paragon tiers cost more than the last, from the designed base"):
    assertEquals(Formulas.paragonToNext(0), 2000L)
    for tier <- 0 to 20 do
      assert(Formulas.paragonToNext(tier + 1) > Formulas.paragonToNext(tier))

  test("a tuned base and step flow through the same curve"):
    // what Tuning does with paragon.base_xp / paragon.step_xp
    assertEquals(Formulas.paragonToNext(0, baseXp = 5000L, stepXp = 2500L), 5000L)
    assertEquals(Formulas.paragonToNext(2, baseXp = 5000L, stepXp = 2500L), 10000L)
    assertEqualsDouble(Formulas.paragonXpRate(4, perTierPercent = 5.0, capPercent = 10.0), 1.10, 1e-9)

  test("the paragon xp bonus is +3% per tier and stops at +50%"):
    assertEqualsDouble(Formulas.paragonXpRate(0), 1.0, 1e-9)
    assertEqualsDouble(Formulas.paragonXpRate(5), 1.15, 1e-9)
    assertEqualsDouble(Formulas.paragonXpRate(17), 1.5, 1e-9) // 51% would exceed the ceiling
    assertEqualsDouble(Formulas.paragonXpRate(99), 1.5, 1e-9)

  test("the warp toll is 1 gold per 20 blocks with a 5 gold floor"):
    assertEquals(Formulas.warpToll(0.0), 5)
    assertEquals(Formulas.warpToll(99.0), 5) // floors to 4, the minimum pays
    assertEquals(Formulas.warpToll(100.0), 5)
    assertEquals(Formulas.warpToll(119.9), 5)
    assertEquals(Formulas.warpToll(120.0), 6)
    assertEquals(Formulas.warpToll(1200.0), 60)

  test("the watch charter: capital 3, hub 2, +1 where a barracks stands"):
    assertEquals(Formulas.guardCharter(capital = true, barracks = false), 3)
    assertEquals(Formulas.guardCharter(capital = false, barracks = false), 2)
    assertEquals(Formulas.guardCharter(capital = true, barracks = true), 4)
    assertEquals(Formulas.guardCharter(capital = false, barracks = true), 3)

  test("edit distance finds the typos the command tree actually sees"):
    assertEquals(Formulas.editDistance("bone-copllection", "bone-collection"), 1)
    assertEquals(Formulas.editDistance("knight", "night"), 1)
    assertEquals(Formulas.editDistance("", "abc"), 3)
    assertEquals(Formulas.editDistance("same", "same"), 0)
    assertEquals(Formulas.editDistance("flaw", "lawn"), 2) // drop the f, add the n

  test("typo hints stay inside the distance the UI promises"):
    // GameSpells/QuestCommand only suggest within 3 - a far typo must not be "helped"
    assert(Formulas.editDistance("firebolt", "quickshot") > 3)

/** The level cap, mirrored so the test does not load the Minecraft-typed Game object. */
private object Game0:
  val MaxLevel = 40
