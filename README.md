# Azeroth Chaos

An **MMORPG-style leveling sandbox** for Minecraft servers, inspired by **World of Warcraft**, written in
**Scala 3** as a **Fabric server-side mod** (vanilla clients can join — no client mod required).

Pick a class, slay monsters for XP, level up, take quests from the town board, roll for colored loot,
party up with friends, and hunt named **rare elites** that spawn on a timer — the World of Warcraft
loop, built entirely from vanilla blocks, items and effects.

## How to play

> **New here?** Run `/quest join`, pick a class with `/quest roles` and `/quest class <name>`, then
> ask an op for `/quest town` and right-click the **loom quest board** to accept your first quest.

1. **Create your hero** — `/quest join`, then choose a class (WoW-style roles):

   | Class | Playstyle |
   |---|---|
   | `warrior` | Stalwart frontline — extra health, harder hits |
   | `paladin` | Holy knight — most health, blessed shield |
   | `hunter` | Ranged beast-stalker — longbow, quick feet |
   | `mage` | Arcane scholar — permanent arcane sight |
   | `rogue` | Shadow striker — swift and deadly |
   | `priest` | Holy healer — permanent regeneration |

2. **Level up by playing** — every monster kill grants XP (bigger mobs, bigger XP). Leveling grants
   +2 health per level, more damage every few levels, a server-wide announcement and a title screen.
   Levels persist across restarts.

3. **Run quests** — right-click the **loom quest board** (or `/quest board`) for a clickable list of
   quests filtered by your level: kill quests, collection deliveries and boss hunts ("The Warden Below").
   Accept with a click, track progress in your action bar, turn in **at the board** for XP, gold and
   occasionally gear.

4. **Loot that scales** — drop rate and rarity **scale with the mob's tier** (from its kill-XP value):
   zombies drop trash, elders and wardens drop epics. WoW-colored gear: white common, green
   uncommon, aqua rare, purple epic — real enchanted items like *Duskcleaver* or *Helm of the Black
   Roar*. Gold coins fund the vendor. Rare elites always drop top-tier loot.

5. **Party up** — `/quest party <name>` groups up to 5 heroes (WoW party size). Party members within
   40 blocks share 50% of kill XP **and quest progress**: any member's kill counts toward the same
   kill-quest for everyone holding it. Your party shows in the action bar.

6. **Group quests** — quests marked **[GROUP]** on the board (Ravager Rampage, The Deep Duke,
   The Warden Below) are boss hunts: every ready party member at the quest board turns in together
   with one command, each getting the full reward plus a **+50% group XP bonus**.

7. **Need/Greed loot lock** — when your party rolls a gear drop, it's locked to the party for 60
   seconds: a chat line with clickable **[NEED]** / **[GREED]** buttons; everyone rolls 1-100,
   highest need wins (else highest greed), no rolls = free-for-all.

8. **Spend your gold** — right-click the **vendor barrel** in town (or `/quest shop`) for a clickable
   catalog from common tools to the epic *Helm of the Black Roar* — a gold sink with guaranteed gear.

9. **Hunt rare elites** — every ~12 minutes a named, buffed rare (e.g. *Griom the Ironfist*,
   *Silithis the Broodmother*) spawns near a random hero with a server announcement: **10x XP and
   guaranteed loot**. Ops can trigger one with `/quest rare`.

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
| `/quest board` | everyone | Quest-board menu (accept / turn in, clickable) |
| `/quest take <id>` | everyone | Accept a quest |
| `/quest log` | everyone | Show your quest log and progress |
| `/quest abandon <id>` | everyone | Drop a quest |
| `/quest turnin <id>` | everyone | Turn a completed quest in (at the board) |
| `/quest party <name>` | everyone | Join/create a party (max 5, shared XP + quest progress) |
| `/quest party leave` | everyone | Leave your party |
| `/quest shop` | everyone | Vendor catalog (also right-click the town vendor barrel) |
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

The mod jar is produced at `build/libs/azeroth-chaos-1.0.0.jar` (already remapped to intermediary
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

1. Install the [Fabric server](https://fabricmc.net/use/) for Minecraft 1.21.
2. Drop `azeroth-chaos-1.0.0.jar` and
   [Fabric API](https://modrinth.com/mod/fabric-api) (`0.102.0+1.21`) into the `mods/` folder.
3. Players connect with an unmodified (vanilla) client — the mod is server-authoritative.

## Persistence

Hero characters (class, level, XP, quest log, stats) persist to
`azeroth-chaos-characters.snbt` in the world folder, so levels and quests survive server restarts.
Delete that file to wipe the realm.

## Tuning

Gameplay knobs live at the top of [`Game.scala`](src/main/scala/dev/azerothchaos/Game.scala)
(max level, party rules, death penalty, group bonus, rare-spawn interval); drop rates and rarity
tiers in [`Loot.scala`](src/main/scala/dev/azerothchaos/Loot.scala). Classes/roles are in
[`HeroClasses.scala`](src/main/scala/dev/azerothchaos/HeroClasses.scala), quests in
[`Quests.scala`](src/main/scala/dev/azerothchaos/Quests.scala), mob XP/tiers and rare elites in
[`Enemies.scala`](src/main/scala/dev/azerothchaos/Enemies.scala), vendor stock in
[`Shop.scala`](src/main/scala/dev/azerothchaos/Shop.scala).

## Project layout

```
src/main/scala/dev/azerothchaos/
  AzerothChaosMod.scala  Fabric entrypoint, event wiring
  Game.scala             Characters: classes, XP/levels, quests, parties, rare spawns
  HeroClasses.scala      Class/role definitions (attribute bonuses + starting gear)
  Quests.scala           Quest definitions (kill / collect / boss objectives, [GROUP] flag)
  Enemies.scala          Kill-XP table, loot tiers, rare-elite spawn table
  Loot.scala             Rarity-colored gear, gold coins, tier-scaled kill loot rolls
  PartyLoot.scala        Party loot lock: need/greed rolls with timed resolution
  Shop.scala             Vendor stock (gold sink for gear)
  CharacterStore.scala   SNBT persistence of heroes in the world folder
  TownBuilder.scala      /quest town: auto-builds a quest hub with the board
  Interactions.scala     Right-click hook for the quest board (loom)
  Combat.scala           Kill credit from damage sources
  QuestCommand.scala     /quest command tree (Brigadier)
  Hud.scala              Action-bar HUD: level, XP bar, quest tracker, party
```

## License

MIT
