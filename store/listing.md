# Store listing: Modrinth, CurseForge and Nexus Mods

Everything to fill in on the three platforms, field by field. Not shipped in the jar, and a
change here does not trigger a release (`**.md` is ignored by `release.yml`).

Do first: **make the GitHub repository public.** It is private today, and a project
with a source link that leads nowhere will be refused or distrusted.

## One project

**Living Horizon** (the mod): the jar, on the clients and, to share the positions, on the
server. There is no data pack any more: a data pack project published before is to be
archived, with a note that the mod on the server replaces it.

## Modrinth: the mod

Create > Project > **Mod**.

| Field | Value |
|---|---|
| Name | `Living Horizon` |
| URL (slug) | `living-horizon` |
| Summary (256 max) | `Shows mobs and players far past your render distance, on the game's terrain and on Voxy and Distant Horizons terrain. Offline players live on, and birds fill the sky.` |
| Visibility | Public once ready (draft until the first version is approved) |
| Categories (pick up to 3 featured) | `Decoration`, `Mobs`, `Utility` |
| Client side | **Required** |
| Server side | **Optional** (shares the players and the mobs) |
| Description | the block below |
| License | **Custom license**, link to the `LICENSE` file of the repository (Living Horizon License) |
| Issue tracker | `https://github.com/MC-Skin-Creator/living-horizon/issues` |
| Source code | `https://github.com/MC-Skin-Creator/living-horizon` |
| Discord | `https://discord.gg/THSV9dexNj` |
| Wiki | empty until it exists |
| Donation | your link (Ko-fi, GitHub Sponsors, Patreon, PayPal), labelled as support for you |
| Icon | `src/main/resources/assets/livinghorizon/icon.png` (512x512) |
| Gallery | the five shots below; first one featured |

Versions: one per jar, that is one per game version and loader (Fabric, Quilt, NeoForge,
Forge; the tables of `stonecutter.properties.toml`). The release workflow uploads them all
once `MODRINTH_TOKEN` and `MODRINTH_ID` exist; create the project by hand first, to have
the id. What each version gets:

| Field | Value |
|---|---|
| Version title | `Living Horizon 0.1.0 (1.21.11)` on Fabric, `(Quilt 1.21.11)`, `(NeoForge 1.21.11)`, `(Forge 1.21.11)` on the others |
| Release channel | **Beta** below 1.0.0; **Alpha** for the targets not yet seen drawing in a world (`mod.release_type`) |
| Loaders | the jar's own: Fabric, Quilt, NeoForge or Forge |
| Game versions | the `mod.mc_releases` of its table (1.20 to 1.21.11, 26.1 to 26.3) |
| Dependencies | Fabric and Quilt: Fabric API **Required**, Voxy **Optional**. NeoForge: Ecstatic **Optional**. Every loader: Distant Horizons **Optional** |
| Changelog | a link to the GitHub release |

## CurseForge: the mod

Create > Project > Class **Mods** (Minecraft).

| Field | Value |
|---|---|
| Project name | `Living Horizon` |
| Slug | `living-horizon` |
| Summary (200 to stay safe) | `Shows mobs and players far past your render distance, on the game's terrain and on Voxy and Distant Horizons terrain, with birds in the sky.` |
| Main category | **Cosmetic** |
| Additional categories | **Mobs**, **Utility & QoL** |
| License | **Custom** (Living Horizon License), link to the `LICENSE` file |
| Description | the block below, pasted in the editor |
| Logo | `src/main/resources/assets/livinghorizon/icon.png` (512x512) |
| Links | Source and Issues as above; Donation: your link |
| Allow others to distribute | leave **allowed**: the license allows the unchanged jar in modpacks, with credit |
| Environment | **Client** and **Server** (the server side is optional) |

Files: one per jar, uploaded by the release workflow once `CURSEFORGE_TOKEN` and
`CURSEFORGE_ID` exist, with the same title, release type, loader and game versions as on
Modrinth, plus **Client** and the Java version (17 up to 1.20.4, 21 up to 1.21.11, 25 for
26.x). Relations: Fabric API required on Fabric and Quilt; Voxy and Distant Horizons
optional, where they have a CurseForge page, and Lodistance and Ecstatic (NeoForge) optional.

CurseForge reviews a new project before it goes public; it takes from hours to days.

## Nexus Mods: the mod

