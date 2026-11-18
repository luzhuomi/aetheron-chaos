# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~6,900 lines across 30 files, 66 commits, 23 unit specs (`./gradlew test` —
`FormulasTest` for the math, `TablesTest` for the real quest/spell tables via `TestRealm.boot()`),
a host-facing `aetheron-chaos-tuning.conf`, an ops readout (`/quest tuning`), per-hero stashes
and two-sided markets — see README.md and AGENTS.md.

## Before anything new

- **An in-game pass over the last two cycles.** Since the last live session these shipped on
  build+smoke alone: banner pride (the whole pride payout path), guild create/invite/banner,
  re-forge, party frames, the dawn merchant sweep, `/quest tuning`, paragon colors, the
  defender's window, and now the stash (`deposit/withdraw`, shelf-full messages) and buy
  orders (instant fill, cross-hub fill, surplus refund, dawn call-back). One played session
  checking that list is worth more than the next feature.

## Top picks (next sessions)

1. **Gifts and a listing fee** — two small economy completions. `stash send <hero> <n>`:
   lift a stack off one shelf and `Stash.receive` puts it on another hero's - item *mail*,
   the one interaction the post never covered (a fee, `stash.send_gold`, is a sink too).
   And the planned trade-post listing fee (~2% of asking, `trade.listing_fee_fraction`) —
   the market now has two faucets' worth of traffic and no cost to list.
2. **The Proving Ring** — on-demand waves, because invasions fire on the realm's clock, not
   the party's. A party at the capital pays a gold ante (a sink!) to ring the bell: escalating
   war-band waves, a captain every third, gold and sigil odds scaling with how far they get.
   Rides Invasion's wave machinery — watch the guard sweep: the ring must sit outside the
   watch's spawn-sweep radius or spawn through the invasion's exemption.
3. **Exploration quests** — objectives are still only Kill/Collect/WarBand, and the warp
   network deserves content. A `Visit(hub)` objective (marked when the hero stands at that
   hub's board) enables a cartographer chain: "the realm's roads" — visit every hub, get a
   title and a warp-toll discount. Teaches the map to new heroes. `TablesTest` grows with
   it: every visit-chain must name real hubs.

## B. Content & combat

- [ ] **Bestiary** — kills currently vanish into one counter. Persist per-mob-family kill
      counts on the character (new save fields, missing = zero), add `/quest bestiary`:
      counts, lore lines that unlock at thresholds, and two or three titles riding it
      (Achievements already reads Game counters, so new counters fit the pattern).
- [ ] **Realm-wide weekly hunt** — the guild daily, but for the whole server: "the realm
      fells 400 monsters this week." Every kill counts; everyone who contributed claims a
      cache (gold + a sigil chance) at the board when it's done. Co-op glue for quiet
      servers, and it rides the daily-rotation machinery.

## C. Endgame

- [ ] **Paragon milestones past 6** — the color ladder stops at gold. Only worth doing once
      someone actually reaches 6; until then any further milestone is a guess.

## D. Social & economy

- [ ] **Trade-post listing fee** — a non-refundable ~2% of asking to post
      (`trade.listing_fee_fraction`). A targeted sink and it keeps junk-spam off the boards.
      Do not charge it on the NPC-floor sweep; the fee is the price of the merchant's attention.
- [ ] **Rested XP** — WoW's rest, for the casual hero: time away accrues a pool (cap ~a
      day's worth, level-scaled); kill XP pays +50% until the pool is spent, with a line on
      the HUD while it lasts. Cheap to build — characters autosave with a timestamp, so
      "time away" is `now - last save` at load. Tunable: `rest.hours_cap`, `rest.bonus`.
- [ ] **Guild perks, lightly** — guilds are flat (charter, daily, banner, pride). Let member
      kills feed guild XP: a few levels unlocking small, visible perks — a banner pattern
      choice, +2 roster seats, +5 gold on the shared daily. Keeps the social rung worth growing.
- [ ] **More sigil sinks** — three sources, and only forging/re-forge/netherite patterns drink
      them. Options: re-roll the daily board for a sigil; a collector who trades 3 sigils for
      a random epic cache. Watch the sigil-economy note before adding either.

## E. Engineering

- [ ] **`/quest tuning reload`** — the conf file is read at world load; an op command that
      re-reads it live (and prints the diff via the existing report) would make retuning a
      running server a one-step thing.

## Notes for future sessions

- **The market is two-sided now; keep it that way.** Buy orders are escrow, not mint —
  filling one moves gold buyer→seller and lands goods on the buyer's shelf; unfilled ones
  refund at the dawn sweep (same pass as the NPC floor). No new gold enters. The planned
  listing fee is the first real fee on that traffic; if boards clog, it goes in first.
- **TestRealm.boot()** (src/test) makes the real quest/spell/loot tables loadable in unit
  tests: sets a `WorldVersion` (`new DataVersion(3953, "main")`), runs `Bootstrap.bootStrap()`.
  Any new table — Visit objectives, Proving Ring wave tables, weekly-hunt goals — should get
  the same treatment: shape it as data, pin it in TablesTest.
- The old courier idea (buy at another hub, walk to collect) is **superseded**: buy orders +
  shelf delivery moved the goods instead. If anyone wants items delivered to a chest at their
  hub instead of a ledger shelf, that's a physical-drop feature, not a market one.
- **Faucets vs sinks.** Faucets: kill/quest gold, the guild daily (20g/member/day), the
  trade-post NPC floor (half asking out of nothing). Sinks: shop, warp tolls, charter 100g,
  banner 200g, barracks 150g, forge 120g+sigil, stash deposit 2g/stack. If gold inflates,
  first dial is `trade.npc_floor_fraction`, then `guild.daily_reward_gold`; the listing fee
  and the Proving Ring ante above are the planned structural sinks.
- The NPC floor eats postings the next dawn — a seller who posts at 23:50 and logs off may
  find the merchant got there first. That's the design (the market clears), but if it reads
  as harsh, raise the grace to two days rather than removing the floor. Same grace logic
  now applies to unfilled buy orders — one dawn, then the pledge returns at the post.
- A filled order never sees a board: `sell` tries open orders first, `want` scans boards
  first. If anyone reports "I posted it and nobody bought it" while an order exists, check
  the phrase match first — matching is `displayName.toLowerCase.contains(phrase)`, the same
  rule as watches.
- Guild banners are re-raised by the mason's daily round; if a hub is removed (/quest clear),
  the guild's `bannerAt` points at nothing and the status line says so — raising again moves
  it. Don't be surprised by the honest "its hub is gone now" line.
- The old watchlist still stands: Pickpocket income, Quick Shot cadence, Avatar uptime,
  `healer.xp_per_hp`, sigil economy (three sources; forging, re-forge and netherite patterns
  are the sinks).
- Threat temperaments and the defender's window (`threat.defender_bonus`) are flavor until
  someone plays them; the window is the one combat knob most likely to need a trim.
- Death already costs XP — resist adding a gold tax on top; one penalty is honest, two is
  punishing. (The stash is a bank, not a penalty: it was one feature the design clearly
  wanted, and it is safe by construction.)
- Deliberately absent, don't re-propose without a reason: PvP, player housing, instanced
  dungeons. The design is a co-op realm on vanilla clients.
- `aetheron-chaos-tuning.conf` is read once at world load; `/quest tuning` shows what was
  consulted, not every possible key — that's deliberate (it shows what the realm actually
  uses). A live reload is the open item above.
- Mojmap 1.21 gotchas live in AGENTS.md (kept current there).
