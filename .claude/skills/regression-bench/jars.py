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

SCENE_VERSION = 3
SEED = "20261014"
USERNAME = "LhBench"
PLAYER_UUID = "6c68be6e-6368-4c68-9265-6e6368000001"
WIDTH, HEIGHT = 854, 480

# Short, so that the mountain is far terrain (Distant Horizons', Voxy's) from its viewpoints.
RENDER_DISTANCE = 4
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
}

# A tour of the scene before the pictures: Voxy builds its terrain only from the chunks the game
# loads, so every part of the scene is loaded once, the mountain included. The same for every
# profile, so that the mobs met on the way are the same too.
TOUR = [(0.5, -58.4, -90.5), (0.5, -58.4, 120.5), (64.5, -58.4, 240.5), (0.5, -58.4, 330.5), (0.5, -58.4, 440.5),
        (0.5, -58.4, 0.5)]
TOUR_WAIT = 5

# Two screenshots in a row match when fewer pixels than this share differ: a shader pack's own
# grain (Photon's) keeps a few percent moving forever.
PLAIN_STILL, SHADED_STILL = 0.003, 0.03

# Mobs whose look never changes on its own with no AI: a horse grazes, a chicken lays eggs.
KINDS = ["cow", "sheep", "pig", "villager", "iron_golem"]
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
    to = home / "jars" / tag / node / "livinghorizon.jar"
    if to.is_file():
        return to
    release = github(f"releases/tags/{tag}", repository)
    for asset in release.get("assets", []):
        if asset["name"].endswith(f"+mc{base}{suffix}.jar"):
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
            kind = KINDS[(i + z) % len(KINDS)]
            yaw = (i * 45) % 360
            extra = ""
            if kind == "sheep":
                extra = ",Color:0b"
            elif kind == "villager":
                extra = ',VillagerData:{type:"minecraft:plains",profession:"minecraft:none",level:1}'
            commands.append(f"summon minecraft:{kind} {x}.5 {FEET} {z}.5 {{NoAI:1b,Silent:1b,PersistenceRequired:1b,"
                            f"Rotation:[{yaw}f,0f],UUID:[I;1818650722,1701015656,0,{number}]{extra}}}")
    return commands


def golem(x: int, z: int, yaw: int, number: int) -> str:
    """An iron golem: tall, pale against the stone, and still without AI."""
    return (f"summon minecraft:iron_golem {x}.5 {FEET} {z}.5 {{NoAI:1b,Silent:1b,PersistenceRequired:1b,"
            f"Rotation:[{yaw}f,0f],UUID:[I;1818650722,1701015656,1,{number}]}}")


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
        "spawn-protection=0", "online-mode=false", "difficulty=peaceful", "gamemode=spectator",
        "view-distance=10", "spawn-animals=false", "spawn-npcs=false", "spawn-monsters=false", ""]))
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

    def loaded(x: int, z: int) -> bool:
        run(f"execute if loaded {x} 0 {z}")
        return "passed" in wait_for(r"Test (passed|failed)", 60)

    commands = scene_commands()
    summons = sum(1 for c in commands if c.startswith("summon"))
    try:
        wait_for(r"Done \(", 600)
        # Every chunk the scene shows generated and loaded before anything is placed: a mob summoned
        # where the chunk's entities are not loaded yet is lost when the chunk is let go.
        batches = [(cx * 16, cz * 16, (cx + 15) * 16, (cz + 15) * 16)
                   for cx in range(-28, 28, 16) for cz in range(-12, 36, 16)]
        for x1, z1, x2, z2 in batches:
            run(f"forceload add {x1} {z1} {x2} {z2}")
        for x1, z1, x2, z2 in batches:
            for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2), ((x1 + x2) // 2, (z1 + z2) // 2)):
                deadline = time.time() + 600
                while not loaded(x, z):
                    if time.time() > deadline:
                        raise RuntimeError(f"the chunk at {x}, {z} never loaded")
                    time.sleep(2)
        # The entities load a little after the blocks.
        time.sleep(20)
        run("kill @e[type=!minecraft:player]")
        time.sleep(5)
        run("kill @e[type=!minecraft:player]")
        time.sleep(2)
        for command in commands:
            run(command)
        run("say scene built")
        wait_for(r"scene built", 300)
        run("execute if entity @e[type=!minecraft:player]")
        answer = wait_for(r"Test (passed|failed)", 60)
        count = re.search(r"count: (\d+)", answer, re.IGNORECASE)  # "Count: N" from 1.21.11
        found = int(count.group(1)) if count else 0
        if found != summons:
            raise RuntimeError(f"the scene holds {found} mobs, {summons} were summoned")
        say(f"  {found} mobs placed")
        run("forceload remove all")
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

def prepare(game_dir: Path, mods: list[Path], world: Path, pack: Path | None, version: int | None) -> None:
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
        'graphicsPreset:"custom"', 'inactivityFpsLimit:"minimized"', "textBackgroundOpacity:0.0", "chatScale:0.0", ""]), "utf-8")
    (game_dir / "config").mkdir()
    config = {"version": 1000, "skyBirds": False, "easterEggs": False, "offlineLife": False,
              "animationMaxDistance": 1, "seedMobs": False, "debugHud": False}
    (game_dir / "config" / "livinghorizon.json").write_text(json.dumps(config, indent=2), "utf-8")
    # Distant Horizons draws clouds of its own, which drift with time: no two runs would match.
    (game_dir / "config" / "DistantHorizons.toml").write_text(
        "[client.advanced.graphics.genericRendering]\nenableCloudRendering = false\n", "utf-8")
    if pack:
        (game_dir / "shaderpacks").mkdir()
        shutil.copy2(pack, game_dir / "shaderpacks" / pack.name)
        # Iris, and Oculus (Iris on Forge) under its own name.
        for name in ("iris.properties", "oculus.properties"):
            (game_dir / "config" / name).write_text(f"enableShaders=false\nshaderPack={pack.name}\n", "utf-8")


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


