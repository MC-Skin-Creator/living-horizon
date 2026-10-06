# Store listing: Modrinth and CurseForge

Everything to fill in on the two platforms, field by field. Not shipped in the jar, and a
change here does not trigger a release (`**.md` is ignored by `release.yml`).

Do first: **make the GitHub repository public.** It is private today, and a GPL project
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
| Summary (256 max) | `Gives life to the distance: animals, villagers, birds and other players keep living on the horizon, far past your render distance. Client-side, made for Voxy.` |
| Visibility | Public once ready (draft until the first version is approved) |
| Categories (pick up to 3 featured) | `Decoration`, `Mobs`, `Utility` |
| Client side | **Required** |
| Server side | **Unsupported** |
| Description | the block below |
| License | **GNU General Public License v3 only** (`GPL-3.0-only`) |
| Issue tracker | `https://github.com/MC-Skin-Creator/far-far-entities/issues` |
| Source code | `https://github.com/MC-Skin-Creator/far-far-entities` |
| Wiki / Discord | empty until they exist |
| Donation | your link (Ko-fi, GitHub Sponsors, Patreon, PayPal), labelled as support for you |
| Icon | 512x512 PNG, no text |
| Gallery | the five shots below; first one featured |

First version (Versions > Create):

| Field | Value |
|---|---|
| Version number | `0.1.0` |
| Version title | `Living Horizon 0.1.0 (1.21.11)` |
| Release channel | **Beta** (below 1.0.0) |
| Loaders | Fabric |
| Game versions | 1.21.11 |
| Dependencies | Fabric API: **Required**. Voxy: **Optional** |
| File | `livinghorizon-0.1.0+mc1.21.11.jar` (primary) |
| Changelog | the "Unreleased" section of `changelog/en.md` |

The release workflow does this version for you once `MODRINTH_TOKEN` and `MODRINTH_ID`
exist; create the project by hand first, to have the id.

## CurseForge: the mod

Create > Project > Class **Mods** (Minecraft).

| Field | Value |
|---|---|
| Project name | `Living Horizon` |
| Slug | `living-horizon` |
| Summary (200 to stay safe) | `Gives life to the distance: animals, villagers, birds and other players keep living on the horizon, past your render distance. Client-side, made for Voxy.` |
| Main category | **Cosmetic** |
| Additional categories | **Mobs**, **Utility & QoL** |
| License | **GNU General Public License version 3 (GPL-3.0)** |
| Description | the block below, pasted in the editor |
| Logo | 400x400 or larger, square PNG |
| Links | Source and Issues as above; Donation: your link |
| Allow others to distribute | leave **allowed**: the GPL allows it, and modpacks need it |
| Environment | **Client** |

First file (Files > Upload):

| Field | Value |
|---|---|
| File | the jar |
| Display name | `Living Horizon 0.1.0 (1.21.11)` |
| Release type | **Beta** |
| Game versions | 1.21.11, **Fabric**, **Java 21**, **Client** |
| Relations | Fabric API: Required dependency. Voxy: Optional, if it has a CurseForge page |
| Changelog | same as Modrinth |

CurseForge reviews a new project before it goes public; it takes from hours to days.

## Modrinth and CurseForge: the data pack

Project type **Data Pack** on each.

| Field | Value |
|---|---|
| Name | `Living Horizon Data Pack` |
| Slug | `living-horizon-data-pack` |
| Summary | `Companion of Living Horizon: publishes exact positions of players and mobs, so far-away ones appear where they really are. A data pack, no server mod.` |
| Categories | `Utility` (Modrinth); Utility & QoL (CurseForge) |
| License | GPL-3.0, as the mod |
| Game version | 1.21.11 |
| File | `livinghorizon-datapack-0.1.0+mc1.21.11.zip`, from the GitHub release |
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

**Gives life to the distance.**

Voxy shows you mountains, forests and villages far beyond your render distance, but
empty. Living Horizon fills them: animals grazing around far-off villages, villagers and
golems, flocks of birds crossing the sky, and other players walking where they really
are, with their skin, their armour, and the horse, boat or happy ghast they were riding.

Client-side, on any server. Nothing is sent to the server.

## What you get

- **Distant players.** They keep walking where they are, past the range where the server
  stops sending them: skin, cape, elytra, armour, and what they ride.
- **A horizon that lives.** Animals, villagers and golems graze and wander around the
  villages Voxy draws. Terrain hides them pixel by pixel, shader packs included.
- **Birds.** Flocks, geese in a V, gulls over the coast, pigeons on tall buildings, ducks
  on lakes, bats at night. Perched birds fly off when you come close.
- **Players who logged off** stay asleep where they were, until they come back.
- **Everything is configurable**: which mobs, which birds, how many, how far.

## How it works

Out of the box, the mod uses only what a vanilla client already receives: the locator
bar sends every player's direction at any distance, and as you move, the mod
triangulates where they are. The direction is always right; the distance sharpens as you
walk sideways relative to them.

For **exact positions**, drop the companion **data pack** (separate download, a folder in
`world/datapacks`) on the server. It publishes every player's and mob's position through
scoreboard objectives that the server already sends to every client. No server mod, no
plugin.

## Requirements

- Minecraft **1.21.11**, [Fabric Loader](https://fabricmc.net/use/) 0.17.3 or newer,
  [Fabric API](https://modrinth.com/mod/fabric-api), Java 21.
- [Voxy](https://modrinth.com/mod/voxy) is what it is made for (recommended).
- [Mod Menu](https://modrinth.com/mod/modmenu) is optional: it adds the settings button.

## Good to know

- Without the data pack, `/gamerule locatorBar false` on the server leaves only the
  memory of the last position, and a player who sneaks freezes their copy where it was.
- Everyone's position is public to whoever reads the data pack's scores: say so to your
  players before installing it.
- `/livinghorizon` lists who is followed and where each position comes from.

## Links

[Source and issues](https://github.com/MC-Skin-Creator/far-far-entities)
```

## Gallery, in this order

The first image is the card in search results: it has to read at a glance.

1. A Voxy village in the distance with visible figures: villagers, animals, a golem.
2. A far-off player on a hill in front of Voxy mountains, with their mount.
3. A flock of birds or geese in a V over a coastline, at sunset.
4. The settings screen (mobs and birds lists).
5. The same view with the mod off and on, side by side.

Icon: 512x512, readable at 96 px, no text. Idea: a distant village in blurred LOD on the
horizon with small signs of life (a bird, chimney smoke, a tiny player).

## First version changelog

Same as `changelog/en.md` once dated; the release workflow links to the GitHub release.
