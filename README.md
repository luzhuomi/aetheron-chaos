# Aetheron Chaos

An **MMORPG-style leveling sandbox** for Minecraft servers, inspired by **World of Warcraft**, written in
**Scala 3** as a **Fabric server-side mod** (vanilla clients can join — no client mod required).

Pick a class, slay monsters for XP, level up, take quests from the town board, roll for colored loot,
party up with friends, and hunt named **rare elites** that spawn on a timer — the World of Warcraft
loop, built entirely from vanilla blocks, items and effects.

## How to play

> **New here?** Your first join raises a **starter town** at your spawn point, and you wake up in its
> plaza. Right-click **Questmaster Boralus** to accept your first quest, then `/quest join` and pick
> a class with `/quest roles` and `/quest class <name>`.

1. **Arrive in town** — the first hero to join a realm gets a starter town built at their spawn:
   a plaza with a **loom quest board**, vendor barrel, hearth and lantern-lit fences, plus permanent
   **town NPCs**: Questmaster Boralus (opens the quest board), Merchant Tilli (opens the shop), and
   guards who hand out hints. **Town guard**: plaza lanterns suppress hostile spawns, and any hostile
   stray that wanders in (creepers first!) is escorted back outside the plaza every few seconds —
   unless a hero has pulled it into combat. Every new hero is teleported into the plaza and respawns
   there. Ops can raise more hubs anywhere with `/quest town`; towns and boards persist across restarts.

