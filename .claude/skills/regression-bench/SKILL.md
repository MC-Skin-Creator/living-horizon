---
name: regression-bench
description: Run Living Horizon's regression bench on the dedicated test machine - the same frozen scene played on every game version, alone and with Sodium, Voxy, Distant Horizons and shader packs, its pictures and numbers compared with the reference the user validated and between versions, in an HTML report. Use when a develop build (or a branch) must be checked across versions before release, when the user asks whether a build draws the same as the previous one, to approve new baselines, or to set the bench machine up.
---

# The regression bench

`game-test` checks one scenario on one version, headless, without Voxy. The bench is its
companion for a machine with a graphics card: it plays one fixed scene on **every Fabric node
from 1.21.4** (the client game tests need it), once per **profile** (a set of mods), and
compares each picture with the validated reference on that same machine (see *References*).

Each game version is installed once, by its node's Gradle run. A profile is only a folder of
mods Fabric Loader is pointed at (`-Plh.mods`, `fabric.addMods`): `instances/<node>/<profile>/mods`.

## The scene

The `regression` scenario (`src/gametest/.../scenario/RegressionScenario.java`) builds the
benchmark world (`debug/BenchmarkWorld`): flat, a fixed seed, every mob summoned with a fixed
id and no AI, a wall and a hill, the crowd of players who logged off. Nothing moves: copies'
animations held from the start, players who logged off standing, no birds, easter eggs,
clouds, view bobbing or hand. From the same spot it takes, in three views (`near`: render
distance 12; `far`: 5, where Voxy's or Distant Horizons' terrain takes over; `zoom`: field of
view 30), models, simplified mobs, debug boxes and the depth view. Each shot is taken twice a
second apart (`-a`, `-b`): pixels that differ between the two (a pack's water, its temporal
noise) are left out of the comparison. It logs numbers as `LHTEST regression stat key=value`
(mobs remembered, shown, sent, models, pictures, far depth state, each link's status).
`--perf` runs `/livinghorizon benchmark` at the end; its frame times are compared too.

Any change to that scene changes every picture: approve a new baseline in the same pull request's
follow-up run, and say so.

## Set the machine up (Windows)

```
powershell -ExecutionPolicy Bypass -File .claude/skills/regression-bench/setup.ps1 -Schedule -KeepAwake
```

It installs what is missing (Git, Python 3.12, Temurin 21 and 25), makes `LH_BENCH_HOME`
(`~/living-horizon-bench`) with a Python environment, a clone for the scripts (`tool/`, kept
on `develop`) and `bench.cmd`. `-Schedule` runs `bench.cmd watch` every hour while the user
is logged on: it plays the newest develop pre-release once. The game needs an unlocked
desktop: a locked or sleeping screen draws nothing (`-KeepAwake`).

## Use it

```
bench.cmd run                               # the newest develop pre-release, every node and profile
bench.cmd run --ref develop --nodes 26.3.x  # a branch, tag or commit; some nodes
bench.cmd run --ref local                   # this checkout as it is (on a dev machine: `LH_BENCH_HOME/venv` python, bench.py)
bench.cmd run --profiles voxy,voxy-photon --perf
bench.cmd compare last                      # compare again (after changing bench.toml)
bench.cmd approve <run>                     # the user validated it: it becomes the reference
bench.cmd mods [--update]                   # the mod versions pinned per game version
bench.cmd runs
```

A full run is 8 nodes by up to 7 profiles, a few minutes each: hours. Results are in
`LH_BENCH_HOME/results/<build>_<time>/`: `report.html` (open it, or publish it as an
artifact for the user), per node and profile the pictures, `stats.json`, `game.log`.

The report's summary is a grid, node by profile: **same**, **changed** (a picture, a number or
a frame time differs from the baseline), **new** (no baseline yet), **failed** (an assertion,
a crash, or stopped). A changed picture shows baseline, this run, and what changed in red,
circled in yellow. Below, every node against the reference node (1.21.11): the game draws a
little differently between versions, so those pictures only inform, but the mob counts must
match, since the world is the same.

