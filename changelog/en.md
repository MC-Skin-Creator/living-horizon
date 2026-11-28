# Living Horizon — Changelog

## Unreleased

## 0.19.0 — 2026-11-28

### Server

#### New
- **Offline players in the player list, your choice** — Players with the mod see who logged off in their player list (Tab). Server owners can keep that list to those online with `offlineInTabList` in `config/livinghorizon-server.json`. Players with an older version of the mod always list them.

### Client

#### New
- **Simplified images keep moving** — Distant mobs, players and entities drawn as simplified images now keep moving: they walk, ride with their mount's legs moving, and row their boats, instead of gliding along frozen in one pose. This uses more video memory: each one that moves takes three times as much for its images. *Animated Picture Distance* sets how far they keep moving, *Animated Pictures* turns it off, and *Picture Preview...* shows which ones are animated.
- **Offline players in the player list** — Players who logged off now appear in the player list (Tab), after those online, with their head and their name in grey, the last to leave first: you see at a glance who plays on the server. Turn it off with *Offline in Player List*. Even alone in a world, the list now opens when it has offline players to show.
- **Potato preset** — A new *Potato* preset sets everything to its lowest for the weakest computers. Nearly every distant mob is then drawn as a simplified image, which still walks, rather than a full model.

#### Improved
- **Lighter settings screens** — The settings players rarely change now sit behind an *Advanced...* button in each section, on the mod's screen and on its page in Sodium's video settings. The *Debug Options* keep only the tools to look at what the mod does. No setting changes its value.
- **Every build easier to find** — All Minecraft versions and loaders are now published as beta. They show up on CurseForge and in launchers that hide alpha versions.
- **Mobs from GeckoLib mods in the distance** — Mobs from mods that use GeckoLib, such as Naturalist, are now shown in the distance and as simplified images (*Simplified Mobs*), instead of vanishing past the render distance. (Minecraft 1.21.2 to 1.21.8.)
- **Offline mark, your choice** — The (offline) after the name above players who logged off can now be hidden with *Offline Mark*, leaving their name alone.
- **Smoother switch to simplified images** — Distant players online now turn gently to the angle their simplified image shows them at, as mobs do, instead of spinning all at once as the image takes over. Every figure now takes the same few blocks to turn, whatever *Simplification Distance* is set to. (Minecraft 1.21.2 and later for players online.)
- **Distant mobs vanish in a puff of smoke** — When a distant mob you can see turns out to be gone (it died or wandered off while you were away), it now disappears in the same puff of smoke as a mob dying in the game, instead of blinking out. It follows the game's *Particles* setting. Endermen teleport away instead.

#### Fixed
- **Simplified mobs stand properly with Fresh Animations** — With Fresh Animations, the simplified images of distant mobs now show them standing straight, the same way from every side, instead of leaning, head down or in a different pose from one angle to the next. A mob also keeps its own look from the pack, horns and all, instead of sometimes wearing another mob's.
- **Distant GeckoLib mobs walk naturally** — Distant mobs from mods that use GeckoLib now play their walking animation as they move, instead of sliding along in their idle pose. (Minecraft 1.20.1 to 1.21.4.)
- **Giant mobs stay visible from afar** — Mobs from other mods whose model is much bigger than their hitbox, such as giant bosses, are now shown in the distance whenever part of them is in view. They no longer vanish as soon as their middle leaves the screen or a hill hides their feet.
- **The Graveyard's birds fly in the distance** — Distant crows, ravens and magpies from The Graveyard now fly and flap their wings, instead of walking on the ground.
- **Distant mobs no longer flash up in the sky** — Mobs coming into view far away no longer appear in the air for an instant and then jump to their place. They show up where they stand.
- **Useless Reptile's dragons animated in the distance** — Distant dragons from Useless Reptile now play their animations, instead of standing stiff with their wings and limbs spread out. (Minecraft 1.21.11 and later.)

## 0.18.1 — 2026-11-24

### Client

#### Fixed
- **Distant players and mobs animated by Entity Model Features packs** — With Entity Model Features and a resource pack that animates players or mobs, the players and mobs Living Horizon draws now move as the pack says, instead of walking with their arms only and their legs still. Seated offline players keep their legs bent. (Minecraft 1.20.1 to 1.21.1.)
- **Distant axolotls and fish swim as they should** — With Entity Model Features and Fresh Animations, distant axolotls, squid and fish now swim normally, instead of lying on their side, flapping as if out of the water.
- **Offline players move with Epic Fight** — With Epic Fight installed, offline players now walk, sit, sleep and ride with its animations, instead of standing frozen in one pose while they move. (NeoForge, Minecraft 1.21.1, with Epic Fight 21.)
- **Distant mobs keep their pack's textures** — With Entity Texture Features and Entity Model Features, as Fresh Animations needs them, distant mobs now keep the pack's textures, instead of losing them or all taking the texture and pose of one other mob. (Minecraft 1.20.1 to 1.21.8.)
- **Each distant mob keeps its own random texture** — With Entity Texture Features and a resource pack of random mob textures, the simplified images of distant mobs (*Simplified Mobs*) now each keep their own texture, instead of all wearing the texture of the first one pictured.

## 0.18.0 — 2026-11-21

### Client

#### New
- **Simplified Birds** — A new switch in *Performance*, on by default, draws distant 3D birds as simplified images, like mobs. It now works on its own: birds were left as full models whenever distant players, mobs and entities were all turned off. Perched birds now switch at the size they really take on screen, instead of staying full models farther than *Simplification Size* says.

