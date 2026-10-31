# Living Horizon — Changelog

## Unreleased

### Server

#### Improved
- **Gliding players shared** — the server now tells players who is gliding with an elytra, so that they see them fly from afar; players with an older version still see them as before.

### Client

#### New
- **Links to the mod's pages** — beside the Discord logo, the mod's settings now have the logos of Modrinth, CurseForge, Nexus Mods and the mod's website, to open its pages in one click.
- **Distances shown in the world** — hovering or changing a distance on the mod's settings screen now draws it in the world behind: a glowing wall that far away, the edge of the chunks *Scan Radius* reads, or a ceiling at *Min. Flight Height*.

#### Improved
- **Simplification Distance can be turned off** — *Simplification Distance* now goes down to OFF, so far mobs, entities, players and birds become flat pictures only when they look smaller on screen than the *Simplification Size*.
- **Distant players gliding** — distant players flying with an elytra now glide through the sky, wings spread, instead of running upright in the air (with the mod on the server).
- **Resource packs switched back and forth** — far mobs' *Simplified Entities* (flat pictures of them) are no longer all drawn again when a resource pack is turned off and back on, and loading resources no longer pauses the game while they are looked for.
- **Superb Warfare vehicles far away** — Superb Warfare's tanks, planes, boats and guns now stay visible past the render distance from the start, behind Voxy's and Distant Horizons' hills like everything else, no longer off until turned on in *Mobs & Entities...*.

#### Fixed
- **Distant mobs sharp in Distant Horizons' fog** — distant mobs no longer stay sharp against Distant Horizons' fogged terrain: they fade into its fog like the land around them, without a shader pack (Minecraft 1.21.6 and later).

## 0.14.0 — 2026-10-29

### Client

#### New
- **Vehicles from other mods far away** — vehicles from other mods, such as Immersive Aircraft's planes, now stay visible past the render distance where they were left, like boats; they are listed under the *Entities* tab of *Mobs & Entities...* from the start, and the ones you ride without steering (minecarts, some other mods' vehicles) are off until you turn them on.
- **Settings for entities** — boats, vehicles and mannequins now have settings of their own, apart from the mobs: *Show Entities*, *Max Entities* and *Entity Render Distance*, which has no limit by default.
- **Simplified Mobs and Simplified Entities** — *Simplified Entities* is now two switches: *Simplified Mobs*, and *Simplified Entities* for boats, vehicles and mannequins, which far away become flat pictures too, seen from above and below as well; *Simplified Players* no longer depends on either, and the button that shows the pictures is now *Picture Preview...*, with a tab for the entities' pictures.
- **Changelog button** — the mod's settings now have a changelog button beside the Discord logo, to read what changed in each version (a *Read the changelog* button on Sodium's page).

#### Improved
- **Servers without the mod** — joining a server without Living Horizon, or with a version of it too old or too new, now says in the chat that distant players are not shown there; with *Show Players* off, nothing is said.
- **Mobs & Entities in two tabs** — *Mobs & Entities...* now lists the mobs and the other entities (boats, vehicles, mannequins) under two tabs, where a boat or a vehicle is simply shown or hidden.
- **Mobs & Entities changes seen at once** — turning a kind on or off in *Mobs & Entities...* now shows in the world behind it straight away, not once the screen is closed.
- **Distant mobs settings in three parts** — the *Distant Mobs & Entities* settings are now split into *General*, *Mobs* and *Entities*, with the list, the scan and *Forget All* under *General*.
- **Players riding far away** — with *Simplified Players*, a far player in a boat, a minecart, a plane or on a happy ghast is now pictured from above and below like what they ride, no longer seen side-on when you look down on them.
- **Planes left in the air** — a player who logs off while flying stays in the air on their plane, which hovers where it was, or flies round in circles if it was flying along.
- **Saved pictures shown** — the *Picture Preview* screen now says under each mob whether its picture is shared, read back from an earlier game, new and saved for the next ones, or not saved, with when and in which world it was made.
- **Sitting pets stay put** — cats, wolves and parrots told to sit now stay sitting far away, instead of standing up and wandering off.

#### Fixed
- **Crash opening Mobs & Entities from the title screen** — opening *Mobs & Entities...* from the title screen no longer crashes the game (Minecraft 26.x).
- **Other mods' vehicles turning black** — far vehicles and mobs from some other mods, such as Immersive Aircraft's planes, no longer turn black for a moment as you travel towards them.
- **Mounts drawn twice** — the mount or vehicle a far player rides, such as another mod's plane, is no longer drawn a second time beside them.

## 0.13.0 — 2026-10-28

