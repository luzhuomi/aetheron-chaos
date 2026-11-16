# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~3,450 lines, 21 files, 31 commits — see README.md.

## Top picks (next sessions)

1. **Class quests at milestones** — unlock the Lv 15 spell only after a small class-flavored
   quest; the chain machinery from quest-chains makes this mostly data now.
2. **Teleport between towns for gold** — waypoint network at quest boards; content + gold sink
   in one feature, and adopted villages suddenly become a travel network.

## A. Make the trinity real

- [ ] **Threat polish** — the table landed (damage/heal/taunt aggro, hysteresis retarget).
      Next: show it (a /quest threat readout), threat from party buffs, mob-specific modifiers.

## B. Content that multiplies what exists

- [ ] **Class quests at milestones** — unlock the Lv 15 spell only after a small class-flavored
      quest (WoW Classic style; makes unlocks memorable).

## C. Endgame

- [ ] **Prestige tiers** — at max level, XP converts to a paragon counter with small bonuses
      and a title prefix in chat.
- [ ] **Legendary tier** — boss-hunt-only drops + shop upgrade path
      (rare + 100g + boss token → legendary). Extends the gold sink.
- [ ] **Titles/achievements** — 100 rare kills, 1,000 coins earned, etc.; shown in
      `/quest stats`, optionally broadcast.

## D. Social & economy

- [ ] **Teleport between towns for gold** — waypoint network at quest boards; content +
      gold sink in one feature.
- [ ] **Invasion nights** — occasionally hostiles besiege a town for ~5 minutes; everyone
      defends, shared reward. Reuses the town guard system.
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
  **Avatar uptime** and the **healer XP rate** (`Game.HealerXpPerHp = 2.0`) first when tuning.
- Mojmap 1.21 gotchas live in the project's history: `UseItemCallback` returns
  `InteractionResultHolder` (consume/pass), no `setSecondsOnFire` (use `setRemainingFireTicks`),
  mining fatigue = `DIG_SLOWDOWN`, nausea = `CONFUSION`, no `BOWS`/`SHIELDS` item tags
  (use `BOW_ENCHANTABLE` / `Items.SHIELD`), no vanilla spawn-cancel event (town guard sweeps
  instead).