#### Improved
- **Birds cost far fewer frames** — Birds now only fly where you can see them: without Voxy, Distant Horizons or another far-terrain mod, none are kept past the render distance, where the fog hid them anyway. Their simplified images are saved between games, so joining a world no longer stutters for several seconds, and 3D birds and simplified images take less work to draw, under shaders too. This can give more frames per second wherever birds are around.

#### Fixed
- **Birds hidden by distant hills** — Birds now disappear behind the far terrain's hills even when distant players, mobs and entities are all turned off, instead of showing through them. (With Voxy, Distant Horizons or Lodistance.)
- **Birds gather over distant lakes, forests and coasts** — Birds now find water, trees, fields and coasts on Voxy's and Distant Horizons' terrain past your render distance, instead of only around you. Flocks and seabirds then settle naturally across the whole landscape.
- **Offline players look right with Fresh Animations** — Offline players no longer look like flying bots or sink into the ground when they walk with Fresh Animations' player model, and they now sit properly on cushions and carpets.
- **Smoother return of mobs as you come close** — When you come back near a mob shown as a distant copy, the copy now hurries to where the real mob stands, so the real one takes over sooner and without jumping, instead of staying hidden for several seconds. The copy of a mob that is no longer there disappears within a second.
- **Mobs kept on servers that only show them up close** — On servers that send mobs only within a few dozen blocks, the mobs you walk away from now stay in the distance instead of vanishing for good. Until now they were only kept if you moved away quickly.
- **Small mobs stay visible with a lower Entity Distance** — With *Entity Distance* below 100%, small mobs such as chickens, cats and wolves now stay visible all the way into the distance, instead of disappearing for a few blocks before Living Horizon took over.

## 0.17.0 — 2026-11-20

### Server

#### Improved
- **Minecraft Comes Alive looks shared** — On a server with Minecraft Comes Alive, Living Horizon now shares the look each player chose in it once they log off. Players with the mod then see offline players as Minecraft Comes Alive draws them, even those who left before they joined.

### Client

#### New
- **Changelog in your language** — The in-game changelog is now available in every language Living Horizon is translated into, instead of only English and French. It follows the game's language, and shows English for any other language.
- **Shareable list of hidden mobs** — `config/livinghorizon.json` now has a `hiddenMobs` list of the mobs never shown in the distance, by id (`"minecraft:pig"`) or for a whole mod (`"cobblemon:*"`). Modpacks can share their choices in one short list, and mobs set to *Hidden* in *Mobs & Entities...* are added to it.
- **Option to hide underground mobs** — The new *Hide Underground Mobs* setting, off by default, stops drawing distant mobs in caves, mines and cellars, which nobody can see from afar. This lightens the game where many mobs live underground and leaves *Max Mobs* to those on the surface. A key, unbound by default, turns it on and off in game; the Nether and the End are never affected.

#### Improved
- **No more regular hitches** — Living Horizon now saves the mobs it remembers and the offline players in the background, instead of making the game hitch once a minute or as players log off.
- **Less graphics work for distant mobs** — Hiding distant mobs behind the far terrain now only works on the part of the screen where they stand, instead of the whole screen. This can give a little more performance, mostly at high resolutions. (Minecraft 1.21.9 and later, with Voxy and shaders, Distant Horizons or Lodistance.)
- **Faster world opening** — Opening a world or joining a server where Living Horizon remembers many mobs no longer freezes the game for a moment: they now appear as soon as they are read.
- **Minecarts keep rolling in the distance** — A minecart still moving when it leaves your view now carries on along its rails, as far as the game would keep it running, instead of stopping where you last saw it. This applies to minecarts turned on in *Mobs & Entities...*.
- **Other mods' monsters shown in the distance** — Monsters from other mods that versions before 0.13 hid by default are now shown in the distance like every other mob. If you had turned some off yourself, hide them again in *Mobs & Entities...*.
- **Smoother arrival on servers** — Joining a server no longer freezes the game for a moment when many distant mobs or offline players are to be shown. They now appear over the first few seconds instead.
- **Smoother teleports and fast flights** — The game no longer hitches when you teleport, change dimension or fly fast with an elytra, while Living Horizon remembers the many mobs you leave behind.
- **Smoother with thousands of distant mobs** — With thousands of distant mobs in view, estimated mobs included, Living Horizon now spends about half as much time on them at every frame, and less to pick which ones to show and to keep them moving. This matters most with a high *Max Mobs* or *Mob Render Distance*.

#### Fixed
- **Ambient Birds' birds keep flying in the distance** — Birds from the Ambient Birds mod now keep flying, wings spread, once they are past your render distance, instead of hanging frozen in the sky.
- **Birds and bats keep their distance** — Bats now fly away when you walk towards them, and no flying bird or bat comes within reach of your hand. You can no longer stand among them and swing through them.
- **Mobs that die far away stay gone** — In single player, a mob that dies out of your sight no longer comes back in the distance for a few seconds.
- **The ender dragon comes alive in the distance** — A distant ender dragon now beats its wings, flies head first and bends its neck and tail as it turns, instead of gliding stiffly with its wings still. It soars in wide circles like the real one, and always keeps its full model rather than a simplified image.
- **Distant mobs stay visible on LAN** — Distant mobs now stay shown after the player hosting a LAN world lowers their *Entity Distance*, instead of vanishing for good or for part of the way as you walk back to them.
- **Lycanites Mobs shown correctly in the distance** — Distant Lycanites mobs such as the Jouste, the Krake or the Bobeko now appear as simplified images instead of vanishing, and hide properly behind Voxy's or Distant Horizons' hills instead of showing through them. (Forge, Minecraft 1.20.1.)
- **Crash on Mac fixed** — The game no longer crashes about a second after you join a world on a Mac or an older graphics card. Distant mobs still hide behind the far terrain there, and *Skip Hidden Mobs* keeps working.
- **Offline players keep their Minecraft Comes Alive look** — With Minecraft Comes Alive, offline players are now shown with the villager or custom player model they chose, instead of their plain skin. Players who logged off before you joined need the mod on the server too.
- **Mobs stay in their own dimension** — Distant mobs are now only shown in the dimension where you saw them, instead of appearing in another one, such as squids floating in the Nether's air.
- **Mobs stay in their boat or minecart** — Mobs riding a boat or a minecart now stay visible in the distance with it, seated inside wherever it goes, instead of vanishing or walking off on their own.
- **Offline players fall off edges** — An offline player you push over a ledge now falls, instead of staying in the air at the edge.
- **Offline players stay where they are** — Offline players near you no longer jump back a few blocks to where they logged off when their skin or outfit appears. They carry on from where they stand, in their new look.
- **Simplification settings apply to players** — Other players online now become simplified images once they are past the *Simplification Distance* or smaller on screen than the *Simplification Size*, as mobs do, instead of keeping their full model as long as the game shows them. This needs *Simplified Players* on, as it is by default.
- **Valkyrien Skies ships' passengers in the right place** — Seats, vehicles and riders on a Valkyrien Skies ship no longer float in the wrong place in the distance, away from their ship. (Minecraft 1.20.1.)

