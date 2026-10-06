# Living Horizon

Gives life to the distance. Voxy shows you mountains, forests and villages far past your
render distance, but empty. Living Horizon fills them: animals grazing around far-off
villages, villagers and golems, flocks of birds crossing the sky, and other players
walking where they really are, with their skin, their armour, and the horse, boat or
happy ghast they were riding.

A client-side Fabric mod for Minecraft **1.21.11**. Nothing is sent to the server: on its
own, the mod uses only what a vanilla client already receives. With the companion **data
pack** on the server (not a mod: a folder in `world/datapacks`), every position is exact
at any distance.

## What you can do

- **See other players far away.** They keep walking where they are, past the range where
  the server stops sending them, with their skin, cape, elytra and armour, and what they
  ride.
- **Watch the distance live.** Animals, villagers and golems graze and wander around the
  villages Voxy draws, and are hidden by the terrain pixel by pixel, shaders included.
- **Look up.** Flocks, geese in a V, gulls on the coast, bats at night - and, now and
  then, a flying saucer taking a cow (`/livinghorizon ufo`).
- **Find players where they left.** Someone who logged off stays asleep where they were,
  until they come back.
- **Tune it.** Everything is in the settings (Mod Menu, or the key under *Living Horizon*
  in Controls): which mobs, which birds, how many, how far.

## Install

