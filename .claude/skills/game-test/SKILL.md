---
name: game-test
description: Run the real Minecraft client with the mod, headless, drive it with a scripted scenario (world, mobs, settings, screens) and take screenshots to look at. Use whenever a change affects what the game draws or shows (rendering, impostors, distant mobs or players, birds, screens, settings) and must be checked in the real game, when a bug report comes with a screenshot to reproduce, or when the user asks to test, try or see something in game.
---

# Testing in the real game

The mod is checked in the real client, not only by unit tests: Fabric's client game
tests start Minecraft with the mod, a scenario written in Java plays it (makes a world,
summons mobs, changes the settings, opens screens) and takes screenshots, which you
then look at. No graphics card: the game renders on the CPU (Mesa llvmpipe, OpenGL 4.5)
into a virtual display (Xvfb).

## Run it

```
.claude/skills/game-test/run.sh                  # every scenario
.claude/skills/game-test/run.sh impostors        # one, or several: a,b
.claude/skills/game-test/run.sh all --sodium     # with Sodium loaded too
.claude/skills/game-test/run.sh impostors --shaders <pack folder>   # Sodium, Iris and a shader pack
```

It takes 1 to 3 minutes per scenario. The script:

- writes the session's proxy into `~/.gradle/gradle.properties` (the Gradle daemon does
  not always pick it up from `JAVA_TOOL_OPTIONS`), and retries when Maven Central
  answers 429;
- runs `xvfb-run ./gradlew runClientGameTest -Plh.scenario=<names>`;
- prints the scenario's `LHTEST` log lines, any assertion or crash, and the screenshots.

Give it `LH_NODE=1.21.11` even for the active version: without it Gradle configures every
node and downloads the libraries of all of them. When Maven Central keeps answering 429
all the same, point Gradle at its Google mirror for the session with an init script in
`~/.gradle/init.d/` that replaces `repo.maven.apache.org` by
`https://maven-central.storage-download.googleapis.com/maven2/` in every repository list
(settings' `pluginManagement` and `dependencyResolutionManagement`, and each project's).
26.3 opens its window through EGL: `apt-get install libegl1 libegl-mesa0` when the log
says "Could not load EGL library".

The full log is `build/game-test.log`. Screenshots are in
`build/run/clientGameTest/screenshots/`, numbered in order and named
`<scenario>-<name>.png`. That folder is emptied before every run; it is never the `run/`
folder `runClient` plays in.

## Look at the screenshots

Read them (the Read tool shows images). The window is 854x480, GUI scale 1: distant
figures are a few pixels, so crop and enlarge them, and put two side by side, with
Python and PIL:

```python
from PIL import Image
a = Image.open("…/0001_impostors-models-zoom.png").convert("RGB").crop((0, 95, 854, 170))
b = Image.open("…/0002_impostors-impostors-zoom.png").convert("RGB").crop((0, 95, 854, 170))
out = Image.new("RGB", (a.width, a.height * 2 + 4), (255, 0, 0))
out.paste(a, (0, 0)); out.paste(b, (0, a.height + 4))
out.resize((out.width * 2, out.height * 2), Image.NEAREST).save("/tmp/…/compare.png")
```

Write such working images to the scratchpad, not the repository. Mean colours over a box
(`getpixel`) compare brightness between two shots. Send the user the images that show
the result (before and after, model and impostor) with `SendUserFile`.

## Measure what the mod costs

`LH_NODE=1.21.11 run.sh perf` builds a scene far from the player (about 500 remembered
mobs, 40 test players who logged off, birds), turns the camera once round per phase and
prints a table: the frame time (average, maximum, cost against the mod off), the mod's
own tick and extraction times, and what the render thread allocates per frame. The
phases are the mod off, the defaults, each `opt*` switch flipped in turn, impostors off,
then the mod off again; the gap between the two "mod off" rows is the noise. Run it on
the same machine before and after a change and compare. The frame time here is the whole
game's, server tick included, and moves by a millisecond or two between runs: the mod's
own columns are the precise ones. llvmpipe draws on the CPU: the frame times say nothing
of a graphics card, and Voxy's and Distant Horizons' parts are not covered. It takes
about 6 minutes.