2. **Create your hero** — `/quest join`, then choose a class (WoW-style roles):

   | Class | Playstyle |
   |---|---|
   | `warrior` | Stalwart frontline — extra health, harder hits; war techniques with any axe (spellbook below) |
   | `paladin` | Holy knight — most health; blessings through any shield (spellbook below) |
   | `hunter` | Ranged beast-stalker — skill-shots with any bow (spellbook below), quick feet |
   | `mage` | Arcane scholar — ranged elemental bolts (spellbook below), permanent arcane sight |
   | `rogue` | Shadow striker — blink, blades and stolen coin via any sword (spellbook below) |
   | `priest` | Holy healer — heals allies, cleanses, protects (spellbook below), regen |

   **Spellbooks** — every class but a plain fighter has a discipline: RIGHT-CLICK while holding
   your focus to use the equipped technique; SNEAK+right-click cycles known ones (`/quest spell`
   also lists/equips them). Techniques are learned automatically as you level, sit on a cooldown
   (the focus visibly greys out — vanilla item cooldown) and show in your action bar. When your
   hands hold anything else, right-click works as vanilla — eat, place, pull, block freely.
   Focuses: mage — any blaze rod · priest — any book · rogue — any sword · hunter — any bow (the
   charged draw is replaced by instant skill-shots) · warrior — any axe · paladin — any shield.

   *Mage* — hold **any blaze rod as a wand focus**. Bolts are real projectiles with element riders:

   | Spell | Unlock | Effect |
   |---|---|---|
   | Fire Bolt | Lv 1 | 3.5 dmg + sets alight |
   | Frost Bolt | Lv 5 | 3 dmg + slowness |
   | Thunder Bolt | Lv 12 | 5.5 dmg + knockback |
   | Flame Nova | Lv 15 | AoE burst (3-block radius) + fire |
   | Storm Nova | Lv 25 | AoE burst (4-block radius) + knockback |

   *Priest* — hold **any book as a prayer book focus** (your starting *Book of Radiance* works).
   Damage is modest — priests exist to keep the party standing:

   | Spell | Unlock | Effect |
   |---|---|---|
   | Holy Smite | Lv 1 | 2.5 dmg holy bolt (minimal damage, as befits a priest) |
   | Heal | Lv 3 | mends up to 6 HP on the ally you aim at (falls back to yourself) |
   | Purify | Lv 6 | cleanses slowness/poison/blindness/weakness/... from you + allies within 6 blocks |
   | Protection | Lv 15 | Damage Resistance 12s on you + allies within 6 blocks |
   | Judgment | Lv 25 | AoE holy burst (4-block radius) for groups |

   *Rogue* — your **blade is the focus**: hold any sword, right-click for martial techniques
   (left-click fighting is untouched):

   | Technique | Unlock | Effect |
   |---|---|---|
   | Shadowstep | Lv 1 | blink up to 8 blocks toward your aim (lands on clear ground) |
   | Fan of Knives | Lv 3 | 3.5 dmg to every foe within 3.5 blocks of you |
   | Pickpocket | Lv 8 | steal 1–3 gold from each nearby foe's pockets (60% per foe) |
   | Evasion | Lv 14 | Speed II for 8s |
   | Vanish | Lv 22 | invisibility 6.5s + a small mend |

   *Hunter* — your **bow is the focus**: right-click fires skill-shots (real arrows; the
   charged draw is replaced, so your ranged basic is Quick Shot at a 1.25s cadence):

   | Shot | Unlock | Effect |
   |---|---|---|
   | Quick Shot | Lv 1 | 3 dmg arrow, fast |
   | Serpent Shot | Lv 5 | 2.5 dmg + poison |
   | Piercing Shot | Lv 12 | 3 dmg, flies through up to 6 foes |
   | Arrow Volley | Lv 18 | 5-arrow fan, 2.5 dmg each |
   | Crippling Shot | Lv 25 | 4.5 dmg + heavy slowness |

   *Warrior* — your **battle-axe is the focus**: hold any axe (you start with *Warrior's Cleaver*,
   or buy a *Woodcutter's Axe*) for techniques of force:

   | Technique | Unlock | Effect |
   |---|---|---|
   | Charge | Lv 1 | leap up to 6 blocks toward your aim, slamming + knocking back foes on landing |
   | Deep Wound | Lv 3 | the aimed foe bleeds (withers) for 5s |
   | Whirlwind | Lv 12 | 4 dmg to all foes around you (4 blocks) |
   | Intimidating Shout | Lv 18 | scare foes within 5 blocks into fleeing |
   | Avatar | Lv 25 | harder hits for 10s |

   *Paladin* — your **shield is the focus**: hold any shield (you start with *Blessed Bulwark*)
   to channel holy blessings — you trade the vanilla block for blessings that protect the whole
   body. Paladins also self-mend:

   | Blessing | Unlock | Effect |
   |---|---|---|
   | Consecrate | Lv 1 | 2.5 holy dmg to all foes around you (3 blocks) |
   | Lay on Hands | Lv 5 | mend up to 10 HP on yourself |
   | Hammer of Justice | Lv 12 | stun the aimed foe in place ~3s (+ small dmg) |
   | Guardian's Blessing | Lv 15 | Damage Resistance on yourself for 6s |
   | Divine Storm | Lv 25 | 5 holy dmg to all foes around you (4.5 blocks) |

   Kills from any spell or shot grant XP, quest progress and loot exactly like melee kills;
   arrows you conjure can't be picked back up (they're magic, not ammunition).

3. **Level up by playing** — every monster kill grants XP (bigger mobs, bigger XP). Leveling grants
   +2 health per level, more damage every few levels, a server-wide announcement and a title screen.
   Levels persist across restarts.

4. **Run quests** — right-click the **loom quest board** or the questmaster (or `/quest board`) for a
   clickable list of quests filtered by your level: kill quests, collection deliveries and boss hunts
   ("The Warden Below"). Accept with a click, track progress in your action bar, turn in **at the
   board** for XP, gold and occasionally gear.
   *Tip: chat lines are only clickable while the chat screen is open — press `T` first (it frees the
   mouse from the crosshair), then click, and `Esc` to return. `/quest take <id>` with Tab-completion
   works without opening chat.*

5. **Loot that scales** — drop rate and rarity **scale with the mob's tier** (from its kill-XP value):
   zombies drop trash, elders and wardens drop epics. WoW-colored gear: white common, green
   uncommon, aqua rare, purple epic — real enchanted items like *Duskcleaver* or *Helm of the Black
   Roar*. Gold coins fund the vendor. Rare elites always drop top-tier loot.

6. **Party up** — `/quest party <name>` groups up to 5 heroes (WoW party size). Party members within
   40 blocks share 50% of kill XP **and quest progress**: any member's kill counts toward the same
   kill-quest for everyone holding it. Your party shows in the action bar.

