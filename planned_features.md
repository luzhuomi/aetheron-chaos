# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~6,500 lines across 29 files, 61 commits, 14 unit specs (`./gradlew test`),
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
3. **Buy orders at the trade post** — the market is one-sided: sellers post, buyers browse,
   demand is invisible. Let a hero post **wanted** orders ("10 ender pearls at 8g each")
   with the gold held in escrow (the owed-gold ledger already exists); a sell posting at or
   under an order's price fills it instantly, and unspent escrow comes back at the dawn
   sweep. This is what turns the trade post into a real auction house.

## B. Content & combat

- [ ] **The Proving Ring** — on-demand waves, because invasions fire on the realm's clock,
      not the party's. A party at the capital pays a gold ante (a sink!) to ring the bell:
      escalating war-band waves, a captain every third, gold and sigil odds scaling with how
      far they get. Rides Invasion's wave machinery — watch the guard sweep: the ring must
      sit outside the watch's spawn-sweep radius or spawn through the invasion's exemption.
- [ ] **Bestiary** — kills currently vanish into one counter. Persist per-mob-family kill
      counts on the character (new save fields, missing = zero), add `/quest bestiary`:
      counts, lore lines that unlock at thresholds, and two or three titles riding it
      (Achievements already reads Game counters, so new counters fit the pattern).
- [ ] **Exploration quests** — objectives are Kill/Collect/WarBand only, and the warp
      network deserves content. A `Visit(hub)` objective (marked when the hero stands at
      that hub's board) enables a cartographer chain: "the realm's roads" — visit every
      hub, get a title and a warp-toll discount. Teaches the map to new heroes.

## C. Endgame

- [ ] **Paragon milestones past 6** — the color ladder stops at gold. Only worth doing once
      someone actually reaches 6; until then any further milestone is a guess.

## D. Social & economy

- [ ] **Trade post: courier delivery** — watchers hear about postings at any hub, but buying
      still means walking. A courier (a gold toll like the warp's, item arrives at the buyer's
      hub) would close the loop; the posting and owed-gold machinery is all there already.
- [ ] **Trade-post listing fee** — a non-refundable ~2% of asking to post
      (`trade.listing_fee_fraction`). A targeted sink (see the inflation note below) and it
      keeps junk-spam off the boards. Refund nothing on the NPC-floor sweep; the fee is the
      price of the merchant's attention.
- [ ] **A realm-wide weekly hunt** — the guild daily, but for the whole server: "the realm
      fells 400 monsters this week." Every kill counts; everyone who contributed claims a
      cache (gold + a sigil chance) at the board when it's done. Co-op glue for quiet
      servers, and it rides the daily-rotation machinery.
- [ ] **Rested XP** — WoW's rest, for the casual hero: time away accrues a pool (cap ~a
      day's worth, level-scaled); kill XP pays +50% until the pool is spent, with a line on
      the HUD while it lasts. Cheap to build — characters autosave with a timestamp, so
      "time away" is `now - last save` at load. Tunable: `rest.hours_cap`, `rest.bonus`.
- [ ] **Guild perks, lightly** — guilds are flat (charter, daily, banner). Let member kills
      feed guild XP: a few levels unlocking small, visible perks — a banner pattern choice,
      +2 roster seats, +5 gold on the shared daily. Keeps the social rung worth growing.
- [ ] **More sigil sinks** — three sources, and only forging/re-forge/netherite drink them.
      Options: re-roll the daily board for a sigil; a collector who trades 3 sigils for a
      random epic cache. Watch the sigil economy note before adding either.

## E. Engineering

- [ ] **`/quest tuning reload`** — the conf file is read at world load; an op command that
      re-reads it live (and prints the diff via the existing report) would make retuning a
      running server a one-step thing.

## Notes for future sessions

- **Verify in-game before building much more on top.** Since the last live check, these
  shipped on build+smoke alone: guild create/invite/banner, re-forge, party frames, the
  dawn merchant sweep, `/quest tuning`, paragon colors, the defender's window. One in-game
  pass over that list is worth more than a new feature right now.
- **Faucets vs sinks.** Faucets: kill/quest gold, the guild daily (20g/member/day), the
  trade-post NPC floor (half asking out of nothing). Sinks: shop, warp tolls, charter 100g,
  banner 200g, barracks 150g, forge 120g+sigil. If gold inflates, first dial is
  `trade.npc_floor_fraction`, then `guild.daily_reward_gold`; the listing fee and the
  Proving Ring ante above are the planned structural sinks.
- The NPC floor eats postings the next dawn — a seller who posts at 23:50 and logs off may
  find the merchant got there first. That's the design (the market clears), but if it reads
  as harsh, raise the grace to two days rather than removing the floor.
- Guild banners are re-raised by the mason's daily round; if a hub is removed (/quest clear),
  the guild's `bannerAt` points at nothing and the status line says so — raising again moves
  it. No cleanup bug, but don't be surprised by the honest "its hub is gone now" line.
- The old watchlist still stands: Pickpocket income, Quick Shot cadence, Avatar uptime,
  `healer.xp_per_hp`, sigil economy (three sources; forging, re-forge and netherite patterns
  are the sinks).
- Threat temperaments and the defender's window (`threat.defender_bonus`) are flavor until
  someone plays them; the window is the one combat knob most likely to need a trim.
- Death already costs XP — resist adding a gold tax on top; one penalty is honest, two is
  punishing.
- Deliberately absent, don't re-propose without a reason: PvP, player housing, instanced
  dungeons. The design is a co-op realm on vanilla clients.
- `aetheron-chaos-tuning.conf` is read once at world load; `/quest tuning` shows what was
  consulted, not every possible key — that's deliberate (it shows what the realm actually
  uses). A live reload is the open item above.
- Mojmap 1.21 gotchas live in AGENTS.md (kept current there).