### Client

#### Improved
- **Smoother game among distant mobs and offline players** — working out where distant mobs and players who logged off can walk no longer takes time from the game: it is done in the background.
- **Smoother frames with distant mobs** — the game waits less on the graphics driver each frame with distant mobs over Voxy or Distant Horizons, most of all on NVIDIA cards with *Threaded optimization* on.
- **Distant phantoms and ghasts flying** — distant phantoms now circle in the sky and distant ghasts drift about, instead of hanging still in the air.
- **Phantoms and ghasts seen from below** — as *Simplified Entities* (flat pictures of far mobs), phantoms and ghasts are now pictured from below and from above too, like birds, not only from the side.
- **Smoother with many distant mobs on Vulkan** — the game no longer loses time on every distant mob, every frame, when it draws with Vulkan (Minecraft 26.2 and later) or over Distant Horizons (Minecraft 26.3).
- **Every mob seen from afar** — monsters, bats, fish and every other mob are now shown far away from the start, roaming like the animals, so endermen stay in view across the End; animals you hid stay hidden.

#### Fixed
- **Buttons piled on top of each other** — the buttons at the bottom of *Distant Mobs & Entities* no longer sit on top of one another, and *Restore Defaults* is back to the left of *Done* in the settings (Minecraft 1.20.1).
- **Game freezing when changing dimension or leaving a world** — the game no longer freezes for good when you go through a portal or leave a world (with Distant Horizons).

## 0.12.1 — 2026-10-27

### Client

#### Fixed
- **Benchmark open to every player** — `/livinghorizon benchmark` is no longer offered to players; it is kept for testing the mod.

## 0.12.0 — 2026-10-23

### Server

#### New
- **Server settings** — `config/livinghorizon-server.json`, written the first time the server starts, chooses what the server shares: players online, players who logged off and mobs, how far, with teammates only, and which players and dimensions are never shared; changes apply within seconds, without a restart.

#### Fixed
- **Players kicked from Forge servers** — a Forge server with the mod no longer drops the players who have it too as they join, with "mismatched mod channel list" (Forge 1.20.2 and later).
- **Spectators and invisible players seen from afar** — players in spectator mode or invisible are no longer shown to other players far away, nor where they logged off.

### Client

#### New
- **Discord button** — the mod's settings now have a Discord logo beside the title, to join the server and ask a question or report a problem (a *Join the Discord* button on Sodium's page).
- **Mod version shown** — the settings screens and the debug panel now show the version of Living Horizon, handy to give with a bug report.
- **Simplification Size** — a new setting also turns far mobs, players and birds into *Simplified Entities* (flat pictures of them) when they look smaller on screen than a number of pixels, even before the *Simplification Distance* (10 pixels by default).

#### Improved
- **Smoother with hundreds of distant mobs** — *Skip Hidden Mobs* costs much less with many distant mobs and players in view, the far terrain is copied less often behind the scenes, and the light on the figures is read less often.
- **Minecraft 1.21.1 easier to find** — the Fabric, Quilt and NeoForge builds for Minecraft 1.21 and 1.21.1 are now published as beta, so they show on CurseForge and in launchers that hide alpha builds.
- **Fewer stutters with many distant figures** — far mobs, players and birds ask the game for much less memory each frame, so it stutters less.
- **Smoother with many birds** — birds behind you or too small to see are no longer drawn.

#### Fixed
- **Distant mobs floating in the void** — distant mobs are no longer drawn where no terrain is drawn under them yet, neither the game's nor Voxy's or Distant Horizons'.
- **Single player worlds closing at once on Forge** — a single player world no longer closes as soon as it opens, with "mismatched mod channel list" (Forge 1.20.2 and later).
- **Distant mobs vanishing over Distant Horizons' terrain** — in single player, distant mobs no longer vanish or stop walking on Distant Horizons' hills and mountains.

## 0.11.0 — 2026-10-22

### Server

#### Improved
- **Lighter on big servers** — the server spends less time choosing whose skin and mount to send each player, and sends a long list of players who logged off in several parts instead of one message too large for the game.
- **Players need this version too** — on a server with this version, players need it as well to see distant players.

#### Fixed
- **Offline players lost when the world file cannot be read** — a `livinghorizon-positions.dat` the server cannot read is no longer written over: it is kept as `livinghorizon-positions.dat.bad`, and the console says so.

### Client

#### New
- **Benchmark** — `/livinghorizon benchmark` measures what the mod costs in your world, part by part, and writes a report you can send with a bug report.
- **Benchmark world** — `/livinghorizon benchmark world` makes a new test world, always the same, where the benchmark runs from the same spot every time, among mobs and players who logged off; your own world is kept as it is.
- **Max Offline** — a new setting chooses how many players who logged off are shown at once, the nearest first (256 by default); the others show again as you come near.

