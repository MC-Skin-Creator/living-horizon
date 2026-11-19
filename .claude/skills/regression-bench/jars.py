"""The regression bench's second engine: published jars, on any loader and game version.

The client game tests only exist on Fabric from 1.21.4 and need the sources. This plays the jar a
release ships (GitHub's assets) in the real game, installed once per game version and loader
(Fabric, Quilt, Forge, NeoForge) with minecraft-launcher-lib, and drives it from outside, as a
flight simulator's visual tests do: fixed places, a picture at each, compared.

- the scene is a world made once per game version by that version's vanilla dedicated server,
  and kept until the scene changes (SCENE_VERSION): flat, time and weather stopped, rows of mobs without AI
  that have no idle animation of their own (no horse, no chicken), with fixed ids, a wall and a
  hill. Each launch opens a fresh copy, so nothing a launch did carries over;
- the mod's settings are written before launch (`config/livinghorizon.json`): no birds, no
  easter eggs, players who logged off standing, every distant copy still from the start;
- the game opens the world itself (Quick Play) and the mod reads its far mobs from the save.
  After a settling time the bench hides the interface (F1), then for each viewpoint types a
  teleport in the chat and takes two of the game's own screenshots (F2) a moment apart. The keys
  go straight to the game's window, so several games run side by side.

Windows only for the keys (the bench machine).
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

SCENE_VERSION = 4
SEED = "20261014"
USERNAME = "LhBench"
PLAYER_UUID = "6c68be6e-6368-4c68-9265-6e6368000001"
WIDTH, HEIGHT = 854, 480

# Short, so that the mountain is far terrain (Distant Horizons', Voxy's) from its viewpoints.
RENDER_DISTANCE = 4
# Every mob faces the same way, north, towards the spot: the same picture of each kind everywhere.
MOB_YAW = 180
# The Nether's tunnel, closed on every side (no lava runs into it, no light changes): its floor,
# from the spot south along z. The End's causeway: its floor, from END_Z south towards the island,
# so that its mobs face the camera as everywhere else.
NETHER_FEET = 41
END_FEET = 61
END_Z = -600
# Where the pictures are taken: x, y, z, yaw, pitch (yaw 0 faces south, +z, where the rows are).
VIEWPOINTS = {
    # The mountain north of the spot, past the render distance, a crowd of golems right behind it:
    # every one of them must be hidden. One showing through is a line of figures on its face.
    "mountain": (0.5, -58.4, -20, 180, 0),
    # The same from higher up, the crowd just below the mountain's ridge.
    "mountain-high": (0.5, -36, -10, 180, 4),
    # From the side: the control group beside the mountain seen, the crowd still hidden.
    "mountain-side": (-70, -56, -30, -142, 2),
    # The spot: the near rows the game draws, the middle ones past its entity distance, the wall.
    "spot": (0.5, -58.4, 0.5, 0, 0),
    # Above and behind: every row at once, the far ones past the render distance.
    "overview": (0.5, -20, -40, 0, 15),
    # Behind the wall, facing it: the mobs on the other side must be hidden.
    "wall": (-36, -58.4, 140, 180, 0),
    # In front of the hill: the far rows behind it hidden, the first one before it seen.
    "hill": (64, -58.4, 150, 0, 0),
    # From the far end, looking back: the near rows are remembered copies from here.
    "back": (0.5, -50, 470, 180, 5),
    # The Nether: down a closed tunnel, its rows of mobs going away, half of the far ones behind a wall.
    "nether": (0.5, NETHER_FEET + 1.6, -10, 0, 0),
    # The End: along a causeway in the void, its rows of mobs, the main island far behind them.
    "end": (0.5, END_FEET + 1.6, END_Z - 10, 0, 2),
}
# The dimension of each viewpoint not in the Overworld.
DIMENSIONS = {"nether": "minecraft:the_nether", "end": "minecraft:the_end"}

# A tour of the scene before the pictures: Voxy builds its terrain only from the chunks the game
# loads, so every part of the scene is loaded once, the mountain included. The same for every
# profile, so that the mobs met on the way are the same too.
TOUR = [(0.5, -58.4, -90.5), (0.5, -58.4, 120.5), (64.5, -58.4, 240.5), (0.5, -58.4, 330.5), (0.5, -58.4, 440.5),
        (0.5, -58.4, 0.5)]
TOUR_WAIT = 5
# The same in the other dimensions, after a wait at their first stop for the far terrain mods to
# build what is round it (Distant Horizons builds a dimension only while the player is in it).
OTHER_TOURS = {
    "minecraft:the_nether": [(0.5, NETHER_FEET, 0.5), (0.5, NETHER_FEET, 200.5), (0.5, NETHER_FEET, 400.5),
                             (0.5, NETHER_FEET, 0.5)],
    "minecraft:the_end": [(0.5, END_FEET, END_Z + 0.5), (0.5, END_FEET, END_Z + 200.5), (0.5, END_FEET, END_Z + 390.5),
                          (0.5, END_FEET, END_Z + 0.5)],
}
OTHER_WAIT = 60

# Two screenshots in a row match when fewer pixels than this share differ: a shader pack's own
# grain (Photon's) keeps a few percent moving forever.
PLAIN_STILL, SHADED_STILL = 0.003, 0.03

# Mobs whose look never changes on its own with no AI: a horse grazes, a chicken lays eggs, a
# strider shivers out of lava.
KINDS = ["cow", "sheep", "pig", "villager", "iron_golem"]
NETHER_KINDS = ["zombified_piglin", "piglin", "hoglin", "wither_skeleton"]
END_KINDS = ["enderman", "shulker", "iron_golem"]
# Rows of mobs, blocks south of the spot: the game draws the first, the mod the next two (past
# the game's entity distance, still sent by the server), and remembers the far ones (never sent:
# read from the save).
ROWS = [24, 72, 88, 200, 260, 320, 400]
FEET = -60
# The mountain's south face, north of the spot, and the rows of the crowd behind it.
MOUNTAIN_Z = -100
CROWD_ROWS = [-114, -120, -126]


def say(line: str) -> None:
    print(f"bench: {line}", flush=True)


# --- Nodes and jars ----------------------------------------------------------------------------

def node_parts(node: str, tables: dict) -> tuple[str, str]:
    """The game version a node runs and its loader: '1.20.1-forge' -> ('1.20.1', 'forge')."""
    loader = "fabric"
    for suffix in ("-neoforge", "-forge", "-quilt"):
        if node.endswith(suffix):
            loader = suffix[1:]
    return tables[node]["mod"]["mc_releases"][-1], loader


def release_jar(tag: str, node: str, repository: str, home: Path, github) -> Path:
    """The jar a release ships for a node, downloaded once."""
    base, suffix = node, ""
    for loader in ("neoforge", "forge", "quilt"):
        if node.endswith(f"-{loader}"):
            base, suffix = node[: -len(loader) - 1], f"-{loader}"
    # The node 26.2.x ships as mc26.2, 26.1.x as mc26.1.2: the release it was built for.
    wide = base.endswith(".x")
    base = re.sub(r"\.x$", "", base)
    patch = r"(\.\d+)*" if wide else ""
    name = re.compile(rf"\+mc{re.escape(base)}{patch}{re.escape(suffix)}\.jar$")
    to = home / "jars" / tag / node / "livinghorizon.jar"
    if to.is_file():
        return to
    release = github(f"releases/tags/{tag}", repository)
    for asset in release.get("assets", []):
        if name.search(asset["name"]):
            from bench import download
            say(f"downloading {asset['name']}")
            download(asset["browser_download_url"], to)
            return to
    sys.exit(f"bench: {tag} has no jar for {node}")


# --- The game, installed once per version and loader -------------------------------------------

def minecraft_dir(home: Path) -> Path:
    return home / "minecraft"


def java_for(game: str, home: Path) -> str:
    import minecraft_launcher_lib as mll
    mc = minecraft_dir(home)
    data = json.loads((mc / "versions" / game / f"{game}.json").read_text("utf-8"))
    component = data.get("javaVersion", {}).get("component", "java-runtime-gamma")
    path = mll.runtime.get_executable_path(component, str(mc))
    if not path:
        mll.runtime.install_jvm_runtime(component, str(mc))
        path = mll.runtime.get_executable_path(component, str(mc))
    return path


def loader_version(game: str, loader: str, pins: dict, update: bool) -> str | None:
    """The loader version used for a game version: the newest when first asked, then pinned with
    the mods (`pins.json`), so that a new Forge never passes for a change of the mod's."""
    if loader == "vanilla":
        return None
    import minecraft_launcher_lib as mll
    key = f"loader:{loader}"
    if key in pins.setdefault(game, {}) and not update:
        return pins[game][key]["version"]
    # The list's highest: its "latest" follows the order Forge's metadata lists them in (47.4.5
    # for 1.20.1, when 47.4.29 is out).
    versions = mll.mod_loader.get_mod_loader(loader).get_loader_versions(game, stable_only=True)
    version = max(versions, key=lambda v: tuple(int(n) for n in re.findall(r"\d+", v)))
    pins[game][key] = {"version": version}
    return version


