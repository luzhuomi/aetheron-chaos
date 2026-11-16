# Planned Features

A living roadmap for Aetheron Chaos — captured ideas, not promises. When a feature ships,
delete its entry here (the README is the source of truth for what exists).
Current state: ~3,000 lines, 20 files, 23 commits — see README.md.

## Top picks (next sessions)

1. **Bossbar + taunt pair** — bossbar HP for rares (`ClientboundBossEventPacket`, ~30 lines)
   plus paladin *Challenge* (AoE aggro pull) and warrior *Taunt* (single target). Turns rares
   from "buffed mob" into boss encounters and makes the tank role real.
2. **Adopt vanilla villages as towns** — detect village structures, graft in a board/vendor/NPCs.
   Biggest content multiplier relative to systems that already exist.

## A. Make the trinity real

- [ ] **Bossbar HP for rares** — vanilla boss event packet, synced from the `activeRares`
      registry; bar follows the rare the engaged group is fighting.
- [ ] **Taunt abilities** — paladin *Challenge* (AoE pull), warrior *Taunt* (single target);
      `Mob.setTarget` server-side. Tanks gain a real job in parties.
- [ ] **Healer XP from healing** — small XP per HP restored on allies, so support priests
      aren't starved in melee parties.
- [ ] **Party-scaled rares** — HP × (1 + 0.5 per extra member), announced at spawn.

## B. Content that multiplies what exists

- [ ] **Adopt vanilla villages as towns** — detect the structure, graft board/vendor/NPCs;
      quest hubs everywhere, exploration gains meaning.
- [ ] **Quest chains** — a `chain` field on `Quest`: finishing A unlocks B
      (*Road Patrol → Clean the Roads → Slay the Roadbreaker*). Data-only change, big story feel.
- [ ] **Daily quests** — 3 rotating slots per day from a pool; reinforces the existing daily
      motif (hearth ration, mason pass, rare timer). A reason to log in daily.
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
  and **Avatar uptime** first when tuning.
- Mojmap 1.21 gotchas live in the project's history: `UseItemCallback` returns
  `InteractionResultHolder` (consume/pass), no `setSecondsOnFire` (use `setRemainingFireTicks`),
  mining fatigue = `DIG_SLOWDOWN`, nausea = `CONFUSION`, no `BOWS`/`SHIELDS` item tags
  (use `BOW_ENCHANTABLE` / `Items.SHIELD`), no vanilla spawn-cancel event (town guard sweeps
  instead).
