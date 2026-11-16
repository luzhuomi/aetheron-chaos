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
   **town NPCs**: Questmaster Boralus (opens the quest board), Merchant Tilli (opens the shop),
   Hearthkeeper Finn (a **free meal once per day** — bread and chicken, tracked per real-world
   day), and guards who hand out hints. **Town guard**: the town is a sanctuary — hostile mobs
   inside town land are escorted back outside every few seconds unless a hero has actually *hit*
   them; a creeper with its fuse lit is relocated even mid-fight, so it detonates at the walls
   instead of the fence line (they lock onto any hero within ~16 blocks, which is how they used
   to sneak past the old "in combat" exemption). **Daily mason pass**: every real-world day (and
   on server start), missing town blocks are auto-restored from the stored layout — holes only,
   your own builds and changes are never touched. Every new hero is teleported into the plaza and
   respawns there. Ops can raise more hubs anywhere with `/quest town`; towns and boards persist across restarts.
   **Village adoption**: when a hero wanders into a vanilla village, the realm adopts it — a quest
   hub (board, vendor, hearth, guards) is grafted onto the village **meeting point**, which is
   already an open plaza, so the villagers' own buildings are never touched. Adopted hubs get the
   same guard, repair and turn-in rules as built towns, and each village is adopted only once.

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
   | Heal | Lv 3 | mends up to 6 HP on the ally you aim at (falls back to yourself) — **healing allies grants you XP** |
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
   | Taunt | Lv 5 | the aimed foe must come at you — hold its attention |
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
   | Challenge | Lv 8 | sound the war-horn: all foes within 5 blocks turn on you |
   | Hammer of Justice | Lv 12 | stun the aimed foe in place ~3s (+ small dmg) |
   | Guardian's Blessing | Lv 15 | Damage Resistance on yourself for 6s |
   | Divine Storm | Lv 25 | 5 holy dmg to all foes around you (4.5 blocks) |

   **Class trials**: one signature spell per class is earned, not given — at its level the trial
   quest appears on your board (marked `[TRIAL]`, only ever offered to your class), and the spell
   stays sealed until you complete it. Warrior *Whirlwind* ← Hollow Battalion (8 pillagers, Lv 12);
   paladin *Hammer of Justice* ← The Unbroken Circle (4 creepers, Lv 12); hunter *Piercing Shot*
   ← The Long Shot (4 phantoms, Lv 12); rogue *Evasion* ← Guild Trials: Silence (6 spiders, Lv 14);
   mage *Flame Nova* ← The Crucible of Embers (4 blazes, Lv 15); priest *Protection* ←
   The Shepherd's Vigil (6 glowstone dust, Lv 15). Completing the trial announces the spell **and
    hands over a class keepsake** — *Hollowblades, the Second Rank* (warrior's axe), *The Unbroken
    Circle* (paladin's shield, Mending), *The Long Shot* (hunter's bow), *Silence Between Steps*
    (rogue's blade), *Emberbank* (mage's blaze-rod focus), *The Shepherd's Lamp* (a lantern that has
    not been put down since) — each Epic-rated, each carrying the story of its trial in its lore.
   `/quest spell` shows what you're trialling for.

   Kills from any spell or shot grant XP, quest progress and loot exactly like melee kills;
   arrows you conjure can't be picked back up (they're magic, not ammunition).

3. **Level up by playing** — every monster kill grants XP (bigger mobs, bigger XP). Leveling grants
   +2 health per level, more damage every few levels, a server-wide announcement and a title screen.
   Levels persist across restarts.

