# Living Horizon — Changelog

## Unreleased

### New
- **Simplified Players** — a new setting keeps distant players as full models while mobs still turn into flat pictures far away.
- **Languages** — Living Horizon now speaks German, Spanish, Italian, Brazilian Portuguese, Polish, Turkish, Russian, Ukrainian, Simplified and Traditional Chinese, Japanese and Korean.
- **Sodium** — Living Horizon's settings now also appear in Sodium's *Video Settings*, as pages of their own below Sodium's (Sodium 0.8 and later).
- **Players who logged off on a mount** — they keep their horse, boat or happy ghast and ride it around where they left: the horse walks, the boat stays on the water and the happy ghast drifts through the air.
- **Video memory** — *Simplified Entities...* shows, in a tab for mobs and one for players, how much video memory their pictures take, and two new settings choose the most each may take.

### Improved
- **Settings** — every setting has a clearer name and description, worded like Minecraft's own options, and distances get sliders the full width of the list.
- **Players who logged off** — away from you they walk a small round around where they left, as distant animals do, riders included, and far away they turn into light pictures like players online, standing, seated, lying down or in a bed.
- **Riders** — far away, a player on a horse, a boat, a minecart or anything else they ride becomes one light picture with it, instead of two full models.
- **Crowds of players** — past a hundred or so players far away, the others no longer stay full models.

### Fixed
- **Resizing the game window** — distant mobs keep hiding behind hills, Voxy's and Distant Horizons' included, after the window changes size, instead of showing through them until the game restarts.
- **Distant Horizons** — far away, players, players who logged off and slimes show like other mobs, instead of vanishing under its terrain (Minecraft 26.1 and later).
- **Distant Horizons** — far away, its terrain no longer cuts into mobs and players standing in front of it, as if it were closer than it is (Minecraft 26.1 and later).
- **View bobbing** — walking no longer makes distant mobs blink, nor Distant Horizons' terrain come and go in front of them, at every step (Minecraft 26.1 and later).

## 0.7.0 — 2026-10-17

### New
- **Players who logged off** — they live their life around where they left, as far as a village: they stroll, sit, look at chests and workstations, walk through doors to a bed at night, and raise a shield against monsters in sight and hits; they keep to the ground: break their floor and they fall, bury them and they climb back on top.
- **Offline names** — players who logged off show their name marked *(offline)*, and aiming at one up close tells when they were last seen; settings hide their names, or the players themselves.
- **3D birds** — every bird is now a small model, like the game's mobs, with beating wings and legs when perched; far away they become light pictures, like distant mobs with impostors on. *Bird style* in the settings brings back the flat 2D birds.
- **Hmm…** — I thought I saw something in the sky. 😉

### Improved
- **Distant mobs** — slimes, magma cubes and rabbits now hop their way around instead of sliding, a slime squashing as it lands, and chickens flap their wings stepping down a block.
- **Ducks** — when you come near, they take off together and land farther along the water instead of flying away for good.

### Fixed
- **Spyglass** — distant mobs seen through a spyglass are measured at their zoomed size: *Animation size* now matches what you see, and they keep walking when they look big.
- **Opening a world** — distant mobs walk and graze from the start, instead of only turning on the spot.
- **Perched birds** — tits, robins and ducks no longer dart back and forth, stand in the air or inside the leaves, or swim onto dry land, and ducks no longer appear right next to you only to fly off at once; ducks now swim about their pond instead of jumping in a loop, and robins and tits hop more and take off less.
- **Mod list** — Forge, NeoForge and Quilt no longer show Living Horizon under the wrong license.

## 0.6.0 — 2026-10-15

### New
- **Impostors** — mob pictures are saved on disk and reused the next time you play, so they are only drawn again after a change of resource packs, mods or game version.

### Improved
- **Mod icon** — a new pixel-art icon: Steve in front, and mobs, villagers, players and birds alive all the way to the mountains behind him.
- **Single player** — distant mobs teleport or sprint to another spot far less often as you come back: they now follow the real mobs while the game still moves them, and disappear when those die.

### Fixed
- **Impostors screen** — two figures with the same name, such as villagers in different outfits, no longer swap places and flicker.
- **Impostors** — the thin dark lines and black specks that sometimes showed along the top or bottom edge of far mobs are gone, and long mobs such as horses and sniffers are no longer cut off at the muzzle or tail.
- **GeckoLib mobs on Minecraft 1.21.2 to 1.21.8** — a world no longer crashes a few seconds after loading when a mob from a GeckoLib mod was met far away; such mobs are not shown far away yet.
- **Impostors** — the game no longer slows down with impostors on: the same mobs are no longer drawn into new pictures over and over, and mobs that look alike share one picture, kept from one world to the next.

## 0.5.0 — 2026-10-13

