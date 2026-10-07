# Store listing: Modrinth and CurseForge

Everything to fill in on the two platforms, field by field. Not shipped in the jar, and a
change here does not trigger a release (`**.md` is ignored by `release.yml`).

Do first: **make the GitHub repository public.** It is private today, and a project
with a source link that leads nowhere will be refused or distrusted.

## Two projects

1. **Living Horizon** (the mod): the jar.
2. **Living Horizon Data Pack** (optional companion): the zip, as a *Data Pack* project on
   each platform, linked from the mod page. The release workflow only publishes the jar;
   the zip is on the GitHub release and is uploaded by hand to the data pack project.

## Modrinth: the mod

Create > Project > **Mod**.

| Field | Value |
|---|---|
| Name | `Living Horizon` |
| URL (slug) | `living-horizon` |
| Summary (256 max) | `Gives life to the distance: players and mobs stay visible far past your render distance, with or without Voxy or Distant Horizons, and birds bring the sky to life. Client-side.` |
| Visibility | Public once ready (draft until the first version is approved) |
| Categories (pick up to 3 featured) | `Decoration`, `Mobs`, `Utility` |
| Client side | **Required** |
| Server side | **Unsupported** |
| Description | the block below |
| License | **Custom license**, link to the `LICENSE` file of the repository (Living Horizon License) |
| Issue tracker | `https://github.com/MC-Skin-Creator/living-horizon/issues` |
| Source code | `https://github.com/MC-Skin-Creator/living-horizon` |
| Wiki / Discord | empty until they exist |
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
| Summary (200 to stay safe) | `Gives life to the distance: players and mobs stay visible past your render distance, with or without Voxy or Distant Horizons, and birds bring the sky to life.` |
| Main category | **Cosmetic** |
| Additional categories | **Mobs**, **Utility & QoL** |
| License | **Custom** (Living Horizon License), link to the `LICENSE` file |
| Description | the block below, pasted in the editor |
| Logo | `src/main/resources/assets/livinghorizon/icon.png` (512x512) |
| Links | Source and Issues as above; Donation: your link |
| Allow others to distribute | leave **allowed**: the license allows the unchanged jar in modpacks, with credit |
| Environment | **Client** |

Files: one per jar, uploaded by the release workflow once `CURSEFORGE_TOKEN` and
`CURSEFORGE_ID` exist, with the same title, release type, loader and game versions as on
Modrinth, plus **Client** and the Java version (17 up to 1.20.4, 21 up to 1.21.11, 25 for
26.x). Relations: Fabric API required on Fabric and Quilt; Voxy and Distant Horizons
optional, where they have a CurseForge page.

CurseForge reviews a new project before it goes public; it takes from hours to days.

## Modrinth and CurseForge: the data pack

Project type **Data Pack** on each.

| Field | Value |
|---|---|
| Name | `Living Horizon Data Pack` |
| Slug | `living-horizon-data-pack` |
| Summary | `Companion of Living Horizon: publishes exact positions of players and mobs, so far-away ones appear where they really are. A data pack, no server mod.` |
| Categories | `Utility` (Modrinth); Utility & QoL (CurseForge) |
| License | Living Horizon License, as the mod |
| Game versions | one zip per game version, as the mod: 1.20 to 1.21.11, 26.1 to 26.3 |
| File | `livinghorizon-datapack-0.1.0+mc<game version>.zip`, from the GitHub release |
| Release type | Beta |

Description:

```markdown
# Living Horizon Data Pack

Companion of [Living Horizon](https://modrinth.com/mod/living-horizon). Put it in a
world's `datapacks/` folder and run `/reload`: the mod then knows the exact position of
every player and remembered mob, at any distance, instead of estimating it.

## What it does

Five times a second it writes each player's position, yaw and dimension into scoreboard
objectives shown in sidebars of four team colours. The server sends those to every client;
nobody sees them on screen. Every five seconds it also publishes the mobs it remembers
(animals, villagers, golems, named mobs).

## Good to know

- Everyone's position becomes public to whoever reads the scores. Tell your players.
- Anyone in a team of colour `black`, `dark_blue`, `dark_green` or `dark_aqua` would see
  the coordinates in their sidebar. If your server uses them, edit the four `setdisplay`
  lines in `load.mcfunction`.
- `/function livinghorizon:uninstall`, then `/datapack disable`, removes everything.

Needs the Living Horizon mod on the client. Nothing to install on the clients otherwise.
```

## Description of the mod (both platforms)

Markdown. Modrinth takes it as is; on CurseForge, paste it in the editor and let it
format the headings and lists.

```markdown
# Living Horizon

**Gives life to the distance.** Players and mobs stay visible far past your render
distance, through several rendering techniques. It supports far-terrain mods such as
Voxy and Distant Horizons, and adds birds to bring the sky to life, so you can take in
your Minecraft world at its best. Client-side, on any server.

You can now play with a lower simulation distance and still see a living world.

## Getting started

1. Install the file of your game version and loader (see *Requirements*). Voxy and
   Distant Horizons are optional.
2. Keep the default settings: they are the best balance. *Restore defaults* puts them back.
3. In single player, run *Scan the chunks around* in the settings (or `/livinghorizon scan`)
   to show the mobs around you at once.

The scan radius is in **chunks** (32 = 512 blocks); every other distance is in **blocks**.

**Careful:** *Mobs drawn at most* and *Mob distance* both on *Unlimited* can crash the game.

## Good to know

- Settings: Options screen, Mod Menu or `/livinghorizon config`.
- The scan only works in single player. On a server, the optional **data pack** shares
  the mobs and makes player positions exact.
- With the data pack, every player's position is public: tell your players.

## Features

- See other players far away.
- See mobs far away.
- Birds added to the sky.
- Players who logged off stay where they left.
- Play with a lower simulation distance, the distance stays alive.
- Everything can be configured.

## Requirements

- Minecraft 1.20 to 1.21.11 and 26.1 to 26.3.
- Fabric or Quilt (with [Fabric API](https://modrinth.com/mod/fabric-api)), NeoForge or Forge.
- [Voxy](https://modrinth.com/mod/voxy) or [Distant Horizons](https://modrinth.com/mod/distanthorizons): optional. The mod also works without them, at any render distance.
- [Mod Menu](https://modrinth.com/mod/modmenu): optional, on Fabric and Quilt.

## Links

[Source and issues](https://github.com/MC-Skin-Creator/living-horizon)
```

## Gallery, in this order

The first image is the card in search results: it has to read at a glance.

1. A village on the far terrain (Voxy or Distant Horizons) in the distance with visible figures: villagers, animals, a golem.
2. A far-off player on a hill in front of far-terrain mountains, with their mount.
3. A flock of birds or geese in a V over a coastline, at sunset.
4. The settings screen (mobs and birds lists).
5. The same view with the mod off and on, side by side.

Icon: the mod's own, `src/main/resources/assets/livinghorizon/icon.png`; the same picture is the
data pack's `pack.png`, to use as the data pack project's icon.

## First version changelog

Same as `changelog/en.md` once dated; the release workflow links to the GitHub release.
