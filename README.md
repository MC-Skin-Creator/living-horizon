<p align="center"><img src="src/main/resources/assets/livinghorizon/icon.png" alt="Living Horizon" width="256"></p>

# Living Horizon

**Living Horizon** is a Minecraft mod that shows mobs and players far past your render
distance. It draws them on the game's terrain and on the terrain of **Voxy** and **Distant
Horizons**. It can also help performance: you can lower your simulation distance and still
see a living world.

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

The mod uses two techniques to stay light.

### No AI far away

Far entities have no AI. They play ready-made animations that fit the ground around them.

- **Mobs**: no AI. They walk, graze and look around.
- **Players who logged off**: a small AI when you are close, so they use beds and chests.
  Ready-made animations when you are far.
- **Birds**: a small AI when you are close, ready-made flights when you are far.

Players who logged off are only visual. The world does not know they exist: you walk
through them, they cannot block or push you, and mobs ignore them.

### Simplified Entities

Many games draw far objects as **impostors**: a flat picture instead of a 3D model. Living
Horizon does the same for far mobs and players. It costs much less to draw, so you can show
many more entities.

Keep *Simplified Entities* on if you raise *Mob Render Distance* or *Max Mobs*. A *Preset*
(*Low*, *Normal*, *High*, *Ultra*) sets everything at once.

**Careful:** *Max Mobs* and *Mob Render Distance* both on *Unlimited* can crash the game.

### Everything runs on your computer

Your game handles the far animations, the players who logged off and the birds. The server
only shares positions. So two players can see the same far mob doing different things.

## Multiplayer

Everything works in single player. On a server, some features need the mod on the server:

| Feature | Single player | World opened to LAN | Server with the mod | Server without the mod |
|---|---|---|---|---|
| Mobs on the horizon | ✓ | ✓ | ✓ | Only the mobs you met |
| Players at any distance | — | ✓ | ✓ | ✗ |
| Players who logged off | ✓ | ✓ | ✓, even those who left before you joined | Only those you saw leave |
| Birds | ✓ | ✓ | ✓ | ✓ |

- **Server**: put the same jar in the server's `mods/` folder (with the Fabric API on Fabric
  and Quilt). Nothing else to set up.
- **LAN**: nothing to do.
- Players without the mod can still join.
- The server shares every player's position with the players who have the mod. Tell your
  players.

## Getting started

1. Install the file for your game version and loader (see *Requirements*). Voxy and Distant
   Horizons are optional.
2. Keep the default settings. *Restore Defaults* puts them back.

Open the settings from the Options screen, Mod Menu, Sodium's *Video Settings* (Sodium 0.8
and later) or `/livinghorizon config`.

## Useful commands

| Command | What it does |
|---|---|
| `/livinghorizon config` | Opens the settings |
| `/livinghorizon scan` | Scans the mobs around you again if needed. Single player only, and done on its own the first time you open a world. |

## Requirements

- Minecraft 1.20 to 1.21.11 and 26.1 to 26.3.
- Fabric or Quilt (with [Fabric API](https://modrinth.com/mod/fabric-api)), NeoForge or Forge.
- [Voxy](https://modrinth.com/mod/voxy) or [Distant Horizons](https://modrinth.com/mod/distanthorizons): optional.
- [Mod Menu](https://modrinth.com/mod/modmenu): optional, on Fabric and Quilt.

Download from the [releases](https://github.com/MC-Skin-Creator/living-horizon/releases)
page, one file per game version and loader:

| Minecraft | Fabric, Quilt | NeoForge | Forge |
|---|---|---|---|
| 1.20, 1.20.1 | ✓ | | 1.20.1 |
| 1.20.2 | ✓ | | ✓ |
| 1.20.3, 1.20.4 | ✓ | | 1.20.4 |
| 1.20.5, 1.20.6 | ✓ | 1.20.6 | 1.20.6 |
| 1.21, 1.21.1 | ✓ | 1.21.1 | 1.21.1 |
| 1.21.2, 1.21.3 | ✓ | 1.21.3 | 1.21.3 |
| 1.21.4, 1.21.5 | ✓ | ✓ | ✓ |
| 1.21.6 to 1.21.8 | ✓ | 1.21.8 | 1.21.8 |
| 1.21.9, 1.21.10 | ✓ | 1.21.10 | 1.21.10 |
| 1.21.11 | ✓ | ✓ | ✓ |
| 26.1 to 26.1.2 | ✓ | ✓ | 26.1.2 |
| 26.2, 26.3 | ✓ | ✓ | ✓ |

## Compatibility

| Mod | Notes |
|---|---|
| [Voxy](https://modrinth.com/mod/voxy) | Mobs and players are visible on its terrain. |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | From 2.3 on. Mobs and players are visible on its terrain. |
| Iris and shader packs | Photon and Complementary checked. |
| [Sodium](https://modrinth.com/mod/sodium) | From 0.8, the settings are also in Sodium's *Video Settings*. |
| GeckoLib | Works. From 1.21.2 to 1.21.8 its mobs are not drawn far away yet ([#67](https://github.com/MC-Skin-Creator/living-horizon/issues/67)). |

Mobs from other mods should work. Mods not tested yet are listed under the
[`mod compatibility`](https://github.com/MC-Skin-Creator/living-horizon/labels/mod%20compatibility)
label.

## More

- Something wrong? Open an [issue](https://github.com/MC-Skin-Creator/living-horizon/issues)
  with your game version, loader and a screenshot.
- Questions, news and help: join the [Discord](https://discord.gg/THSV9dexNj).
- [docs/technical.md](docs/technical.md): every setting, the debug tools and how the mod
  works in the code.
- Build: `./gradlew :1.21.11:build` for one target, `./gradlew buildAndCollect` for every jar.
- License: source-available under the [Living Horizon License](LICENSE), copyright 2026
  clixmods. The unchanged jar may be redistributed with credit; forks are not allowed.
- Credits: made by clixmods. Collaborators and testers: ISessen. Full list in [CREDITS.md](CREDITS.md).
- Contributing: pull requests to this repository only, see [CONTRIBUTING.md](CONTRIBUTING.md).
- AI is welcome on this project, but keep thinking for yourself. AI is a great tool when you
  know how to use it. I am a professional developer, specialised in video games, since long
  before AI was used in the industry. AI lets me work ten times faster, so I use it: Living
  Horizon is made with [Claude](https://claude.ai), and Claude works best here. It reads
  `CLAUDE.md` and the skills in `.claude/skills/`, which describe how the project works.

Minecraft is a trademark of Mojang Studios. This project is not affiliated with or
endorsed by Mojang Studios or Microsoft.