## 0.16.0 — 2026-11-05

### Client

#### New
- **Support for Lodistance's terrain** — Distant mobs and players now hide behind Lodistance's far terrain like behind any hill and blend into its fog, and distant mobs walk on its ground, as they do with Voxy and Distant Horizons. (Minecraft 26.3, with OpenGL, without shaders.)
- **Support for Far View's terrain** — Distant mobs now show and walk on Far View's far terrain and hide behind its hills, instead of being hidden past the render distance. (Forge and NeoForge, Minecraft 1.20.1 to 1.21.4.)
- **Support for Ecstatic's terrain** — Distant mobs now appear on Ecstatic's terrain and walk on its ground, instead of vanishing past the render distance. They hide correctly behind its hills, stay visible in front of them, and blend into its fog. (NeoForge, Minecraft 1.21.1, single player; the fog without shaders.)
- **Simplified mobs lit by shaders** — With a shader pack that reads normal maps, such as Photon with *Normal Mapping* on, the simplified images of distant mobs are now lit by the pack like their 3D models, one side in the sun and the other in shade, instead of looking flat and darker. These images take twice the video memory; the new *Shader Lighting* setting turns this off. (Minecraft 1.21.6 and later.)
- **Push offline players aside** — You can now push offline players out of your way by walking into them. They never push you back, as they only exist in your game.
- **Offline players go fishing** — Offline players near water can now stop at the water's edge to fish for a while. Only one fishes at a time along the same stretch of shore.
- **Offline players take shelter from the rain** — When it rains, some offline players now walk to stand under a roof or a tree they can reach, and wait there until it stops. The others carry on as usual.
- **Offline players gather by the fire** — In the evening, offline players now go and stand in front of a campfire or a furnace they can reach, each in a spot of their own.
- **Offline players chat together** — Two offline players close to each other can now walk up to one another and chat for a while, face to face. Never more than two at once, and only one conversation in the same area.
- **Offline players look back at you** — Offline players now turn to look at you when you look at them from close by. If one is facing you and you crouch, it crouches too.

#### Improved
- **More natural nights for offline players** — Offline players now sleep in a bed they can reach, or stay on their feet, instead of lying down on the ground.
- **Sharper distant ships** — The simplified images of tall boats and vehicles, such as Shippy Ships' sailing ships, are now sharper. The tallest ones, like the galleon, now get one too, instead of always being drawn as a full model, which is heavier on performance.
- **Smoother switch to simplified images** — Distant mobs and players now turn little by little to the angle their simplified image shows them at, as they near the distance where it replaces them, instead of snapping round at the moment of the switch. The change from the 3D model to the image is less noticeable.
- **NeoForge 1.21.4 and 1.21.11 builds easier to find** — The NeoForge builds for Minecraft 1.21.4 and 1.21.11 are now published as beta. They now show up on CurseForge and in launchers that hide alpha versions.

#### Fixed
- **Mobs shown only once with Far View** — Mobs that Far View draws as boxes between 128 blocks and the render distance are now shown only once, instead of twice on top of each other. (Forge and NeoForge, Minecraft 1.20.1 to 1.21.4.)
- **Offline players' names shown on Forge** — Aiming at an offline player now shows their name and when they were last seen, instead of nothing at all. (Forge, Minecraft 1.20.1 to 1.20.4.)
- **Better integration with Bobby** — Distant mobs now stay visible just past the distance the server sends them, over the terrain Bobby keeps, instead of disappearing. Distant players also no longer stop short as they walk out of that distance.
- **Shippy Ships' galleon visible from afar** — The galleon from Shippy Ships now stays visible in the distance where you left it, like the mod's other ships, instead of vanishing as soon as it leaves the render distance.
- **Offline players keep moving in the distance** — Once you walk away, offline players now keep strolling right where they are, instead of walking back to where they logged off or standing perfectly still. Where there is no room for a stroll, they take a few steps, turn around or sit down for a while.

## 0.15.0 — 2026-10-31

### Server

#### New
- **Mobs in unexplored land** — Living Horizon now shows a server's players the mobs the world would place in regions nobody has explored yet, up to 512 blocks around each of them by default. Their positions are estimated from the seed: they may differ, or be gone, once you get there. This feature can be turned off or its range changed in the server settings.
- **Seed sharing** — Servers can now let Living Horizon share the world seed with players who have the mod, so their game can estimate mobs anywhere by itself, through a spyglass or with *Guess Nearby Mobs*. This option is off by default, as knowing the seed reveals everything about the world.