7. **Group quests** — quests marked **[GROUP]** on the board (Ravager Rampage, The Deep Duke,
   The Warden Below) are boss hunts: every ready party member at the quest board turns in together
   with one command, each getting the full reward plus a **+50% group XP bonus**.

8. **Need/Greed loot lock** — when your party rolls a gear drop, it's locked to the party for 60
   seconds: a chat line with clickable **[NEED]** / **[GREED]** buttons; everyone rolls 1-100,
   highest need wins (else highest greed), no rolls = free-for-all.

9. **Signature loot** — every mob family has an iconic drop that *only it* ever carries: the warden
   alone drops *Deepbrand, Bane of the Dark*, the elder guardian its trident *Heart of the Deep
   Duke*, witches their grimoire, phantoms their shroud. Named rare elites triple the odds.

10. **Quest tracker with compass** — `/quest track <id>` pins up to 4 quests to your action bar with a
   live 8-way compass: it points at the nearest objective mob (or dropped delivery item) while you
   hunt, and flips to pointing back at the quest board once the objective is done. New quests
   auto-track while there's room; `/quest untrack <id>` clears one.

11. **Trade gold** — right-click the **vendor barrel** or Merchant Tilli (or `/quest shop`) for a
    clickable catalog from common tools to the epic *Helm of the Black Roar* — a gold sink with
    guaranteed gear. Sell too: **hold an item and `/quest sell`** (or sneak+right-click the merchant)
    and she pays by rarity — 5/12/30/65 gold for common-to-epic loot — and buys everyday junk
    (mob drops, crops, ores, gems). Buy-high, sell-low keeps it a sink.

12. **Hunt rare elites** — every ~12 minutes a named, buffed rare (e.g. *Griom the Ironfist*,
    *Silithis the Broodmother*) spawns near a random hero with a server announcement: **10x XP and
    guaranteed loot**. Ops can trigger one with `/quest rare`. Fair fight rule: if the whole party
    (or a solo hero) dies while engaged with the rare, it fades away — no respawn-grinding it down.

### Rules

- Death costs 10% of your stored XP — soft, like MMO resilience, no item loss.
- Quest turn-ins require standing at a registered quest board (until any board exists, anywhere works).
- Max level 40; `/quest top` ranks the realm's heroes.

### Commands

| Command | Who | Description |
|---|---|---|
| `/quest join` | everyone | Create/join your hero (auto-rejoin on reconnect) |
| `/quest leave` | everyone | Leave the realm |
| `/quest roles` | everyone | List the hero classes |
| `/quest class <name>` | everyone | Choose a class (grants its gear) |
| `/quest spell [id]` | everyone | Spellbook: list or equip a spell (mage/priest) |
| `/quest board` | everyone | Quest-board menu (accept / turn in, clickable) |
| `/quest take <id>` | everyone | Accept a quest |
| `/quest log` | everyone | Show your quest log and progress |
| `/quest abandon <id>` | everyone | Drop a quest |
| `/quest track <id>` | everyone | Pin a quest to the action bar with a live compass (max 4) |
| `/quest untrack <id>` | everyone | Unpin a quest from the action bar |
| `/quest turnin <id>` | everyone | Turn a completed quest in (at the board) |
| `/quest party <name>` | everyone | Join/create a party (max 5, shared XP + quest progress) |
| `/quest party leave` | everyone | Leave your party |
| `/quest shop` | everyone | Vendor catalog (also right-click the town vendor barrel) |
| `/quest sell` | everyone | Sell the item in your main hand to the merchant for gold |
| `/quest buy <id>` | everyone | Buy gear with gold coins |
| `/quest loot need <n>` / `greed <n>` | everyone | Roll on party-locked loot |
| `/quest stats` | everyone | Your hero's stat sheet |
| `/quest top` | everyone | Server leaderboard |
| `/quest town` | op (level 2) | Auto-build a quest hub in front of you |
| `/quest clear [all]` | op (level 2) | Remove built hubs |
| `/quest rare` | op (level 2) | Force-spawn a rare elite near you |
| `/quest reset [player]` | everyone / op | Reset a hero to level 1 |