4. **Run quests** — right-click the **loom quest board** or the questmaster (or `/quest board`) for a
   clickable list of quests filtered by your level: kill quests, collection deliveries and boss hunts
   ("The Warden Below"). Accept with a click, track progress in your action bar, turn in **at the
   board** for XP, gold and occasionally gear.
   **Quest chains**: some quests tell a story — finishing one posts the next chapter on the board
   with a purple announcement. The *Pillager Road* chain runs *Road Patrol → Warband of the Pillager
   → Ravager Rampage*; the void line links *Void Walkers → Pocketful of Pearls*. Locked chapters
   stay off the board until you earn them (your completed-quest history is saved with your hero).
   **Daily tasks**: every real-world day the realm posts the **same three dailies** at every board
   (chosen from a pool of nine by the date alone — no state to corrupt). Modest XP and gold,
   one shot per day each, and at dawn the server announces the new set. They rotate on the same
   rhythm as Finn's ration and the mason's pass: log in, do your three, reap the rewards.
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
    and she pays by rarity — 5/12/30/65/110 gold for common-to-legendary loot — and buys everyday junk
    (mob drops, crops, ores, gems). Buy-high, sell-low keeps it a sink.

12. **Hunt rare elites** — every ~12 minutes a named, buffed rare (e.g. *Griom the Ironfist*,
    *Silithis the Broodmother*) spawns near a random hero with a server announcement: **10x XP and
    guaranteed loot**. A **boss bar** with its name and live HP appears at the top of every hero's
    screen while it lives (vanilla boss-event packet — no client mods). **Warbands draw mightier
    prey**: if the hero it lands on is in a party, the rare's health scales +50% per extra member
    and the announcement says so — group fights stay epic without becoming free for one hero. Ops can trigger one with
    `/quest rare`. Fair fight rule: if the whole party (or a solo hero) dies while engaged with the
    rare, it fades away — no respawn-grinding it down. Every rare you fell also leaves a **Sigil of
    the Elite** in your pack — the forger's price for legendary gear (see #16).
13. **Hold the line** — warrior *Taunt* (aimed) and paladin *Challenge* (war-horn, 5 blocks) force
    foes to come at you. Stand between the party and the rare, keep its eyes on you, and the healer
    stops getting mauled — a real tank trio, server-side. Healers level too: every HP of *Heal*
    actually restored to another hero pays out support XP (over-healing a full tank pays nothing —
    heal the wounded, not the healthy).
14. **Threat is real** — engaged mobs keep a threat score: every hit lands threat, *healing the
    mob's target* pulls threat onto the mender, and a taunt slams you to the top of the list.
    Every couple of seconds a mob turns to whoever leads its table — but only on a clear lead
    (30%), so targets never flicker. The tank's job is to top the list and stay there; healers
    must respect who the mob is angry at; bursty DPS waits its turn. Frightened mobs (Intimidating
    Shout) stop keeping score entirely. Mobs no hero has ever fought are left to vanilla AI.
     Now visible and textured: **`/quest threat`** reads every table within thirty blocks the way
     the mob sees it — who it's watching, who leads its grudge ledger, where you stand and what a
     taunt would buy you. **Party buffs earn notice**: a Protection cast on the hero a mob is angry
     at gives the caster a share of that aggro (30%), so support play shows up on the table. And
     **mobs keep score differently**: witches and phantoms nurse grudges (×1.4, ×1.25), ravagers dig
     into the ground they chose (×0.6), and creepers never keep score at all — a taunt bounces off
     and they fight purely on vanilla instinct.
15. **Wayside portals** — every quest board is a station on the realm's travel network. Standing at
    a board, the menu lists every other hub with a gold toll (1 gold per 20 blocks folded, 5
    minimum — a real sink), and `/quest warp <n>` takes you there in a swirl of portal particles,
    arriving on the far plaza. Built hubs, the capital and adopted villages are all stations, so
    exploring new villages pays out as a travel network the very same day you find them.
