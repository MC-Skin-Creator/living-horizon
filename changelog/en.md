# Living Horizon — Changelog

## Unreleased

### Fixed
- **Birds and bats** — none of them lets you come within reach any more: bats move away when you walk towards them, instead of letting you stand among them.
- **Shader packs** — distant figures shown as impostors are no longer dark with Iris shaders such as Photon or Complementary: they catch the sun like full models.

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
