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
| Summary (256 max) | `Shows mobs and players far past your render distance, on the game's terrain and on Voxy and Distant Horizons terrain. Players who logged off live on, and birds fill the sky.` |
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
| Dependencies | Fabric and Quilt: Fabric API **Required**, Voxy **Optional**. Every loader: Distant Horizons **Optional** |
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
optional, where they have a CurseForge page.

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
| Requirements | Fabric API (off-site link to Modrinth), noted "Fabric and Quilt only"; Voxy, Distant Horizons and Mod Menu as optional, in the description |
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

### Mobs on the horizon

All mobs are visible from far away, on the game's terrain and **on Voxy and Distant
Horizons terrain**. This works for any entity Minecraft can draw, mobs from other mods
included. Choose which ones in *Mobs & Entities...*.

### Players at any distance

Other players are visible at any distance, with their gear and their mount.

**Players who logged off live on.** When a player logs off, a copy with their name appears
where they left. It walks around, sits, opens chests and sleeps at night, until the player
comes back.

### Birds in the sky

Birds fly over the land and are visible from far away: flocks, geese, birds of prey, gulls,
ducks, bats... They land on trees, buildings, fields and beaches.

## Performance

- **No AI far away.** Far entities play ready-made animations that fit the ground around
  them. Players who logged off and birds have a small AI only when you are close.
- **Simplified Entities.** Far mobs and players are drawn as flat pictures instead of 3D
  models. It costs much less to draw, so you can show many more entities.
- **Everything runs on your computer.** The server only shares positions, so two players
  can see the same far mob doing different things.

Players who logged off are only visual: you walk through them, they cannot block or push
you, and mobs ignore them.

**Careful:** *Max Mobs* and *Mob Render Distance* both on *Unlimited* can crash the game.

## Multiplayer

Everything works in single player. On a server, some features need the mod on the server:

| Feature | Single player | LAN | Server with the mod | Server without the mod |
|---|---|---|---|---|
| Mobs on the horizon | ✓ | ✓ | ✓ | Only the mobs you met |
| Players at any distance | — | ✓ | ✓ | ✗ |
| Players who logged off | ✓ | ✓ | ✓ | Only those you saw leave |
| Birds | ✓ | ✓ | ✓ | ✓ |

To set up a server, put the same file in its `mods` folder. Nothing else to set up. Players
without the mod can still join. The server shares every player's position with the players
who have the mod: tell your players.

## Getting started

1. Install the file for your game version and loader. Voxy and Distant Horizons are optional.
2. Keep the default settings. *Restore Defaults* puts them back.

Open the settings from the Options screen, Mod Menu, Sodium's *Video Settings* or
`/livinghorizon config`.

## Requirements

- Minecraft 1.20 to 1.21.11 and 26.1 to 26.3.
- Fabric or Quilt (with [Fabric API](https://modrinth.com/mod/fabric-api)), NeoForge or Forge.
- [Voxy](https://modrinth.com/mod/voxy) or [Distant Horizons](https://modrinth.com/mod/distanthorizons): optional.
- [Mod Menu](https://modrinth.com/mod/modmenu): optional, on Fabric and Quilt.

## Made with AI

I use AI to make Living Horizon, mostly [Claude](https://claude.ai). I am not pretending I
wrote everything by hand.

I have been a developer for a long time. I learned to code before AI was a thing, in Notepad
and Sublime Text, and I have worked with .NET, C++, PHP and Java. I am a developer by
profession, specialised in video games, and I was one before companies used AI the way they
do today.

At work, AI was pushed into the workflow, and now I use it every day. It lets me work about
ten times faster. Most developers I know use it too. I understand that it bothers some
people, and I am sorry if that disappoints you, but it is part of how I work now.

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
