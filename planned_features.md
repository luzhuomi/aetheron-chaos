# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~6,300 lines across 29 files, 54 commits, plus a unit-tested `Formulas.scala`
(`./gradlew test`) and a host-facing `aetheron-chaos-tuning.conf` — see README.md and AGENTS.md.

## Top picks (next sessions)

1. **Tests over more of the math** — the pure-logic harness now exists (`Formulas.scala` +
   munit). Next candidates: the Shop price/buy tables, the Loot rarity bands (`maxRarityFor`,
   the tier+1 enchant bump), and the guild daily/charter numbers — each needs a small pure
   extraction, exactly like the first one.
2. **A guild, a little deeper** — charters, rosters and the shared daily landed. Next: a
   rented **banner at a hub plaza** (a real banner block a guild raises for gold, like the
   barracks did) or a guild column on `/quest top`. Both ride existing machinery.

## A. Make the trinity real

- [ ] **Party frames, chat-side** — the HUD shows party names; a compact `/quest party`
      readout with everyone's HP (from the server's side) would make supporting playable.
      No new entities; the data is all server-side already.

## C. Endgame

- [ ] **Paragon milestones past 6** — the color ladder stops at gold. If tiers keep
      climbing, something has to keep meaningfully changing past there (a fourth forge
      pattern? a hearth banner?). Only worth doing once someone actually reaches 6.

## D. Social & economy

- [ ] **Trade post: an NPC floor** — the board now watches phrases and quotes recent sales,
      but a lone seller can still corner a hub. An NPC that buys anything the board has
      ignored for a day would keep the market moving on a quiet server.

## E. Engineering

- [ ] **`/quest tuning` (admin readout)** — the conf file works and logs at load; an admin
      command that prints the live effective values (and which came from the file) would
      make a mis-set knob obvious without digging through console logs.

## Notes for future sessions

- Balance is untested in live play. New numbers to watch first: the **guild daily**
  (20 kills shared → 20 gold per member — a fresh income source; if a 3-man guild makes
  60g/day for ~10 minutes of play, that may be generous), the **re-forge** (120g + 1 sigil
  is the only sigil sink that doesn't consume gear — if sigils pile up, it's priced too low),
  and the **defender's window** (×1.5 for 5s can flip a close table; if tanks feel
  sticky, drop `threat.defender_bonus` first).
- The old watchlist still stands: Pickpocket income, Quick Shot cadence, Avatar uptime,
  `healer.xp_per_hp`, and the sigil economy (three sources; netherite patterns, paragon
  patterns and the re-forge are the sinks).
- Prestige pacing is a guess; the tuning file now makes it movable (`paragon.base_xp`,
  `paragon.step_xp`) — the defaults (2,000 + 1,500/tier) are what the tests pin.
- The trade post's price guidance reads its own sale ledger — on a fresh world it has
  nothing to say until items actually sell. That's honest, not a bug; don't seed it.
- `aetheron-chaos-tuning.conf` overrides are read once at world load. A knob changed
  mid-session will not apply until restart — say so in any admin tooling.
- Mojmap 1.21 gotchas (including this session's: `ResourceKey.location()`,
  `ItemEnchantments.EMPTY`, no public enchantment-conflict API) live in AGENTS.md.