## Report to the user

Write the report in the user's language (French for this project's author), captions included.

**Every compared run gets an artifact** the user opens to look at the pictures: for each game,
profile and viewpoint, the reference, the build tested and the difference, side by side, with a
full-size view that flips between them. Build it, copy it into the scratchpad (the Artifact tool
only sends files from there or the working folder) and publish it, then give the link first:

```
LH_BENCH_HOME/venv/Scripts/python.exe artifact.py <run> --lang fr   # writes results/<run>/_artifact/
```

Publish `index.html` as `file_path` with `root` the copied folder and `files` mapping every
`shots/*.webp` and `zoom/*.webp` to itself. One picture holds a viewpoint's three panels, so a run
of ten games, four profiles and ten viewpoints stays under the 255 files of one publish.

Every report also ends with the pictures in the conversation, so that the user judges each change for themselves: send
(`SendUserFile`, display `render`) the review sheet of every node and profile compared,
`results/<run>/_review/<node>-<profile>.png` (every viewpoint in three columns: the reference,
this run, and what the bench found, in red framed in yellow, in blue what moves by itself and is
left out; a viewpoint that changed is framed in red). The third column lets the user check the
bench as much as the build: a difference they see that is not in red is one the bench missed,
and is worth a word back. Then the enlarged zooms of the changed ones,
`_diff/<node>/<profile>/<shot>-zoom.png`. Say for each change what it shows (a mob more or less,
a mob through the far terrain, a picture not settled) and whether it looks like a regression or
an intended change, with the changelog entry that explains it when there is one. Never sum up a
comparison without them.

## References: always compare with the validated reference

**A run is compared with the validated reference, never with whatever ran last.** The reference is
a run the user looked at and said was right, approved with `approve <run>`: its pictures, frame
rates and stats are copied into `LH_BENCH_HOME/baselines/<node>/<profile>/`, and `approved.json`
there names the run and the release. It stays on this machine until the user validates another one.
Every other folder of `results/` is only a run (a comparison played once, a test of a pull request,
a release played to compare with): never take one of those as the reference, even the newest,
even one of a release.

**Whenever the user asks for a regression bench** ("test v0.19.0-dev.842", "is this build
stable"):

1. `bench.cmd status` (on a dev machine `~/living-horizon-bench/venv/Scripts/python.exe bench.py status`: the system Python lacks its packages): which release each game and
   profile is validated against, and which have no validated reference. Tell the user.
2. Play the build asked for alone (`jars --tag <build>`, no `--against`): it is compared with those
   references. A game or profile with no validated reference comes out **new**: say so, never
   approve anything to fill the gap, and never present a comparison with another run as a result
   against the reference.
3. Approve only when the user says this run is the new reference (`approve <run>`, which replaces
   the old one for the games and profiles it played). Never on your own, not even a full release.

`--against <run>` compares two runs directly (a pull request against the release it starts from,
say); it is a side comparison, kept apart from the reference, and reported as such.

```
bench.cmd status
bench.cmd jars --tag v0.19.0-dev.842 --fps 10 --parallel 4 --memory 2G
bench.cmd approve v0.16.0-jars_20261123-2157    # only once the user validated that run
```

Validated today: **v0.16.0, profile vanilla, the ten `jar_nodes`**, from run
`v0.16.0-jars_20261123-2157` (approved 2026-11-28). Sodium, Distant Horizons and Voxy have no
validated reference yet. The runs of 22 and 23 November comparing v0.16.0 with pull request #366
are kept in `results/` as a comparison only.

`jar_nodes` in `bench.toml` is the default list of games (ten: one per change in how the mod
draws, and Forge and NeoForge where most players are); list it to the user before a run and
take `--nodes` from what they keep.

The references stay in `LH_BENCH_HOME`, never in the repository (`baselines/<node>/<profile>`,
`approved.json` says which release): they belong to this machine's graphics card and driver, and
are made again after a driver update, a pinned mod update (`mods --update`) or a change of the
scene (`SCENE_VERSION`). Copy `pins.json` from one machine to another for the same mod versions;
pictures and frame rates still differ between machines, so references never move between them.

## Read a result

What the comparison counts: pixels steady in both pairs (what moves by itself is left out) that
differ beyond the tolerance, in blobs of two by two at least (an edge shifted by one pixel draws
thin lines, which do not count). Each blob is framed in the strip and enlarged, before and after,
below it. New warnings and errors of the mod in the game's log (its own lines, or an error whose
stack goes through it) mark a result changed too: a bug does not always show in a picture, and
this is how the far depth failing on Forge 1.20.1 in v0.17.0-dev.744 showed first.