#### Improved
- **Elytra flight visible from afar** — The server now shares which players are flying with an elytra, so they are correctly shown gliding at long range.

### Client

#### New
- **Quick links to Living Horizon** — The mod's settings now offer shortcuts to Modrinth, CurseForge, Nexus Mods and the official website, alongside Discord.
- **Mobs in unexplored areas** — Regions nobody has explored yet are no longer empty: Living Horizon estimates from the seed which mobs the world would place there, as far as *Mob Render Distance* reaches. As they are only estimates, they may differ or be gone once you get there. Available in single player and on servers with the mod; *Show Guessed Mobs* hides them.
- **Estimated mobs through a spyglass** — Looking through a spyglass now shows the estimated mobs of unexplored areas wherever you aim, however far. On a server, the server must share its seed.
- **Estimate the mobs around you** — The new *Guess Nearby Mobs* button shows the estimated mobs up to 1,024 chunks around you at once, and keeps them for your next sessions. On a server, the server must share its seed.
- **Typed values** — Sliders in the mod's settings that end in *Unlimited* (maximum counts and render distances) now accept a value typed on the keyboard, even beyond their last step, and Sodium's page shows it too. Right-click a slider to enter an exact value; the arrow keys also move each slider step by step.
- **Distance preview in the world** — When you hover or change a distance setting, its limit is now shown directly in the world, behind the menu. You can see at a glance how far the chosen value reaches.

#### Improved
- **Optional simplification distance** — *Simplification Distance* can now be turned off. Mobs, entities, players and birds then only become simplified images once they look small enough on screen.
- **Up to 4,000 distant mobs** — *Max Mobs* now goes up to 4,000 before *Unlimited*.
- **Unlimited at the end of the slider** — *Unlimited* now sits at the right end of the render distance sliders, after 4,000 blocks, instead of the left. The sliders now read from the shortest to the longest distance.
- **Confirmation before Unlimited** — The game now asks for confirmation before setting a number of mobs, entities or offline players, or a render distance, to *Unlimited*. These values can sharply lower the frame rate, or even crash the game. On Sodium's page, the question comes with *Apply*.
- **More sensible default limits** — No setting is unlimited by default any more, to avoid large drops in performance: *Player Render Distance*, *Animation Distance* and *Entity Render Distance* stop at 4,000 blocks. The *High* and *Ultra* presets show at most 1,500 and 3,000 mobs, *Ultra* up to 2,048 blocks, and settings left on the old defaults move to the new ones.
- **More natural elytra flight** — Distant players flying with an elytra are now shown with their wings spread in a real flying pose, instead of running upright in the air.
- **Smoother resource pack changes** — Simplified images of mobs are no longer needlessly rebuilt when a resource pack is turned off and back on. Loading resources also no longer freezes the game while they are looked up.
- **Ice and Fire flying mobs seen from every angle** — Ice and Fire's dragons, amphitheres and hippogryphs now have simplified images seen from below and from above, like phantoms and planes, instead of from the side only. They look right as they fly overhead or when you look down on them.
- **Sharper huge mobs in the distance** — Very large mobs now keep their full 3D model while they still look large on screen, instead of turning into a blocky simplified image.

#### Fixed
- **Distant mobs visible on the far terrain** — Distant mobs, estimated ones included, now stay visible on the far terrain, instead of being hidden as if nothing were under them: for a while after a world opens, among thousands of estimated mobs, or when Voxy or Distant Horizons is slow to answer.
- **Distant Horizons' fog on mobs** — Distant mobs now get the same fog as Distant Horizons' terrain and fade with it, instead of standing out sharply against the landscape. (Without shaders.)
- **Ice and Fire mobs correctly simplified** — Distant Ice and Fire dragons now have whole simplified images at their real size, instead of a close-up of their legs or a wing. Dragons of different ages no longer share one image, and sea serpents keep their 3D model instead of a cut-off image.
- **Players who logged off keep their boat** — A player who logged off in the middle of the water no longer loses the boat they rest in once you move away: they keep it near or far, and the next time you play.
- **Players who logged off no longer float in the air** — A player who logged off in the air, gliding with an elytra or falling, no longer goes back up there once you move away: they rest on the ground under them. With Voxy or Distant Horizons, they are on the ground even before you come near.
- **Distant mobs rest naturally** — Distant mobs past the *Animation Distance*, or smaller than the *Min. Animated Size*, now stand where they are, instead of sliding back and forth with their legs still and trembling. They walk on from where they stopped once they are close enough again.

## 0.14.0 — 2026-10-29

### Client

#### New
- **Modded vehicles visible from afar** — Vehicles from other mods, such as Immersive Aircraft's planes, now stay visible past the render distance where they were left, just like boats. Vehicles you ride without steering, such as minecarts, are off by default and can be turned on in *Mobs & Entities...*.
- **Dedicated entity settings** — Boats, vehicles and mannequins now have their own settings, separate from mobs: whether they are shown, how many and how far. Their render distance is unlimited by default.
- **Separate simplification for mobs and entities** — *Simplified Entities* is now two options: *Simplified Mobs* and *Simplified Entities*. Boats, vehicles and mannequins can now become simplified images in the distance too, including when seen from above or below. *Simplified Players* is now independent, and the pictures screen is now called *Picture Preview...*, with a tab for entities.
- **In-game changelog** — A new button in the mod's settings shows what changed in each version, without leaving the game. It is also available on Sodium's page.

