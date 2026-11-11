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
bench.cmd jars --tag v0.16.0 --tag v0.17.0-dev.744 --nodes 1.20.1-forge --profiles vanilla,dh,dh-complementary
bench.cmd jars --tag v0.17.0-dev.750 --nodes 1.20.1-forge --against v0.16.0-jars
bench.cmd compare last --against v0.16.0-jars
```

Several `--tag`s run side by side and the later ones are compared with the first. As many games
run at once as the memory holds (4 GB each), or `--parallel N`.

- The game is installed once per version and loader in `LH_BENCH_HOME/minecraft`
  (minecraft-launcher-lib, offline, the game's own Java runtime), the loader's newest version
  pinned in `pins.json` like the mods.
- The scene is a world each game version's vanilla dedicated server makes once and the bench
  keeps (`worlds/<game>/scene-<SCENE_VERSION>`): flat, time and weather stopped, rows of mobs
  without AI that never animate on their own (no horse, no chicken: a horse without AI still
  grazes), fixed ids, a wall and a hill, and north of the spot a mountain with a crowd of iron
  golems right behind it and a control group beside it. Each launch opens a copy of it. Change
  `scene_commands` and raise `SCENE_VERSION` together: every world is made again once.
- `config/livinghorizon.json` is written before launch (no birds or easter eggs, every copy
  still), the game opens the world by itself (Quick Play) and the mod reads the far mobs from the
  save. After the profile's `settle` seconds the bench hides the interface (F1), then for each
  viewpoint (`VIEWPOINTS`) types a `/tp` in the chat, checks in the game's log that it ran
  (Escape and again when a menu was in the way), waits `--wait` seconds and takes two of the
  game's own screenshots (F2). The render distance is 4, so that the mountain is far terrain
  from its three viewpoints: one golem drawn through it is a line of figures on its face.
- The keys go straight to each game's window, and the windows are turned off for the mouse and
  the keyboard as they open: a click would give one the focus and the camera would follow the
  mouse. The machine stays usable; the windows may overlap. Windows only.
- Profiles take the loader's mods from Modrinth (the Fabric API added on Fabric and Quilt;
  Embeddium and Oculus stand for Sodium and Iris on Forge), the shader pack turned on through
  Iris's or Oculus's settings file.
- Numbers: how many mobs the mod read from the save (`scan.mobs`). The game's log is kept with the
  pictures (`game.log`).

## Profiles

`bench.toml` next to this file; `LH_BENCH_HOME/bench.toml` overrides it per key. A profile
names Modrinth projects (`mods`) and a shader pack (`shaderpack`, a Modrinth project for
Iris). A profile whose mods do not exist for a game version is skipped there (Voxy before
1.21.8). Defaults: `vanilla`, `sodium`, `voxy`, `dh`, `iris` (Complementary),
`voxy-photon`, `dh-complementary`.

## Limits

- `run` is Fabric only, from 1.21.4 (the client game tests); `jars` covers the rest.
- Baselines belong to one machine: another graphics card or driver draws other pixels.
  Update the driver, then run and approve again.
- A build older than this bench has no `regression` scenario: it is refused.
- Never commit anything from `LH_BENCH_HOME`.