16. **Strike legends** — the loot treadmill no longer stops at epic. Each rare elite you slay leaves a
    **Sigil of the Elite** (a named amethyst shard; the merchant will buy those back for 25 gold), and
    so does a war-band captain, while holding a siege pays one to anyone who scored 5+. Bring a sigil,
    gold and the base gear it matches to the **Legend Forge** (`/quest forge`, also listed in the shop
    menu) and the village forger strikes a **legendary** — gold-named gear at the top of what vanilla
    allows. Nine patterns, in two tiers:
    - **one sigil**: *Sunforged Greatblade* (iron sword, Sharpness V), *Dawnsong* (bow, Power V +
      Flame), *Aegis of the Dawn* (iron chestplate, Protection IV + Thorns III), *Crown of the Steady
      Light* (iron helmet, Protection IV + Projectile Protection IV), *Bulwark of the Quiet Dawn*
      (shield, Unbreaking III + Mending), *Greaves of the Long Road* (iron boots, Protection IV +
      Feather Falling IV).
    - **two sigils, netherite** (melt diamond gear): *Dawnbreaker* (netherite sword, Sharpness V +
      Fire Aspect II + Mending), *Widowhollow* (netherite axe, Sharpness V + Efficiency IV + Mending),
      *Hauberk of the Last Watch* (netherite chestplate, Protection IV + Thorns III + Mending).
    The base gear and the sigils are consumed, legendaries can't be eaten by the forge, and the forge
    tells you exactly what you're still short of.
17. **Invasion nights** — roughly every half hour a **war band marches on one of the hubs** (always
    the one the heroes are nearest to, so it is reachable). For **five minutes** waves of pillagers,
    skeletons, vindicators and friends arrive just outside the town's ground — one wave every half
    minute, each a little tougher than the last. A red **siege bar** names the town, counts the
    hostiles driven off and counts the minutes down. Every kill pays 2 gold on the spot; when the
    horns blow, the heroes who stood are paid again in gold and XP scaled by what they drove off, and
    anyone who scored 5+ earns a **Sigil of the Elite** — the forger notices a siege held. Nobody
    defending is its own ending (the town keeps its sheep, and its grudges), and heroes who loitered
    away from the walls are named. The town guard still escorts off any hostile nobody is fighting, so
    the fight happens at the walls where a siege belongs. Ops can call one with `/quest invade [hub]`.
18. **War-band captains and siege duty** — a **named captain leads every third wave** (a vindicator,
    witch or pillager captain; from the sixth wave onward, a **ravager**). Captains are named and
    visible above their head, carry **2.6x health and 1.5x damage**, and the siege bar says who leads
    the band while one lives. Because they are named, **rare rules pay for them**: rare XP, guaranteed
    loot and a Sigil of the Elite. Fell one and the war band actually falters — **the next wave
    arrives two files thinner** — so the siege has a priority target, not just a crowd. Sieges are
    quest content too: the daily rotation can post **Hold the Wall**, whose objective counts only
    hostiles driven off during a siege, and its quest compass points straight at the war band while
    one is on the ground.
19. **Titles and the ladder** — the realm notices repetition. Sixteen titles are plain checks on
    counters the realm already keeps: kills, rare elites slain, quests turned in, **sieges held**,
    **captains felled**, **legends forged**, level, deaths. Earning one is announced to the hero and
    broadcast to the realm. `/quest titles` shows what you carry and the six titles nearest to hand
    with live progress; `/quest title <id>` carries one — it rides on your action bar next to your
    class and beside your name in `/quest top`. No new systems, no UI: recognition layered on the
    play you already do. Earned titles and their counters persist in the character save.