Requires [Fabric Loader](https://fabricmc.net/use/) 0.17.3 or newer and the
[Fabric API](https://modrinth.com/mod/fabric-api). [Voxy](https://modrinth.com/mod/voxy)
is what the mod is made for and is recommended. [Mod Menu](https://modrinth.com/mod/modmenu)
is optional and adds the settings button.

| Minecraft | Java | Fabric Loader | Fabric API |
|---|---|---|---|
| 1.21.11 | 21 | 0.17.3 or newer | 0.141.6+1.21.11 |

Drop `livinghorizon-<version>+mc1.21.11.jar` into `.minecraft/mods/`. For exact positions,
also drop the data pack zip into the world's `datapacks/` folder (see below).

Jars and the data pack come from the
[releases](https://github.com/MC-Skin-Creator/far-far-entities/releases) page. Releases
are cut from the commits merged into `main`; a version below `1.0.0` is marked *beta*.

## The data pack: exact positions

`build/libs/<version>/livinghorizon-datapack-<version>+mc1.21.11.zip`, built from the
`datapack/` folder. Drop it in the world's `datapacks/` folder and run `/reload`.

Five times a second it writes each player's position, yaw, dimension and whether they
ride something into four scoreboard objectives (`lh.x`, `lh.y`, `lh.z`, `lh.m`). An
objective shown in any display slot is sent by the server to **every** client, at any
distance, so the pack shows its four in the sidebars of four team colours (`black`,
`dark_blue`, `dark_green`, `dark_aqua`): nobody sees them on screen, every client
receives them, and the mod reads them. `/livinghorizon` says whether the pack is found.

- Anyone in a team of one of those colours would see the coordinates in their sidebar.
  If your server uses them, change the four `setdisplay` lines in `load.mcfunction`.
- Any client can read these scores: everyone's position is public to whoever looks.
- `/function livinghorizon:uninstall`, then `/datapack disable "file/<pack name>"`,
  removes everything it created.

`DatapackTest` compiles every function with the game's own command dispatcher, and
parses `pack.mcmeta` and the predicate with the game's own codecs.

## Players who logged off

A player who logs off stays where they were last, asleep on the ground (or sitting, see
`offlinePose`), until they come back. This is remembered per server across sessions, in
`config/livinghorizon/resting/<server>.json`. With the data pack, even players who logged
off before you joined are there: the pack keeps everyone's last position, and their
skin is fetched from Mojang by name.

## Mobs far away

Animals (and villagers, golems, allays, named mobs) stay visible once out of range,
where they were, playing a loop: standing and looking around, now and then a slow walk
around a small circle. No AI runs anywhere; it is a picture of what lives there.

- **With the data pack**, every five seconds it publishes every remembered mob loaded
  around any player: position, kind, variant (horse coat, sheep colour, llama, rabbit,
  axolotl, parrot) and age, under the mob's UUID, in the same four objectives. Whoever
  was last near a mob leaves its position for everyone, and the server resets the scores
  of a mob that dies, so it disappears for everyone. Which mobs: the entity type tag
  `tags/entity_type/remembered.json`, numbered in `mob_kind.mcfunction` in the order of
  `MobKinds.TYPES` (`DatapackTest` checks the three agree).
- **Without it**, each client remembers the mobs it met itself
  (the types in `mobTypes`, chosen in game), in `config/livinghorizon/mobs/<server>.json`.
- A mob met up close keeps its exact look (the game's own save of it); one known only
  from the pack is built from kind, variant and age.
- Coming back within range of a remembered mob that is not there forgets it.
- Before a mob moves, the ground around it is read (`track/MobPaths`, from loaded chunks or
  Voxy's world): it walks the widest circle that stays on level ground, else to and fro
  along a clear line, else only turns on the spot - never off a cliff nor into a wall.
  Animals graze between walks.
- `maxDistantMobs` (200) of them are drawn at once: the nearest.

## Birds

Purely a picture, on this client: flocks crossing the sky, geese in a V, birds of prey
circling, gulls over the coast (landing on the beach now and then), pigeons sitting on
tall buildings, robins hopping in fields, tits in the trees, ducks on lakes and rivers,
bats at night (some hanging under leaves and roofs), now and then parrots. Perched birds
fly off when you come close; ducks never let you near. Birds never vanish at once: they
scatter and shrink away. An easter egg: now and then a flying saucer takes a cow
(`/livinghorizon ufo`).

Where each kind goes is read off the terrain a few columns at a time - from the chunks
loaded here, or farther from Voxy's world (`compat/VoxyWorld`): crops or farmland for
robins, leaves for tits, a man-made block well above its neighbours for pigeons, a beach or
ocean biome for gulls. Birds are pixel sprites from `textures/misc/birds.png` (flying: seen
from above, one plane per wing; perched: seen from the side, facing the camera); bats and
parrots are the game's own.

Settings: on/off, how many (`birdDensity`, percent), how far (`birdMaxDistance`), how low
flocks may fly (`birdMinHeight`), size, and each kind on its own ("Choose birds...",
`hiddenBirds`).

## Voxy's depth

With a shader pack that keeps Voxy's terrain out of the game's depth buffer (Photon sets
`excludeLodsFromVanillaDepth`), anything drawn past the render distance would show through
Voxy's mountains. `compat/VoxyDepth` writes Voxy's depth into the game's - with Voxy's own
depth blit - just before the entities are drawn, and puts the original back just after,
except where an entity was drawn. Distant mobs are then hidden by Voxy terrain pixel by
pixel, and the pack still finds the depth it expects. Without a pack, Voxy does this itself.

## Where the position comes from, without the data pack

| Distance from you | What the client knows | What is drawn |
|---|---|---|
| Inside the server's view distance | The entity itself | The real player (the mod only stops their mount from being culled) |
| View distance → 332 blocks | The locator bar sends their **chunk** | A copy, kept inside that chunk |
| Past 332 blocks | The locator bar sends a **direction** only | A copy, at an estimated distance |
| No locator bar signal | Nothing | The copy stays where it was, then fades after `lostTimeoutSeconds` |

The locator bar (the bar above the hotbar that shows where other players are) is the
key: the server sends it to every client, with the player's UUID, at any distance.
Past 332 blocks it is only an angle. One angle is a line, not a point - but as you move,
you see that line from somewhere else, and the lines cross. `track/PositionFilter` is a
particle filter that does exactly that. In practice:

- **The direction is always right**, to about half a degree.
- **The distance is found when you move sideways** relative to them: in the tests, a
  minute of walking (340 blocks) across the line of sight places a player 1.7 km away
  to within 25 to 190 blocks.
- **If you both stand still, or they walk straight away from you**, the distance
  cannot be known from a direction alone. The estimate then keeps the last distance it
  had, or follows the speed they were last seen walking at.

The copy is drawn by the game's own renderer, so skins, capes, elytras, armour and
shader packs work. It is drawn where it really is, at its true size, so terrain in front
of it hides it: Voxy writes its terrain into the game's depth buffer and removes the
distance fog. The game's far clipping plane (four times the render distance) is pushed
out to the farthest player when needed (`extendFarPlane`).

### What breaks it

- `/gamerule locatorBar false` on the server: only the memory of the last position is left.
- A player who **sneaks** disappears from the locator bar (vanilla behaviour), so the copy
  freezes where it was.
- Another dimension: the locator bar only covers your own.
- A player who was **never** close to you is drawn standing, with their skin, and no mount:
  what they ride is only known once the server has sent it.

## Using it

- `/livinghorizon` lists the players followed, where their position comes from, and how
  sure it is (`±` blocks).
- A key (unbound by default, under *Living Horizon* in Controls) toggles the copies.
- `config/livinghorizon.json`:

| Setting | Default | |
|---|---|---|
| `enabled` | `true` | |
| `minApparentPixels` | `0` | Above 0, a far player is never drawn smaller than this many pixels (and stops shrinking) |
| `extendFarPlane` | `true` | Push the far clipping plane out to the farthest player, so terrain can hide them |
| `lostTimeoutSeconds` | `30` | How long a copy stays once the locator bar loses them |
| `distantMobs` | `true` | Keep remembered mobs visible far away |
| `maxDistantMobs` | `200` | How many of them are drawn at once, nearest first |
| `mobTypes` | the 31 kinds of the data pack | Which mobs are shown, and remembered by this client (in game: "Choose mobs...") |
| `stillMobTypes` | happy ghast | Mobs shown standing still instead of walking their loop (in game: "Still") |
| `rememberNamedMobs` | `true` | A mob with a name tag is shown whatever its type |
| `voxyOcclusion` | `true` | Voxy's depth on distant mobs and players, pixel by pixel (see above) |
| `offlinePose` | `"sleep"` | A player who logged off: `"sleep"`, `"sit"` or `"hidden"` |
| `showVehicles` | `true` | Draw the mount they were last seen on |
| `glowOutline` | `false` | Glowing outline, seen through terrain |
| `renderTrackedVehiclesFar` | `true` | Never cull a mount that carries another player |

## Building

```sh
./gradlew :1.21.11:build          # compiles and runs the tests
./gradlew buildAndCollect         # jar and data pack zip in build/libs/<version>/
```

Fabric, official Mojang mappings, Stonecutter with a
single `1.21.11` node so that more versions can be added the same way later.

## License

Living Horizon is free software under the [GNU General Public License v3.0](LICENSE)
(`GPL-3.0-only`), copyright 2026 clixmods. The mod, the data pack and the textures
are all covered.

- You can read it, fork it, change it and redistribute it, modpacks and servers included.
- A modified version, or any work that reuses its code, must stay open under the same
  license, keep this copyright notice and say what was changed.
- Anyone who wants to carry the project on, or fix it when it is no longer maintained,
  is welcome to.

Please give a fork its own name and say it is based on Living Horizon when you publish it
on Modrinth or CurseForge: the license covers the code, not the name.

## Contributing

Pull requests are welcome. By contributing you agree that your work is distributed under
the same license, and you keep the copyright on it. Commits are in English, as
conventional commits (`feat:`, `fix:`, ...); `CLAUDE.md` describes the rules this
repository follows.

Minecraft is a trademark of Mojang Studios. This project is not affiliated with or
endorsed by Mojang Studios or Microsoft.