#### Improved
- **Clearer message when versions differ** — when the mod on the server is too old or too new for yours, the log says which one to update, instead of saying the server does not have the mod.

#### Improved
- **Smoother with many players who logged off** — players who logged off cost less to show far away, and those hidden behind hills are skipped.

#### Fixed
- **Players who logged off past the Player Render Distance** — players who logged off no longer show farther than the *Player Render Distance* setting.
- **Distant mannequins wearing the default skin** — mannequins far away no longer lose their skin and show as Alex: they keep the one they wear up close, a player's or a resource pack's (Minecraft 1.21.9 and later).
- **Water vanishing under Photon with Distant Horizons** — with *Hide Behind Far Terrain* on, the water no longer turns into its bare, shadowed bed whenever a distant mob or player is in view (Minecraft 26.3).
- **Distant mannequins' capes sticking up** — capes of mannequins far away no longer stick up in the air: they hang down as they do up close (Minecraft 1.21.9 and later).
- **Settings file lost after a typo** — a mistake made editing `livinghorizon.json` by hand no longer wipes the file: it is kept as `livinghorizon.json.bad`, and the log says so.
- **Remembered mobs and offline players lost** — a game that closes while saving no longer damages the files of remembered mobs and players who logged off, and a damaged one is kept as a `.bad` file instead of being emptied.
- **Stutter when joining a big server** — joining a server with many players who logged off no longer makes the game stutter.
- **Out of memory from a damaged picture** — a damaged saved picture of a far mob (*Simplified Entities*) can no longer make the game run out of memory: it is drawn again instead.
- **Distant mobs hidden by Distant Horizons' terrain under shaders** — with Oculus and a shader pack, distant mobs no longer vanish behind Distant Horizons' terrain, more and more as the render distance grows (Forge).
- **Distant mobs shaking their heads** — mobs and players far away no longer shake their heads very fast with Entity Culling, alone or with Chloride.
- **Players drawn in the wrong modded dimension** — players online or logged off in one modded dimension, such as the Aether, are no longer drawn in another, such as the Twilight Forest (on a server, it needs this version too).
- **Distant mobs washed out by fog** — far mobs on Voxy's terrain no longer turn pale and fade into the fog colour past a certain distance: they take the same fog as the land around them (without a shader pack).

## 0.10.0 — 2026-10-20

### Server

#### New
- **Mod on a server** — put the jar in the server's `mods` folder, on Fabric, Quilt, NeoForge or Forge (plus the Fabric API on Fabric and Quilt). Nothing else to set up. It replaces the data pack.
- **Player positions** — the server sends where every player is, five times a second, to the players who have the mod. It also sends their skin, armour, held items and mount.
- **Mobs** — the server sends the mobs within 2,048 blocks of each player, even ones they never met: animals, villagers, golems, allays and boats, with their saddle, harness, armour or chest.
- **Players who logged off** — the server keeps where each player logged off, how they looked, their mount and when they left, and sends it to everyone, even players who join later.
- **Players from before the mod** — the first time it runs, the server reads the players the world already had (the `playerdata` folder, their names from `usercache.json`) and shows them as logged off, where they left, in what they wore.
- **World file** — the server saves all of this in `livinghorizon-positions.dat`, in the world folder. A mob is forgotten when its chunk loads and it is no longer there.
- **LAN worlds** — a world opened to LAN shares everything through the host's game. Nothing to install.
- **Players without the mod** — they can still join. The server sends them nothing.
- **Same mod on both sides** — keep the mod up to date on the server and on the players' games: a game that cannot read the server's messages ignores them.
- **Positions are shared** — the server shares every player's position with all the players who have the mod. Tell your players.
- **No more data pack** — the mod no longer reads it: remove it from the world's `datapacks` folder. Nothing goes through the scoreboard any more, so coordinates are no longer readable in scores or in the sidebar.

#### Fixed
- **Players vanishing when a world closes** — when a server or a LAN world closes, the players in it now stay where they were as offline players.

### Client

#### New
- **Every player at any distance** — on a server with the mod, you see all the other players, with their skin, armour, held items and mount.
- **Mobs you never met** — on a server with the mod, distant mobs show at once, even ones you never walked near, with their saddle, harness, armour or chest.
- **Players who logged off, even before you joined** — you see them where they left, with how they looked and what they rode.
- **Your own world** — in single player, players who logged off in your world show as soon as you open it, even those from before the mod.
- **Everything as you join** — distant mobs and players who logged off show as soon as you join, not a few seconds later.
- **Servers without the mod** — you can still join. No distant player is shown, only the mobs you met, and the log says so once.

