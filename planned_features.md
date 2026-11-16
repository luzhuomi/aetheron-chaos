# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~5,700 lines, 26 files, 47 commits — see README.md; contributor/tooling
notes now live in AGENTS.md.

## Top picks (next sessions)

1. **A guild, lightly** — parties exist; a named guild with a roster in chat and one shared
   daily would be the next social rung. No new entities needed.
2. **A forge that improves** — eleven patterns and two paragon gates are enough for now.
   Next: one pattern that takes a legendary back and re-rolls its enchantment line
   (a familiar MMO sink for spent legends).

## A. Make the trinity real

- [ ] **Threat, deeper** — the readout (`/quest threat`), buff threat, mob temperaments and
      the on-HUD lost-aggro tell all landed. Next: taunt cooldown tuning per class (the
      spell cooldowns are uniform today), and a defender's-benefit window (brief threat bonus
      for whoever a mob just turned away from).

## C. Endgame

- [ ] **Paragon rewards beyond numbers** — paragon tiers, gated forge patterns and titles landed.
      Next: a cosmetic per milestone (a banner at the hero's hearth? a title color?) — cheap,
      vanilla-client-safe celebration.

## D. Social & economy

- [ ] **A guild, lightly** — parties exist; a named guild with a roster in chat and one shared
      daily would be the next social rung. No new entities needed.
- [ ] **Trade post depth** — postings, waiting gold and market signs landed. Next: a "watch"
      on a listing (a message to the buyer when something they asked for is posted), and
      price guidance from what the board has actually sold for.

## E. Engineering

- [ ] **Unit tests for pure logic** — XP/paragon curves, sell prices, did-you-mean distance.
      No Minecraft runtime needed; the pure helpers would need a little extraction first.
- [ ] **`tuning.conf`** — one file for cooldowns/prices/levels instead of constants
      scattered across Spells.scala / Shop.scala / Game.scala. (AGENTS.md currently tells
      contributors where each knob lives; this makes players/admins able to move them.)

## Notes for future sessions

- Balance is untested in live play: watch **Pickpocket income**, **Quick Shot cadence**,
  **Avatar uptime**, the **healer XP rate** (`Game.HealerXpPerHp = 2.0`) and the **sigil economy**
  (a sigil now comes from three places - a rare kill, a war-band captain, 5+ kills in a siege -
  and the two-sigil netherite patterns are the big sink) first when tuning.
- The watch is a combat surface too: golem companies at 60% health / 70% damage were chosen so they
  help without taking the fight over; a barracks fields +1 guard at +15% more health. If hubs start
  clearing war bands unaided, drop `GuardWatch.GuardHealthScale` before touching the barracks bonus.
- Prestige pacing is a guess: the first tier is 2,000 XP (`Game.paragonToNext`) and each costs 1,500
  more, with +3% XP per tier feeding itself. If max-level heroes ascend weekly, raise the base; the
  bonuses themselves (+2 hearts / +0.5 attack per tier) are deliberately small.
- Threat temperaments (`Threat.MobThreatScale`) are flavor, not math someone checked. If witches feel
  uncontrollable or taunts feel weak on ravagers, those maps are one line each — and creepers being
  scale 0 means the trinity deliberately does not apply to them.
- The trade post lets sellers name any price up to 5,000 gold with no NPC floor. If gold piles
  inflate, the levers are `TradePost.MaxPrice`, the merchant's buy table, and re-man/barracks
  gold sinks — in that order.
- Mojmap 1.21 gotchas now live in AGENTS.md (kept current there); the list here is retired.