20. **The realm's watch** — every hub keeps a company of real **iron-golem guards** (the capital 3,
    other hubs 2, and one more where a barracks stands — see 24): they stand on the plaza, and when a war band marches on their town **they answer
    the horn** and take the field with each wave. They are scaled below raid-tier (60% health, 70%
    damage) so they are sturdy help, not the answer — and they can **fall**, which is the point:
    a hub that loses guards stays **undermanned**, the next siege announcement says how few turned
    out, a fallen guard makes the war band press early, and the end-of-siege report credits the
    town's own watch for what it accounted for. Re-man it yourself — `/quest guard` reads the
    strength of every hub, `/quest guard eman` pays 40 gold per missing guard and raises them now —
    or wait for the mason's daily round, which re-forms every watch for free. The company is the
    world, not a table: the golems are ordinary entities, so they survive restarts and are re-adopted
    onto the roll (up to the hub's charter), and hitting one of your own guards is no kill at all.
21. **Prestige tiers** — the level cap is a door, not a wall. At level 40 the XP bar does not go
    dead: every point past the cap feeds a **paragon tier** (the first costs 2,000 XP, each one more
    than the last). Each tier is small and permanent — **+2 hearts, +0.5 attack, +3% experience
    earned** (to +50%) — applied through the same attribute-modifier path as class and level bonuses,
    so they survive logouts, restarts and class changes. Paragon heroes wear a **★ tier** on the HUD
    (the bar becomes a paragon-progress bar at the cap) and on `/quest top`, ascending is broadcast
    to the realm with a title screen, and three new titles hang on the ladder (*the Reborn*,
    *the Everwalking*, *the Undying*). `/quest prestige` reads the whole thing out: your tier, the
    progress to the next, and what it grants. The tier persists in the character save; old saves
    simply read as tier 0.
22. **Hero-to-hero trading** — a vanilla client has no trade window, so the trade is the chat
    window: `/quest trade <hero>` invites a hero standing within 40 blocks of you, and they answer
    with a click ([Accept] / [Decline]). Each side offers **the stack in hand** (`/quest trade give`
    — the whole stack, named legendary gear and all; `/quest trade clear` takes an offer back), and
    both offers are read out to both heroes every step of the way. Nothing moves until **both**
    confirm (`/quest trade confirm`, or the clickable [Confirm]) — and at the moment of the swap every
    promised stack is re-checked against the giver's pack as it is *right now*: if anything was spent,
    dropped or moved between offer and handshake, the trade calls itself off and says what was missing,
    rather than lying about what changed hands. Distance is re-checked too, and a trade dies the moment
    either hero logs off.
23. **The trade post at the plaza** — the async half of the economy. Every hub now has a **market
    sign** (raised on the plaza edge the first time the realm wakes) where a hero can **leave a stack
    and a price**: `/quest post sell <gold>` posts the stack in hand — real items, named legendaries
    included — and any hero who walks up can `buy <n>` it. Sale gold doesn't teleport to an absent
    seller: it **sits at the post** until they next pass through (`/quest post coin`), or arrives the
    moment they log back in, and sales are announced realm-wide so the market has a pulse. The board
    caps at ten postings per hub and three per hero; `/quest post cancel <n>` takes a posting back.
    The sign itself is part of the world — it lists how much is posted and the commands — and it is
    rewritten whenever the board changes. Listings and waiting gold persist in the world folder, so
    the market outlives restarts.
24. **A barracks at the plaza** — the other building a hub can grow: for 150 gold a hero raises a
    **guardhold** on the plaza's edge — floor stones, an arms-rack wall, an anvil, a lantern, and an
    iron sword racked where the plaza can see it. A hub with a barracks keeps a **bigger watch**
    (its charter rises by one, and its golems come out of better-kept gear: +15% health), and the
    new guards step onto the plaza the moment it's raised. The building is the world (real blocks,
    real item frame) and the hub's banner-of-arms persists in the town save — old town files simply
    read as "no barracks yet". `/quest barracks` reads which hubs hold one; `/quest barracks build`
    raises it at the hub you stand at, and the realm hears about it.

### Rules

- Death costs 10% of your stored XP — soft, like MMO resilience, no item loss.
- Quest turn-ins require standing at a registered quest board (until any board exists, anywhere works).
- Max level 40; past the cap, experience feeds paragon tiers (`/quest prestige`). `/quest top` ranks the realm's heroes.

### Commands