- **The first run** has nothing to compare with: show the user its pictures; approve it only once
  they say it is the reference.
- **Changed, expected** (the pull request changed what is drawn): show it; approve it as the new
  reference only if the user says so.
- **Changed, unexpected**: a regression. Find the build that brought it (`run --ref <older tag>`),
  then debug on that node with `game-test` or `runClientGameTest` and the instance's mods
  (`-Plh.mods=<LH_BENCH_HOME>/instances/<node>/<profile>/mods`).
- **Changed between releases on purpose**: v0.17's mobs appear as soon as they are read, where
  v0.16's came a few seconds later; with 20 s to settle, 0.17's pictures hold mobs past the render
  distance that 0.16's do not yet. Look at the zooms before calling a change a regression.
- **Noise** (a picture changed run after run with nothing changed): run the same build twice,
  then raise that profile's `noise`, `tolerance` or `max_pixels` in `LH_BENCH_HOME/bench.toml`.
  Keep `max_pixels` small: a distant mob is a few dozen pixels.
- **Mods are pinned** (`pins.json`): a Sodium or Voxy update never passes for a change of ours.
  `mods --update` takes the newest; the next run then differs where a mod did, and is approved
  once checked. The loader version a mod asks for (Distant Horizons and Iris on 26.3 ask for
  0.19.5) is passed as `-Plh.loader`.

## Published jars, any loader (`jars`)

The client game tests stop at Fabric 1.21.4. For Forge, NeoForge, Quilt and older Fabric
nodes, and to test exactly what a release ships, `jars` plays a release's own jar (`jars.py`),
the way a flight simulator's visual tests do: one launch, the camera taken to fixed places, a
pair of pictures at each, compared.

```
bench.cmd jars --tag v0.16.0 --tag v0.17.0-dev.757 --nodes 1.21.11 --profiles dh,voxy
bench.cmd jars --tag v0.16.0 --tag v0.17.0-dev.744 --nodes 1.20.1-forge --profiles vanilla,dh
bench.cmd jars --tag v0.17.0-dev.760 --nodes 1.21.11 --profiles dh,voxy --against v0.16.0-jars
bench.cmd compare last --against v0.16.0-jars
```

Several `--tag`s run side by side and the later ones are compared with the first. As many games
run at once as the memory holds (4 GB each), or `--parallel N`. Measured on Fabric 1.21.11, two
releases, Distant Horizons and Voxy, each without then with shaders: 4 minutes, 2 games at a
time (about 2 minutes a game: 50 s to start, 20 s to settle, 16 viewpoints in 40 s, the shaders
10 to 15 s to compile). The first run of a game version adds its install, its world (3 minutes)
and the far terrain's warm-up (4 minutes for Distant Horizons, under one for Voxy), once.