### New
- **Impostors on Minecraft 1.20.1 to 1.21.8** — far players and mobs are drawn as light pictures of themselves on these versions too, and the Impostor Manager shows them.

### Fixed
- **Forge 1.20.1 to 1.20.4** — the game no longer crashes on startup with the mod installed.
- **Impostors screen on Minecraft 1.21.8 and older** — opening it no longer crashes the game.
- **Joining a world** — mobs already around you when you join are drawn far away too, instead of only those met afterwards.

## 0.4.2 — 2026-10-12

### Fixed
- **Mobs leaving your range** — a mob no longer vanishes for a few frames before its distant copy appears.
- **Mobs leaving your range** — the distant copy now faces the way the mob did, instead of sometimes turning to another direction.
- **Minecraft 1.21.9 to 26.1** — parts of distant mobs, such as a villager's clothes or a held item, no longer show through Voxy's terrain under shaders, nor through Distant Horizons' terrain.
- **Minecraft 26.1 and later** — the see-through parts of distant mobs, such as a slime's outer layer, no longer show through Voxy's terrain under shaders, nor through Distant Horizons' terrain.

## 0.4.1 — 2026-10-11

### Fixed
- **Shader packs** — distant figures shown as impostors are no longer dark with Iris shaders such as Photon or Complementary: they catch the sun like full models.
- **Photon with Voxy** — distant mobs no longer wear the shadow of the terrain behind them, and Voxy's water stays as it is.
- **Shaders on Minecraft 26.2 and later** — distant mobs hide behind Voxy's and Distant Horizons' hills with a shader pack on, older Iris versions included, as they do without one.
- **Minecraft 26.1** — distant mobs hide behind Voxy's and Distant Horizons' hills, as on other versions.
- **Voxy 0.2.18** — the mod reads Voxy's terrain again: distant mobs stand on it and hide behind its hills.
- **Distant Horizons on Minecraft 26.3 with Iris installed** — distant mobs hide behind Distant Horizons' hills again.
- **Voxy on Minecraft 26.3 with a shader pack** — distant mobs hide behind Voxy's hills.
- **Distant Horizons with Photon's render scale** — distant mobs hide behind the right hills, not hills shifted on the screen.

## 0.4.0 — 2026-10-10

### New
- **First scan by itself** — the first time you enter a single player world, the mobs of the 32 chunks around you are read at once, so the horizon is alive from the start. The scan distance is now given in chunks, like the render distance, and the other distances say *blocks*.
- **Animation settings** — two new sliders choose when distant mobs stop playing their animations: below a size on screen, or past a distance.
- **Quality presets** — Low, Normal, High and Ultra set impostor distance, the number and range of distant mobs and the birds in one go, for powerful machines that want a fuller horizon. Impostors have their own settings category, and *Restore defaults* now sits beside *Done*.

### Fixed
- **Black distant mobs** — without shaders, distant mobs no longer turn black for a moment, mostly while flying.

## 0.3.2 — 2026-10-09

### Fixed
- **Photon** — with Voxy and Photon's upscaling (TAAU) on, distant mobs hide behind the right hills again.

## 0.3.1 — 2026-10-08

### Fixed
- **Minecraft 1.20 to 1.21.4** — distant mobs now hide behind Voxy's and Distant Horizons' hills, as on newer versions.
- **Older Distant Horizons** — works with Distant Horizons 2.3 and newer, not only the latest version.

## 0.3.0 — 2026-10-07

### New
- **Vulkan** — with the game's Vulkan renderer, distant mobs hide behind hills too, Distant Horizons included.

### Fixed
- **Distant Horizons without shaders** — distant mobs no longer fade into the terrain.
- **Distant Horizons** — no more smears where the normal render distance ends.

## 0.2.0 — 2026-10-07

### New
- **Distant mobs** — animals, villagers and golems stay alive on the horizon, past your render distance, grazing and walking around.
- **Distant players** — other players keep walking far away, with their skin, armour and mount. Players who log off stay asleep where they left.
- **Birds** — flocks, geese in a V, gulls over the coast, bats at night.
- **Distant Horizons support** — works with Distant Horizons as well as Voxy.
- **Hidden behind hills** — distant mobs behind a hill are not drawn, which keeps the game fast.
- **Impostors** (off by default) — very far mobs and players are drawn as flat pictures, much lighter with many of them.
- **Scan the chunks around** — in single player, mobs from the chunks around you show up on the horizon right away.
- **Settings** — a "Living Horizon..." button in the Options menu, with the world still visible behind it, and a button to restore the defaults.
- **Quilt, NeoForge and Forge** — the mod also runs on Quilt, NeoForge and Forge, on Minecraft 1.20 to 26.3.
- **A logo** in the mod list and the data pack list.