| Command | Who | Description |
|---|---|---|
| `/quest join` | everyone | Create/join your hero (auto-rejoin on reconnect) |
| `/quest leave` | everyone | Leave the realm |
| `/quest roles` | everyone | List the hero classes |
| `/quest class <name>` | everyone | Choose a class (grants its gear) |
| `/quest spell [id]` | everyone | Spellbook: list or equip a spell (mage/priest) |
| `/quest board` | everyone | Quest-board menu (accept / turn in / warp, clickable) |
| `/quest warp <n>` | everyone | Wayside portal to hub #n — stand at a board, pay the gold toll |
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
| `/quest forge [pattern]` | everyone | Legend Forge: base gear + gold + 1–2 sigils → legendary gear |
| `/quest loot need <n>` / `greed <n>` | everyone | Roll on party-locked loot |
| `/quest stats` | everyone | Your hero's stat sheet |
| `/quest prestige` | everyone | Paragon tiers past the level cap: progress, bonuses, cost |
| `/quest trade <hero>` | everyone | Invite a nearby hero to trade goods (chat-window trade) |
| `/quest trade` | everyone | Read the open trade: both offers, confirm status, clickable actions |
| `/quest trade yes/no/give/clear/confirm/cancel` | everyone | Answer an invitation; offer the stack in hand; clear it; confirm; call it off |
| `/quest post` | everyone | The trade post at the hub you stand at: read the market board |
| `/quest post sell <gold>` | everyone | Post the stack in hand at the hub market for a price |
| `/quest post buy/cancel <n>` | everyone | Buy posting #n / take your own posting back |
| `/quest post coin` | everyone | Collect gold your past sales have earned |
| `/quest barracks` | everyone | Which hubs keep a barracks (and what one costs) |
| `/quest barracks build` | everyone | Raise a barracks at the hub you stand at (150 gold) |
| `/quest titles` | everyone | Title ladder: what you've earned and what's next, with progress |
| `/quest title <id>` | everyone | Carry an earned title (action bar + leaderboard), `none` to set down |
| `/quest guard` | everyone | The realm's watch: strength and shortfalls at every hub |
| `/quest guard eman` | everyone | Pay 40 gold per missing guard and re-man the hub you stand at |
| `/quest top` | everyone | Server leaderboard |
| `/quest threat` | everyone | Every threat table within thirty blocks: who the mobs are angry at, where you stand |
| `/quest town` | op (level 2) | Auto-build a quest hub in front of you |
| `/quest clear [all]` | op (level 2) | Remove built hubs |
| `/quest rare` | op (level 2) | Force-spawn a rare elite near you |
| `/quest invade [hub]` | op (level 2) | Call a war band down on a hub (nearest by default) |
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

Feature roadmap lives in [planned_features.md](planned_features.md).

```
src/main/scala/dev/aetheronchaos/
  AetheronChaosMod.scala  Fabric entrypoint, event wiring
  Game.scala             Realm state core: characters, XP/levels, parties, kill credit, tick
  GameSpells.scala       Spellbook state: equip, cast, cooldowns, spell menu
  GameShop.scala         Vendor flows: shop menu, buy, sell
  GameRares.scala        Rare-elite scheduler, live registry, spawn
  GameQuests.scala       Quest log, tracker compass, turn-in, board menu
  HeroClasses.scala      Class/role definitions (attribute bonuses + starting gear)
  Spells.scala           Spellbooks: mage elemental bolts/novas + priest holy heal/purify/protect
  Quests.scala           Quest definitions (kill / collect / boss objectives, [GROUP], chains, dailies)
  Enemies.scala          Kill-XP table, loot tiers, rare-elite spawn table
  Loot.scala             Rarity-colored gear, signature mob drops, gold coins, tier-scaled rolls
  PartyLoot.scala        Party loot lock: need/greed rolls with timed resolution
  Shop.scala             Vendor stock (gold sink for gear)
  CharacterStore.scala   SNBT persistence of heroes in the world folder
  TownBuilder.scala      Starter town on first join, /quest town hubs, town NPCs, village adoption
  TownStore.scala        SNBT persistence of towns / quest-board registrations
  Trading.scala          Face-to-face player trading: invite, offers, double-confirm, safe swap
  TradePost.scala        Hub market signs: posted stacks with prices, sale gold waiting at the post
  Interactions.scala     Right-click hooks: quest board, vendor barrel, town NPCs
  Combat.scala           Kill credit from damage sources, threat feed
  Threat.scala           Per-mob threat table: damage/heal/taunt/buff aggro, hysteresis retarget, /quest threat readout
  QuestCommand.scala     /quest command tree (Brigadier)
  Hud.scala              Action-bar HUD: level, XP bar, quest tracker, party
```

## License

MIT