def install(game: str, loader: str, home: Path, version_of_loader: str | None = None) -> str:
    """The version id to launch, installing the game and its loader the first time."""
    import minecraft_launcher_lib as mll
    mc = minecraft_dir(home)
    mc.mkdir(parents=True, exist_ok=True)
    marker = mc / f"lh-installed-{game}-{loader}-{version_of_loader or 'none'}.txt"
    if marker.is_file():
        return marker.read_text("utf-8").strip()
    say(f"installing Minecraft {game}" + ("" if loader == "vanilla" else f" with {loader} {version_of_loader}"))
    mll.install.install_minecraft_version(game, str(mc))
    version = game
    if loader != "vanilla":
        version = mll.mod_loader.get_mod_loader(loader).install(game, str(mc), loader_version=version_of_loader,
                                                                 java=java_for(game, home))
    marker.write_text(version, "utf-8")
    return version


# --- The scene ---------------------------------------------------------------------------------

def scene_commands() -> list[str]:
    # Old and new rule names alike (1.21.11 renamed them): a name the game does not know does nothing.
    commands = [f"gamerule {rule} false" for rule in (
        "doDaylightCycle", "advance_time", "doWeatherCycle", "advance_weather", "doMobSpawning", "spawn_mobs",
        "doPatrolSpawning", "spawn_patrols", "doTraderSpawning", "spawn_wandering_traders", "doInsomnia",
        "spawn_phantoms", "doFireTick", "fire_spread_radius_around_player")]
    commands += [
        "gamerule randomTickSpeed 0", "gamerule random_tick_speed 0", "gamerule spawnRadius 0",
        "gamerule respawn_radius 0", "time set 6000", "weather clear 1000000", f"setworldspawn 0 {FEET} 0 0",
        # A wall across the left of the middle rows: the mobs past it are hidden.
        f"fill -64 {FEET} 100 -8 {FEET + 19} 100 minecraft:stone",
        # A tower to the side, to see the far terrain line up with the game's.
        f"fill 60 {FEET} 60 62 {FEET + 24} 62 minecraft:stone_bricks",
    ]
    # A stepped hill in front of the right of the far rows: copies behind terrain.
    for level in range(8):
        commands.append(f"fill {16 + level * 2} {FEET + level * 2} {226 + level * 2} {112 - level * 2} "
                        f"{FEET + level * 2 + 1} {250 - level * 2} minecraft:grass_block")
    # The mountain: past the render distance from its viewpoints, high and wide enough to hide a
    # whole crowd standing right behind it.
    commands.append(f"fill -48 {FEET} {MOUNTAIN_Z - 8} 48 {FEET + 34} {MOUNTAIN_Z} minecraft:stone")
    number = 0
    for z in CROWD_ROWS:
        for x in range(-44, 45, 4):
            number += 1
            commands.append(golem(x, z, 180, number))
    # The control group, beside the mountain: in sight, drawn by every version.
    for x in range(56, 81, 4):
        number += 1
        commands.append(golem(x, CROWD_ROWS[0], 180, number))
    for z in ROWS:
        for i, x in enumerate(range(-120, 121, 8)):
            number += 1
            commands.append(mob(KINDS[(i + z) % len(KINDS)], x, FEET, z, 0, number))

    # The Nether: a tunnel of blackstone, closed on every side, its floor along z from the spot.
    nether = "execute in minecraft:the_nether run "
    commands += [nether + c for c in fills(-13, NETHER_FEET - 8, -21, 13, NETHER_FEET + 60, 421, "minecraft:blackstone")]
    commands += [nether + c for c in fills(-12, NETHER_FEET, -20, 12, NETHER_FEET + 59, 420, "minecraft:air")]
    # Lit all along by two lines of light blocks, which are not drawn: its far rows seen, not black.
    for x in (-6, 6):
        commands += [nether + c for c in fills(x, NETHER_FEET + 8, -20, x, NETHER_FEET + 8, 420, "minecraft:light")]
    # Across the left half, like the Overworld's wall: the mobs behind it hidden.
    commands += [nether + c for c in fills(-12, NETHER_FEET, 150, 0, NETHER_FEET + 19, 150, "minecraft:blackstone")]
    for z in ROWS:
        for i, x in enumerate(range(-8, 9, 4)):
            number += 1
            commands.append(nether + mob(NETHER_KINDS[(i + z) % len(NETHER_KINDS)], x, NETHER_FEET, z, 2, number))

    # The End: a causeway of end stone in the void, north of the main island, out of reach of the
    # dragon's fight (192 blocks round the island): no dragon ever comes.
    end = "execute in minecraft:the_end run "
    commands += [end + c for c in fills(-26, END_FEET - 1, END_Z - 20, 26, END_FEET - 1, END_Z + 400, "minecraft:end_stone")]
    for z in ROWS:
        for i, x in enumerate(range(-24, 25, 8)):
            number += 1
            commands.append(end + mob(END_KINDS[(i + z) % len(END_KINDS)], x, END_FEET, END_Z + z, 3, number))
    return commands


