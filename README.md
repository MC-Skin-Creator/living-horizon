<p align="center"><img src="src/main/resources/assets/livinghorizon/icon.png" alt="Living Horizon" width="256"></p>

# Living Horizon

**Gives life to the distance.** Players and mobs stay visible far past your render
distance, through several rendering techniques. It supports far-terrain mods such as
Voxy and Distant Horizons, and adds birds to bring the sky to life, so you can take in
your Minecraft world at its best. Works on any server; add the mod to the server (or play
on LAN) to see players at any distance.

You can now play with a lower simulation distance and still see a living world.

## Features

### Other players, at any distance

Other players stay visible wherever they are, with their skin, cape, elytra and armour, and
riding what they ride: a horse, a boat, a happy ghast... They hide behind hills and
buildings like anything else, so you see someone walk over a ridge or disappear into a
valley. Needs the mod on the server (or a world opened to LAN).

### Players who logged off live on

When someone leaves, they stay where they were and go on with their day until they come back:

- they stroll around, sit down, open a chest, stand at a crafting table or a workstation;
- at night they find a free bed and sleep, and get up in the morning;
- when a monster comes near, they draw a sword and raise their shield (they never fight);
- someone who left on a horse, a boat or a happy ghast keeps it and rides it about.

Their name shows with *(offline)*, and looking at one up close tells you when they were last
seen. They are only a picture on your screen: they block nobody and nothing can hurt them.

### Mobs on the horizon

Animals, villagers, golems and named mobs stay visible far away once you have met them. They
graze, wander and look around, each in its own way: rabbits and slimes hop, chickens flap up
a step. They only walk where they could, never off a cliff or through a fence, and when you
come back the copy walks to the real mob and hands over without a jump.

In single player, *Scan Nearby Mobs* shows every mob in the chunks around you at once. On a
server with the mod, the server shares its mobs, so you also see the ones others met. Mobs
from other mods are shown too.

### A sky full of birds

Flocks crossing the sky, geese in a V, birds of prey circling, gulls over the coast, pigeons
on tall buildings, robins in the fields, tits in the trees, ducks on lakes and rivers, bats at
night, and now and then parrots. Perched birds fly off when you come close and land a bit
farther. Birds are small 3D models with beating wings, or flat pixel sprites if you prefer.

### Smooth with hundreds of figures

Past a distance, far players and mobs are drawn as flat pictures of themselves
(*Simplified Entities*). A mob 200 blocks away is only a few pixels tall: you hardly see the
difference, and hundreds of figures on screen stay smooth. The pictures are made by the game
itself, so skins, armour, resource packs and other mods' models all show.

### Fully configurable

Pick a *Preset* (*Low*, *Normal*, *High*, *Ultra*), or choose yourself which mobs and birds
are shown, how many and how far. The settings are also in Sodium's *Video Settings*
(Sodium 0.8 and later).

## Getting started

1. Install the file of your game version and loader (see *Requirements*). Voxy and
   Distant Horizons are optional.
2. Keep the default settings: they are the best balance. *Restore Defaults* puts them back.
3. In single player, press *Scan Nearby Mobs* in the settings (or `/livinghorizon scan`)
   to show the mobs around you at once.

The scan radius is in **chunks** (32 = 512 blocks); every other distance is in **blocks**.

**Careful:** *Max Mobs* and *Mob Render Distance* both on *Unlimited* can crash the game.

## Good to know

- Settings: Options screen, Mod Menu or `/livinghorizon config`.
- The scan only works in single player. On a server, put the mod on the server too: it
  shares the players and the mobs with everyone who has the mod.
- Every player's position is then shared with the players who have the mod: tell your players.

| Where you play | Distant mobs | Distant players |
|---|---|---|
| Single player | Yes | Players who logged off |
| A world opened to LAN | Yes | Yes |
| A server with the mod | Yes, shared by the server | Yes |
| A server without the mod | Only the ones you met | No |

To set up a server, put the same jar in its `mods/` folder (with the Fabric API on Fabric
and Quilt). Nothing else to configure; players without the mod can still join.

## Commands

| Command | What it does |
|---|---|
| `/livinghorizon` | Lists the players followed and says whether the server shares positions |
| `/livinghorizon config` | Opens the settings |
| `/livinghorizon scan` | Shows the mobs around you at once (single player) |
| `/livinghorizon bird <kind>` | Calls a group of birds: `geese`, `gulls`, `ducks`, `raptors`... |

## Requirements

- Minecraft 1.20 to 1.21.11 and 26.1 to 26.3.
- Fabric or Quilt (with [Fabric API](https://modrinth.com/mod/fabric-api)), NeoForge or Forge.
- [Voxy](https://modrinth.com/mod/voxy) or [Distant Horizons](https://modrinth.com/mod/distanthorizons): optional. The mod also works without them, at any render distance.
- [Mod Menu](https://modrinth.com/mod/modmenu): optional, on Fabric and Quilt.

Jars are on the [releases](https://github.com/MC-Skin-Creator/living-horizon/releases)
page, one per game version and loader:

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
| [Voxy](https://modrinth.com/mod/voxy) | Mobs and players are visible on its terrain, hidden behind its hills. |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | From 2.3 on. Mobs and players are visible on its terrain, hidden behind its hills. |
| Iris and shader packs | Photon and Complementary checked. |
| [Sodium](https://modrinth.com/mod/sodium) | From 0.8, the settings are also in Sodium's *Video Settings*. |
| GeckoLib | Works. From 1.21.2 to 1.21.8 its mobs are not drawn far away yet ([#67](https://github.com/MC-Skin-Creator/living-horizon/issues/67)). |

Voxy, Distant Horizons and shader packs look exactly the same with or without Living
Horizon. Mobs from other mods should just work; mods still to check are tracked under the
[`mod compatibility`](https://github.com/MC-Skin-Creator/living-horizon/labels/mod%20compatibility)
label.

## More

- Something wrong? Open an [issue](https://github.com/MC-Skin-Creator/living-horizon/issues)
  with your game version, loader and a screenshot.
- [docs/technical.md](docs/technical.md): every setting, the debug tools and how the mod
  works in the code.
- Build: `./gradlew :1.21.11:build` for one target, `./gradlew buildAndCollect` for every jar.
- License: source-available under the [Living Horizon License](LICENSE), copyright 2026
  clixmods. The unchanged jar may be redistributed with credit; forks are not allowed.
- Contributing: pull requests to this repository only, see [CONTRIBUTING.md](CONTRIBUTING.md).

Minecraft is a trademark of Mojang Studios. This project is not affiliated with or
endorsed by Mojang Studios or Microsoft.