#### Improved
- **Notice on incompatible servers** — When you join a server without Living Horizon, or with a version that is too old or too new, a chat message now tells you that distant players will not be shown there. The message does not appear if *Show Players* is off.
- **Mobs & Entities in two tabs** — The *Mobs & Entities...* screen now splits mobs and other entities into two tabs, making them easier to find. Boats and vehicles there are simply shown or hidden.
- **Mob choices applied instantly** — Showing or hiding a type in *Mobs & Entities...* now takes effect at once in the world behind the menu, without closing it.
- **Better organised mob settings** — The *Distant Mobs & Entities* settings are now split into three parts: *General*, *Mobs* and *Entities*.
- **Riders shown more faithfully from afar** — With *Simplified Players*, a distant player in a boat, a minecart or a plane, or on a happy ghast, is now correctly shown from above and below, instead of always appearing side-on.
- **Planes that stay airborne** — A player who logs off mid-flight now stays in the air in their plane, which hovers or circles depending on whether it was still or moving.
- **Picture origin shown** — The *Picture Preview* screen now shows where each picture comes from (shared, reused from an earlier session, new, or not saved), along with when and in which world it was made.
- **Sitting pets stay seated** — Cats, wolves and parrots told to sit now stay seated in the distance, instead of getting up and wandering off.

#### Fixed
- **Mobs & Entities crash fixed** — Opening *Mobs & Entities...* from the title screen no longer crashes the game. (Minecraft 26.x.)
- **Modded vehicles keep their colours** — Distant vehicles and mobs from some mods, such as Immersive Aircraft's planes, now keep their colours as you approach, instead of turning black for a moment.
- **Mounts shown only once** — The mount or vehicle of a distant player, such as a modded plane, no longer appears a second time beside them.

## 0.13.0 — 2026-10-28

### Client

#### Improved
- **Smoother play with distant mobs** — Living Horizon now works out in the background where distant mobs and offline players can walk, without slowing the game down.
- **Smoother rendering with Voxy and Distant Horizons** — The game waits less on the graphics driver each frame when distant mobs are shown on Voxy's or Distant Horizons' terrain. The gain is largest on NVIDIA cards with *Threaded optimization* on.
- **Phantoms and ghasts flying in the distance** — Distant phantoms now circle in the sky and ghasts drift slowly, instead of hanging motionless in the air.
- **Phantoms and ghasts seen from below** — As simplified images, phantoms and ghasts are now also shown from below and above, like birds, not only from the side.
- **Smoother with many distant mobs** — The game no longer wastes time on each distant mob every frame. (Vulkan on Minecraft 26.2 and later, Distant Horizons on Minecraft 26.3.)
- **Every mob visible in the distance** — Monsters, bats, fish and all other mobs are now shown in the distance by default and roam like animals. Endermen, for instance, stay visible across the End. Animals you had hidden stay hidden.

#### Fixed
- **Settings buttons back in order** — The buttons at the bottom of *Distant Mobs & Entities* no longer overlap, and *Restore Defaults* is back to the left of *Done*. (Minecraft 1.20.1.)
- **Freeze when changing dimension fixed** — The game no longer freezes for good when you go through a portal or leave a world. (With Distant Horizons.)

## 0.12.1 — 2026-10-27

### Client

#### Fixed
- **Benchmark command reserved for testing** — `/livinghorizon benchmark` is no longer offered to players: it is only meant for testing the mod.

## 0.12.0 — 2026-10-23

### Server

#### New
- **Server settings** — Server owners can now choose what Living Horizon shares: online players, offline players and mobs, up to what distance, only between teammates if you wish, and which players or dimensions are never shared. The settings live in `config/livinghorizon-server.json` and apply within seconds, without a restart.

#### Fixed
- **Joining Forge servers fixed** — Players with the mod are no longer kicked from a Forge server that has it too, with the message "mismatched mod channel list". (Forge 1.20.2 and later.)
- **Spectators and invisible players stay hidden** — Players in spectator mode or under invisibility are no longer visible to others in the distance, nor where they logged off.

### Client

#### New
- **Discord button** — The mod's settings now let you join Living Horizon's Discord in one click, to ask a question or report a problem. The button is also available on Sodium's page.
- **Mod version shown** — The settings screens and the debug panel now show the Living Horizon version, handy to include in a bug report.
- **Simplification Size** — A new setting turns distant mobs, players and birds into simplified images as soon as they look smaller than a set size on screen (10 pixels by default), even before the *Simplification Distance*. Whatever is barely visible becomes much cheaper to draw.

#### Improved
- **Smoother with hundreds of distant mobs** — *Skip Hidden Mobs* costs much less with many distant mobs and players on screen, and Living Horizon does less work behind the scenes.
- **Minecraft 1.21.1 builds easier to find** — The Fabric, Quilt and NeoForge builds for Minecraft 1.21 and 1.21.1 are now published as beta. They now show up on CurseForge and in launchers that hide alpha versions.
- **Fewer stutters with crowds in the distance** — Distant mobs, players and birds now ask the game for much less memory each frame, which reduces stutters.
- **Smoother with many birds** — Birds behind you or too small to be seen are no longer drawn.

#### Fixed
- **Distant mobs always on solid ground** — Distant mobs are no longer drawn where no terrain is visible under them yet, whether the game's, Voxy's or Distant Horizons'. They no longer float in the void.
- **Single player worlds open again on Forge** — A single player world no longer closes as soon as it opens with the message "mismatched mod channel list". (Forge 1.20.2 and later.)
- **Distant mobs stay on Distant Horizons' terrain** — In single player, distant mobs no longer disappear or stop walking on Distant Horizons' hills and mountains.

## 0.11.0 — 2026-10-22

### Server