#### Improved
- **Distant players' positions** — they come from what the server shares only. The locator bar guess is gone, and so is the *Lost Player Time* setting.

#### Fixed
- **Players on a happy ghast** — a far player riding a happy ghast no longer has a second ghast beside them, and one already riding when you join no longer floats in the air.
- **Happy ghast flying back after its rider gets off** — the happy ghast a far player gets off no longer reappears where it was first ridden and flies back: it stays where it was left.
- **Old skins far away** — a distant player who changes skin no longer keeps the old one far away.
- **Players turned sideways in boats** — a player who logs off in a boat no longer sits turned to the side: they face the way the boat does.
- **Riders sitting inside their horse** — a distant player riding a horse no longer sits inside it or on its back end, as a full model or as a flat picture.
- **Horse shown twice after its rider logs off** — a player who logs off on a horse no longer leaves a second copy of the horse beside the one they rest on.
- **Greyed-out Website and Issues buttons** — the Website and Issues buttons on the mod's page in Mod Menu are no longer greyed out (Fabric).

## 0.9.0 — 2026-10-19

### Fixed
- **Offline players standing in the water** — players who logged off while swimming are no longer left standing in the water: they wait on the nearest shore, or row a boat when the shore is more than 10 blocks away.
- **Water vanishing around boats** — the water no longer turns clear around offline players rowing a boat under a shader pack such as Photon, nor disappears in a large square around far boats.
- **Rowers losing their boat far away** — far away, offline players rowing a boat are no longer shown standing without it while their picture is made.

## 0.8.0 — 2026-10-18

### New
- **Simplified Players setting** — keeps distant players as full models while distant mobs still turn into flat pictures far away.
- **12 new languages** — German, Spanish, Italian, Brazilian Portuguese, Polish, Turkish, Russian, Ukrainian, Simplified and Traditional Chinese, Japanese and Korean.
- **Settings in Sodium's menu** — Living Horizon's settings also appear in Sodium's *Video Settings*, on pages of their own (Sodium 0.8 and newer).
- **Offline players keep their mount** — players who logged off on a horse, a boat or a happy ghast keep riding it around where they left.
- **Video memory settings** — the *Simplified Entities...* screen shows how much video memory the pictures of mobs and players use, and *Mob Picture Memory* and *Player Picture Memory* set a limit for each.

### Improved
- **Clearer settings** — every setting has a clearer name and description, in the style of Minecraft's own options, and distance sliders use the full width.
- **Offline players** — players who logged off walk a small loop around where they left, and turn into flat pictures far away like online players, whether standing, sitting, lying down or in bed.
- **Riders** — far away, a player riding a horse, a boat, a minecart or anything else becomes one flat picture with their mount, which is much lighter than two full models.
- **Large crowds of players** — with more than about a hundred distant players, the extra ones now turn into flat pictures too instead of staying full models.

### Fixed
- **Mobs showing through hills after resizing the window** — distant mobs no longer show through hills, Voxy's and Distant Horizons' included, after the game window changes size.
- **Players and slimes vanishing with Distant Horizons** — distant players, offline players and slimes no longer disappear under Distant Horizons' terrain (Minecraft 26.1 and newer).
- **Distant Horizons' terrain cutting into mobs** — Distant Horizons' terrain no longer cuts into mobs and players standing in front of it (Minecraft 26.1 and newer).
- **Distant mobs blinking while walking** — with view bobbing on, distant mobs no longer blink and Distant Horizons' terrain no longer flickers in front of them at every step (Minecraft 26.1 and newer).

## 0.7.0 — 2026-10-17

### New
- **Offline players come to life** — players who logged off live around where they left: they stroll, sit, look at chests and workstations, go to bed at night, and raise a shield when hit or when monsters are near. They stay on the ground: break the block under them and they fall, bury them and they climb back up.
- **Offline names** — players who logged off show *(offline)* after their name, and looking at one up close tells when they were last seen. Settings can hide their names, or the players themselves.
- **3D birds** — birds are now small 3D models like Minecraft's mobs, with flapping wings and legs when perched, and turn into flat pictures far away. The *Bird Style* setting brings back the old 2D birds.
- **Hmm…** — I thought I saw something in the sky. 😉

### Improved
- **Hopping mobs** — distant slimes, magma cubes and rabbits now hop instead of sliding, slimes squash when they land, and chickens flap their wings when stepping down a block.
- **Ducks** — ducks now fly off together when you come near and land farther along the water, instead of leaving for good.

