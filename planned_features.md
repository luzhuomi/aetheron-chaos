# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~5,100 lines, 25 files, 41 commits — see README.md.

## Top picks (next sessions)

1. **Threat polish** — the table landed (damage/heal/taunt aggro, hysteresis retarget).
   Next: show it (`/quest threat`), threat from party buffs, mob-specific modifiers.
2. **A trade post at the plaza** — trading works face to face now. Next: a hub sign where
   a hero leaves a stack and a price, and another hero buys it later (asynchronous, safe).

## A. Make the trinity real

- [ ] **Threat polish** — the table landed (damage/heal/taunt aggro, hysteresis retarget).
      Next: show it (a /quest threat readout), threat from party buffs, mob-specific modifiers.

## B. Content that multiplies what exists

- [ ] **A barracks at the plaza** — the watch is real now (companies, horn, falls, re-maning).
      Next: a built barracks that raises a hub's charter, and gear for guards (a re-manned
      guard with a sword the war band can see).

## C. Endgame

- [ ] **Paragon-gated patterns** — prestige tiers exist now. Next: one or two forge patterns that
      require a paragon tier as well as sigils, so the cap has a gear goal as well as a number.
- [ ] **A forge that improves** — nine patterns is enough for now. If it grows: seasonal patterns,
      or one that takes a legendary back and re-rolls its enchantment line (a familiar MMO sink).

## D. Social & economy

- [ ] **A trade post at the plaza** — trading works face to face now. Next: a sign at the hub where
      a hero can leave a stack and a price, and another hero buys it later (asynchronous, safe).
- [ ] **A guild, lightly** — parties exist; a named guild with a roster in chat and one shared
      daily would be the next social rung. No new entities needed.

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
  (a sigil now comes from three places - a rare kill, a war-band captain, 5+ kills in a siege -
  and the two-sigil netherite patterns are the big sink) first when tuning.
- The watch is a combat surface too: golem companies at 60% health / 70% damage were chosen so they
  help without taking the fight over. If they clear war bands unaided, drop `GuardWatch.GuardHealthScale`.
- Prestige pacing is a guess: the first tier is 2,000 XP (`Game.paragonToNext`) and each costs 1,500
  more, with +3% XP per tier feeding itself. If max-level heroes ascend weekly, raise the base; the
  bonuses themselves (+2 hearts / +0.5 attack per tier) are deliberately small.
- Mojmap 1.21 gotchas live in the project's history: `UseItemCallback` returns
  `InteractionResultHolder` (consume/pass), no `setSecondsOnFire` (use `setRemainingFireTicks`),
  mining fatigue = `DIG_SLOWDOWN`, nausea = `CONFUSION`, no `BOWS`/`SHIELDS` item tags
  (use `BOW_ENCHANTABLE` / `Items.SHIELD`), no vanilla spawn-cancel event (town guard sweeps
  instead).
