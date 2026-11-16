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

  test("kill loot rarity bands climb with the mob tier and stop below legendary"):
    assertEquals(Formulas.maxRarityOrdinalFor(1), 0) // Common
    assertEquals(Formulas.maxRarityOrdinalFor(2), 1) // Uncommon
    assertEquals(Formulas.maxRarityOrdinalFor(3), 2) // Rare
    assertEquals(Formulas.maxRarityOrdinalFor(4), 2)
    assertEquals(Formulas.maxRarityOrdinalFor(5), 3) // Epic
    for tier <- 1 to 12 do
      assert(Formulas.maxRarityOrdinalFor(tier) < 4, s"tier $tier reached Legendary - legends are forged, not found")
    for tier <- 1 until 12 do
      assert(Formulas.maxRarityOrdinalFor(tier + 1) >= Formulas.maxRarityOrdinalFor(tier), s"tier ${tier + 1} went backwards")

  test("the enchant bump lands at tier 4 and stops at the cap"):
    for tier <- 1 to 3 do assertEquals(Formulas.enchantBumpFor(tier), 0, s"tier $tier should not bump lines")
    for tier <- 4 to 9 do assertEquals(Formulas.enchantBumpFor(tier), 1)
    assertEquals(Formulas.EnchantLevelCap, 5)

  test("the merchant's buy ladder rises by rarity and always pays real coin"):
    assertEquals(Formulas.rarityBuyPrice(0), 5)
    for r <- 0 until 4 do
      assert(Formulas.rarityBuyPrice(r + 1) > Formulas.rarityBuyPrice(r), s"rarity $r paid as well as ${r + 1}")
    assert(Formulas.rarityBuyPrice(4) < 5000, "the merchant should never outbid the trade post ceiling")

  test("sigils buy back at a fixed price, worth more than common gear"):
    assertEquals(Formulas.sigilBuyPrice, 25)
    assert(Formulas.sigilBuyPrice > Formulas.rarityBuyPrice(0))

  test("the junk table pays for the drops the dailies collect, and never for coin"):
    for id <- List("rotten_flesh", "bone", "string", "gunpowder", "slime_ball", "ender_pearl") do
      assert(Formulas.junkBuyPrices.contains(id), s"the merchant stopped buying $id - a daily depends on it")
      assert(Formulas.junkBuyPrices(id) >= 1)
    assert(!Formulas.junkBuyPrices.contains("gold_nugget"), "gold nuggets are already money")

/** The level cap, mirrored so the test does not load the Minecraft-typed Game object. */
private object Game0:
  val MaxLevel = 40