The API cannot create a page: create it by hand, once, under the game **Minecraft** (domain
`minecraft`; Nexus has no separate Java or Bedrock game). The page is
`https://www.nexusmods.com/minecraft/mods/1376`, so the repository variable `NEXUSMODS_ID` is
`1376`, beside the `NEXUSMODS_TOKEN` secret (an API key from the account's settings).

| Field | Value |
|---|---|
| Name | `Living Horizon` |
| Category | **Miscellaneous** (no category fits a client-side visual mod better) |
| Summary | the CurseForge one |
| Description | the block below, in Nexus's editor (BBCode: headings and lists by hand) |
| Requirements | Fabric API (off-site link to Modrinth), noted "Fabric and Quilt only"; Voxy, Distant Horizons, Lodistance, Ecstatic and Mod Menu as optional, in the description |
| Permissions | the Living Horizon License: no modified upload, no asset reuse, redistribution of the unchanged jar with credit, link to `LICENSE` |
| AI content | Nexus asks every page to declare generative AI use: tick it and say what for |
| Images | a banner of about 1400x400 and the gallery below, in 16:9 |

Files: none by hand. The release workflow creates one file per jar, named after its target
(`Living Horizon (Fabric 1.21.11)`, `(NeoForge 26.2.x)`, ...), with the loader and game
versions in its description, then adds a version to that same file on every release and
archives the previous one. The page's changelog gets the release notes once per release.
Renaming a file on Nexus makes the next release create a new one beside it.

## Description of the mod (both platforms)

Markdown. Modrinth takes it as is; on CurseForge, paste it in the editor and let it
format the headings and lists.

```markdown
# Living Horizon

**Living Horizon** shows mobs and players far past your render distance. It draws them on
the game's terrain and on the terrain of **Voxy** and **Distant Horizons**. It can also help
performance: you can lower your simulation distance and still see a living world.

> ⚠️ **This is a beta, so expect bugs.** I mainly play on 1.21.11 with Voxy, and it works
> there. I have not tested every loader and game version. Found a bug? Tell me on the
> [issues](https://github.com/MC-Skin-Creator/living-horizon/issues) page or on the
> [Discord](https://discord.gg/THSV9dexNj). Thanks to anyone who helps!

## Features

### Distant Mobs

Shows mobs far past your render distance. These distant mobs are light copies of the real
ones, with no AI.

<details>
<summary>How distant mobs work</summary>

- They play ready-made animations that fit the ground around them: they walk, graze, hop and
  look around. Phantoms, ghasts and the ender dragon fly.
- A mob shows where your game or the server last saw it, and stays there from one session to
  the next. If it died or left since, it is forgotten when you come near.
- On a server with the mod, the server shares the mobs it knows, even those you never met.
  In single player, the mod reads the mobs around you the first time you open a world.
- **Guessed mobs** fill the land nobody has explored yet. The mod works out from the world's
  seed which mobs the game would put there, and where. They fill a world drawn far away by
  Distant Horizons, for example. They are a guess: they may be gone, or stand a little
  elsewhere, when you get there. This needs the mod on the server, and works in single
  player. Look through a spyglass to see them farther away, or press *Guess Nearby Mobs* to
  show them up to 1024 chunks around.
- They stand on the game's terrain, and on the terrain of far-terrain mods such as Voxy or
  Distant Horizons. Hills hide them.
- Far away, they become flat pictures instead of 3D models (*Simplified Mobs*). They cost much
  less to draw, so you can show many more.
- A mob in a boat or a minecart stays in it, and a rolling minecart keeps rolling. Pets told
  to sit stay seated.
- Boats stay where they were left, and so do vehicles from other mods, such as Immersive
  Aircraft's planes.
- *Mobs & Entities...* shows or hides each kind. Mobs with a name tag can always be shown.
  *Hide Underground Mobs* leaves out the mobs in caves and mines.
- They are only visual. Farms, spawning and the mob cap do not change, and you cannot hit a
  distant mob.

</details>

### Distant Players

Shows other players at any distance, with their skin, gear and mount. This needs the mod on
the server: it shares their positions with the players who have the mod.

<details>
<summary>How distant players work</summary>

- Your game draws each player where the server says they are. A player gliding with an
  elytra is shown gliding.
- Far away, they become flat pictures instead of 3D models (*Simplified Players*).
- The server's owner chooses what is shared: how far, with teammates only, and which players
  and dimensions stay hidden. Players in spectator mode or invisible are never shared.
- *Player Render Distance* limits how far you see them.

</details>

### Offline Players

When a player logs off, a copy with their name stays where they left, until they come back.
It is only visual: the world does not know it exists.

<details>
<summary>How offline players work</summary>

- Up close, they have a small AI. They stroll, sit, look in chests and use workstations. They
  sleep in a bed at night, gather by a campfire in the evening and take shelter from the
  rain. Two of them nearby may chat, and one near water may fish.
- Far away, they play ready-made animations.
- They look back at you when you look at them up close, crouch when you crouch, and raise a
  shield against monsters.
- You can push them out of your way. They cannot push you, and mobs ignore them.
- They keep their mount: a horse, a boat, a happy ghast. One who left swimming waits on the
  shore, or rows a boat.
- Their name shows with *(offline)*. Look at one up close to see when they were last seen.
- On a server with the mod, you see those who left before you joined. In single player, the
  world's players show from the start, even those from before the mod was installed.
  Without the mod on the server, you only see those you saw leave.
- They keep the look another mod gave them, such as their Minecraft Comes Alive model.
- *Max Offline* limits how many show at once, and *Roaming Radius* how far they wander.

</details>

### Birds

Adds birds to the Overworld's sky, visible from far away. They exist in your game only: two
players do not see the same birds.

<details>
<summary>How birds work</summary>

- Up close, they have a small AI. They perch on trees, buildings, fields and beaches, ducks
  swim, and pigeons and bats fly off when you come close.
- Far away, they play ready-made flights.
- Flocks, geese flying in a V, birds of prey, seagulls over the coast, pigeons on tall
  buildings, robins, tits, ducks, parrots and bats at night. *Bird Types...* picks them.
- They are small 3D models that flap their wings, and flat pictures far away (*Simplified
  Birds*). *Bird Style* turns them into flat 2D birds.
- *Bird Density*, *Bird Distance*, *Bird Size* and *Min. Flight Height* set how many you see,
  how far, how big and how high.

</details>

### Performance

Living Horizon draws less the farther away things are. You can lower your simulation
distance and still see a living world.

<details>
<summary>How the mod stays light</summary>

- **Simplified Mobs**, **Simplified Entities** and **Simplified Players**: past the
  *Simplification Distance*, or when smaller on screen than the *Simplification Size*, a
  mob, a vehicle or a player is drawn as a flat picture instead of a 3D model.
  **Simplified Birds** does the same for far birds. Many games do the same with far
  objects, which they call impostors. It costs much less to draw, so you can show many more.
- With *Smooth Simplification*, a figure first turns to the angle of its picture, so the
  switch does not jump.
- The pictures are saved on your disk and reused in your next sessions. They are made again
  only after a change of resource packs, mods or game version. *Mob Picture Memory* and
  *Player Picture Memory* limit the video memory they take.
- Distant mobs have no AI: they play ready-made animations. Offline players and birds have a
  small AI only when you are close.
- Past the *Animation Distance*, or when smaller on screen than the *Min. Animated Size*,
  mobs stop walking and stand still.
- *Skip Hidden Mobs* does not draw the mobs that the terrain hides entirely.
  *Hide Underground Mobs* leaves out the mobs in caves and mines.
- *Max Mobs*, *Max Entities* and *Max Offline* limit how many are shown at once, nearest
  first.
- A *Preset* (*Low*, *Normal*, *High*, *Ultra*) sets all of this at once. Keep *Simplified
  Mobs* on if you raise *Mob Render Distance* or *Max Mobs*.
- Everything runs on your computer. The server only shares positions, so it stays light,
  and two players can see the same far mob doing different things.
- **Careful:** *Max Mobs* and *Mob Render Distance* both on *Unlimited* can crash the game.

</details>

### Settings

Open them from the Options screen or `/livinghorizon config`.

<details>
<summary>What the settings offer</summary>

- A *Preset* (*Low*, *Normal*, *High*, *Ultra*) sets everything at once.
- When you change a distance, its limit shows in the world behind the menu.
- Right-click a slider that ends in *Unlimited* to type a value. The arrow keys move any
  slider one step.
- Keys of your choice switch distant players on and off, switch *Hide Underground Mobs* and
  open the settings.
- The mod and its in-game changelog are in 14 languages.
- The settings are also in other mods' menus, such as Sodium's *Video Settings*.
- Distant mobs are lit by shader packs, such as Photon.

</details>

## Multiplayer

Everything works in single player. On a server, some features need the mod on the server:

| Feature | Single player | LAN | Server with the mod | Server without the mod |
|---|---|---|---|---|
| Distant Mobs | ✓ | ✓ | ✓ | Only the mobs you met |
| Distant Players | — | ✓ | ✓ | ✗ |
| Offline Players | ✓ | ✓ | ✓ | Only those you saw leave |
| Birds | ✓ | ✓ | ✓ | ✓ |

To set up a server, put the same file in its `mods` folder. Nothing else to set up. Players
without the mod can still join. The server shares every player's position with the players
who have the mod: tell your players. Players in spectator mode or invisible are never shared.

The server writes `config/livinghorizon-server.json` the first time it starts. Edit it to
choose what it shares: players online, offline players, mobs, how far, with teammates
only, and which players and dimensions are never shared. Changes apply after a few seconds.

## Getting started

1. Install the file for your game version and loader. Voxy, Distant Horizons, Lodistance,
   Ecstatic and Far View are optional.
2. Keep the default settings. *Restore Defaults* puts them back.

Open the settings from the Options screen, Mod Menu, Sodium's *Video Settings* or
`/livinghorizon config`.

## Requirements

- Minecraft 1.20 to 1.21.11 and 26.1 to 26.3.
- Fabric or Quilt (with [Fabric API](https://modrinth.com/mod/fabric-api)), NeoForge or Forge.
- [Voxy](https://modrinth.com/mod/voxy), [Distant Horizons](https://modrinth.com/mod/distanthorizons), [Lodistance](https://www.curseforge.com/minecraft/mc-mods/lodistance), [Ecstatic](https://modrinth.com/mod/ecstatic) or [Far View](https://www.curseforge.com/minecraft/mc-mods/far-view): optional.
- [Mod Menu](https://modrinth.com/mod/modmenu): optional, on Fabric and Quilt.

## FAQ

### How it works

<details>
<summary>Is it only visual? Does it break mob farms?</summary>

It is only visual, no AI. The real mobs work as without the mod, so farms, spawning and the
mob cap do not change. You cannot hit a distant mob.

</details>

<details>
<summary>Are the distant mobs real, or placed at random?</summary>

They are real mobs, where your game or the server last saw them. One that died or despawned
since can still show until you come near it. Then it is forgotten.

</details>

<details>
<summary>How is it different from a higher simulation or entity distance?</summary>

A higher simulation distance makes the server run more real mobs, which costs a lot, and the
server still only sends them so far. Living Horizon draws copies that the server does not
run. They play ready-made animations, and far away they become flat pictures (*Simplified
Entities*).

</details>

<details>
<summary>How far does it show mobs and players?</summary>

Mobs: 512 blocks by default, up to *Unlimited* with *Mob Render Distance*. Players, boats and
vehicles: any distance by default, which *Player Render Distance* and *Entity Render Distance*
can limit. A mob only shows once your game or the server has seen it.

</details>

### Compatibility

<details>
<summary>Does it work with my mod?</summary>

Most likely. Voxy, Distant Horizons (2.3 and later), Lodistance (Fabric 26.3, with OpenGL and
without shaders), Ecstatic (NeoForge 1.21.1, single player, without shaders), Far View (Forge and NeoForge, 1.20.1 to 1.21.4), Bobby, Sodium, Iris and shader packs work with it; Photon and Complementary were checked. Living Horizon only adds its own figures: it does not change how other
mods draw the world. Mods not tested yet are listed under the
[`mod compatibility`](https://github.com/MC-Skin-Creator/living-horizon/labels/mod%20compatibility)
label. If something breaks, open an
[issue](https://github.com/MC-Skin-Creator/living-horizon/issues) with the mod's name and
version.

</details>

<details>
<summary>Do mobs from other mods show far away?</summary>

Yes, as long as they use Minecraft's own entity system. If a mod's entities work too
differently, ask me to add support for it, in an
[issue](https://github.com/MC-Skin-Creator/living-horizon/issues) or on the
[Discord](https://discord.gg/THSV9dexNj) :)

</details>

<details>
<summary>Do I need Voxy, Distant Horizons, Lodistance, Ecstatic or Far View?</summary>

No. Without them, distant mobs and players show on the game's own terrain, out to your
render distance. With one of them, they also show on its far terrain.

</details>

### Performance and settings

<details>
<summary>Will it lower my frame rate?</summary>

Drawing more entities costs frame rate. If your game slows down, keep *Simplified Mobs* and
*Simplified Entities* on and pick a lower *Preset*. *Max Mobs* and *Mob Render Distance* both on *Unlimited* can
crash the game.

</details>

<details>
<summary>Can I choose what is shown?</summary>

Yes. *Show Mobs*, *Show Players*, *Offline Players* and *Show Birds* turn each part on or
off. *Mobs & Entities...* chooses mob by mob.

</details>

<details>
<summary>Does it change my world or load chunks?</summary>

No. It changes nothing in your world and loads no chunk. It keeps what it remembers in files
of its own: in the `config` folder, and in `livinghorizon-positions.dat` in the world folder
for what the server shares. Removing the mod leaves your world as it was.

On a server, the mod only sends positions to the players who have it.

</details>

### Multiplayer

<details>
<summary>Does the server need the mod?</summary>

No. Almost everything works in single player, and you can join any server with it. But
players at any distance need the mod on the server, and the server then shares more mobs and
offline players. See *Multiplayer*.

</details>

<details>
<summary>Does it work on Paper or Spigot servers?</summary>

You can play on them with the mod, as on any server without it. The server side runs on
Fabric, Quilt, NeoForge and Forge only, so a Paper or Spigot server shares nothing. A
plugin for Paper is discussed in
[#156](https://github.com/MC-Skin-Creator/living-horizon/issues/156).

</details>

<details>
<summary>I see no distant mobs or players. Why?</summary>

- Distant players need the mod on the server. Its owner may also limit who is shared.
- On a server without the mod, only the mobs you met show.

</details>

<details>
<summary>Can it be used to cheat? Is it allowed on servers?</summary>

It only shows other players where the server allows it. Distant players only show on a
server that has the mod, and its owner chooses what it shares: how far, with teammates
only, and which players and dimensions stay hidden. `"sharePlayers": false` in the server's
settings turns it off. Invisible players and players in spectator mode are never shared.
Sneaking players are, for now
([#269](https://github.com/MC-Skin-Creator/living-horizon/issues/269)).

On other servers, you only see what your own game saw. The mod sends nothing to the server.
Each server has its own rules about mods, though: ask its staff.

</details>

### Downloads and license

<details>
<summary>Is there a Bedrock version?</summary>

No. Living Horizon is a mod for Minecraft Java Edition.

</details>

<details>
<summary>My game version is not supported. Will it be?</summary>

Tell me on the [Discord](https://discord.gg/THSV9dexNj) which version and loader you play.
It helps me choose what comes next.

</details>

<details>
<summary>Can I put it in a modpack?</summary>

Yes. Use the official file and credit it.

</details>

<details>
<summary>What if the project is abandoned?</summary>

The license plans for it. Once the project is declared abandoned (its GitHub repository
archived, or its README saying so), its license turns into the GPL: anyone may carry the project on,
with credit. See section 5 of the [LICENSE](https://github.com/MC-Skin-Creator/living-horizon/blob/main/LICENSE).

</details>

### AI

<details>
<summary>Was it made with AI?</summary>

Yes. I am a full-stack developer, specialised in video games, and it is my job. I have made
mods since 2017: Call of Duty: Black Ops III maps such as
[Nuketown Zombies Legacy](https://steamcommunity.com/sharedfiles/filedetails/?id=2180904327),
with over a million downloads on the Steam Workshop (see my
[portfolio](https://clixmods.github.io/)). The ideas and the techniques come from game
development: impostors, LODs and ready-made animations are tricks games have used for years.
AI writes much of the code. I decide how the mod works, test it in the game and fix what
goes wrong. Without AI, a project this size would have taken me too long to make at all.

</details>

<details>
<summary>Is it on Modrinth? Why is it unlisted there?</summary>

It is on Modrinth, but unlisted: it does not show in Modrinth's search. Since August 2026,
[Modrinth's rules](https://modrinth.com/legal/rules) do not allow a project made mainly
with AI to be public, so its staff unlisted it. CurseForge, Nexus Mods and the
[releases](https://github.com/MC-Skin-Creator/living-horizon/releases) page have the same
files.

Unlisted does not mean unsafe. It is about how the mod is made, not about what it does:

- The code is public: anyone can read it on [GitHub](https://github.com/MC-Skin-Creator/living-horizon).
- GitHub builds every release from this code, and its build logs are public.
- The mod has no code of its own that connects to the internet. Player skins come from
  Mojang through the game, and the Discord link opens only when you click it.

</details>

## Made with AI

AI is part of this project's workflow: Living Horizon is made with [Claude](https://claude.ai).

## Links

[Source and issues](https://github.com/MC-Skin-Creator/living-horizon) · [Discord](https://discord.gg/THSV9dexNj)
```

## Gallery, in this order

The first image is the card in search results: it has to read at a glance.

1. A village on the far terrain (Voxy or Distant Horizons) in the distance with visible figures: villagers, animals, a golem.
2. A far-off player on a hill in front of far-terrain mountains, with their mount.
3. A flock of birds or geese in a V over a coastline, at sunset.
4. The settings screen (mobs and birds lists).
5. The same view with the mod off and on, side by side.

Icon: the mod's own, `src/main/resources/assets/livinghorizon/icon.png`.

## First version changelog

Same as `changelog/en.md` once dated; the release workflow links to the GitHub release.
