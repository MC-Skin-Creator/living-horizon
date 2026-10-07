# Living Horizon — Changelog

## Unreleased

### New
- **Distant Horizons support** — the mod now works with Distant Horizons as well as Voxy, and uses whichever is installed. Birds, mobs and far-away spots read its terrain, and distant mobs are hidden by its hills pixel by pixel.
- **Distant players** — other players keep walking where they are, past the server's view distance, with their skin, their armour and what they ride.
- **Distant mobs** — animals, villagers and golems stay on the horizon, grazing and wandering around the villages Voxy shows.
- **Birds** — flocks, geese in a V, gulls over the coast, bats at night.
- **Livelier mobs** — distant mobs play walks of up to six blocks, chosen from the blocks around them, with pauses to graze and glance around, instead of circling. Grass, flowers and dug paths no longer stop them; fences and cliffs still do. They never jump: as you get close they walk back to where they were, then up to the real mob before the game takes over, and they walk to their new place when they move.
- **Mobs hidden by what hides them** — a distant mob entirely behind a hill, Voxy's or Distant Horizons' included, is not drawn at all, which saves a lot with many mobs. On by default; the switches moved to *Debug...*.
- **Players who logged off** stay asleep where they left, until they come back.
- **Settings beside the game** — the settings screens sit on the left with no blur behind them, so changes show on the world as you make them.
- **Impostors** (off by default) — far enough away, distant players and mobs are drawn as a flat picture of themselves instead of their whole model, which costs much less with many of them. The pictures follow skins and resource packs; an *Impostors...* screen in the settings shows them and bakes them again.
- **Polygon count on F3** — the debug screen shows how many polygons the distant players and mobs cost.
- **Settings in the Options menu** — a "Living Horizon..." button next to Done, a new limit on how far distant mobs and players are drawn, and an unlimited number of mobs.
- **Scan the chunks around** — in single player, a button in the settings (or `/livinghorizon scan`) reads the mobs of the chunks around you from the world itself: they show up on the horizon without walking there first.
- **Settings reshuffled** — distant players and distant mobs now switch on and off on their own, the far plane moved to *Debug...* and the glowing outline became its *Outlines...*, impostors are on from 128 blocks, mobs are capped at 512 and 512 blocks by default (unlimited on both can crash the game), logged-off players sit, the flying saucer is called "Easter egg", and the scan radius goes up to 1024 chunks (the command takes any radius), and a *Restore defaults* button puts every setting back.
- **Quilt, NeoForge and Forge** — the mod now also runs on Quilt (with the Fabric API), NeoForge and Forge, next to Fabric, on Minecraft 1.20 to 1.21.11 and 26.1 to 26.3 (NeoForge from 1.20.6).
- **A logo** — the mod and the data pack have their own icon, in the mod list and the data pack list.
- **Outlines** (*Debug...* > *Outlines...*) — an outline seen through terrain around each kind of figure, each on its own switch, in the colours of the debug boxes: white for the mobs the game draws, cyan for real mobs the mod draws, green for 3D copies, blue for players, magenta for impostors, and a cross where a distant mob is not drawn at all. Handy for screenshots comparing what the mod draws and what it saves.