- **Installed once** per game version and loader in `LH_BENCH_HOME/minecraft`
  (minecraft-launcher-lib, offline, the game's own Java runtime), the loader's newest version
  pinned in `pins.json` like the mods.
- **The scene** is a world each game version's vanilla dedicated server makes once and the bench
  keeps (`worlds/<game>/scene-<SCENE_VERSION>`): flat, time and weather stopped, rows of mobs
  without AI that never animate on their own (no horse, no chicken: a horse without AI still
  grazes), fixed ids, a wall and a hill, and north of the spot a mountain with a crowd of iron
  golems right behind it and a control group beside it. Each launch opens a copy of it. Change
  `scene_commands` and raise `SCENE_VERSION` together: every world is made again once, and
  every reference must be taken and approved again.
  Every mob faces the same way (`MOB_YAW`, towards the cameras), and every kind of the scene is
  listed in the mod's *still* kinds (`stillMobTypes`): its copies far away neither stroll nor turn
  on the spot. No setting stops a copy's head from glancing about: the simplified pictures are
  baked head straight, but a copy drawn as a model may turn its head, and a head alone that
  differs is no regression.
  In the Nether, a tunnel of blackstone closed on every side (no lava runs into it), rows of
  Nether mobs and a wall across half of it; in the End, a causeway north of the main island,
  out of reach of the dragon's fight (no dragon ever comes), the island behind its rows.
- **The far terrain is built once**: Distant Horizons and Voxy keep theirs in the world
  (`data/DistantHorizons.sqlite` per dimension, `voxy/`). A warm-up launch per game version and
  far terrain mod builds it (Distant Horizons while the game runs and a tour of the scene; Voxy
  with `/voxy import world`), closes the game cleanly so that it is written out, and keeps it in
  `worlds/<game>/scene-<V>-far/<mod jar>`; every launch after starts with it. Each dimension is
  built in turn: Distant Horizons a minute at the first stop of each tour, Voxy one import per
  dimension (its command reads the region files of the dimension the player is in).
- **Settings written before launch**: `config/livinghorizon.json` (no birds or easter eggs,
  every copy still), `options.txt` (render distance 4, so that the mountain is far terrain from
  its viewpoints; no clouds; minimal particles, so no ash or spores in the Nether; lightning
  flashes hidden, the End's sky too; `graphicsPreset:"custom"`, or from 1.21.11 the preset puts
  its own render distance and clouds back), Distant Horizons' clouds off.
- **Six viewpoints** (`VIEWPOINTS`): `mountain-high` (the crowd behind the far mountain, all of
  it hidden), `spot` (the rows from the ground: the game's, then the mod's; the frame rate),
  `overview` (every row at once), `hill` (the rows behind a hill of the game's hidden), `nether`
  and `end`. Ten pictures a game: the six without shaders, then `SHADED` (`mountain-high`, `spot`)
  again with each pack, where a pack changes what the mod does; never the other dimensions.
- **One launch per far terrain mod**: the game opens the world itself (Quick Play), the bench
  hides the interface (F1), goes through `VIEWPOINTS` without shaders, then through `SHADED` again
  with each shader pack of the profile, changed in the same game: the agent (below) sets it as
  the pack Iris holds, shaders off, and Iris's toggle key (K) turns them on with it, again until
  its log says "Using shaderpack" with that pack (Oculus the same). Iris's reload key would read
  its settings file again, but only with its debug options on, which change how it draws. The
  pictures of a pack are named after it: `photon-mountain-high`, `complementary-spot`.
- **At each viewpoint** an `/execute in <dimension> run tp` typed in the chat (checked in the
  game's log; Escape and again when a menu was in the way), a few seconds more when it changes
  dimension, then the game's own screenshots (F2) one after another until
  three in a row match: the chunks round a place just reached take a second or two to draw, in
  bursts. Without shaders a match is under 0.3 % of pixels moved, with them under 3 % (Photon's
  grain).
  A picture with sky at the bottom, where every Overworld viewpoint has its ground, is still
  loading however still it stands (a chunk not yet received): waited for up to 40 s more
  (`holes`).
  Before that, the bench asks the game through the agent whether the chunks round the camera are
  built (`terrain_ready`): Sodium's build queue empty for a second, 40 s at most, what it said
  kept in `run.json` (`terrain`). Its count of sections is the size of its grid, the same
  everywhere: a chunk not received yet is not waited for, only one not built. Without it the far terrain
  mods fill the hole of a chunk not built yet with their own terrain, still enough to pass for a
  finished picture, and two releases differ where nothing changed.
- **Hands off**: the keys go straight to each game's window, and a guard (`Guard`) checks every
  window of every game four times a second for as long as it runs: turned off for the mouse and
  the keyboard (again if it comes back on), put back where and as it was if moved, resized or
  minimised anyway (the taskbar, Windows' shortcuts), and made to give up the focus so that the
  game lets the mouse go (a window just opened takes it, and the camera would follow the mouse).
  The machine stays usable; the windows may overlap. Windows only.
- **The agent**: every game starts with `agent/BenchAgent.java` as a Java agent (`-javaagent`,
  compiled with the JDK's `javac` into `LH_BENCH_HOME/tools` the first time), which reads lines
  from a folder in the game's directory. It sets Iris's next pack, by Iris's own class names,
  which no version obfuscates. And from 26.3 the window is SDL's, which hands a key to the game
  only while it is the desktop's foreground window: one game at a time, and the user's mouse
  with it. There the agent types instead, handing the keys and text to the game's keyboard
  handler on its own thread, by the game's names: from 26.1 the game is not obfuscated.
- **Mods**: the profile's from Modrinth (the Fabric API added on Fabric and Quilt; Embeddium and
  Oculus stand for Sodium and Iris on Forge), in versions that fit together (`fitting` reads each
  jar's dependencies: Voxy 0.2.16 takes Sodium 0.8.12 at most), plus `speed_mods` where they
  exist. A mod asking for a newer Fabric Loader gets it.

### Frame rates (`--fps`)

`jars --fps 10` reads the game's frame rate for 10 s at one view, the spot (facing the rows: the
near ones the game's, the far ones the mod's), after its pictures without shaders. The agent reads
the game's own counter once a second (`Minecraft.fps`, `class_310.field_1738` under Fabric's
names, `f_91021_` under old Forge's, what the debug screen shows), and `run.json` keeps its mean, low, high and every second
(`fps`). The games run with no frame cap then (`maxFps:260`; 60 otherwise, to share the graphics
card). One game measures at a time: it waits for the others to finish the viewpoint they are at
(`busy`), they start no other meanwhile, and the guard minimises their windows, so that they
draw next to nothing. Frame rates
compare between versions and builds on one machine only. For the mod's own cost (mod off, each
part alone) use its `/livinghorizon benchmark`, which takes minutes.

## Profiles

`bench.toml` next to this file; `LH_BENCH_HOME/bench.toml` overrides it per key. A profile
names Modrinth projects (`mods`) and shader packs (`shaderpacks`, Modrinth projects for Iris).
A profile whose mods do not exist for a game version is skipped there (Voxy before 1.21.8, and
on Forge); a pack missing for it is left out, and where no Iris fits the far terrain mod (Distant
Horizons on 1.20.2 and 1.20.4) the profile is played without Iris and its packs. Defaults: `vanilla`, `sodium`, `dh` (Distant
Horizons) and `voxy` (Voxy), each without shaders, then with Photon, then with Complementary.
`run` keeps a profile's first pack on from the start.

`speed_mods` are added to every game where they exist and fit: FerriteCore, Lithium (Canary on
Forge), FastLoad, Smooth Boot, Noisium. Never one that changes what is drawn or slows
a window without the focus: no Dynamic FPS, no culling mod, nothing that rewrites the chunk
system or the lighting. A speed mod is one more thing that can break a game: Chunky made Distant
Horizons crash the world's server on 1.20.1, ModernFix made Lithium crash it now and then. A
game that breaks only with them is no regression.

## Limits

- `run` is Fabric only, from 1.21.4 (the client game tests); `jars` covers the rest.
- Baselines belong to one machine: another graphics card or driver draws other pixels.
  Update the driver, then run and approve again.
- A build older than this bench has no `regression` scenario: it is refused.
- Never commit anything from `LH_BENCH_HOME`.