def lock(pid_list: set[int], locked: set[int]) -> None:
    """Turns the game's windows off for the mouse and the keyboard: a click would give one the focus,
    the game would take the mouse and the camera would turn with it; a drag would move it. The keys
    the bench sends go straight to the window and still arrive."""
    import ctypes
    for hwnd in windows_of(pid_list, titled=False):
        if hwnd not in locked:
            ctypes.windll.user32.EnableWindow(hwnd, False)
            locked.add(hwnd)
    for hwnd in locked:
        release(hwnd)


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


def press(hwnd: int, key: str) -> None:
    """A key straight into the game's window: no focus needed, nothing else on the desktop touched."""
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
    # The T that opened the chat may have been typed into it too.
    for _ in range(2):
        press(hwnd, "BACK")
    for char in text:
        user32.PostMessageW(hwnd, 0x0102, ord(char), 1)
    time.sleep(0.1)
    press(hwnd, "ENTER")


def teleport(hwnd: int, log: Path, x, y, z, yaw, pitch) -> bool:
    """Types the teleport and checks in the game's log that it ran. A game may be paused behind a
    menu (another game's window took the focus as it opened): Escape closes it, then again."""
    release(hwnd)

    def done() -> int:
        return log.read_text("utf-8", "replace").count("Teleported ") if log.is_file() else 0

    for attempt in range(4):
        before = done()
        command(hwnd, f"/tp @s {x} {y} {z} {yaw} {pitch}")
        deadline = time.time() + 4
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


def shoot(hwnd: int, shots: Path, known: set[Path]) -> Path | None:
    """One of the game's screenshots (F2), once it is written; None when none comes."""
    press(hwnd, "F2")
    deadline = time.time() + 10
    while time.time() < deadline:
        new = sorted(set(shots.glob("*.png")) - known, key=lambda p: p.stat().st_mtime) if shots.is_dir() else []
        if new and written(new[-1]):
            known.update(new)
            return new[-1]
        time.sleep(0.05)
    return None