def fills(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block: str) -> list[str]:
    """One box, filled in slices along z: a fill changes 32768 blocks at most."""
    per = max(1, 32768 // ((x2 - x1 + 1) * (y2 - y1 + 1)))
    return [f"fill {x1} {y1} {z} {x2} {y2} {min(z + per - 1, z2)} {block}" for z in range(z1, z2 + 1, per)]


# Every mob of the scene carries it: anything else is removed before the world is saved.
TAG = "lh-bench"


def mob(kind: str, x: int, feet: int, z: int, group: int, number: int) -> str:
    """A mob with no AI, facing like every other one (`MOB_YAW`), with an id of its own."""
    extra = ""
    if kind == "sheep":
        extra = ",Color:0b"
    elif kind == "villager":
        extra = ',VillagerData:{type:"minecraft:plains",profession:"minecraft:none",level:1}'
    return (f"summon minecraft:{kind} {x}.5 {feet} {z}.5 {{NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"{TAG}\"],"
            f"Rotation:[{MOB_YAW}f,0f],UUID:[I;1818650722,1701015656,{group},{number}]{extra}}}")


def golem(x: int, z: int, yaw: int, number: int) -> str:
    """An iron golem: tall, pale against the stone, and still without AI."""
    return mob("iron_golem", x, FEET, z, 1, number)


def scene(home: Path, game: str) -> Path:
    """The scene's world for a game version, made once by that version's own dedicated server and
    kept (`worlds/<game>/scene-<SCENE_VERSION>`) until the scene changes: no upgrade at each launch."""
    world = home / "worlds" / game / f"scene-{SCENE_VERSION}"
    if (world / "level.dat").is_file():
        return world
    install(game, "vanilla", home)
    mc = minecraft_dir(home)
    data = json.loads((mc / "versions" / game / f"{game}.json").read_text("utf-8"))
    server_dir = home / "worlds" / game / f"server-{SCENE_VERSION}"
    if server_dir.is_dir():
        shutil.rmtree(server_dir)
    server_dir.mkdir(parents=True)
    from bench import download
    download(data["downloads"]["server"]["url"], server_dir / "server.jar")
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text("\n".join([
        "level-name=scene", "level-type=minecraft\\:flat", f"level-seed={SEED}", "generate-structures=false",
        "spawn-protection=0", "online-mode=false", "gamemode=spectator",
        # Not peaceful: the Nether's and the End's monsters would be refused, or removed as the world
        # opens. They have no AI, and nothing spawns on its own (the spawn rules are off).
        "difficulty=easy",
        # Not spawn-animals=false nor spawn-npcs=false: up to 1.20.x the server then throws out every
        # animal and villager, summoned ones too. The spawn rules are off, and whatever came while the
        # chunks were made is removed before the mobs are placed.
        "view-distance=10", ""]))
    say(f"making the scene's world with the vanilla {game} server (once)")
    server = subprocess.Popen([java_for(game, home), "-Xmx3G", "-jar", "server.jar", "--nogui"], cwd=server_dir,
                              stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    out: list[str] = []

    def wait_for(pattern: str, seconds: int) -> str:
        end = time.time() + seconds
        while time.time() < end:
            line = server.stdout.readline()
            if not line:
                break
            out.append(line)
            if re.search(pattern, line):
                return line
        raise RuntimeError(f"the scene's server never said {pattern!r}:\n{''.join(out[-30:])}")

    def run(command: str) -> None:
        server.stdin.write(command + "\n")
        server.stdin.flush()

    def loaded(dimension: str, x: int, z: int) -> bool:
        run(f"execute in {dimension} if loaded {x} 0 {z}")
        return "passed" in wait_for(r"Test (passed|failed)", 60)

    commands = scene_commands()
    summons = sum(1 for c in commands if "summon " in c)
    try:
        wait_for(r"Done \(", 600)
        # The rules first: in Easy, monsters would spawn in the chunks about to be loaded.
        for command in commands:
            if command.startswith("gamerule"):
                run(command)
        # Every chunk the scene shows generated and loaded before anything is placed: a mob summoned
        # where the chunk's entities are not loaded yet is lost when the chunk is let go.
        batches = [("minecraft:overworld", cx * 16, cz * 16, (cx + 15) * 16, (cz + 15) * 16)
                   for cx in range(-28, 28, 16) for cz in range(-12, 36, 16)]
        # The Nether's tunnel; the End's causeway, and its main island for Voxy to import.
        batches += [("minecraft:the_nether", -32, -48, 31, 447),
                    ("minecraft:the_end", -32, END_Z - 32, 31, END_Z + 415),
                    ("minecraft:the_end", -128, -128, 127, 127)]
        for dimension, x1, z1, x2, z2 in batches:
            run(f"execute in {dimension} run forceload add {x1} {z1} {x2} {z2}")
        for dimension, x1, z1, x2, z2 in batches:
            for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2), ((x1 + x2) // 2, (z1 + z2) // 2)):
                deadline = time.time() + 600
                while not loaded(dimension, x, z):
                    if time.time() > deadline:
                        raise RuntimeError(f"the chunk at {x}, {z} of {dimension} never loaded")
                    time.sleep(2)
        def count(selector: str, dimension: str = "minecraft:overworld") -> int:
            run(f"execute in {dimension} if entity {selector}")
            answer = wait_for(r"Test (passed|failed)", 60)
            found = re.search(r"count: (\d+)", answer, re.IGNORECASE)  # "Count: N" from 1.21.11
            return int(found.group(1)) if found else 0

        def missing() -> str:
            """Which kinds of mob are short, in which dimension: wanted, then held."""
            short = []
            for dimension in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"):
                ours = [c for c in commands if " summon " in f" {c}"
                        and (dimension in c or "execute in" not in c and dimension == "minecraft:overworld")]
                for kind in sorted(set(KINDS + NETHER_KINDS + END_KINDS)):
                    wanted = sum(1 for c in ours if f"summon minecraft:{kind} " in c)
                    if wanted:
                        # distance=0..: in the dimension the command runs in only.
                        held = count(f"@e[tag={TAG},type=minecraft:{kind},distance=0..]", dimension)
                        if held != wanted:
                            short.append(f"{dimension.split(':')[1]} {kind} {held}/{wanted}")
            return ", ".join(short) or "none short by kind"

        # The entities load a little after the blocks, later still on a server busy generating:
        # a mob summoned where they are not loaded yet is lost. Placed again until all of them stay.
        found = 0
        for attempt in range(4):
            time.sleep(20 + 40 * attempt)
            run("kill @e[type=!minecraft:player]")
            time.sleep(5)
            run("kill @e[type=!minecraft:player]")
            time.sleep(2)
            for command in commands:
                run(command)
            run("say scene built")
            wait_for(r"scene built", 300)
            time.sleep(5)
            # Whatever came on its own meanwhile.
            run(f"kill @e[type=!minecraft:player,tag=!{TAG}]")
            time.sleep(2)
            found = count(f"@e[tag={TAG}]")
            if found == summons:
                break
            say(f"  {found} of {summons} mobs stayed, placing them again: {missing()}")
        if found != summons:
            raise RuntimeError(f"the scene holds {found} mobs, {summons} were summoned: {missing()}")
        say(f"  {found} mobs placed")
        for dimension in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"):
            run(f"execute in {dimension} run forceload remove all")
        run("save-all flush")
        wait_for(r"Saved the game", 300)
        run("stop")
        server.wait(timeout=300)
    finally:
        if server.poll() is None:
            server.kill()
        (world.parent / f"scene-{SCENE_VERSION}-build.log").write_text("".join(out), "utf-8")
    (server_dir / "scene" / "livinghorizon-scene.txt").write_text(f"regression bench scene {SCENE_VERSION}\n")
    shutil.copytree(server_dir / "scene", world)
    (world / "session.lock").unlink(missing_ok=True)
    shutil.rmtree(server_dir, ignore_errors=True)
    say(f"scene ready: {world}")
    return world


# --- One launch, every viewpoint ---------------------------------------------------------------

def prepare(game_dir: Path, mods: list[Path], world: Path, packs: list[Path], version: int | None) -> None:
    # A game just stopped may hold its files for a moment longer.
    for _ in range(30):
        try:
            if game_dir.is_dir():
                shutil.rmtree(game_dir)
            break
        except PermissionError:
            time.sleep(2)
    (game_dir / "mods").mkdir(parents=True)
    for jar in mods:
        try:
            os.link(jar, game_dir / "mods" / jar.name)
        except OSError:
            shutil.copy2(jar, game_dir / "mods" / jar.name)
    saved = game_dir / "saves" / "lh-scene"
    shutil.copytree(world, saved)
    # Commands allowed, for the teleports: a world the dedicated server made has them off.
    import nbtlib
    level = nbtlib.load(saved / "level.dat")
    level["Data"]["allowCommands"] = nbtlib.Byte(1)
    level.save()
    (game_dir / "options.txt").write_text("\n".join(([f"version:{version}"] if version else []) + [
        f"renderDistance:{RENDER_DISTANCE}", "simulationDistance:12", "fov:0.0", "guiScale:2", "bobView:false",
        'renderClouds:"false"', "entityDistanceScaling:0.5", "pauseOnLostFocus:false", "onboardAccessibility:false",
        "skipMultiplayerWarning:true", "joinedFirstServer:true", "tutorialStep:none", "fullscreen:false",
        "soundCategory_master:0.0", "maxFps:60", "enableVsync:false", "lang:en_us", "darkMojangStudiosBackground:false",
        "narrator:0", "hideServerAddress:true", "showSubtitles:false", "realmsNotifications:false",
        "menuBackgroundBlurriness:0", "chatOpacity:0.0",
        # From 1.21.11 a graphics preset other than custom puts its own render distance, clouds and
        # entity distance back over these; and a game nobody touches drops its frame rate.
        'graphicsPreset:"custom"', 'inactivityFpsLimit:"minimized"', "textBackgroundOpacity:0.0", "chatScale:0.0",
        # No ash or spores drifting in the Nether, no flash in the End's sky: nothing that moves.
        "particles:2", "hideLightningFlashes:true", ""]), "utf-8")
    (game_dir / "config").mkdir()
    # Every kind of the scene "still": its copies far away neither stroll nor turn on the spot.
    still = sorted({f"minecraft:{kind}" for kind in KINDS + NETHER_KINDS + END_KINDS}
                   | {"minecraft:happy_ghast", "minecraft:mannequin"})
    config = {"version": 1000, "skyBirds": False, "easterEggs": False, "offlineLife": False,
              "animationMaxDistance": 1, "seedMobs": False, "debugHud": False, "stillMobTypes": still}
    (game_dir / "config" / "livinghorizon.json").write_text(json.dumps(config, indent=2), "utf-8")
    # Distant Horizons draws clouds of its own, which drift with time: no two runs would match.
    (game_dir / "config" / "DistantHorizons.toml").write_text(
        "[client.advanced.graphics.genericRendering]\nenableCloudRendering = false\n", "utf-8")
    if packs:
        (game_dir / "shaderpacks").mkdir()
        for pack in packs:
            shutil.copy2(pack, game_dir / "shaderpacks" / pack.name)
        shader_settings(game_dir, packs[0].name, False)


def shader_settings(game_dir: Path, pack: str, on: bool) -> None:
    """Iris's settings, and Oculus's (Iris on Forge) under its own name, read as the game starts."""
    for name in ("iris.properties", "oculus.properties"):
        (game_dir / "config" / name).write_text(f"enableShaders={str(on).lower()}\nshaderPack={pack}\n", "utf-8")


def windows_of(pid_list: set[int], titled: bool = True) -> list[int]:
    """The game's visible windows, those titled Minecraft only by default (not Forge's loading window)."""
    import ctypes
    from ctypes import wintypes
    user32 = ctypes.windll.user32
    found: list[int] = []

    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def each(hwnd, _):
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if pid.value in pid_list and user32.IsWindowVisible(hwnd):
            title = ctypes.create_unicode_buffer(256)
            user32.GetWindowTextW(hwnd, title, 256)
            if not titled or "Minecraft" in title.value:
                found.append(hwnd)
        return True

    user32.EnumWindows(each, 0)
    return found


class Guard:
    """Keeps a game's windows out of the user's reach for as long as it runs, checked four times a
    second: a click would give one the focus, the game would take the mouse and the camera would
    turn with it; a drag would move or resize it, and the pictures with it.

    - every window of the game is turned off for the mouse and the keyboard (`EnableWindow`), and
      again whenever it comes back on; the keys the bench sends go straight to it and still arrive;
    - a window moved, resized or minimised all the same (the taskbar, Windows' own shortcuts) is
      put back where and as it was first seen, without being activated;
    - a window that has the focus gives it up: the game lets the mouse go."""

    def __init__(self, root: int):
        import threading
        self.root = root
        self.places: dict[int, tuple] = {}
        self.stopped = threading.Event()
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def run(self) -> None:
        while not self.stopped.wait(0.25):
            try:
                self.check()
            except Exception as e:  # never let a check end the guard
                say(f"  window guard: {e}")

    def check(self) -> None:
        import ctypes
        from ctypes import wintypes
        user32 = ctypes.windll.user32
        for hwnd in windows_of(pids(self.root), titled=False):
            if user32.IsWindowEnabled(hwnd):
                user32.EnableWindow(hwnd, False)
                release(hwnd)
            if user32.IsIconic(hwnd):
                user32.ShowWindow(hwnd, 4)  # SW_SHOWNOACTIVATE
                continue
            rect = wintypes.RECT()
            user32.GetWindowRect(hwnd, ctypes.byref(rect))
            place = (rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top)
            if hwnd not in self.places:
                self.places[hwnd] = place
            elif place != self.places[hwnd]:
                x, y, w, h = self.places[hwnd]
                user32.SetWindowPos(hwnd, 0, x, y, w, h, 0x0004 | 0x0010)  # SWP_NOZORDER, SWP_NOACTIVATE
            if user32.GetForegroundWindow() == hwnd:
                release(hwnd)
                if hwnd in SDL_WINDOWS:
                    give_focus_away(hwnd)

    def stop(self) -> None:
        self.stopped.set()


def give_focus_away(hwnd: int) -> None:
    """SDL keeps a window's focus while it is the foreground one, whatever it is told: the desktop
    takes the foreground instead, the way a click on it would."""
    import ctypes
    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
    theirs = user32.GetWindowThreadProcessId(hwnd, None)
    ours = kernel32.GetCurrentThreadId()
    user32.AttachThreadInput(ours, theirs, True)
    try:
        user32.SetForegroundWindow(user32.GetShellWindow())
    finally:
        user32.AttachThreadInput(ours, theirs, False)


def release(hwnd: int) -> None:
    """Tells the game its window lost the focus. A window just opened takes it, and the game then
    holds the mouse: moving it turns the camera, locked window or not. Told it lost it, the game
    lets the mouse go (without pausing: pauseOnLostFocus is off), and with the window locked no
    click gives it back."""
    import ctypes
    ctypes.windll.user32.PostMessageW(hwnd, 0x0008, 0, 0)  # WM_KILLFOCUS


# Virtual key and scan code: the game reads the scan code, so the keyboard's layout does not matter.
KEYS = {"F1": (0x70, 0x3B), "F2": (0x71, 0x3C), "T": (0x54, 0x14), "BACK": (0x08, 0x0E), "ENTER": (0x0D, 0x1C),
        "ESCAPE": (0x1B, 0x01), "K": (0x4B, 0x25)}
# The same keys as SDL names them (scan code, key code), for the games drawn through it (26.3 on).
SDL_KEYS = {"F1": (58, 0x4000003A), "F2": (59, 0x4000003B), "T": (23, 0x74), "BACK": (42, 0x08), "ENTER": (40, 0x0D),
            "ESCAPE": (41, 0x1B), "K": (14, 0x6B)}

# Every game runs with `agent/BenchAgent`: by window, the folder it reads. It sets Iris's next
# shader pack, and types for the games drawn through SDL (26.3 on): SDL hands a key to a game only
# while its window is the desktop's foreground one, which the bench never takes (the game would
# take the user's mouse with it).
AGENTS: dict[int, Path] = {}
SDL_WINDOWS: set[int] = set()
_sent = [0]


def sdl(game: str) -> bool:
    """Whether a game version draws its window through SDL rather than GLFW."""
    return tuple(int(n) for n in re.findall(r"\d+", game)[:2]) >= (26, 3)


def agent_jar(home: Path) -> Path:
    """The agent's jar, built from its source with the JDK's compiler the first time and whenever
    the source changes."""
    import hashlib
    import zipfile
    source = Path(__file__).with_name("agent") / "BenchAgent.java"
    digest = hashlib.sha256(source.read_bytes()).hexdigest()[:12]
    jar = home / "tools" / f"bench-agent-{digest}.jar"
    if jar.is_file():
        return jar
    build = home / "tools" / f"agent-build-{os.getpid()}"
    shutil.rmtree(build, ignore_errors=True)
    build.mkdir(parents=True)
    subprocess.run([javac(), "--release", "17", "-d", str(build), str(source)], check=True)
    part = jar.with_suffix(f".part{os.getpid()}")
    with zipfile.ZipFile(part, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nPremain-Class: BenchAgent\n")
        for path in build.rglob("*.class"):
            archive.write(path, path.relative_to(build).as_posix())
    part.replace(jar)
    shutil.rmtree(build, ignore_errors=True)
    return jar


def javac() -> str:
    """A Java compiler: JAVA_HOME's, the PATH's, or one Gradle downloaded."""
    candidates = [Path(os.environ["JAVA_HOME"]) / "bin" / "javac.exe"] if os.environ.get("JAVA_HOME") else []
    found = shutil.which("javac")
    candidates += [Path(found)] if found else []
    candidates += sorted((Path.home() / ".gradle" / "jdks").glob("*/bin/javac.exe"), reverse=True)
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    sys.exit("bench: no Java compiler (javac) for the agent the games of 26.3 on need: install a JDK")


def tell_agent(hwnd: int, lines: list[str]) -> None:
    """Drops a file of actions where the game's agent reads them, written aside then renamed."""
    inbox = AGENTS[hwnd]
    inbox.mkdir(parents=True, exist_ok=True)
    _sent[0] += 1
    part = inbox / f"{_sent[0]:08d}.part"
    part.write_text("\n".join(lines) + "\n", "utf-8")
    part.rename(part.with_suffix(".txt"))


def press(hwnd: int, key: str) -> None:
    """A key straight into the game's window: no focus needed, nothing else on the desktop touched."""
    if hwnd in SDL_WINDOWS:
        tell_agent(hwnd, [f"key {SDL_KEYS[key][0]} {SDL_KEYS[key][1]}"])
        time.sleep(0.1)
        return
    import ctypes
    vk, scan = KEYS[key]
    user32 = ctypes.windll.user32
    user32.PostMessageW(hwnd, 0x0100, vk, 1 | (scan << 16))
    time.sleep(0.03)
    user32.PostMessageW(hwnd, 0x0101, vk, 1 | (scan << 16) | (1 << 30) | (1 << 31))
    time.sleep(0.03)


def command(hwnd: int, text: str) -> None:
    """Types a command in the chat and runs it."""
    import ctypes
    user32 = ctypes.windll.user32
    press(hwnd, "T")
    time.sleep(0.25)
    if hwnd in SDL_WINDOWS:
        tell_agent(hwnd, [f"text {text}"])
        time.sleep(0.1)
        press(hwnd, "ENTER")
        return
    # The T that opened the chat may have been typed into it too.
    for _ in range(2):
        press(hwnd, "BACK")
    for char in text:
        user32.PostMessageW(hwnd, 0x0102, ord(char), 1)
    time.sleep(0.1)
    press(hwnd, "ENTER")


def teleport(hwnd: int, log: Path, x, y, z, yaw, pitch, dimension: str = "minecraft:overworld") -> bool:
    """Types the teleport and checks in the game's log that it ran. A game may be paused behind a
    menu (another game's window took the focus as it opened): Escape closes it, then again."""
    release(hwnd)

    def done() -> int:
        return log.read_text("utf-8", "replace").count("Teleported ") if log.is_file() else 0

    for attempt in range(4):
        before = done()
        command(hwnd, f"/execute in {dimension} run tp @s {x} {y} {z} {yaw} {pitch}")
        deadline = time.time() + 10
        while time.time() < deadline:
            if done() > before:
                return True
            time.sleep(0.05)
        press(hwnd, "ESCAPE")
        time.sleep(1)
    return False


def data_version(home: Path, game: str) -> int | None:
    """The game's data version, from its jar: options.txt says it, or the game takes the file for an old one."""
    import zipfile
    jar = minecraft_dir(home) / "versions" / game / f"{game}.jar"
    try:
        with zipfile.ZipFile(jar) as archive:
            return int(json.loads(archive.read("version.json"))["world_version"])
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return None


def shoot(hwnd: int, shots: Path, known: set[Path], seconds: float = 10) -> Path | None:
    """One of the game's screenshots (F2), once it is written; None when none comes."""
    press(hwnd, "F2")
    deadline = time.time() + seconds
    while time.time() < deadline:
        new = sorted(set(shots.glob("*.png")) - known, key=lambda p: p.stat().st_mtime) if shots.is_dir() else []
        if new and written(new[-1]):
            known.update(new)
            return new[-1]
        time.sleep(0.05)
    return None


def settled_pair(hwnd: int, shots: Path, known: set[Path], most: float = 10.0,
                 still: float = 0.003, first: float = 10) -> tuple[list[Path] | None, float]:
    """Screenshots one after another until two in a row match: the chunks round a viewpoint just
    reached are drawn over a second or two, and a picture taken before misses them (holes in the
    ground, the far terrain not there yet). Matching: fewer than `still` of the pixels differ by more
    than a shader's own noise. After `most` seconds the last two are taken all the same. The pair and
    the seconds it took."""
    import numpy as np
    from PIL import Image
    started = time.time()
    # The first may wait for a shader pack compiling: the game draws nothing meanwhile.
    previous = shoot(hwnd, shots, known, first)
    if previous is None:
        return None, 0.0
    drawing = time.time()
    matches = 0
    while True:
        current = shoot(hwnd, shots, known)
        if current is None:
            return None, time.time() - started
        with Image.open(previous) as a, Image.open(current) as b:
            new = np.asarray(b.convert("RGB"), dtype=np.int16)
            moved = (np.abs(np.asarray(a.convert("RGB"), dtype=np.int16) - new).max(axis=2) > 12).mean()
        # Twice in a row: chunks are built in bursts, and two frames between two bursts match too.
        # A picture with sky where the ground should be is still loading, however still it stands.
        matches = matches + 1 if moved < still and not holes(new) else 0
        waited = time.time() - drawing
        if matches >= 2 or waited > most and (waited > HOLES_MOST or not holes(new)):
            return [previous, current], round(time.time() - started, 1)
        previous = current


# Seconds a picture with holes is waited for at most, past `most`.
HOLES_MOST = 40


def holes(picture) -> bool:
    """Whether the ground in front of the camera is missing: in the Overworld every viewpoint has
    the ground at the bottom of the picture, and a chunk not yet received or built shows the sky
    there instead, as still as any finished picture. Sky-blue in more than a third of the bottom
    tenth (the Nether and the End have no blue sky: never there)."""
    bottom = picture[-max(1, picture.shape[0] // 10):]
    r, g, b = bottom[..., 0], bottom[..., 1], bottom[..., 2]
    return float(((b > g + 15) & (b > r + 40) & (b > 140)).mean()) > 0.33


def written(picture: Path) -> bool:
    """Whether the game has finished writing a screenshot: it saves them on a thread of its own."""
    try:
        from PIL import Image
        with Image.open(picture) as image:
            image.load()
        return True
    except Exception:
        return False


import threading

# Games start one after the other, this many seconds apart: two of one version starting together
# unpack the same native libraries and the second fails, and the starts are the heaviest part.
STARTING = threading.Lock()
STAGGER = 15


def close(process, hwnd: int, seconds: int = 120) -> bool:
    """Closes the game as its window's close button does: it saves the world, and the far terrain
    mods write their databases out. Whether it ended by itself in time."""
    import ctypes
    ctypes.windll.user32.PostMessageW(hwnd, 0x0010, 0, 0)  # WM_CLOSE
    try:
        process.wait(timeout=seconds)
        return True
    except subprocess.TimeoutExpired:
        return False


# --- The far terrain, built once --------------------------------------------------------------

def lod_key(mods: list[Path]) -> str | None:
    """What the far terrain kept for a launch depends on: the far terrain mods' jars. None without one."""
    names = sorted(jar.stem for jar in mods if re.search(r"distanthorizons|voxy", jar.name, re.IGNORECASE))
    return re.sub(r"[^A-Za-z0-9.+_-]", "_", "_".join(names)) if names else None


def lod_cache(home: Path, game: str, key: str) -> Path:
    return home / "worlds" / game / f"scene-{SCENE_VERSION}-far" / key


def lod_files(world: Path) -> list[Path]:
    """The far terrain mods' files in a world: Distant Horizons' databases (one per dimension),
    Voxy's folder."""
    found = [p for p in world.rglob("DistantHorizons*") if p.is_file()]
    if (world / "voxy").is_dir():
        found += [p for p in (world / "voxy").rglob("*") if p.is_file()]
    return found


def keep_lod(game_dir: Path, cache: Path) -> int:
    """Stores the far terrain a warm-up built; how many files. Written aside, then moved in place,
    so that two warm-ups at once never leave half of one."""
    world = game_dir / "saves" / "lh-scene"
    files = lod_files(world)
    if not files or cache.is_dir():
        return len(files)
    aside = cache.with_name(cache.name + f".part{os.getpid()}")
    for path in files:
        to = aside / path.relative_to(world)
        to.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, to)
    try:
        aside.rename(cache)
    except OSError:
        shutil.rmtree(aside, ignore_errors=True)
    return len(files)


def restore_lod(game_dir: Path, cache: Path) -> None:
    shutil.copytree(cache, game_dir / "saves" / "lh-scene", dirs_exist_ok=True)


def pids(root: int) -> set[int]:
    import psutil
    try:
        process = psutil.Process(root)
        return {root} | {c.pid for c in process.children(recursive=True)}
    except psutil.NoSuchProcess:
        return set()


def launch(home: Path, version: str, game: str, game_dir: Path, settle: int, wait: float, out: Path,
           warm: bool = False, packs: list[Path] = (), shader_settle: int = 40, voxy: bool = False,
           memory: str = "3G", timeout: int = 900) -> dict:
    """Starts the game in the scene, lets it settle, then goes from viewpoint to viewpoint and takes
    a pair of pictures at each, without shaders, then once more with each pack; closes the game.
    `warm`: a tour of the scene instead of the pictures, so that the far terrain mods build all of
    it, then a clean close so that they write it out."""
    import minecraft_launcher_lib as mll
    from bench import stop_tree
    arguments = [f"-Xmx{memory}"]
    inbox = game_dir / "lh-bench-inbox"
    arguments.append(f"-javaagent:{agent_jar(home)}={inbox}")
    options = {"username": USERNAME, "uuid": PLAYER_UUID, "token": "0", "gameDirectory": str(game_dir),
               "executablePath": java_for(game, home), "jvmArguments": arguments,
               "customResolution": True, "resolutionWidth": str(WIDTH), "resolutionHeight": str(HEIGHT),
               "quickPlaySingleplayer": "lh-scene", "launcherName": "lh-bench"}
    argv = mll.command.get_minecraft_command(version, str(minecraft_dir(home)), options)
    with STARTING:
        started = time.time()
        with open(out / "launcher.log", "w", encoding="utf-8", errors="replace") as log:
            process = subprocess.Popen(argv, cwd=game_dir, stdout=log, stderr=subprocess.STDOUT)
        time.sleep(STAGGER)
    latest = game_dir / "logs" / "latest.log"
    joined = None
    hwnd = None
    result: dict = {"ok": False, "stopped": None, "shots": []}
    guard = Guard(process.pid)
    try:
        while process.poll() is None and time.time() - started < timeout:
            time.sleep(2)
            text = latest.read_text("utf-8", "replace") if latest.is_file() else ""
            broken = re.search(r"^.*(Error during pre-loading phase|ModLoadingException.*|Minecraft Crash Report|"
                               r"Crash report saved to.*|.*crash report has been saved.*|Mixin apply.*failed.*|"
                               r"Incompatible mods found.*|Incompatible mod set.*|"
                               r"ModResolutionException.*|.*/FATAL\].*)$", text, re.MULTILINE)
            if broken:
                result["stopped"] = "the game broke: " + broken.group(0).strip()[-300:]
                return result
            if joined is None and re.search(rf"{USERNAME} joined the game", text):
                joined = time.time()
                say(f"  in the world after {int(joined - started)} s, settling {settle} s")
            if joined and time.time() - joined >= settle:
                break
        if process.poll() is not None or joined is None:
            result["stopped"] = "the game ended early" if process.poll() is not None else "never reached the world"
            return result
        windows = windows_of(pids(process.pid))
        if not windows:
            result["stopped"] = "no game window found"
            return result
        hwnd = windows[0]
        AGENTS[hwnd] = inbox
        if sdl(game):
            SDL_WINDOWS.add(hwnd)
        shots = game_dir / "screenshots"
        pictures = time.time()
        if warm and voxy:
            # Voxy reads the save into its terrain, one dimension at a time: the one the player is in.
            for dimension in ["minecraft:overworld", *OTHER_TOURS]:
                if dimension != "minecraft:overworld":
                    x, y, z = OTHER_TOURS[dimension][0]
                    if not teleport(hwnd, latest, x, y, z, 0, 0, dimension):
                        result["stopped"] = f"the teleport to {dimension} never ran"
                        return result
                    time.sleep(TOUR_WAIT)
                took = voxy_import(hwnd, latest, game_dir)
                if took is None:
                    result["stopped"] = f"Voxy's import of {dimension} never ran or never ended: its terrain is not kept"
                    return result
                say(f"  Voxy imported {dimension} in {took} s")
            time.sleep(TOUR_WAIT)
            result["ok"] = close(process, hwnd)
            if not result["ok"]:
                result["stopped"] = "the game did not close by itself: its far terrain may be half written"
            return result
        if warm:
            tours = [("minecraft:overworld", TOUR, 0)] + [(d, t, OTHER_WAIT) for d, t in OTHER_TOURS.items()]
            for dimension, tour, first in tours:
                for n, (x, y, z) in enumerate(tour):
                    if not teleport(hwnd, latest, x, y, z, 0, 0, dimension):
                        result["stopped"] = f"the tour's teleport in {dimension} never ran"
                        return result
                    time.sleep(first if n == 0 and first else TOUR_WAIT)
            # Back to where the pictures start, the terrain around built too.
            x, y, z = TOUR[-1]
            teleport(hwnd, latest, x, y, z, 0, 0)
            time.sleep(TOUR_WAIT * 2)
            result["ok"] = close(process, hwnd)
            if not result["ok"]:
                result["stopped"] = "the game did not close by itself: its far terrain may be half written"
            return result
        press(hwnd, "F1")
        # Without shaders, then with each pack: changed in the same game (Iris's settings read again
        # on its reload key, Oculus's too).
        for pack in [None, *packs]:
            prefix = pack_label(pack) + "-" if pack else ""
            if pack:
                # Turned on where the pictures start: the pack compiles for that dimension.
                x, y, z, yaw, pitch = next(iter(VIEWPOINTS.values()))
                teleport(hwnd, latest, x, y, z, yaw, pitch)
                time.sleep(wait)
                turned = time.time()
                if not shaders_on(hwnd, latest, game_dir, pack.name):
                    result["stopped"] = f"the shader pack {pack.name} never loaded"
                    return result
                time.sleep(3)
                # The pack compiles, then its frames settle (temporal effects): until two match.
                settled_pair(hwnd, shots, set(shots.glob("*.png")) if shots.is_dir() else set(),
                             most=shader_settle, still=SHADED_STILL)
                say(f"  {pack_label(pack)} on and settled in {int(time.time() - turned)} s")
            here = "minecraft:overworld"
            for name, (x, y, z, yaw, pitch) in VIEWPOINTS.items():
                dimension = DIMENSIONS.get(name, "minecraft:overworld")
                name = prefix + name
                if process.poll() is not None:
                    result["stopped"] = f"the game ended before {name}"
                    return result
                before = set(shots.glob("*.png")) if shots.is_dir() else set()
                if not teleport(hwnd, latest, x, y, z, yaw, pitch, dimension):
                    result["stopped"] = f"{name}: the teleport never ran"
                    return result
                # Into another dimension: its world loaded, its far terrain read from disk, and the
                # shader pack compiled again for it (Iris makes a pipeline per dimension).
                moved = dimension != here
                here = dimension
                time.sleep(wait + (5 if moved else 0))
                result.setdefault("terrain", {})[name] = terrain_ready(hwnd, game_dir)
                pair, waited = settled_pair(hwnd, shots, before, most=20 if moved else 10,
                                            still=SHADED_STILL if pack else PLAIN_STILL,
                                            first=60 if moved else 10)
                if not pair:
                    result["stopped"] = f"{name}: no screenshot came"
                    return result
                shutil.copy2(pair[0], out / f"{name}-a.png")
                shutil.copy2(pair[1], out / f"{name}-b.png")
                result["shots"].append(name)
                result.setdefault("waited", {})[name] = waited
        say(f"  {len(result['shots'])} viewpoints in {int(time.time() - pictures)} s")
        result["ok"] = True
        return result
    finally:
        guard.stop()
        AGENTS.pop(hwnd, None)
        SDL_WINDOWS.discard(hwnd)
        stop_tree(process.pid)
        try:
            process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            pass
        result["seconds"] = int(time.time() - started)
        if latest.is_file():
            shutil.copy2(latest, out / "game.log")


def terrain_ready(hwnd: int, game_dir: Path, most: float = 40) -> str:
    """Waits until the game has built the chunks round the camera, as Sodium tells the agent: its
    build queue empty and no section more for a second. A picture taken before misses chunks, and
    Voxy or Distant Horizons fill the hole with their own terrain, as still as a finished picture:
    the two releases then differ where nothing changed. What Sodium said last, for the run's record."""
    answer_file = game_dir / "lh-bench-ready.txt"
    started = time.time()
    counts: list[int] = []
    said = "?"
    while time.time() - started < most:
        answer_file.unlink(missing_ok=True)
        tell_agent(hwnd, [f"ready {answer_file}"])
        deadline = time.time() + 2
        while not answer_file.is_file() and time.time() < deadline:
            time.sleep(0.05)
        try:
            said = answer_file.read_text("utf-8").strip()
        except OSError:
            said = "? no answer"
        if said.startswith("?"):
            return said  # no Sodium, or no world: nothing to wait for
        built, sections = said.split()
        counts = counts + [int(sections)] if built == "1" else []
        if len(counts) >= 4 and len(set(counts[-4:])) == 1:
            return said
        time.sleep(0.25)
    return f"{said} after {most:.0f} s, not built"


def pack_label(pack: Path) -> str:
    """A shader pack's short name, the prefix of its pictures: the first word of its Modrinth
    project, the folder it was downloaded to (photon, complementary)."""
    return pack.parent.name.split("-")[0]


def shaders_on(hwnd: int, log: Path, game_dir: Path, pack: str) -> bool:
    """Turns a shader pack on in the running game: the agent sets it as Iris's pack (shaders off),
    then Iris's toggle key turns them on with it; again until its log says it uses that pack. Its
    reload key would read the settings file again, but works only with Iris's debug options on,
    which change how it draws."""

    def loaded() -> int:
        return log.read_text("utf-8", "replace").count(f"Using shaderpack: {pack}") if log.is_file() else 0

    before = loaded()
    for attempt in range(3):
        tell_agent(hwnd, [f"iris {pack}"])
        time.sleep(0.5)
        press(hwnd, "K")
        deadline = time.time() + 30
        while time.time() < deadline:
            if loaded() > before:
                return True
            time.sleep(0.2)
    return False


def voxy_import(hwnd: int, latest: Path, game_dir: Path) -> int | None:
    """Has Voxy import the save's region files of the player's dimension; the seconds it took, None
    when it never ran or never ended."""
    before = latest.read_text("utf-8", "replace") if latest.is_file() else ""
    command(hwnd, "/voxy import world lh-scene")
    started = typed = time.time()
    tries = 1
    deadline = time.time() + 300
    store = game_dir / "saves" / "lh-scene" / "voxy"
    sizes: list[int] = []
    while time.time() < deadline:
        time.sleep(2)
        text = latest.read_text("utf-8", "replace")[len(before):]
        if re.search(r"(?i)import finished", text):
            return int(time.time() - started)
        # Or once its folder has grown and then not for ten seconds: the message may be shown on
        # screen only.
        sizes.append(sum(p.stat().st_size for p in store.rglob("*") if p.is_file()) if store.is_dir() else 0)
        # Nothing written and nothing said 20 s after typing it: the command did not arrive.
        if sizes[-1] == sizes[0] and not re.search(r"(?i)import", text) and time.time() - typed > 20 and tries < 3:
            command(hwnd, "/voxy import world lh-scene")
            typed, tries, sizes = time.time(), tries + 1, []
            continue
        if len(sizes) > 6 and sizes[-1] > sizes[0] and len(set(sizes[-6:])) == 1:
            return int(time.time() - started)
    return None


def warm_up(home: Path, node: str, tables: dict, jar: Path, extra_mods: list[Path], packs: list[Path], out: Path,
            settle: int, version_of_loader: str | None, instance: str) -> str:
    """Builds the far terrain of the scene once for these mods and keeps it; what happened."""
    game, loader = node_parts(node, tables)
    key = lod_key(extra_mods)
    cache = lod_cache(home, game, key)
    if cache.is_dir():
        return "far terrain already built"
    version = install(game, loader, home, version_of_loader)
    world = scene(home, game)
    out.mkdir(parents=True, exist_ok=True)
    game_dir = home / "instances-jar" / instance
    prepare(game_dir, [jar, *extra_mods], world, packs, data_version(home, game))
    result = launch(home, version, game, game_dir, settle, 0, out, warm=True,
                    voxy=any("voxy" in jar.name.lower() for jar in extra_mods))
    if not result["ok"]:
        return f"warm-up failed: {result['stopped']}"
    return f"far terrain built and kept ({keep_lod(game_dir, cache)} files, {round(result['seconds'] / 60, 1)} min)"


def play(home: Path, node: str, tables: dict, jar: Path, extra_mods: list[Path], packs: list[Path], out: Path,
         settle: int, wait: float, version_of_loader: str | None, instance: str, shader_settle: int = 40,
         memory: str = "3G") -> dict:
    """One jar on one node with a set of mods: one launch, every viewpoint. The far terrain a
    warm-up built is put back into the world first."""
    game, loader = node_parts(node, tables)
    version = install(game, loader, home, version_of_loader)
    world = scene(home, game)
    out.mkdir(parents=True, exist_ok=True)
    # A folder per game: several run at once.
    game_dir = home / "instances-jar" / instance
    prepare(game_dir, [jar, *extra_mods], world, packs, data_version(home, game))
    key = lod_key(extra_mods)
    if key and lod_cache(home, game, key).is_dir():
        restore_lod(game_dir, lod_cache(home, game, key))
    result = launch(home, version, game, game_dir, settle, wait, out, packs=packs,
                    shader_settle=shader_settle, memory=memory)
    failures = [result["stopped"]] if result["stopped"] else []
    if not result["ok"] and (out / "game.log").is_file():
        text = (out / "game.log").read_text("utf-8", "replace")
        failures += [l.strip() for l in text.splitlines() if re.search(r"Exception|ERROR|FATAL", l)][:8]
    return {"ok": result["ok"], "stopped": result["stopped"], "failures": failures, "shots": result["shots"],
            "stats": {}, "waited": result.get("waited", {}), "terrain": result.get("terrain", {}),
            "minutes": round(result.get("seconds", 0) / 60, 1)}