#### Improved
- **Lighter on large servers** — The server spends less time choosing which skins and mounts to send to each player. Long lists of offline players are now sent in several parts, instead of one message too large for the game.
- **Update required for players** — On a server running this version, players need it too to see distant players.

#### Fixed
- **Offline players file protected** — If the server cannot read `livinghorizon-positions.dat`, the file is no longer overwritten: it is kept as `livinghorizon-positions.dat.bad` and the console reports it.

### Client

#### New
- **Benchmark** — The `/livinghorizon benchmark` command measures what each part of the mod costs in your world and produces a report to attach to a bug report.
- **Benchmark world** — `/livinghorizon benchmark world` creates a test world that is always the same, filled with mobs and offline players, so measurements can be compared from one run to the next. Your own world is left untouched.
- **Max Offline** — A new setting limits how many offline players are shown at once, nearest first (256 by default). The others reappear as you get closer.

#### Improved
- **Clearer message when versions differ** — When the mod on the server is too old or too new for yours, the log now says which one to update, instead of claiming the server does not have the mod.
- **Smoother with many offline players** — Offline players cost less to show in the distance, and those hidden behind hills are no longer drawn.

#### Fixed
- **Player Render Distance respected** — Offline players are no longer shown beyond the *Player Render Distance*.
- **Distant mannequins with their real skin** — Distant mannequins now keep the skin they wear up close, whether a player's or a resource pack's, instead of turning into Alex. (Minecraft 1.21.9 and later.)
- **Water preserved under Photon with Distant Horizons** — With *Hide Behind Far Terrain* on, water now stays normal when a distant mob or player is in view, instead of revealing its bare, shadowed bed. (Minecraft 26.3.)
- **Mannequins' capes hang naturally** — Capes of distant mannequins now hang down as they do up close, instead of sticking up in the air. (Minecraft 1.21.9 and later.)
- **Settings file protected** — A mistake made while editing `livinghorizon.json` by hand no longer wipes the file: it is kept as `livinghorizon.json.bad` and the log reports it.
- **Saves protected** — If the game closes during a save, the files of remembered mobs (those Living Horizon keeps showing in the distance) and offline players are no longer damaged. A damaged file is kept as `.bad` instead of being emptied.
- **Smoother joining on large servers** — Joining a server with many offline players no longer makes the game stutter.
- **Damaged images rebuilt automatically** — A damaged saved simplified image can no longer make the game run out of memory: it is simply rebuilt.
- **Distant mobs visible in front of Distant Horizons' terrain with Oculus** — With Oculus and a shader pack, distant mobs no longer disappear behind Distant Horizons' terrain, an issue that got worse as the render distance increased. (Forge.)
- **Natural head movements with Entity Culling** — Distant mobs and players no longer shake their heads very fast with Entity Culling, alone or with Chloride.
- **Players in the right modded dimension** — Online and offline players in a modded dimension such as the Aether are no longer shown in another, such as the Twilight Forest. On a server, the server needs this version too.
- **Voxy's fog on mobs** — Distant mobs on Voxy's terrain now get the same fog as the landscape around them, instead of fading to the fog colour beyond a certain distance. (Without shaders.)

## 0.10.0 — 2026-10-20

### Server

#### New
- **Living Horizon on servers** — The mod can now be installed on the server too: put the jar in its `mods` folder (Fabric, Quilt, NeoForge or Forge, plus the Fabric API on Fabric and Quilt). No setup is needed, and it replaces the data pack.
- **Players shared in real time** — The server sends the position of every player five times a second to the players who have the mod, with their skin, armour, held items and mount.
- **Mobs shared** — The server sends the mobs within 2,048 blocks of each player, even ones that player has never met: animals, villagers, golems, allays and boats, with their saddle, harness, armour or chest.
- **Offline players shared** — The server remembers where each player logged off, their appearance, their mount and when they left, and passes it on to everyone, including players who join later.
- **Players from before the install** — On first start, the server finds the players the world already knew and shows them as offline players where they left, with their gear.
- **Saved with the world** — Everything is saved in `livinghorizon-positions.dat`, in the world folder. A mob is forgotten when its chunk loads and it is no longer there.
- **LAN worlds** — A world opened to LAN shares everything through the host's game, with nothing else to install.
- **Players without the mod welcome** — Players without the mod can still join; the server simply sends them nothing.
- **Same version on both sides** — Keep the mod up to date on both the server and the players' games: a game that cannot understand the server's messages ignores them.
- **Privacy: positions are shared** — The server shares every player's position with all the players who have the mod, at any distance. Make sure to let your players know.
- **No more data pack** — The data pack is no longer used: remove it from the world's `datapacks` folder. Coordinates no longer go through the scoreboard, so they no longer show in scores or in the sidebar.

#### Fixed
- **Players kept when a world closes** — When a server or a LAN world closes, the players still in it now stay where they were as offline players, instead of vanishing.

### Client

#### New
- **Every player, at any distance** — On a server with the mod, you now see all the other players, wherever they are, with their skin, armour, held items and mount.
- **Mobs you have never met** — On a server with the mod, distant mobs appear right away, even ones you have never been near, with their saddle, harness, armour or chest.
- **Offline players visible as you join** — You see offline players where they left, with their appearance and their mount, even if they left before you arrived.
- **Offline players in single player** — In single player, the offline players of your world appear as soon as you open it, including those from before the mod was installed.
- **A living horizon from the start** — Distant mobs and offline players appear as soon as you join, not a few seconds later.
- **Servers without the mod** — You can still join them. Only the mobs you have already met are shown there, with no distant players, and the log mentions it once.

#### Improved
- **More reliable distant player positions** — Distant players are now placed only from the positions the server shares. The locator bar estimate is gone, along with the *Lost Player Time* setting.