def settled_pair(hwnd: int, shots: Path, known: set[Path], most: float = 10.0,
                 still: float = 0.003) -> tuple[list[Path] | None, float]:
    """Screenshots one after another until two in a row match: the chunks round a viewpoint just
    reached are drawn over a second or two, and a picture taken before misses them (holes in the
    ground, the far terrain not there yet). Matching: fewer than `still` of the pixels differ by more
    than a shader's own noise. After `most` seconds the last two are taken all the same. The pair and
    the seconds it took."""
    import numpy as np
    from PIL import Image
    started = time.time()
    previous = shoot(hwnd, shots, known)
    if previous is None:
        return None, 0.0
    matches = 0
    while True:
        current = shoot(hwnd, shots, known)
        if current is None:
            return None, time.time() - started
        with Image.open(previous) as a, Image.open(current) as b:
            moved = (np.abs(np.asarray(a.convert("RGB"), dtype=np.int16)
                            - np.asarray(b.convert("RGB"), dtype=np.int16)).max(axis=2) > 12).mean()
        # Twice in a row: chunks are built in bursts, and two frames between two bursts match too.
        matches = matches + 1 if moved < still else 0
        if matches >= 2 or time.time() - started > most:
            return [previous, current], round(time.time() - started, 1)
        previous = current


def written(picture: Path) -> bool:
    """Whether the game has finished writing a screenshot: it saves them on a thread of its own."""
    try:
        from PIL import Image
        with Image.open(picture) as image:
            image.load()
        return True
    except Exception:
        return False


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
           warm: bool = False, shaders: bool = False, shader_settle: int = 40, voxy: bool = False,
           timeout: int = 900) -> dict:
    """Starts the game in the scene, lets it settle, then goes from viewpoint to viewpoint and takes
    a pair of pictures at each; closes the game. `warm`: a tour of the scene instead of the pictures,
    so that the far terrain mods build all of it, then a clean close so that they write it out."""
    import minecraft_launcher_lib as mll
    from bench import stop_tree
    options = {"username": USERNAME, "uuid": PLAYER_UUID, "token": "0", "gameDirectory": str(game_dir),
               "executablePath": java_for(game, home), "jvmArguments": ["-Xmx3G"],
               "customResolution": True, "resolutionWidth": str(WIDTH), "resolutionHeight": str(HEIGHT),
               "quickPlaySingleplayer": "lh-scene", "launcherName": "lh-bench"}
    argv = mll.command.get_minecraft_command(version, str(minecraft_dir(home)), options)
    started = time.time()
    with open(out / "launcher.log", "w", encoding="utf-8", errors="replace") as log:
        process = subprocess.Popen(argv, cwd=game_dir, stdout=log, stderr=subprocess.STDOUT)
    latest = game_dir / "logs" / "latest.log"
    joined = None
    result: dict = {"ok": False, "stopped": None, "shots": []}
    try:
        locked: set[int] = set()
        while process.poll() is None and time.time() - started < timeout:
            time.sleep(2)
            lock(pids(process.pid), locked)
            text = latest.read_text("utf-8", "replace") if latest.is_file() else ""
            broken = re.search(r"^.*(Error during pre-loading phase|ModLoadingException.*|Minecraft Crash Report|"
                               r"Crash report saved to.*|Mixin apply.*failed.*|Incompatible mods found.*|Incompatible mod set.*|"
                               r"ModResolutionException.*)$", text, re.MULTILINE)
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
        lock(pids(process.pid), locked)
        shots = game_dir / "screenshots"
        pictures = time.time()
        if warm and voxy:
            # Voxy reads the whole save into its terrain: every chunk the scene holds, the mountain too.
            before = latest.read_text("utf-8", "replace") if latest.is_file() else ""
            command(hwnd, "/voxy import world lh-scene")
            deadline = time.time() + 300
            store = game_dir / "saves" / "lh-scene" / "voxy"
            sizes: list[int] = []
            while time.time() < deadline:
                time.sleep(2)
                text = latest.read_text("utf-8", "replace")[len(before):]
                if re.search(r"(?i)import finished", text):
                    break
                # Or once its folder has grown and then not for ten seconds: the message may be shown
                # on screen only.
                sizes.append(sum(p.stat().st_size for p in store.rglob("*") if p.is_file()) if store.is_dir() else 0)
                if len(sizes) > 6 and sizes[-1] > sizes[0] and len(set(sizes[-6:])) == 1:
                    break
            say(f"  Voxy imported the save in {int(time.time() - deadline + 300)} s")
            time.sleep(TOUR_WAIT)
            result["ok"] = close(process, hwnd)
            if not result["ok"]:
                result["stopped"] = "the game did not close by itself: its far terrain may be half written"
            return result
        if warm:
            for x, y, z in TOUR:
                if not teleport(hwnd, latest, x, y, z, 0, 0):
                    result["stopped"] = "the tour's teleport never ran"
                    return result
                time.sleep(TOUR_WAIT)
            # Back to where the pictures start, the terrain around built too.
            time.sleep(TOUR_WAIT * 2)
            result["ok"] = close(process, hwnd)
            if not result["ok"]:
                result["stopped"] = "the game did not close by itself: its far terrain may be half written"
            return result
        press(hwnd, "F1")
        # Without shaders, then with them: turned on in the same game (Iris's key, Oculus's too).
        for prefix in ["", "shaders-"] if shaders else [""]:
            if prefix:
                press(hwnd, "K")
                turned = time.time()
                time.sleep(3)
                # The pack compiles, then its frames settle (temporal effects): until two match.
                settled_pair(hwnd, shots, set(shots.glob("*.png")) if shots.is_dir() else set(),
                             most=shader_settle, still=SHADED_STILL)
                say(f"  shaders on and settled in {int(time.time() - turned)} s")
            for name, (x, y, z, yaw, pitch) in VIEWPOINTS.items():
                name = prefix + name
                if process.poll() is not None:
                    result["stopped"] = f"the game ended before {name}"
                    return result
                before = set(shots.glob("*.png")) if shots.is_dir() else set()
                if not teleport(hwnd, latest, x, y, z, yaw, pitch):
                    result["stopped"] = f"{name}: the teleport never ran"
                    return result
                time.sleep(wait)
                pair, waited = settled_pair(hwnd, shots, before, still=SHADED_STILL if prefix else PLAIN_STILL)
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
        stop_tree(process.pid)
        try:
            process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            pass
        result["seconds"] = int(time.time() - started)
        if latest.is_file():
            shutil.copy2(latest, out / "game.log")


