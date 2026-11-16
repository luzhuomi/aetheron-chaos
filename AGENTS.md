# AGENTS.md — working on Aetheron Chaos

A server-side Fabric mod (Scala 3) that turns a vanilla server into a small MMORPG:
classes, quests, spells, threat, sieges, markets. Vanilla clients connect unchanged — that
constraint is the design.

## Commands

| Task | Command |
|---|---|
| Build | `./gradlew build` (~10 s; jar lands in `build/libs/`) |
| Smoke-run the dedicated server | `{ sleep 40; echo stop; } \| ./gradlew runServer` — the `echo stop` is required, `runServer` never exits by itself |
| Leftover smoke servers | `ps aux \| grep "[f]abric.dli.config"`, then `pkill -f "fabric.dli.config"` if any are stuck |

The smoke world is `run/world`; it holds a capital hub (x≈-113, z≈-78) and saved heroes, so
console commands like `/quest invade 1` exercise sieges and the watch offline. Anything that
needs a joined hero (titles unlocking, trading, the HUD) can only be checked in-game.

## Hard rules

- **Vanilla-client compatible.** No custom entities, no asset packs, no client mod. UI is
  chat, the action bar (`Hud.scala`), sign text, and the `/quest` Brigadier tree. Clickable
  chat text only fires on LEFT-click with the chat window open (T).
- **Server-authoritative.** All state lives server-side; character/town/market state persists
  as SNBT files in the world folder (`aetheron-chaos-{characters,towns,postings}.snbt`).
  Missing keys in old saves must read as empty/false — every feature extends saves backwards-compatibly.
- Every system exposes an entry point used by `Game.tick` (schedulers) or a Fabric event hook
  (`Combat`, `Interactions`, `AetheronChaosMod`). New features follow the existing pattern:
  one object in `dev.aetheronchaos`, a section in README.md, a `/quest` subcommand if heroes
  touch it directly.

## Mojmap (MC 1.21) gotchas — verified against this jar, don't rediscover them

- `Enchantments.FEATHER_FALLING`, not `fall_speed_breaker`; `RESPIRATION` is Aqua Affinity.
  Enchantments only apply where vanilla allows: `unbreaking`/`mending` need
  `#minecraft:enchantable/durability` (blaze rods and sea lanterns are out).
  `Loot.stack` skips rejected enchantments silently — check data tags, not exceptions.
- `setPersistenceRequired()` is on `Mob`, not `Entity`. Item frames in 1.21 are **entities**
  (`world.entity.decoration.ItemFrame`, ctor `(Level, BlockPos, Direction)`).
- `BlockState.getMaterial()` is gone — use `canBeReplaced()`. `setBlock` needs
  `block.defaultBlockState`.
- `ServerLevel.getEntity(UUID)`; `Level.getChunk(cx, cz)` takes chunk coords; no
  `Level.getMaxLevel`; `distanceToSqr` has no `Vec3i` overload (use `pos.getCenter`);
  `PathNavigation.moveTo(x, y, z, speed)` has no boolean overload.
- Sound/particle names: `SoundEvents.IRON_GOLEM_DEATH`, `ParticleTypes.SOUL_FIRE_FLAME`.
- `UseItemCallback` returns `InteractionResultHolder`; there is no `setSecondsOnFire`
  (use `setRemainingFireTicks`); mining fatigue = `DIG_SLOWDOWN`, nausea = `CONFUSION`;
  no `BOWS`/`SHIELDS` item tags (use `BOW_ENCHANTABLE` / `Items.SHIELD`); there is no
  vanilla spawn-cancel event (town guard sweeps instead).

## Scala 3 gotchas hit in this repo

- `sealed` is a reserved identifier; `mutable.LinkedHashMap.remove` returns `Option`;
  the `s` interpolator prints `%%` literally; an `edit` that drops a trailing newline can
  splice two lines together; tuple `>=` needs explicit component comparison (no Ordering
  extension for tuples here); `ListBuffer.remove` takes an index — use `-=` for elements.
- Match on nullable Minecraft getters: `level.getBlockEntity(pos) match case s: SignBlockEntity =>`
  works; wrapping in `Option(...)` breaks the pattern types.

## Conventions

- One feature at a time, end to end: implement → `./gradlew build` → smoke boot (check the
  log for `at dev.aetheronchaos` and `Exception`) → README section + command table →
  commit. Commit messages are one sentence of what a player would notice.
- `planned_features.md` is the roadmap: when a feature ships, delete its entry (README is
  the source of truth for what exists) and keep the "Current state" line honest
  (line/file/commit counts).
- Tuning lives in named `private val` knobs inside each system (`GuardWatch.GuardHealthScale`,
  `Threat.Hysteresis`, `Game.paragonToNext`) — not magic numbers in logic, not a config file.
- Keep balance notes for the next session in `planned_features.md` ("Notes for future sessions").

## Where things are

See the file map at the end of README.md. The load-bearing ones: `Game.scala` (character
state, XP/paragon, schedulers), `QuestCommand.scala` (the whole `/quest` tree),
`TownBuilder`/`TownStore` (hubs), `Combat.scala` (damage/death hooks), `Threat.scala`,
`Invasion.scala`, `GuardWatch.scala`, `Trading.scala`, `TradePost.scala`.