## Write a scenario

The scenarios are in `src/gametest/java/fr/clixmods/livinghorizon/gametest/`:

- `LivingHorizonGameTests`: the entry point, the list `SCENARIOS`. A new scenario is a
  class implementing `Scenario` (a lower-case `name()` and `run(context)`), added to that
  list. Keep the ones there working: they are the regression checks.
- `Scene`: what they share. `flatWorld(context)` (a new flat world at noon, still, the
  player at 0, 0 facing south, +z; close it with try-with-resources), `command`,
  `summon` and `row` (mobs with `NoAI`, turned every 45 degrees), `configure` (the mod's
  settings), `screenshot`, `screenshotAtlas` (the impostor page), `log` (an `LHTEST` line).
  Every scenario starts from the default settings, with birds and the easter eggs off.
- `scenario/ImpostorsScenario`, `scenario/RememberedMobsScenario`: examples to copy.

The `context` is Fabric's `ClientGameTestContext`: `waitTicks`, `runOnClient` and
`computeOnClient` (anything touching the game: the scenario itself runs on another
thread), `setScreen`, `clickScreenButton(translation key)`, `takeScreenshot`. Throw an
`AssertionError` for a check that must fail the run.

`./gradlew build` compiles the scenarios (`compileGametestJava`) but does not run them.

## What to know about the scene

- **Fog.** Without Voxy, the game's fog hides everything past its render distance. Keep
  the scene within it (render distance 12 chunks is the default here; 16 is slower) or
  the figures are invisible, models and impostors alike.
- **Who draws a mob.** The game draws a mob within its entity distance; past it, while
  the server still sends it, the mod draws the real mob; once the server stops sending
  it, the mod draws its remembered copy. `entityDistanceScaling` at 0.5 makes the game
  stop early (about 35 blocks for a cow), so mobs at 50 blocks are the mod's.
- **Remembered mobs.** The server still sends mobs 150 blocks away: go 200 blocks away
  (`tp`) and wait about 300 ticks. A mob that vanishes within 32 blocks of where the
  player was is taken as killed and forgotten, so the mobs must stand farther than that
  before the `tp`.
- **Occlusion.** With `hideOccludedMobs` on, figures past the terrain are hidden by
  depth; turn it off to look at the figures themselves.
- **Slow.** Chunks draw slowly: `flatWorld` waits 100 ticks. `waitForChunksRender`
  times out at large render distances. The menus' blur is off (`Scene.reset`): on a
  machine with few cores llvmpipe spends them all blurring the loading screen, the server
  runs in step with the client, and "Timeout loading world" ends the run. Raise the
  render distance after `flatWorld`, not before, for the same reason.
- **Options' bounds.** An option set out of its range is ignored: the field of view goes
  from 30 to 110, so 30 is the closest zoom.

## Limits

- No real graphics card: a bug of one driver (NVIDIA, AMD) does not show here.
- **Voxy and Distant Horizons cannot run** (Voxy needs GLSL 4.60, llvmpipe has 4.50).
  Say so when a check depended on them.
- **Shader packs** run through Iris on llvmpipe, slowly: `--shaders` takes an unpacked
  pack folder, downloads Iris next to Sodium for the node's game version (`LH_NODE`), and
  `Scene.shaderPack` copies the pack into Iris's folder and turns it on before the first
  scenario. Photon does not load on llvmpipe (too much shared memory): use Complementary,
  its zip from Modrinth unpacked into the scratchpad. The run folder is emptied at start, so a pack copied there by hand is lost.
- Sodium can be added (`--sodium`, downloaded from Modrinth into `build/game-test-mods`). The
  `sodium` scenario shows the mod's pages in Sodium's video settings; without Sodium it does nothing.

## Never

- Commit screenshots, logs or anything under `build/`.
- Point the game test's run directory at `run/`: it is deleted before every run.