def warm_up(home: Path, node: str, tables: dict, jar: Path, extra_mods: list[Path], pack: Path | None, out: Path,
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
    prepare(game_dir, [jar, *extra_mods], world, pack, data_version(home, game))
    result = launch(home, version, game, game_dir, settle, 0, out, warm=True,
                    voxy=any("voxy" in jar.name.lower() for jar in extra_mods))
    if not result["ok"]:
        return f"warm-up failed: {result['stopped']}"
    return f"far terrain built and kept ({keep_lod(game_dir, cache)} files, {round(result['seconds'] / 60, 1)} min)"


def play(home: Path, node: str, tables: dict, jar: Path, extra_mods: list[Path], pack: Path | None, out: Path,
         settle: int, wait: float, version_of_loader: str | None, instance: str, shader_settle: int = 40) -> dict:
    """One jar on one node with a set of mods: one launch, every viewpoint. The far terrain a
    warm-up built is put back into the world first."""
    game, loader = node_parts(node, tables)
    version = install(game, loader, home, version_of_loader)
    world = scene(home, game)
    out.mkdir(parents=True, exist_ok=True)
    # A folder per game: several run at once.
    game_dir = home / "instances-jar" / instance
    prepare(game_dir, [jar, *extra_mods], world, pack, data_version(home, game))
    key = lod_key(extra_mods)
    if key and lod_cache(home, game, key).is_dir():
        restore_lod(game_dir, lod_cache(home, game, key))
    result = launch(home, version, game, game_dir, settle, wait, out, shaders=pack is not None,
                    shader_settle=shader_settle)
    failures = [result["stopped"]] if result["stopped"] else []
    if not result["ok"] and (out / "game.log").is_file():
        text = (out / "game.log").read_text("utf-8", "replace")
        failures += [l.strip() for l in text.splitlines() if re.search(r"Exception|ERROR|FATAL", l)][:8]
    return {"ok": result["ok"], "stopped": result["stopped"], "failures": failures, "shots": result["shots"],
            "stats": {}, "waited": result.get("waited", {}),
            "minutes": round(result.get("seconds", 0) / 60, 1)}
