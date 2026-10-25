#!/usr/bin/env bash
# Runs the client game tests: the real game with the mod, driven by the scenarios in
# src/gametest/java, under a virtual display. Prints the scenario log lines and the
# screenshots taken. See SKILL.md next to this file.
#
#   .claude/skills/game-test/run.sh [scenario[,scenario...]|all] [--sodium] [--shaders <pack folder>]
#       [--with <mod,mod...>] [--pack <pack,pack...>]
#
# --shaders loads Sodium and Iris and turns the shader pack on (an unpacked folder).
# --with loads other mods, --pack turns resource packs on: Modrinth project names, the
# latest release of each for the node's game version (entity-model-features, fresh-animations).
#
# Runs the active version, 1.21.11. LH_NODE=26.1.x runs another Fabric target (its name in
# stonecutter.properties.toml); the scenarios are the same on every one.
#
# Exit code: the game's (0 when every scenario ran without an assertion failing).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SCENARIO="${1:-all}"
shift || true
WITH_SODIUM=0
SHADERS=""
WITH=""
PACKS=""
while [ $# -gt 0 ]; do
    case "$1" in
        --sodium) WITH_SODIUM=1 ;;
        --shaders) WITH_SODIUM=1; SHADERS="$(cd "$2" && pwd)" || { echo "game-test: no shader pack folder $2"; exit 2; }; shift ;;
        --with) WITH="$2"; shift ;;
        --pack) PACKS="$2"; shift ;;
    esac
    shift
done

RUN_DIR="$ROOT/build/run/clientGameTest"
LOG="$ROOT/build/game-test.log"
mkdir -p "$ROOT/build"

# Cloud sessions reach the internet through a proxy that JAVA_TOOL_OPTIONS describes; the
# Gradle daemon does not always pick it up, so it is written where Gradle reads it. Its
# port changes from one session to the next: the lines are written anew every time.
if [[ "${JAVA_TOOL_OPTIONS:-}" =~ -Dhttps\.proxyHost=([^ ]+) ]]; then
    host="${BASH_REMATCH[1]}"
    port="$(sed -n 's/.*-Dhttps\.proxyPort=\([0-9]*\).*/\1/p' <<<"$JAVA_TOOL_OPTIONS")"
    props="$HOME/.gradle/gradle.properties"
    mkdir -p "$HOME/.gradle"
    touch "$props"
    sed -i '/^systemProp\.https\.proxy\(Host\|Port\)=/d; /^systemProp\.http\.nonProxyHosts=/d' "$props"
    {
        echo "systemProp.https.proxyHost=$host"
        echo "systemProp.https.proxyPort=$port"
        echo "systemProp.http.nonProxyHosts=localhost|127.0.0.1"
    } >>"$props"
fi

# The game version the node runs: the last of its mod.mc_releases, 1.21.11 for the active one.
GAME_VERSION="$(python3 - "$ROOT/stonecutter.properties.toml" "${LH_NODE:-1.21.11}" <<'PY'
import sys, tomllib
with open(sys.argv[1], "rb") as f:
    print(tomllib.load(f)[sys.argv[2]]["mod"]["mc_releases"][-1])
PY
)" || { echo "game-test: no node ${LH_NODE:-1.21.11} in stonecutter.properties.toml"; exit 2; }

# The latest release, or beta when there is none, of a Modrinth project for this version:
# a mod for Fabric into $MODS, or a resource pack (loader "minecraft") into $PACKS.
download() {
    local project="$1" loader="${2:-fabric}" file url
    if [ "$loader" = minecraft ]; then file="$PACKS_DIR/$project.zip"; else file="$MODS/$project.jar"; fi
    [ -s "$file" ] && return 0
    url="$(curl -sS "https://api.modrinth.com/v2/project/$project/version?game_versions=%5B%22$GAME_VERSION%22%5D&loaders=%5B%22$loader%22%5D" \
        | python3 -c 'import json,sys
versions = json.load(sys.stdin)
chosen = next((v for v in versions if v["version_type"] == "release"), versions[0] if versions else None)
print([f["url"] for f in chosen["files"] if f["primary"]][0] if chosen else "")')"
    [ -n "$url" ] && curl -sSfL -o "$file" "$url" || { echo "game-test: could not download $project"; exit 2; }
}

MOD_ARGS=()
MODS="$ROOT/build/game-test-mods/$GAME_VERSION"
PACKS_DIR="$ROOT/build/game-test-packs/$GAME_VERSION"
# The downloads stay for the next runs; only the jars this run asks for are loaded.
LOADED=()
if [ "$WITH_SODIUM" = 1 ]; then
    LOADED+=(sodium)
    if [ -n "$SHADERS" ]; then
        LOADED+=(iris)
        MOD_ARGS+=("-Plh.shaderpack=$SHADERS")
    fi
fi
for project in ${WITH//,/ }; do LOADED+=("$project"); done
if [ ${#LOADED[@]} -gt 0 ]; then
    mkdir -p "$MODS"
    jars=""
    for project in "${LOADED[@]}"; do
        download "$project"
        jars="${jars:+$jars:}$MODS/$project.jar"
    done
    MOD_ARGS+=("-Plh.mods=$jars")
fi
if [ -n "$PACKS" ]; then
    # Its own folder per run: every pack in it is turned on.
    rm -rf "$PACKS_DIR/on"
    mkdir -p "$PACKS_DIR/on"
    for project in ${PACKS//,/ }; do
        download "$project" minecraft
        cp "$PACKS_DIR/$project.zip" "$PACKS_DIR/on/"
    done
    MOD_ARGS+=("-Plh.resourcepacks=$PACKS_DIR/on")
fi

command -v xvfb-run >/dev/null || { echo "game-test: xvfb-run is missing (apt-get install xvfb)"; exit 2; }

# From 26.3 the game opens its window with SDL, which asks Xvfb for an sRGB GLX visual it
# does not have; through EGL it gets Mesa's OpenGL all the same (needs libegl1 and
# libegl-mesa0). Older versions open theirs with GLFW and never read this.
export SDL_VIDEO_FORCE_EGL=1

cd "$ROOT"
status=1
# Maven Central sometimes answers 429 (too many requests): a failed resolve is tried again.
for attempt in 1 2 3; do
    xvfb-run -a -s "-screen 0 1280x720x24" \
        ./gradlew "${LH_NODE:+:$LH_NODE:}runClientGameTest" --no-daemon ${LH_NODE:+--configure-on-demand} "-Plh.scenario=$SCENARIO" "${MOD_ARGS[@]}" >"$LOG" 2>&1
    status=$?
    if [ $status -eq 0 ] || ! grep -q -E "Could not (resolve|GET|download)|429" "$LOG"; then break; fi
    echo "game-test: dependency download failed, trying again ($attempt)"
    sleep $((attempt * 20))
done

echo "game-test: exit $status, full log in $LOG"
grep -E "LHTEST |AssertionError|Exception in|Caused by|Impostors could not|: error:|What went wrong|FAILED" "$LOG" \
    | grep -v -E "OpenAL|Realms|ClassNotFoundException" | sed 's/^.*\[STDOUT\]: //' | head -100
if [ -d "$RUN_DIR/screenshots" ]; then
    echo "game-test: screenshots"
    ls -1 "$RUN_DIR/screenshots" | sed "s#^#  $RUN_DIR/screenshots/#"
fi
exit $status
