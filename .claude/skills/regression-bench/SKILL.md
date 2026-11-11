---
name: regression-bench
description: Run Living Horizon's regression bench on the dedicated test machine - the same frozen scene played on every game version, alone and with Sodium, Voxy, Distant Horizons and shader packs, its pictures and numbers compared with the last approved run and between versions, in an HTML report. Use when a develop build (or a branch) must be checked across versions before release, when the user asks whether a build draws the same as the previous one, to approve new baselines, or to set the bench machine up.
---

# The regression bench

`game-test` checks one scenario on one version, headless, without Voxy. The bench is its
companion for a machine with a graphics card: it plays one fixed scene on **every Fabric node
from 1.21.4** (the client game tests need it), once per **profile** (a set of mods), and
compares each picture with the one approved last time on that same machine.

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
bench.cmd run --ref local                   # this checkout as it is (on a dev machine: python bench.py)
bench.cmd run --profiles voxy,voxy-photon --perf
bench.cmd compare last                      # compare again (after changing bench.toml)
bench.cmd approve last                      # this run's pictures become the baselines
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

## Read a result

What the comparison counts: pixels steady in both pairs (what moves by itself is left out) that
differ beyond the tolerance, in blobs of two by two at least (an edge shifted by one pixel draws
thin lines, which do not count). Each blob is framed in the strip and enlarged, before and after,
below it. New warnings and errors of the mod in the game's log (its own lines, or an error whose
stack goes through it) mark a result changed too: a bug does not always show in a picture, and
this is how the far depth failing on Forge 1.20.1 in v0.17.0-dev.744 showed first.

- **The first run** has nothing to compare with: look at its pictures, then `approve last`.
- **Changed, expected** (the pull request changed what is drawn): approve it.
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
  `scene_commands` and raise `SCENE_VERSION` together: every world is made again once.
- **The far terrain is built once**: Distant Horizons and Voxy keep theirs in the world
  (`data/DistantHorizons.sqlite` per dimension, `voxy/`). A warm-up launch per game version and
  far terrain mod builds it (Distant Horizons while the game runs and a tour of the scene; Voxy
  with `/voxy import world`), closes the game cleanly so that it is written out, and keeps it in
  `worlds/<game>/scene-<V>-far/<mod jar>`; every launch after starts with it.
- **Settings written before launch**: `config/livinghorizon.json` (no birds or easter eggs,
  every copy still), `options.txt` (render distance 4, so that the mountain is far terrain from
  its viewpoints; no clouds; `graphicsPreset:"custom"`, or from 1.21.11 the preset puts its own
  render distance and clouds back), Distant Horizons' clouds off.
- **One launch per far terrain mod**: the game opens the world itself (Quick Play), the bench
  hides the interface (F1), goes through `VIEWPOINTS` without shaders, turns the shader pack on in
  the same game (Iris's key K, Oculus's too) and goes through them again (`shaders-<name>`).
- **At each viewpoint** a `/tp` typed in the chat (checked in the game's log; Escape and again
  when a menu was in the way), then the game's own screenshots (F2) one after another until
  three in a row match: the chunks round a place just reached take a second or two to draw, in
  bursts. Without shaders a match is under 0.3 % of pixels moved, with them under 3 % (Photon's
  grain).
- **Hands off**: the keys go straight to each game's window, the windows ignore the mouse and
  the keyboard, and each game is told it lost the focus so that it lets the mouse go (a window
  just opened takes it, and the camera would follow the mouse). The machine stays usable; the
  windows may overlap. Windows only.
- **Mods**: the profile's from Modrinth (the Fabric API added on Fabric and Quilt; Embeddium and
  Oculus stand for Sodium and Iris on Forge), in versions that fit together (`fitting` reads each
  jar's dependencies: Voxy 0.2.16 takes Sodium 0.8.12 at most), plus `speed_mods` where they
  exist. A mod asking for a newer Fabric Loader gets it.

## Profiles

`bench.toml` next to this file; `LH_BENCH_HOME/bench.toml` overrides it per key. A profile
names Modrinth projects (`mods`) and a shader pack (`shaderpack`, a Modrinth project for
Iris). A profile whose mods do not exist for a game version is skipped there (Voxy before
1.21.8, and on Forge). Defaults: `vanilla`, `sodium`, `dh` (Distant Horizons, then
Complementary), `voxy` (Voxy, then Photon). `run` keeps a profile's pack on from the start.

`speed_mods` are added to every game where they exist and fit: FerriteCore, Lithium (Canary on
Forge), ModernFix, FastLoad, Smooth Boot, Noisium, Chunky. Never one that changes what is drawn
or slows a window without the focus: no Dynamic FPS, no culling mod, nothing that rewrites the
chunk system or the lighting.

## Limits

- `run` is Fabric only, from 1.21.4 (the client game tests); `jars` covers the rest.
- Baselines belong to one machine: another graphics card or driver draws other pixels.
  Update the driver, then run and approve again.
- A build older than this bench has no `regression` scenario: it is refused.
- Never commit anything from `LH_BENCH_HOME`.