### Fixed
- **Mobs frozen through a spyglass** — distant mobs seen through a spyglass now count at their zoomed size, so they keep walking when they look big (*Min. Animated Size* setting).
- **Mobs turning on the spot when opening a world** — distant mobs now walk and graze from the start.
- **Perched birds acting strangely** — tits, robins and ducks no longer dart back and forth, float in the air, stand inside leaves or swim on dry land, and ducks no longer appear right next to you only to fly away. Ducks now swim around their pond, and robins and tits hop more and fly off less.
- **Wrong license in the mod list** — Living Horizon no longer shows the wrong license in the mod list (Forge, NeoForge and Quilt).

## 0.6.0 — 2026-10-15

### New
- **Mob pictures saved between sessions** — the flat pictures of far mobs are saved on disk and reused the next time you play; they are only made again after you change resource packs, mods or game version.

### Improved
- **New mod icon** — a new pixel-art icon: Steve in front, with mobs, villagers, players and birds all the way to the mountains behind him.
- **Distant mobs when you come back** — in single player, distant mobs much less often teleport or run to another spot as you come back: they follow the real mobs while the game still moves them, and disappear when those die.

### Fixed
- **Flickering in the Simplified Entities screen** — two mobs with the same name, such as villagers in different outfits, no longer swap places and flicker.
- **Dark lines on far mobs** — far mobs drawn as flat pictures no longer show thin dark lines or black specks along their top or bottom edge, and long mobs such as horses and sniffers are no longer cut off at the muzzle or tail.
- **Crash with GeckoLib mobs** — a world no longer crashes a few seconds after loading when a mob from a GeckoLib mod was seen far away (Minecraft 1.21.2 to 1.21.8). These mobs are not shown far away yet.
- **Game slowing down with simplified entities** — the game no longer slows down when far mobs are drawn as flat pictures: the same mobs are no longer pictured over and over, and mobs that look alike share one picture.

## 0.5.0 — 2026-10-13

### New
- **Simplified entities on Minecraft 1.20.1 to 1.21.8** — very far mobs and players can now be drawn as flat pictures on these versions too, which is much lighter for your computer.

### Fixed
- **Crash on startup** — the game no longer crashes when it starts with the mod installed (Forge, Minecraft 1.20.1 to 1.20.4).
- **Crash in the Simplified Entities screen** — opening it no longer crashes the game (Minecraft 1.21.8 and older).
- **Mobs missing after joining a world** — mobs that were already around you when you joined now show on the horizon too, not only the ones you meet later.

## 0.4.2 — 2026-10-12

### Fixed
- **Mobs glitching as they walk away** — a mob that walks out of your normal view no longer vanishes for a moment or turns to face another way.
- **Parts of mobs showing through hills** — a villager's clothes, a held item or a slime's outer layer no longer show through Voxy's or Distant Horizons' hills while the rest of the mob is hidden (Minecraft 1.21.9 and newer, with Distant Horizons, or with Voxy and a shader pack).

## 0.4.1 — 2026-10-11

### Fixed
- **Far mobs too dark with shaders** — far mobs drawn as flat pictures are no longer too dark with Iris shader packs such as Photon or Complementary: the sun now lights them like full models.
- **Terrain shadows on distant mobs** — distant mobs no longer show the shadow of the terrain behind them, and Voxy's water looks as it should (Photon with Voxy).
- **Distant mobs showing through hills** — distant mobs no longer show through Voxy's or Distant Horizons' hills (Minecraft 26.1; Minecraft 26.2 and newer with a shader pack, older Iris versions included; Minecraft 26.3 with Iris installed).
- **Voxy 0.2.18 not recognised** — the mod works with Voxy 0.2.18 again: distant mobs stand on its terrain and no longer show through its hills.
- **Distant mobs hidden by the wrong hills** — distant mobs are no longer hidden by hills that are not in front of them (Distant Horizons with Photon's upscaling, TAAU).

## 0.4.0 — 2026-10-10

### New
- **Automatic first scan** — the first time you enter a single player world, the mobs within 32 chunks are found at once, so the horizon is alive from the start. The scan radius is now set in chunks, like the render distance.
- **Animation settings** — two new sliders stop the animations of distant mobs that are too small on screen or too far away, to save performance.
- **Quality presets** — Low, Normal, High and Ultra choose in one click how many distant mobs and birds you see, and how far. High and Ultra are for powerful computers.

### Fixed
- **Distant mobs turning black** — without shaders, distant mobs no longer turn black for a moment, mostly while flying.

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