#### Fixed
- **Happy ghasts shown correctly** — A distant player riding a happy ghast no longer has a second ghast beside them, and one who was already riding when you joined no longer floats in mid-air.
- **Happy ghasts stay where they were left** — When a distant player gets off their happy ghast, it now stays where it was left, instead of reappearing where it was first ridden and flying back.
- **New skins visible from afar** — A distant player who changes skin now appears with the new one, instead of keeping the old one in the distance.
- **Players seated properly in boats** — A player who logs off in a boat now faces the same way as the boat, instead of sitting sideways.
- **Riders seated properly** — A distant player riding a horse now sits correctly, as a full model or as a simplified image, instead of sinking into the horse or sitting on its rump.
- **Only one horse after logging off** — A player who logs off on a horse no longer leaves a copy of the horse beside the one they sit on.
- **Website and Issues buttons working** — The Website and Issues buttons on the mod's page in Mod Menu are no longer greyed out. (Fabric.)

## 0.9.0 — 2026-10-19

### Fixed
- **Offline players out of the water** — Players who logged off while swimming are no longer left standing in the water: they wait on the nearest shore, or row a boat if the shore is more than 10 blocks away.
- **Water intact around boats** — Water no longer turns clear around offline players rowing a boat with a shader pack such as Photon, and no longer disappears in a large square around distant boats.
- **Rowers keep their boat** — In the distance, offline players rowing a boat now keep their boat while their simplified image is being made, instead of appearing to stand without it.

## 0.8.0 — 2026-10-18

### New
- **Simplified Players** — A new setting lets you keep distant players as full 3D models, while distant mobs still become simplified images.
- **12 new languages** — Living Horizon is now available in German, Spanish, Italian, Brazilian Portuguese, Polish, Turkish, Russian, Ukrainian, Simplified and Traditional Chinese, Japanese and Korean.
- **Settings in Sodium** — Living Horizon's settings can now be reached from Sodium's *Video Settings*, on their own pages. (Sodium 0.8 and later.)
- **Offline players with their mount** — A player who logged off on a horse, in a boat or on a happy ghast keeps riding it around where they left.
- **Video memory control** — The *Simplified Entities...* screen shows how much video memory the images of mobs and players use, and two new settings set a limit for each.

### Improved
- **Clearer settings** — Every setting now has a clearer name and description, in the style of Minecraft's own options, and distance sliders use the full width of the screen.
- **Livelier offline players** — Offline players now walk a small loop around where they left. In the distance, they become simplified images like online players, whether standing, sitting, lying down or in bed.
- **Lighter riders** — In the distance, a player riding a horse, a boat, a minecart or any other mount now forms a single simplified image with it, much lighter than two full models.
- **Smoother large crowds of players** — Beyond about a hundred distant players, the extra ones now become simplified images too instead of staying full models.

### Fixed
- **Mobs hidden properly after resizing** — Distant mobs no longer show through hills, including Voxy's and Distant Horizons', after the game window is resized.
- **Players and slimes visible with Distant Horizons** — Distant players, offline players and slimes no longer disappear under Distant Horizons' terrain. (Minecraft 26.1 and later.)
- **Whole mobs in front of Distant Horizons' terrain** — Distant Horizons' terrain no longer cuts into mobs and players standing in front of it. (Minecraft 26.1 and later.)
- **Steady mobs with view bobbing** — With view bobbing on, distant mobs no longer flicker and Distant Horizons' terrain no longer wavers in front of them with every step. (Minecraft 26.1 and later.)

## 0.7.0 — 2026-10-17

### New
- **Offline players come to life** — Offline players now live around where they left: they stroll, sit, look at chests and workstations, go to bed at night, and raise their shield when hit or when monsters come near. They always stay on the ground: they fall if you break the block under them, and climb back up if you bury them.
- **"(offline)" tag** — Offline players show *(offline)* after their name, and looking at one up close tells you when they were last seen. Their names, or the offline players themselves, can be hidden in the settings.
- **3D birds** — Birds are now small 3D models like Minecraft's mobs: they flap their wings, stand on their legs when perched and become simplified images in the distance. The *Bird Style* setting brings back the old 2D birds.
- **Hmm…** — I thought I saw something in the sky. 😉

### Improved
- **Hopping mobs** — Distant slimes, magma cubes and rabbits now move by hopping instead of sliding. Slimes squash when they land, and chickens flap their wings when stepping down a block.
- **More natural ducks** — Ducks now fly off together as you approach and land farther along the water, instead of leaving for good.

### Fixed
- **Mobs animated through a spyglass** — Distant mobs seen through a spyglass now count at their zoomed size and keep walking when they look big, instead of freezing. (*Min. Animated Size* setting.)
- **Mobs animated as a world opens** — Distant mobs now walk and graze from the start, instead of turning on the spot.
- **More natural perched birds** — Tits, robins and ducks no longer dart back and forth, float in the air, stand inside leaves or swim on dry land. Ducks no longer appear right next to you only to fly away and now swim around their pond, and robins and tits hop more and fly off less.
- **Correct license in the mod list** — Living Horizon now shows the correct license in the mod list. (Forge, NeoForge and Quilt.)

## 0.6.0 — 2026-10-15

### New
- **Mob images kept** — Simplified images of distant mobs are now saved to disk and reused from one session to the next. They are only rebuilt after a change of resource packs, mods or game version.

### Improved
- **New icon** — Living Horizon has a new pixel-art icon: Steve in the foreground, surrounded by mobs, villagers, players and birds all the way to the mountains.
- **More consistent distant mobs when you come back** — In single player, distant mobs much less often teleport or rush to another spot when you come back: they follow the real mobs as long as the game keeps moving them, and disappear when those die.

