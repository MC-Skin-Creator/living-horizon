#!/usr/bin/env python3
"""The regression bench: plays the `regression` game test scenario on every game version, once
per set of mods (a profile: Sodium, Voxy, Distant Horizons, Iris with a shader pack...), and
compares the pictures and numbers with the last approved run and between versions.

Each game version is installed once (the node's Gradle run); a profile is only a folder of mods
Fabric Loader is pointed at (-Plh.mods, fabric.addMods). See SKILL.md next to this file.

    python bench.py run [--ref latest-dev|develop|<tag or commit>|local] [--nodes a,b] [--profiles x,y] [--perf]
    python bench.py compare <run>
    python bench.py approve <run> [--nodes a,b] [--profiles x,y]
    python bench.py watch            # runs the newest develop pre-release, if not done yet
    python bench.py mods [--update]  # the pinned mod versions
    python bench.py runs             # the runs kept

Everything lives in LH_BENCH_HOME (default: ~/living-horizon-bench): the clone that is built,
the mods, the instances, the results and the baselines.
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import tomllib
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
HOME = Path(os.environ.get("LH_BENCH_HOME", Path.home() / "living-horizon-bench")).resolve()
WINDOWS = os.name == "nt"
SCENARIO = "regression"
USER_AGENT = "living-horizon-regression-bench"


# --- Settings ----------------------------------------------------------------------------------

def settings() -> dict:
    with open(HERE / "bench.toml", "rb") as f:
        config = tomllib.load(f)
    own = HOME / "bench.toml"
    if own.is_file():
        with open(own, "rb") as f:
            for key, value in tomllib.load(f).items():
                if isinstance(value, dict) and isinstance(config.get(key), dict):
                    for inner, v in value.items():
                        if isinstance(v, dict) and isinstance(config[key].get(inner), dict):
                            config[key][inner].update(v)
                        else:
                            config[key][inner] = v
                else:
                    config[key] = value
    return config


def say(line: str) -> None:
    print(f"bench: {line}", flush=True)


def pick(wanted: str | None, known: list[str], what: str) -> list[str]:
    if not wanted:
        return known
    chosen = [name.strip() for name in wanted.split(",") if name.strip()]
    for name in chosen:
        if name not in known:
            sys.exit(f"bench: no {what} {name}; known: {', '.join(known)}")
    return chosen


# --- The source that is built ------------------------------------------------------------------

def git(*args: str, cwd: Path) -> str:
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def github(path: str, repository: str):
    request = urllib.request.Request(f"https://api.github.com/repos/{repository}/{path}",
                                     headers={"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"})
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def latest_dev(repository: str) -> str:
    """The tag of the newest develop pre-release (vX.Y.Z-dev.N)."""
    for release in github("releases?per_page=30", repository):
        if release.get("prerelease") and "-dev." in release.get("tag_name", ""):
            return release["tag_name"]
    sys.exit("bench: no develop pre-release found")


def checkout(ref: str, config: dict) -> tuple[Path, str]:
    """The source to build and a label for the run. `local` builds the repository this script is in, as it is."""
    if ref == "local":
        sha = git("rev-parse", "--short", "HEAD", cwd=REPO)
        dirty = "-dirty" if git("status", "--porcelain", cwd=REPO) else ""
        return REPO, f"local-{git('rev-parse', '--abbrev-ref', 'HEAD', cwd=REPO).replace('/', '_')}-{sha}{dirty}"
    src = HOME / "src"
    if not (src / ".git").is_dir():
        say(f"cloning into {src}")
        subprocess.run(["git", "clone", f"https://github.com/{config['repository']}.git", str(src)], check=True)
    git("fetch", "--tags", "--force", "origin", cwd=src)
    if ref == "latest-dev":
        ref = latest_dev(config["repository"])
    target = f"origin/{ref}" if ref in ("develop", "main") else ref
    git("checkout", "--force", "--detach", target, cwd=src)
    git("clean", "-fdq", "-e", "build", "-e", ".gradle", cwd=src)
    sha = git("rev-parse", "--short", "HEAD", cwd=src)
    label = ref if ref.startswith("v") else f"{ref.replace('/', '_')}-{sha}"
    return src, label


def nodes_of(src: Path) -> dict:
    with open(src / "stonecutter.properties.toml", "rb") as f:
        return tomllib.load(f)


# --- Mods: Modrinth, pinned per game version ----------------------------------------------------

def modrinth(path: str):
    request = urllib.request.Request(f"https://api.modrinth.com/v2/{path}", headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def download(url: str, to: Path) -> None:
    to.parent.mkdir(parents=True, exist_ok=True)
    partial = to.with_suffix(to.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=300) as response, open(partial, "wb") as out:
        shutil.copyfileobj(response, out)
    partial.replace(to)


def pins_file() -> Path:
    return HOME / "pins.json"


def load_pins() -> dict:
    return json.loads(pins_file().read_text("utf-8")) if pins_file().is_file() else {}


def save_pins(pins: dict) -> None:
    pins_file().write_text(json.dumps(pins, indent=2, sort_keys=True), "utf-8")


def resolve(project: str, game: str, loader: str, pins: dict, update: bool) -> dict | None:
    """The pinned version of a project for a game version: the newest release, else beta, else alpha
    when first asked (or with --update). None when the project has none for that version."""
    key = f"{project}@{loader}"
    if key in pins.setdefault(game, {}) and not update:
        return pins[game][key] or None
    query = f'project/{project}/version?game_versions=["{game}"]&loaders=["{loader}"]'
    versions = modrinth(query.replace('"', "%22").replace("[", "%5B").replace("]", "%5D"))
    chosen = None
    for kind in ("release", "beta", "alpha"):
        chosen = next((v for v in versions if v["version_type"] == kind), None)
        if chosen:
            break
    if not chosen:
        pins[game][key] = {}
        return None
    file = next((f for f in chosen["files"] if f["primary"]), chosen["files"][0])
    pins[game][key] = {"version": chosen["version_number"], "file": file["filename"], "url": file["url"]}
    return pins[game][key]


def fetched(project: str, pin: dict) -> Path:
    path = HOME / "downloads" / project / pin["file"]
    if not path.is_file():
        say(f"downloading {project} {pin['version']}")
        download(pin["url"], path)
    return path


def loader_needed(jar: Path) -> str | None:
    """The lowest Fabric Loader a jar asks for (`depends.fabricloader`), when it says one."""
    try:
        with zipfile.ZipFile(jar) as archive:
            meta = json.loads(archive.read("fabric.mod.json").decode("utf-8", "replace"))
    except (KeyError, zipfile.BadZipFile, json.JSONDecodeError):
        return None
    wanted = meta.get("depends", {}).get("fabricloader")
    ranges = wanted if isinstance(wanted, list) else [wanted] if wanted else []
    found = [m.group(1) for r in ranges for m in re.finditer(r">=\s*(\d+\.\d+\.\d+)", str(r))]
    return max(found, key=version_key) if found else None


def version_key(version: str) -> tuple:
    return tuple(int(part) for part in re.findall(r"\d+", version)[:3])


def instance(node: str, game: str, name: str, profile: dict, pins: dict, update: bool) -> dict | None:
    """The folder of mods (and shader pack) of a profile on a node; None when a mod is missing for it."""
    folder = HOME / "instances" / node / name
    jars = []
    for project in profile.get("mods", []):
        pin = resolve(project, game, "fabric", pins, update)
        if not pin:
            say(f"{node} {name}: no {project} for {game}, skipped")
            return None
        jars.append(fetched(project, pin))
    pack = None
    if profile.get("shaderpack"):
        project = profile["shaderpack"]
        pin = resolve(project, game, "iris", pins, update)
        if not pin:
            say(f"{node} {name}: no {project} for {game}, skipped")
            return None
        archive = fetched(project, pin)
        pack = HOME / "packs" / f"{project}-{pin['version']}" / Path(pin["file"]).stem
        if not pack.is_dir():
            with zipfile.ZipFile(archive) as z:
                z.extractall(pack)
    mods = folder / "mods"
    if mods.is_dir():
        shutil.rmtree(mods)
    mods.mkdir(parents=True)
    for jar in jars:
        try:
            os.link(jar, mods / jar.name)
        except OSError:
            shutil.copy2(jar, mods / jar.name)
    loaders = [v for v in (loader_needed(j) for j in jars) if v]
    return {"mods": mods if jars else None, "pack": pack, "loader": max(loaders, key=version_key) if loaders else None,
            "versions": {p: pins[game][f"{p}@fabric"]["version"] for p in profile.get("mods", [])}
            | ({profile["shaderpack"]: pins[game][f"{profile['shaderpack']}@iris"]["version"]} if pack else {})}


# --- Playing a node ----------------------------------------------------------------------------

def stop_tree(pid: int) -> None:
    try:
        import psutil
        root = psutil.Process(pid)
        for child in root.children(recursive=True):
            child.kill()
        root.kill()
    except Exception:
        if WINDOWS:
            subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)


def stray_games() -> None:
    """A game a stopped run left behind (Distant Horizons may hang as the game closes)."""
    try:
        import psutil
    except ImportError:
        return
    for process in psutil.process_iter(["cmdline"]):
        line = " ".join(process.info.get("cmdline") or [])
        if "KnotClient" in line and "clientGameTest" in line:
            process.kill()


def play(src: Path, node: str, setup: dict, out: Path, perf: bool, base_loader: str, timeout: int) -> dict:
    out.mkdir(parents=True, exist_ok=True)
    gradlew = src / ("gradlew.bat" if WINDOWS else "gradlew")
    command = [str(gradlew), f":{node}:runClientGameTest", "--configure-on-demand", "--console=plain",
               f"-Plh.scenario={SCENARIO}"]
    if setup["mods"]:
        command.append(f"-Plh.mods={setup['mods']}")
    if setup["pack"]:
        command.append(f"-Plh.shaderpack={setup['pack']}")
    if setup["loader"] and version_key(setup["loader"]) > version_key(base_loader):
        command.append(f"-Plh.loader={setup['loader']}")
    if perf:
        command.append("-Plh.perf=true")
    log_path = out / "game.log"
    shots = src / "build" / "run" / "clientGameTest" / "screenshots"
    if shots.is_dir():
        shutil.rmtree(shots, ignore_errors=True)
    started = time.time()
    done_at = None
    with open(log_path, "w", encoding="utf-8", errors="replace") as log:
        process = subprocess.Popen(command, cwd=src, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding="utf-8", errors="replace")
        seen = {"done": False}

        def pump():
            for line in process.stdout:
                log.write(line)
                if f"LHTEST scenario {SCENARIO} done" in line:
                    seen["done"] = True

        reader = threading.Thread(target=pump, daemon=True)
        reader.start()
        stopped = None
        while process.poll() is None:
            time.sleep(2)
            if seen["done"] and done_at is None:
                done_at = time.time()
            if done_at and time.time() - done_at > 120:
                stopped = "hung after the scenario ended"
            elif time.time() - started > timeout * 60:
                stopped = f"stopped after {timeout} minutes"
            if stopped:
                stop_tree(process.pid)
                stray_games()
                break
        process.wait()
        reader.join(timeout=10)
    text = log_path.read_text("utf-8", "replace")
    failures = [l.strip() for l in text.splitlines()
                if re.search(r"AssertionError|What went wrong|FAILED|Exception in thread", l)][:20]
    ok = seen["done"] and not any("AssertionError" in f for f in failures) and (process.returncode == 0 or stopped)
    # Screenshots, named without their number: near-models-a.png.
    for shot in sorted(shots.glob("*.png")) if shots.is_dir() else []:
        name = re.sub(rf"^(\d+_|frame_)?{SCENARIO}-", "", shot.name)
        if name != shot.name:
            shutil.copy2(shot, out / name)
    stats, perf_lines = {}, []
    for line in text.splitlines():
        m = re.search(rf"LHTEST {SCENARIO} stat ([^=]+)=(.*)$", line)
        if m:
            stats[m.group(1).strip()] = m.group(2).strip()
        m = re.search(rf"LHTEST {SCENARIO} perf \| ?(.*)$", line)
        if m:
            perf_lines.append(m.group(1))
    (out / "stats.json").write_text(json.dumps(stats, indent=2, sort_keys=True), "utf-8")
    if perf_lines:
        (out / "perf.txt").write_text("\n".join(perf_lines) + "\n", "utf-8")
    result = {"ok": bool(ok), "exit": process.returncode, "stopped": stopped, "failures": failures,
              "minutes": round((time.time() - started) / 60, 1), "versions": setup["versions"],
              "loader": setup["loader"]}
    (out / "run.json").write_text(json.dumps(result, indent=2), "utf-8")
    return result


# --- Comparing ---------------------------------------------------------------------------------

def image(path: Path):
    import numpy as np
    from PIL import Image
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)


def blobs(mask, cell: int = 6, pad: int = 14, most: int = 8) -> list[tuple[int, int, int, int]]:
    """The places where something changed, as boxes (left, top, right, bottom), largest first: the
    pixels gathered by cells of a few pixels, touching cells merged."""
    import numpy as np
    ys, xs = np.nonzero(mask)
    cells = {(int(y) // cell, int(x) // cell) for y, x in zip(ys, xs)}
    boxes, seen = [], set()
    for start in cells:
        if start in seen:
            continue
        seen.add(start)
        group, todo = [], [start]
        while todo:
            cy, cx = todo.pop()
            group.append((cy, cx))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    other = (cy + dy, cx + dx)
                    if other in cells and other not in seen:
                        seen.add(other)
                        todo.append(other)
        top, left = min(g[0] for g in group) * cell, min(g[1] for g in group) * cell
        bottom, right = (max(g[0] for g in group) + 1) * cell, (max(g[1] for g in group) + 1) * cell
        boxes.append((len(group), max(0, left - pad), max(0, top - pad),
                      min(mask.shape[1], right + pad), min(mask.shape[0], bottom + pad)))
    boxes.sort(reverse=True)
    return [b[1:] for b in boxes[:most]]


def compare_shot(new_dir: Path, old_dir: Path, shot: str, limits: dict, diff_to: Path | None) -> dict:
    """One shot (its pair -a, -b) against another run's.

    A pixel counts when it is steady in both pairs (what moves by itself, a shader's water, is left
    out) and differs by more than the tolerance. Then only what changed over at least two pixels by
    two counts: a terrain's edge shifted by a pixel draws thin lines, a mob is a blob."""
    import numpy as np
    from PIL import Image
    paths = [new_dir / f"{shot}-a.png", new_dir / f"{shot}-b.png", old_dir / f"{shot}-a.png", old_dir / f"{shot}-b.png"]
    if not paths[0].is_file():
        return {"verdict": "missing", "note": "no picture in this run"}
    if not paths[2].is_file():
        return {"verdict": "new", "note": "nothing to compare with"}
    a, b, c, d = (image(p) if p.is_file() else None for p in paths)
    b = a if b is None else b
    d = c if d is None else d
    if a.shape != c.shape:
        return {"verdict": "changed", "note": f"size {c.shape[1]}x{c.shape[0]} -> {a.shape[1]}x{a.shape[0]}"}
    steady = (np.abs(a - b).max(axis=2) <= limits["noise"]) & (np.abs(c - d).max(axis=2) <= limits["noise"])
    changed = (np.abs(a - c).max(axis=2) > limits["tolerance"]) & steady
    # Two by two: thin lines go, blobs stay.
    core = np.zeros_like(changed)
    core[:-1, :-1] = changed[:-1, :-1] & changed[1:, :-1] & changed[:-1, 1:] & changed[1:, 1:]
    share = changed.sum() / max(1, steady.sum())
    unsteady = 1 - steady.mean()
    if "max_pixels" in limits:
        verdict = "changed" if core.sum() > limits["max_pixels"] else "same"
    else:
        verdict = "changed" if share > limits["max_changed"] else "same"
    boxes = blobs(core) if verdict == "changed" else []
    if diff_to is not None and verdict == "changed":
        grey = c.mean(axis=2, keepdims=True).repeat(3, axis=2) * 0.45
        grey[~steady] = grey[~steady] * 0.5 + np.array([0, 40, 110])
        grey[changed & ~core] = [120, 60, 60]
        grey[core] = [255, 30, 30]
        for left, top, right, bottom in boxes:
            grey[top, left:right] = grey[bottom - 1, left:right] = [255, 200, 0]
            grey[top:bottom, left] = grey[top:bottom, right - 1] = [255, 200, 0]
        strip = np.concatenate([c, np.full((c.shape[0], 4, 3), 255), a, np.full((c.shape[0], 4, 3), 255), grey], axis=1)
        diff_to.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray(strip.clip(0, 255).astype("uint8")).save(diff_to)
        # Each place that changed, before and after, enlarged: a far mob is a few pixels.
        zooms = []
        for left, top, right, bottom in boxes:
            scale = max(2, min(8, 240 // max(bottom - top, right - left, 1)))
            pair = [Image.fromarray(x[top:bottom, left:right].clip(0, 255).astype("uint8")) for x in (c, a)]
            pair = [p.resize((p.width * scale, p.height * scale), Image.NEAREST) for p in pair]
            row = Image.new("RGB", (pair[0].width * 2 + 6, pair[0].height), (255, 255, 255))
            row.paste(pair[0], (0, 0))
            row.paste(pair[1], (pair[0].width + 6, 0))
            zooms.append(row)
        if zooms:
            sheet = Image.new("RGB", (max(z.width for z in zooms), sum(z.height + 6 for z in zooms)), (255, 255, 255))
            y = 0
            for z in zooms:
                sheet.paste(z, (0, y))
                y += z.height + 6
            sheet.save(diff_to.with_name(f"{shot}-zoom.png"))
    return {"verdict": verdict, "changed": round(float(share), 6), "unsteady": round(float(unsteady), 4),
            "pixels": int(core.sum()), "places": len(boxes)}


def log_lines(path: Path) -> set[str]:
    """The warnings and errors of a game's log, without what differs from run to run (times,
    numbers, paths), so that two logs compare."""
    found: set[str] = set()
    if not path.is_file():
        return found
    lines = path.read_text("utf-8", "replace").splitlines()
    for i, line in enumerate(lines):
        if not re.search(r"/(WARN|ERROR|FATAL)\]|Exception|^\s*Caused by", line):
            continue
        # The mod's own, or an error whose stack goes through it: the other mods' warnings come and
        # go from one run to the next (Distant Horizons' pools).
        if not re.search(r"Living Horizon|livinghorizon", " ".join(lines[i:i + 4])):
            continue
        line = re.sub(r"^\[[^\]]*\] ", "", line.strip())
        line = re.sub(r"[A-Za-z]:[\\/][^\s,'\"]*", "<path>", line)
        line = re.sub(r"\d+", "N", line)
        found.add(line[:240])
    return found


def shots_in(folder: Path) -> list[str]:
    return sorted({p.name[:-6] for p in folder.glob("*-a.png")})


def number(value: str):
    try:
        return float(value)
    except ValueError:
        return None


def compare_stats(new: dict, old: dict) -> list[dict]:
    rows = []
    for key in sorted(set(new) | set(old)):
        a, b = new.get(key), old.get(key)
        if a != b:
            rows.append({"key": key, "was": b, "now": a})
    return rows


def perf_table(path: Path) -> dict:
    """phase -> average frame time in ms, from the benchmark report's frame table."""
    table, inside = {}, False
    if not path.is_file():
        return table
    for line in path.read_text("utf-8").splitlines():
        if line.startswith("== Frames"):
            inside = True
            continue
        if inside:
            if not line.strip() or line.startswith("cost ms"):
                break
            parts = line.split()
            if parts[0] != "phase" and len(parts) >= 4 and number(parts[3]) is not None:
                table[parts[0]] = float(parts[3])
    return table


def compare_run(run: Path, config: dict, against: Path | None = None) -> dict:
    """Every node and profile of a run against the baselines (or another run), and every node
    against the reference node."""
    say(f"comparing {run.name}" + (f" with {against.name}" if against else ""))
    summary = {"run": run.name, "against": against.name if against else None, "cells": {}, "cross": {}}
    reference = config["reference_node"]
    for node_dir in sorted(p for p in run.iterdir() if p.is_dir() and not p.name.startswith("_")):
        for profile_dir in sorted(p for p in node_dir.iterdir() if p.is_dir()):
            node, name = node_dir.name, profile_dir.name
            limits = config["profiles"].get(name, config["profiles"]["vanilla"])
            base = (against / node / name) if against else HOME / "baselines" / node / name
            played = json.loads((profile_dir / "run.json").read_text("utf-8")) if (profile_dir / "run.json").is_file() else {}
            cell = {"played": played, "shots": {}, "stats": [], "perf": []}
            for shot in shots_in(profile_dir) or shots_in(base):
                cell["shots"][shot] = compare_shot(profile_dir, base, shot, limits, run / "_diff" / node / name / f"{shot}.png")
            stats_new = json.loads((profile_dir / "stats.json").read_text("utf-8")) if (profile_dir / "stats.json").is_file() else {}
            if (base / "stats.json").is_file():
                cell["stats"] = compare_stats(stats_new, json.loads((base / "stats.json").read_text("utf-8")))
            old_perf, new_perf = perf_table(base / "perf.txt"), perf_table(profile_dir / "perf.txt")
            for phase, ms in new_perf.items():
                was = old_perf.get(phase)
                if was is not None:
                    slower = ms - was > config["perf"]["slower_ms"] and ms > was * (1 + config["perf"]["slower_share"])
                    cell["perf"].append({"phase": phase, "was": was, "now": ms, "slower": slower})
            # New warnings and errors in the game's log: a bug does not always show in a picture.
            if (base / "game.log").is_file():
                cell["log"] = sorted(log_lines(profile_dir / "game.log") - log_lines(base / "game.log"))[:40]
            cell["baseline"] = (base / ("run.json" if against else "approved.json")).is_file()
            cell["status"] = status_of(cell)
            summary["cells"].setdefault(node, {})[name] = cell
            # Between versions: this node against the reference node, same profile, same run.
            ref_dir = run / reference / name
            if node != reference and ref_dir.is_dir():
                cross_limits = {k: v for k, v in limits.items() if k != "max_pixels"} | config["cross_version"]
                cross = {shot: compare_shot(profile_dir, ref_dir, shot, cross_limits,
                                            run / "_cross" / node / name / f"{shot}.png")
                         for shot in shots_in(profile_dir)}
                ref_stats = json.loads((ref_dir / "stats.json").read_text("utf-8")) if (ref_dir / "stats.json").is_file() else {}
                counts = [r for r in compare_stats(stats_new, ref_stats)
                          if r["key"].endswith((".remembered", ".shown", ".live")) or r["key"] in ("placed", "crowd")]
                summary["cross"].setdefault(node, {})[name] = {"shots": cross, "counts": counts}
    (run / "summary.json").write_text(json.dumps(summary, indent=2), "utf-8")
    report(run, summary, config)
    return summary


def status_of(cell: dict) -> str:
    if not cell["played"].get("ok", False):
        return "failed"
    if not cell["baseline"]:
        return "new"
    if any(s["verdict"] in ("changed", "missing") for s in cell["shots"].values()) or cell["stats"] \
            or cell.get("log") or any(p["slower"] for p in cell["perf"]):
        return "changed"
    return "same"


# --- The report --------------------------------------------------------------------------------

STYLE = """
:root{--bg:#f6f7f9;--fg:#1d2330;--mute:#5d6678;--card:#fff;--line:#d9dde5;--same:#1f8a4c;--changed:#c26a00;
--failed:#c0262d;--new:#3b6fd8;--skip:#8a8f99}
@media (prefers-color-scheme:dark){:root{--bg:#14171d;--fg:#e4e7ee;--mute:#9aa3b5;--card:#1c2028;--line:#2e3440}}
body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.45 system-ui,sans-serif}
main{max-width:1500px;margin:0 auto;padding:24px 16px}h1{font-size:22px;margin:0 0 4px}h2{font-size:17px;margin:28px 0 8px}
h3{font-size:15px;margin:18px 0 6px}.mute{color:var(--mute)}table{border-collapse:collapse;background:var(--card)}
td,th{border:1px solid var(--line);padding:4px 8px;text-align:left;vertical-align:top}
.same{color:var(--same)}.changed{color:var(--changed)}.failed{color:var(--failed)}.new{color:var(--new)}.skip{color:var(--skip)}
.pill{font-weight:600}img{max-width:100%;border:1px solid var(--line);display:block;margin:4px 0 10px}
.wrap{overflow-x:auto}details{background:var(--card);border:1px solid var(--line);padding:8px 12px;margin:8px 0}
code{font-size:12px}
"""


def report(run: Path, summary: dict, config: dict) -> None:
    cells = summary["cells"]
    nodes = [n for n in config["nodes"] if n in cells] + [n for n in cells if n not in config["nodes"]]
    profiles = list(config["profiles"])
    e = html.escape
    compared = f"run {summary['against']}" if summary.get("against") else "the approved baselines on this machine"
    out = [f"<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
           f"<title>Regression bench</title><style>{STYLE}</style><main>",
           f"<h1>Regression bench: {e(run.name)}</h1>",
           f"<p class=mute>Compared with {e(compared)}. Diff strips: before | this run | "
           f"what changed in red, circled in yellow (blue: pixels that move by themselves, left out).</p>",
           "<h2>Summary</h2><div class=wrap><table><tr><th>node</th>"
           + "".join(f"<th>{e(p)}</th>" for p in profiles) + "</tr>"]
    for node in nodes:
        row = [f"<tr><th>{e(node)}</th>"]
        for name in profiles:
            cell = cells[node].get(name)
            if not cell:
                row.append("<td class=skip>-</td>")
                continue
            changed = sum(1 for s in cell["shots"].values() if s["verdict"] != "same")
            row.append(f"<td><a class='pill {cell['status']}' href='#{e(node)}-{e(name)}'>{cell['status']}</a>"
                       + (f" <span class=mute>{changed} shot(s)</span>" if cell["status"] == "changed" and changed else "")
                       + f"<br><span class=mute>{cell['played'].get('minutes', '?')} min</span></td>")
        out.append("".join(row) + "</tr>")
    out.append("</table></div>")

    out.append("<h2>Details</h2>")
    for node in nodes:
        for name in profiles:
            cell = cells[node].get(name)
            if not cell:
                continue
            played = cell["played"]
            out.append(f"<details id='{e(node)}-{e(name)}' {'open' if cell['status'] in ('changed', 'failed') else ''}>"
                       f"<summary><b>{e(node)} / {e(name)}</b> <span class='pill {cell['status']}'>{cell['status']}</span>"
                       f" <span class=mute>{e(', '.join(f'{k} {v}' for k, v in played.get('versions', {}).items()))}</span></summary>")
            if played.get("stopped"):
                out.append(f"<p class=changed>{e(played['stopped'])}</p>")
            for failure in played.get("failures", []):
                out.append(f"<p class=failed><code>{e(failure)}</code></p>")
            out.append(f"<p><a href='{e(node)}/{e(name)}/game.log'>game log</a></p>")
            rows = "".join(f"<tr><td>{e(shot)}</td><td class={s['verdict']}>{s['verdict']}</td>"
                           f"<td>{s['pixels'] if 'pixels' in s else e(s.get('note', ''))}</td><td>{s.get('places', '')}</td>"
                           f"<td>{s.get('unsteady', '')}</td></tr>" for shot, s in cell["shots"].items())
            out.append(f"<table><tr><th>shot</th><th>verdict</th><th>pixels changed</th><th>places</th><th>moving by itself</th></tr>{rows}</table>")
            for shot, s in cell["shots"].items():
                if s["verdict"] == "changed" and (run / "_diff" / node / name / f"{shot}.png").is_file():
                    out.append(f"<h3>{e(shot)}</h3><img loading=lazy src='_diff/{e(node)}/{e(name)}/{e(shot)}.png'>")
                    if (run / "_diff" / node / name / f"{shot}-zoom.png").is_file():
                        out.append(f"<p class=mute>Each place that changed, before | after:</p>"
                                   f"<img loading=lazy style='max-width:min(100%,900px)' "
                                   f"src='_diff/{e(node)}/{e(name)}/{e(shot)}-zoom.png'>")
                elif s["verdict"] in ("new", "missing") and (run / node / name / f"{shot}-a.png").is_file():
                    out.append(f"<h3>{e(shot)}</h3><img loading=lazy src='{e(node)}/{e(name)}/{e(shot)}-a.png'>")
            if cell.get("log"):
                out.append("<h3>New warnings and errors in the game's log</h3>"
                           + "".join(f"<p class=failed><code>{e(line)}</code></p>" for line in cell["log"]))
            if cell["stats"]:
                out.append("<h3>Numbers that changed</h3><table><tr><th>what</th><th>baseline</th><th>now</th></tr>"
                           + "".join(f"<tr><td>{e(r['key'])}</td><td>{e(str(r['was']))}</td><td>{e(str(r['now']))}</td></tr>"
                                     for r in cell["stats"]) + "</table>")
            if cell["perf"]:
                out.append("<h3>Frame times (ms)</h3><table><tr><th>phase</th><th>baseline</th><th>now</th><th></th></tr>"
                           + "".join(f"<tr><td>{e(p['phase'])}</td><td>{p['was']:.2f}</td><td>{p['now']:.2f}</td>"
                                     f"<td class={'changed' if p['slower'] else 'same'}>{'slower' if p['slower'] else ''}</td></tr>"
                                     for p in cell["perf"]) + "</table>")
            out.append("</details>")

    cross = summary["cross"]
    if cross:
        out.append(f"<h2>Between versions (against {e(config['reference_node'])})</h2>"
                   "<p class=mute>The game draws a little differently from one version to the next: these only inform."
                   " Mob counts that differ are listed, since the same world should hold the same mobs.</p>")
        for node in nodes:
            for name in profiles:
                c = cross.get(node, {}).get(name)
                if not c:
                    continue
                changed = {shot: s for shot, s in c["shots"].items() if s["verdict"] == "changed"}
                if not changed and not c["counts"]:
                    out.append(f"<p><b>{e(node)} / {e(name)}</b> <span class=same>same as {e(config['reference_node'])}</span></p>")
                    continue
                out.append(f"<details><summary><b>{e(node)} / {e(name)}</b> <span class=changed>{len(changed)} shot(s) differ"
                           f"{', mob counts differ' if c['counts'] else ''}</span></summary>")
                if c["counts"]:
                    out.append(f"<table><tr><th>count</th><th>{e(config['reference_node'])}</th><th>{e(node)}</th></tr>"
                               + "".join(f"<tr><td>{e(r['key'])}</td><td>{e(str(r['was']))}</td><td>{e(str(r['now']))}</td></tr>"
                                         for r in c["counts"]) + "</table>")
                for shot, s in changed.items():
                    out.append(f"<h3>{e(shot)} <span class=mute>{s.get('changed', '')}</span></h3>"
                               f"<img loading=lazy src='_cross/{e(node)}/{e(name)}/{e(shot)}.png'>")
                out.append("</details>")
    out.append("</main>")
    (run / "report.html").write_text("\n".join(out), "utf-8")
    say(f"report: {run / 'report.html'}")


# --- Commands ----------------------------------------------------------------------------------

def command_run(args, config: dict) -> Path:
    src, label = checkout(args.ref, config)
    if not (src / "src" / "gametest" / "java" / "fr" / "clixmods" / "livinghorizon" / "gametest" / "scenario"
            / "RegressionScenario.java").is_file():
        sys.exit(f"bench: {label} has no regression scenario (it came after this build)")
    tables = nodes_of(src)
    base_loader = tables["deps"]["fabric_loader"] if "deps" in tables else "0"
    nodes = pick(args.nodes, config["nodes"], "node")
    names = pick(args.profiles, list(config["profiles"]), "profile")
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M")
    run = HOME / "results" / f"{label}_{stamp}"
    run.mkdir(parents=True)
    (run / "_run.json").write_text(json.dumps({"ref": args.ref, "label": label, "nodes": nodes, "profiles": names,
                                               "perf": args.perf, "started": stamp,
                                               "commit": git("rev-parse", "HEAD", cwd=src)}, indent=2), "utf-8")
    pins = load_pins()
    for node in nodes:
        if node not in tables:
            say(f"no node {node} in this build, skipped")
            continue
        game = tables[node]["mod"]["mc_releases"][-1]
        for name in names:
            setup = instance(node, game, name, config["profiles"][name], pins, args.update_mods)
            save_pins(pins)
            if not setup:
                continue
            say(f"{node} / {name}: playing")
            result = play(src, node, setup, run / node / name, args.perf, base_loader, config["timeout_minutes"])
            say(f"{node} / {name}: {'ok' if result['ok'] else 'FAILED'} in {result['minutes']} min"
                + (f" ({result['stopped']})" if result["stopped"] else ""))
    if not args.no_compare:
        compare_run(run, config)
    return run


def command_jars(args, config: dict) -> list[Path]:
    """Releases' published jars, on any loader and version, driven from outside (jars.py): one
    launch per release, node and profile, several at once. The later releases are compared with
    the first (or with --against)."""
    import jars
    from concurrent.futures import ThreadPoolExecutor
    tables = nodes_of(REPO)
    known = [n for n, t in tables.items() if isinstance(t, dict) and "mod" in t]
    if args.nodes == "all":
        nodes = known
    else:
        nodes = pick(args.nodes, known, "node") if args.nodes else config["jar_nodes"]
    names = pick(args.profiles, list(config["profiles"]), "profile")
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M")
    pins = load_pins()
    runs: dict[str, Path] = {}
    tasks = []
    # Everything shared is fetched and installed first, one at a time; then the games run side by side.
    for tag in args.tag:
        run = HOME / "results" / f"{tag}-jars_{stamp}"
        run.mkdir(parents=True)
        runs[tag] = run
        (run / "_run.json").write_text(json.dumps({"tag": tag, "mode": "jars", "nodes": nodes, "profiles": names,
                                                   "started": stamp}, indent=2), "utf-8")
    for node in nodes:
        game, loader = jars.node_parts(node, tables)
        loader_pin = jars.loader_version(game, loader, pins, args.update_mods)
        save_pins(pins)
        jars.install(game, loader, HOME, loader_pin)
        jars.scene(HOME, game)
        for name in names:
            profile = config["profiles"][name]
            projects = [JAR_MODS.get((p, loader), p) for p in profile.get("mods", [])]
            if loader in ("fabric", "quilt"):
                projects.insert(0, "fabric-api")
            extra, missing = [], None
            for project in projects:
                pin = resolve(project, game, "fabric" if loader == "quilt" else loader, pins, args.update_mods)
                if not pin:
                    missing = project
                    break
                extra.append(fetched(project, pin))
            pack = None
            if not missing and profile.get("shaderpack"):
                pin = resolve(profile["shaderpack"], game, "iris", pins, args.update_mods)
                if pin:
                    pack = fetched(profile["shaderpack"], pin)
                else:
                    missing = profile["shaderpack"]
            save_pins(pins)
            if missing:
                say(f"{node} / {name}: no {missing} for {game} on {loader}, skipped")
                continue
            versions = {p.parent.name: p.name for p in extra} | ({loader: loader_pin} if loader_pin else {}) \
                | ({pack.parent.name: pack.name} if pack else {})
            for tag in args.tag:
                jar = jars.release_jar(tag, node, config["repository"], HOME, github)
                tasks.append((tag, node, name, jar, extra, pack, profile.get("settle", args.settle), versions, loader_pin))

    def one(task):
        tag, node, name, jar, extra, pack, settle, versions, loader_pin = task
        out = runs[tag] / node / name
        say(f"{tag} {node} / {name}: starting")
        result = jars.play(HOME, node, tables, jar, extra, pack, out, settle, args.wait, loader_pin,
                           instance=f"{node}-{name}-{tag}")
        result["versions"] = versions
        (out / "stats.json").write_text(json.dumps(result.pop("stats", {}), indent=2), "utf-8")
        (out / "run.json").write_text(json.dumps(result, indent=2), "utf-8")
        say(f"{tag} {node} / {name}: {'ok' if result['ok'] else 'FAILED (' + str(result['stopped']) + ')'}"
            f" in {result['minutes']} min")

    parallel = args.parallel or games_at_once()
    say(f"{len(tasks)} game(s) to play, {parallel} at a time")
    with ThreadPoolExecutor(max_workers=parallel) as pool:
        for future in [pool.submit(one, task) for task in tasks]:
            future.result()
    against = find_run(args.against) if args.against else (runs[args.tag[0]] if len(args.tag) > 1 else None)
    for tag in args.tag:
        if against and runs[tag] != against:
            compare_run(runs[tag], config, against)
    return list(runs.values())


# A profile names the Fabric mods; on another loader the same part is another mod.
JAR_MODS = {("sodium", "forge"): "embeddium", ("iris", "forge"): "oculus"}


def games_at_once() -> int:
    """As many games as the memory holds: 4 GB each (3 for Java, 1 for the rest), 2 GB kept free."""
    try:
        import psutil
        free = psutil.virtual_memory().available / 2 ** 30
    except ImportError:
        return 1
    return max(1, min(6, int((free - 2) // 4)))


def find_run(name: str) -> Path:
    results = HOME / "results"
    exact = results / name
    if exact.is_dir():
        return exact
    matches = sorted(p for p in results.glob(f"{name}*") if p.is_dir())
    if name == "last":
        matches = sorted((p for p in results.iterdir() if p.is_dir()), key=lambda p: p.stat().st_mtime)
    if not matches:
        sys.exit(f"bench: no run {name} in {results}")
    return matches[-1]


def command_approve(args, config: dict) -> None:
    run = find_run(args.run)
    nodes = pick(args.nodes, [p.name for p in run.iterdir() if p.is_dir() and not p.name.startswith("_")], "node")
    for node in nodes:
        for profile_dir in sorted(p for p in (run / node).iterdir() if p.is_dir()):
            if args.profiles and profile_dir.name not in args.profiles.split(","):
                continue
            played = json.loads((profile_dir / "run.json").read_text("utf-8"))
            if not played.get("ok") and not args.force:
                say(f"{node} / {profile_dir.name}: failed, not approved (--force to approve anyway)")
                continue
            base = HOME / "baselines" / node / profile_dir.name
            if base.is_dir():
                shutil.rmtree(base)
            shutil.copytree(profile_dir, base)
            (base / "approved.json").write_text(json.dumps({"run": run.name, "at": dt.datetime.now().isoformat()}), "utf-8")
            say(f"{node} / {profile_dir.name}: approved from {run.name}")


def command_watch(args, config: dict) -> None:
    tag = latest_dev(config["repository"])
    results = HOME / "results"
    if results.is_dir() and any(p.name.startswith(f"{tag}_") for p in results.iterdir()):
        say(f"{tag} already benched")
        return
    say(f"new develop build {tag}")
    args.ref = tag
    command_run(args, config)


def command_mods(args, config: dict) -> None:
    src = REPO
    tables = nodes_of(src)
    pins = load_pins()
    for node in config["nodes"]:
        if node not in tables:
            continue
        game = tables[node]["mod"]["mc_releases"][-1]
        projects = sorted({p for prof in config["profiles"].values() for p in prof.get("mods", [])})
        packs = sorted({prof["shaderpack"] for prof in config["profiles"].values() if prof.get("shaderpack")})
        line = []
        for project in projects:
            pin = resolve(project, game, "fabric", pins, args.update)
            line.append(f"{project} {pin['version'] if pin else '-'}")
        for project in packs:
            pin = resolve(project, game, "iris", pins, args.update)
            line.append(f"{project} {pin['version'] if pin else '-'}")
        print(f"{node:8} ({game}): " + ", ".join(line))
    save_pins(pins)
    if args.update:
        say("pins updated: the next run will differ from the baselines where a mod changed; approve it once checked")


def main() -> None:
    parser = argparse.ArgumentParser(description="Living Horizon's regression bench")
    sub = parser.add_subparsers(dest="command", required=True)
    run = sub.add_parser("run", help="play every node and profile, then compare")
    watch = sub.add_parser("watch", help="run the newest develop pre-release if not benched yet")
    for p in (run, watch):
        p.add_argument("--nodes")
        p.add_argument("--profiles")
        p.add_argument("--perf", action="store_true", help="run /livinghorizon benchmark too")
        p.add_argument("--update-mods", action="store_true", help="take the newest mod versions and pin them")
        p.add_argument("--no-compare", action="store_true")
    run.add_argument("--ref", default="latest-dev", help="latest-dev, develop, a tag, a commit, or local")
    compare = sub.add_parser("compare", help="compare a run (or 'last') again")
    compare.add_argument("run")
    compare.add_argument("--against", help="another run instead of the baselines")
    jar_parser = sub.add_parser("jars", help="a release's published jars, any loader and version")
    jar_parser.add_argument("--tag", required=True, action="append",
                            help="a release (v0.16.0); repeat it to compare the later ones with the first")
    jar_parser.add_argument("--nodes", help="nodes (1.20.1-forge,1.21.1-neoforge...), or all")
    jar_parser.add_argument("--profiles", default="vanilla")
    jar_parser.add_argument("--settle", type=int, default=30, help="seconds in the world before the first picture")
    jar_parser.add_argument("--wait", type=int, default=6, help="seconds at each viewpoint before its pictures")
    jar_parser.add_argument("--parallel", type=int, help="games at once (default: as many as the memory holds)")
    jar_parser.add_argument("--against", help="a run to compare with instead of the first release")
    jar_parser.add_argument("--update-mods", action="store_true")
    approve = sub.add_parser("approve", help="make a run's pictures the baselines")
    approve.add_argument("run")
    approve.add_argument("--nodes")
    approve.add_argument("--profiles")
    approve.add_argument("--force", action="store_true")
    mods = sub.add_parser("mods", help="the pinned mod versions")
    mods.add_argument("--update", action="store_true")
    sub.add_parser("runs", help="the runs kept")
    args = parser.parse_args()
    config = settings()
    HOME.mkdir(parents=True, exist_ok=True)
    if args.command == "run":
        command_run(args, config)
    elif args.command == "watch":
        command_watch(args, config)
    elif args.command == "compare":
        compare_run(find_run(args.run), config, find_run(args.against) if args.against else None)
    elif args.command == "jars":
        command_jars(args, config)
    elif args.command == "approve":
        command_approve(args, config)
    elif args.command == "mods":
        command_mods(args, config)
    elif args.command == "runs":
        for p in sorted((HOME / "results").iterdir() if (HOME / "results").is_dir() else []):
            summary = p / "summary.json"
            states = []
            if summary.is_file():
                cells = json.loads(summary.read_text("utf-8"))["cells"]
                states = [c["status"] for n in cells.values() for c in n.values()]
            print(f"{p.name}: " + ", ".join(f"{states.count(s)} {s}" for s in ("same", "changed", "failed", "new") if states.count(s)))


if __name__ == "__main__":
    main()
