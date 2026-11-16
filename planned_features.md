# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~4,100 lines, 22 files, 36 commits — see README.md.

## Top picks (next sessions)

1. **Siege depth** — the siege engine (scheduler, waves, tally, rewards, boss bar) is generic.
   Next: rare-elite "war band captains" leading a wave, towns undermanned if guards were lost,
   a defend-the-board daily quest off the back of it.
2. **Titles/achievements** — 100 rare kills, first legend forged, a siege held; shown in
   `/quest stats`, optionally broadcast. Cheap on top of counters that already exist.

## A. Make the trinity real

- [ ] **Threat polish** — the table landed (damage/heal/taunt aggro, hysteresis retarget).
      Next: show it (a /quest threat readout), threat from party buffs, mob-specific modifiers.

## B. Content that multiplies what exists

- [ ] **Trial rewards** — class trials currently pay XP/gold only; a small class-flavored keepsake
      drop would make them feel like WoW dungeons-lite.

## C. Endgame

- [ ] **Prestige tiers** — at max level, XP converts to a paragon counter with small bonuses
      and a title prefix in chat.
- [ ] **More legend patterns** — the forge has four. Shields, axes, boots and a netherite-tier
      recipe (2 sigils) would give the sigil sink more destinations.
- [ ] **Titles/achievements** — 100 rare kills, 1,000 coins earned, etc.; shown in
      `/quest stats`, optionally broadcast.

## D. Social & economy

- [ ] **Player trading** — `/quest trade <player>` with double-confirm via chat clicks.
      Keep small; no UI available server-side.

## E. Engineering

- [ ] **Add AGENTS.md** — build/test commands, Mojmap gotchas list, commit conventions.
      Cheap, compounds for future contributors and tooling.
- [ ] **Unit tests for pure logic** — XP curve, sell prices, did-you-mean distance.
      No Minecraft runtime needed.
- [ ] **`tuning.conf`** — one file for cooldowns/prices/levels instead of constants
      scattered across Spells.scala / Shop.scala / Game.scala.

## Notes for future sessions

- Balance is untested in live play: watch **Pickpocket income**, **Quick Shot cadence**,
  **Avatar uptime**, the **healer XP rate** (`Game.HealerXpPerHp = 2.0`) and the **sigil economy**
  (one sigil per rare kill, ~12 min realm-wide — a legend costs 100g + 1 sigil + base gear)
  first when tuning.
- Mojmap 1.21 gotchas live in the project's history: `UseItemCallback` returns
  `InteractionResultHolder` (consume/pass), no `setSecondsOnFire` (use `setRemainingFireTicks`),
  mining fatigue = `DIG_SLOWDOWN`, nausea = `CONFUSION`, no `BOWS`/`SHIELDS` item tags
  (use `BOW_ENCHANTABLE` / `Items.SHIELD`), no vanilla spawn-cancel event (town guard sweeps
  instead).
