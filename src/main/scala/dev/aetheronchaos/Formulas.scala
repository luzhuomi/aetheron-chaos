package dev.aetheronchaos

/**
 * Pure realm arithmetic: no Minecraft types anywhere, so it compiles and runs without a
 * world (see src/test). The systems - Game, TownBuilder, GuardWatch, QuestCommand -
 * delegate their numbers here, which is also where a balance change should be made once.
 */
object Formulas:

  /** XP needed to reach the next level. Quadratic on purpose: early levels are quick. */
  def xpToNext(level: Int): Long = 100L + 60L * level + 35L * level * level

  /** Past the cap, experience feeds paragon tiers, and each tier costs a little more. */
  def paragonToNext(tier: Int): Long = 2000L + 1500L * tier

  /** Paragon tiers sharpen the hero's craft: +3% XP earned per tier, to a +50% ceiling. */
  def paragonXpRate(paragon: Int): Double = math.min(1.5, 1.0 + 0.03 * paragon)

  /** Wayside portal toll: 1 gold per 20 blocks travelled, 5 minimum - a real sink. */
  def warpToll(distanceBlocks: Double): Int = math.max(5, (distanceBlocks / 20.0).floor.toInt)

  /** A hub's watch charter: the capital 3, other hubs 2, and one more where a barracks stands. */
  def guardCharter(capital: Boolean, barracks: Boolean): Int =
    (if capital then 3 else 2) + (if barracks then 1 else 0)

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