### Fixed
- **Simplified Entities screen without flickering** — Two mobs with the same name, such as villagers in different outfits, no longer flicker by swapping places.
- **Cleaner mob images** — Distant mobs shown as simplified images no longer have thin dark lines or black specks along their top or bottom edge. Long mobs such as horses and sniffers are no longer cut off at the muzzle or tail.
- **GeckoLib mob crash fixed** — A world no longer crashes a few seconds after loading when a mob from a GeckoLib mod has been seen in the distance. These mobs are not shown in the distance yet. (Minecraft 1.21.2 to 1.21.8.)
- **Faster Simplified Entities** — The game no longer slows down with simplified images: the same mobs are no longer redrawn over and over, and mobs that look alike share a single image.

## 0.5.0 — 2026-10-13

### New
- **Simplified Entities on Minecraft 1.20.1 to 1.21.8** — Very distant mobs and players can now be shown as simplified images on these versions too, which takes a lot of work off your computer.

### Fixed
- **Startup crash fixed** — The game no longer crashes on startup with the mod installed. (Forge, Minecraft 1.20.1 to 1.20.4.)
- **Simplified Entities screen crash fixed** — Opening the *Simplified Entities* screen no longer crashes the game. (Minecraft 1.21.8 and earlier.)
- **Mobs present as you join** — Mobs that were already around you when you joined a world now appear on the horizon too, not only the ones you meet afterwards.

## 0.4.2 — 2026-10-12

### Fixed
- **Steady mobs as they walk away** — A mob that moves beyond your render distance no longer disappears for a moment or turns to face another way.
- **Mobs fully hidden by hills** — A villager's clothes, a held item or a slime's outer layer no longer show through Voxy's or Distant Horizons' hills while the rest of the mob is hidden. (Minecraft 1.21.9 and later, with Distant Horizons, or with Voxy and shaders.)

## 0.4.1 — 2026-10-11

### Fixed
- **Mobs properly lit with shaders** — Distant mobs shown as simplified images are now lit by the sun like full models, instead of being too dark with Iris shader packs such as Photon or Complementary.
- **Terrain shadows fixed** — Distant mobs no longer show the shadow of the terrain behind them, and Voxy's water looks as it should. (Photon with Voxy.)
- **Mobs properly hidden by hills** — Distant mobs no longer show through Voxy's or Distant Horizons' hills. (Minecraft 26.1; Minecraft 26.2 and later with shaders, including older Iris versions; Minecraft 26.3 with Iris installed.)
- **Voxy 0.2.18 support** — The mod works with Voxy 0.2.18 again: distant mobs stand on its terrain and no longer show through its hills.
- **Mobs hidden by the right hills with Distant Horizons** — Distant mobs are no longer hidden by hills that are not in front of them. (Distant Horizons with Photon's TAAU upscaling.)

## 0.4.0 — 2026-10-10

### New
- **Automatic first scan** — The first time you enter a single player world, Living Horizon looks for the mobs within 32 chunks right away, so the horizon is alive from the start. The scan radius is now set in chunks, like the render distance.
- **Animation settings** — Two new sliders stop animating distant mobs that are too small on screen or too far away, to improve performance.
- **Quality presets** — Low, Normal, High and Ultra set in one click how many distant mobs and birds you see, and how far. High and Ultra are meant for powerful computers.

### Fixed
- **Mobs keep their colours** — Without shaders, distant mobs no longer turn black for a moment, mostly while flying.

## 0.3.2 — 2026-10-09

### Fixed
- **Mobs hidden by the right hills with Photon** — With Voxy and Photon's TAAU upscaling, distant mobs are once again hidden by the right hills.

## 0.3.1 — 2026-10-08

### Fixed
- **Hills taken into account on older versions** — Distant mobs now hide behind Voxy's and Distant Horizons' hills, as on newer versions, instead of showing through them. (Minecraft 1.20 to 1.21.4.)
- **Older Distant Horizons versions supported** — The mod now works with Distant Horizons 2.3 and later, not only the latest version.

## 0.3.0 — 2026-10-07

### New
- **Vulkan support** — With the game's Vulkan renderer, distant mobs also hide behind hills, including Distant Horizons' terrain.

### Fixed
- **Mobs visible in front of Distant Horizons' terrain** — Without shaders, distant mobs no longer fade into Distant Horizons' terrain.
- **No more smears at the edge of the render distance** — The smears that appeared where the normal render distance ends are gone. (Distant Horizons.)

## 0.2.0 — 2026-10-07

### New
- **Distant mobs** — Animals, villagers and golems stay alive on the horizon beyond your render distance, grazing and wandering around.
- **Distant players** — Other players keep walking in the distance, with their skin, armour and mount. Players who log off stay asleep where they left.
- **Birds** — Flocks, geese flying in a V, gulls over the coast and bats at night bring the sky to life.
- **Distant Horizons support** — Living Horizon works with Distant Horizons as well as Voxy.
- **Mobs hidden behind hills** — Distant mobs behind a hill are not drawn, which keeps the game running smoothly.
- **Simplified images in the distance** — Very distant mobs and players can be shown as simplified images, much lighter to draw when there are many of them. This option is off by default.
- **Scan of the surroundings** — In single player, a scan reads the mobs of the chunks around you so they appear on the horizon right away, without having to meet them first.
- **Settings screen** — A *Living Horizon...* button in the Options menu opens the mod's settings, with the world still visible behind them, and a button restores the defaults.
- **Quilt, NeoForge and Forge** — Living Horizon also runs on Quilt, NeoForge and Forge, from Minecraft 1.20 to 26.3.
- **Mod logo** — Living Horizon now has a logo in the mod list and the data pack list.
