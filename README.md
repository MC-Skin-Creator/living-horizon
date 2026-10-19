<p align="center"><img src="src/main/resources/assets/livinghorizon/icon.png" alt="Living Horizon" width="256"></p>

# Living Horizon

**Gives life to the distance.** Players and mobs stay visible far past your render
distance, through several rendering techniques. It supports far-terrain mods such as
Voxy and Distant Horizons, and adds birds to bring the sky to life, so you can take in
your Minecraft world at its best. Works on any server; add the mod to the server (or play
on LAN) to see players at any distance.

You can now play with a lower simulation distance and still see a living world.

## Features

- See other players far away.
- See mobs far away.
- Birds added to the sky.
- Players who logged off stay where they left.
- Play with a lower simulation distance, the distance stays alive.
- Everything can be configured.

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
| [Voxy](https://modrinth.com/mod/voxy) | Mobs and players are visible on its terrain. |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | From 2.3 on. Mobs and players are visible on its terrain. |
| Iris and shader packs | Photon and Complementary checked. |
| [Sodium](https://modrinth.com/mod/sodium) | From 0.8, the settings are also in Sodium's *Video Settings*. |
| GeckoLib | Works. From 1.21.2 to 1.21.8 its mobs are not drawn far away yet ([#67](https://github.com/MC-Skin-Creator/living-horizon/issues/67)). |

Mobs from other mods should just work. Mods still to check are tracked under the
[`mod compatibility`](https://github.com/MC-Skin-Creator/living-horizon/labels/mod%20compatibility)
label.

## More

- [docs/how-it-works.md](docs/how-it-works.md): every setting, the debug tools and how the
  mod works.
- Build: `./gradlew :1.21.11:build` for one target, `./gradlew buildAndCollect` for every jar.
- License: source-available under the [Living Horizon License](LICENSE), copyright 2026
  clixmods. The unchanged jar may be redistributed with credit; forks are not allowed.
- Contributing: pull requests to this repository only, see [CONTRIBUTING.md](CONTRIBUTING.md).

Minecraft is a trademark of Mojang Studios. This project is not affiliated with or
endorsed by Mojang Studios or Microsoft.