## Building

Requires a **JDK 25** for Gradle (Fabric Loom 1.19 requires it; the mod itself still compiles to
Java 21 bytecode for Minecraft 1.21). Point Gradle at it via `org.gradle.java.home` in
`gradle.properties` or a Java 25 `JAVA_HOME`. The Gradle wrapper downloads everything else
(Fabric Loom, Minecraft 1.21 with official Mojang mappings, Fabric API, Scala 3.3 stdlib).

```bash
./gradlew build
```

The mod jar is produced at `build/libs/aetheron-chaos-1.0.0.jar` (already remapped to intermediary
names, with the Scala stdlib bundled as a jar-in-jar — no Scala plugin needed on the server).

### Run a test server

```bash
./gradlew runServer
```

Accept the EULA in `run/eula.txt`, join with a vanilla 1.21/1.21.1 client, then in the server
console run `op your_username` and in game:

```
/quest join
/quest class warrior
/quest town
/quest board
```

## Installing on a real server

2. Install the [Fabric server](https://fabricmc.net/use/) for Minecraft 1.21.
3. Drop `aetheron-chaos-1.0.0.jar` and
   [Fabric API](https://modrinth.com/mod/fabric-api) (`0.102.0+1.21`) into the `mods/` folder.
4. Players connect with an unmodified (vanilla) client — the mod is server-authoritative.

## Persistence

Hero characters (class, level, XP, quest log, stats) persist to
`aetheron-chaos-characters.snbt` in the world folder, so levels and quests survive server restarts.
Written on every disconnect, again on a clean server stop, and autosaved every 5 minutes while
heroes are online so even a hard crash loses at most a few minutes of play. Built towns and their
quest-board registrations persist to `aetheron-chaos-towns.snbt` next to it.
Delete those files to wipe the realm. (Worlds saved under the old `azeroth-chaos-*.snbt` file
names are renamed automatically on first load — no manual step needed.)

## Tuning

Gameplay knobs live at the top of [`Game.scala`](src/main/scala/dev/aetheronchaos/Game.scala)
(max level, party rules, death penalty, group bonus, rare-spawn interval); drop rates and rarity
tiers in [`Loot.scala`](src/main/scala/dev/aetheronchaos/Loot.scala). Classes/roles are in
[`HeroClasses.scala`](src/main/scala/dev/aetheronchaos/HeroClasses.scala), quests in
[`Quests.scala`](src/main/scala/dev/aetheronchaos/Quests.scala), mob XP/tiers and rare elites in
[`Enemies.scala`](src/main/scala/dev/aetheronchaos/Enemies.scala), vendor stock in
[`Shop.scala`](src/main/scala/dev/aetheronchaos/Shop.scala).

## Project layout

```
src/main/scala/dev/aetheronchaos/
  AetheronChaosMod.scala  Fabric entrypoint, event wiring
  Game.scala             Characters: classes, XP/levels, quests, parties, rare spawns
  HeroClasses.scala      Class/role definitions (attribute bonuses + starting gear)
  Spells.scala           Spellbooks: mage elemental bolts/novas + priest holy heal/purify/protect
  Quests.scala           Quest definitions (kill / collect / boss objectives, [GROUP] flag)
  Enemies.scala          Kill-XP table, loot tiers, rare-elite spawn table
  Loot.scala             Rarity-colored gear, signature mob drops, gold coins, tier-scaled rolls
  PartyLoot.scala        Party loot lock: need/greed rolls with timed resolution
  Shop.scala             Vendor stock (gold sink for gear)
  CharacterStore.scala   SNBT persistence of heroes in the world folder
  TownBuilder.scala      Starter town on first join, /quest town hubs, town NPCs
  TownStore.scala        SNBT persistence of towns / quest-board registrations
  Interactions.scala     Right-click hooks: quest board, vendor barrel, town NPCs
  Combat.scala           Kill credit from damage sources
  QuestCommand.scala     /quest command tree (Brigadier)
  Hud.scala              Action-bar HUD: level, XP bar, quest tracker, party
```

## License

MIT
