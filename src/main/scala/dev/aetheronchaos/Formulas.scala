package dev.aetheronchaos

/**
 * Pure realm arithmetic: no Minecraft types anywhere, so it compiles and runs without a
 * world (see src/test). The systems - Game, TownBuilder, GuardWatch, QuestCommand -
 * delegate their numbers here, which is also where a balance change should be made once.
 */
object Formulas:

  /** XP needed to reach the next level. Quadratic on purpose: early levels are quick. */
  def xpToNext(level: Int): Long = 100L + 60L * level + 35L * level * level

  /** Past the cap, experience feeds paragon tiers, and each tier costs a little more.
   *  The base/step are the defaults; Tuning can move them (`paragon.base_xp`, `paragon.step_xp`). */
  def paragonToNext(tier: Int, baseXp: Long = 2000L, stepXp: Long = 1500L): Long = baseXp + stepXp * tier

  /** Paragon tiers sharpen the hero's craft: +3% XP earned per tier, to a +50% ceiling
   *  (both percentages are the defaults; Tuning can move them). */
  def paragonXpRate(paragon: Int, perTierPercent: Double = 3.0, capPercent: Double = 50.0): Double =
    math.min(1.0 + capPercent / 100.0, 1.0 + (perTierPercent / 100.0) * paragon)

  /** Wayside portal toll: 1 gold per 20 blocks travelled, 5 minimum - a real sink. */
  def warpToll(distanceBlocks: Double): Int = math.max(5, (distanceBlocks / 20.0).floor.toInt)

  /** A hub's watch charter: the capital 3, other hubs 2, and one more where a barracks stands. */
  def guardCharter(capital: Boolean, barracks: Boolean): Int =
    (if capital then 3 else 2) + (if barracks then 1 else 0)

  // --- loot rarity & the merchant's tables -----------------------------------------

  /** Which rarity a kill roll of mob tier t may reach (0 Common .. 4 Legendary).
   *  Legendary never drops from a kill - it is forged, not found. */
  def maxRarityOrdinalFor(tier: Int): Int = tier match
    case 1 => 0
    case 2 => 1
    case 3 | 4 => 2
    case _ => 3

  /** Higher tiers push enchant lines up: +1 level from tier 4, to a cap of 5. */
  def enchantBumpFor(tier: Int): Int = if tier >= 4 then 1 else 0
  val EnchantLevelCap = 5

  /** What the merchant pays for our loot by rarity ordinal - well below her selling prices. */
  def rarityBuyPrice(rarityOrdinal: Int): Int = rarityOrdinal match
    case 0 => 5
    case 1 => 12
    case 2 => 30
    case 3 => 65
    case 4 => 110
    case _ => 1

  /** The forger buys back proofs too - she is always hiring reputation. */
  val sigilBuyPrice = 25

  /** Everyday farm and dungeon junk the village shop will happily take: item id -> gold. */
  val junkBuyPrices: Map[String, Int] = Map(
    "rotten_flesh" -> 1, "bone" -> 1, "string" -> 1, "spider_eye" -> 1,
    "gunpowder" -> 2, "slime_ball" -> 2, "magma_cream" -> 2, "blaze_rod" -> 3,
    "leather" -> 2, "feather" -> 1, "ink_sac" -> 1, "glow_ink_sac" -> 2,
    "ender_pearl" -> 5, "ghast_tear" -> 6, "prismarine_shard" -> 2,
    "coal" -> 1, "copper_ingot" -> 1, "raw_iron" -> 2, "raw_gold" -> 3,
    "iron_ingot" -> 3, "gold_ingot" -> 4, "redstone" -> 1, "lapis_lazuli" -> 1,
    "quartz" -> 1, "amethyst_shard" -> 2, "diamond" -> 12, "emerald" -> 10,
    "wheat" -> 2, "potato" -> 1, "carrot" -> 1, "beetroot" -> 1, "egg" -> 1,
    "sugar_cane" -> 1, "cactus" -> 1, "pumpkin" -> 1, "cocoa_beans" -> 1,
    "nether_wart" -> 2
  )

  /** Levenshtein distance - the "did you mean" behind quest and spell typo hints. */
  def editDistance(a: String, b: String): Int =
    var prev = Array.tabulate(b.length + 1)(identity)
    for i <- 1 to a.length do
      val cur = Array.ofDim[Int](b.length + 1)
      cur(0) = i
      for j <- 1 to b.length do
        val sub = prev(j - 1) + (if a(i - 1) == b(j - 1) then 0 else 1)
        cur(j) = math.min(math.min(prev(j) + 1, cur(j - 1) + 1), sub)
      prev = cur
    prev(b.length)
