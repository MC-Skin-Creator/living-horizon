# Store listing: Modrinth and CurseForge

Everything to paste into the two project pages. Not shipped in the jar, and a change
here does not trigger a release (`**.md` is ignored by `release.yml`).

## Identity

| | Modrinth | CurseForge |
|---|---|---|
| Name | Living Horizon | Living Horizon |
| Slug / URL | `living-horizon` | `living-horizon` |
| Type | Mod | Mods (Minecraft) |
| Loader | Fabric | Fabric |
| Game version | 1.21.11 | 1.21.11 |
| Environment | Client: required, Server: unsupported | Client |
| Java | 21 | Java 21 |
| Source | https://github.com/MC-Skin-Creator/far-far-entities | the same |
| Issues | https://github.com/MC-Skin-Creator/far-far-entities/issues | the same |
| License | GPL-3.0 | GNU GPLv3 |

## Summary

Modrinth, 256 characters at most:

> Gives life to the distance: animals, villagers, birds and other players keep living on the horizon, far past your render distance. Client-side, made for Voxy.

CurseForge, 200 characters at most:

> Gives life to the distance: animals, villagers, birds and other players keep living on the horizon, past your render distance. Client-side, made for Voxy.

## Tags

**Modrinth** (categories, up to three shown first): `decoration`, `mobs`, `utility`.
Loader: Fabric. Environment: client.

**CurseForge** (categories): Cosmetic, Mobs, Utility & QoL. Loader: Fabric.

Search keywords to work into the description: voxy, render distance, distant players,
lod, horizon, villagers, birds, ambient, immersion, client-side.

## Dependencies

- **Fabric API**: required (`mc-publish` declares it).
- **Voxy**: recommended, link only: https://modrinth.com/mod/voxy
- **Mod Menu**: optional, adds the settings button: https://modrinth.com/mod/modmenu

## Description

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
