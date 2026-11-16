# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~6,500 lines across 29 files, 60 commits, 14 unit specs (`./gradlew test`),
a host-facing `aetheron-chaos-tuning.conf` and an ops readout (`/quest tuning`) — see
README.md and AGENTS.md.

## Top picks (next sessions)

1. **Banner pride** — guilds now fly real banners at hubs. Next: make sieges notice. When a
   war band marches on a hub where a guild's banner flies, that guild's members get a named
   bonus in the defense payout (a shared-hunt credit per siege kill, or a sigil chance).
   Rides Invasion + Guilds; the banner becomes more than decoration.
2. **Tests for the quest & spell tables** — quest rewards/level gates and spell cooldowns
   are still plain vals in Quests/Spells. Extract the tables like the merchant's were
   extracted, then pin the invariants: every quest is completable at its stated level,
   every trial gate references a real quest id, no chain references a missing quest.

## C. Endgame

- [ ] **Paragon milestones past 6** — the color ladder stops at gold. Only worth doing once
      someone actually reaches 6; until then any further milestone is a guess.

## D. Social & economy

- [ ] **Trade post: courier delivery** — watchers hear about postings at any hub, but buying
      still means walking. A courier (a gold toll like the warp's, item arrives at the buyer's
      hub) would close the loop; the posting and owed-gold machinery is all there already.

## E. Engineering

- [ ] **`/quest tuning reload`** — the conf file is read at world load; an op command that
      re-reads it live (and prints the diff via the existing report) would make retuning a
      running server a one-step thing.

## Notes for future sessions

- **Two new faucets, three new sinks.** Faucets: the guild daily (20g/member/day) and the
  trade-post NPC floor (the merchant pays half asking out of nothing for stale postings —
  generous if boards sit full). Sinks: charter 100g, banner 200g, re-forge 120g+sigil.
  If gold inflates, the floor fraction (`trade.npc_floor_fraction`) is the first dial, then
  `guild.daily_reward_gold` — both in the tuning file now.
- The NPC floor eats postings the next dawn — a seller who posts at 23:50 and logs off may
  find the merchant got there first. That's the design (the market clears), but if it reads
  as harsh, raise the grace to two days rather than removing the floor.
- Guild banners are re-raised by the mason's daily round; if a hub is removed (/quest clear),
  the guild's `bannerAt` points at nothing and the status line says so — raising again moves
  it. No cleanup bug, but don't be surprised by the honest "its hub is gone now" line.
- The old watchlist still stands: Pickpocket income, Quick Shot cadence, Avatar uptime,
  `healer.xp_per_hp`, sigil economy (three sources; netherite/paragon patterns and the
  re-forge are the sinks).
- Threat temperaments and the defender's window (`threat.defender_bonus`) are flavor until
  someone plays them; the window is the one combat knob most likely to need a trim.
- `aetheron-chaos-tuning.conf` is read once at world load; `/quest tuning` shows what was
  consulted, not every possible key — that's deliberate (it shows what the realm actually
  uses). A live reload is the open item above.
- Mojmap 1.21 gotchas live in AGENTS.md (kept current there).
