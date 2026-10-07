#!/usr/bin/env bash
# Runs the client game tests: the real game with the mod, driven by the scenarios in
# src/gametest/java, under a virtual display. Prints the scenario log lines and the
# screenshots taken. See SKILL.md next to this file.
#
#   .claude/skills/game-test/run.sh [scenario[,scenario...]|all] [--sodium]
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
for arg in "$@"; do
    [ "$arg" = "--sodium" ] && WITH_SODIUM=1
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

MOD_ARGS=()
if [ "$WITH_SODIUM" = 1 ]; then
    MODS="$ROOT/build/game-test-mods"
    mkdir -p "$MODS"
    if [ ! -s "$MODS/sodium.jar" ]; then
        # Sodium's latest release for this Minecraft version, from Modrinth.
        url="$(curl -sS "https://api.modrinth.com/v2/project/sodium/version?game_versions=%5B%221.21.11%22%5D&loaders=%5B%22fabric%22%5D" \
            | python3 -c 'import json,sys
for v in json.load(sys.stdin):
    if v["version_type"] == "release":
        print([f["url"] for f in v["files"] if f["primary"]][0]); break')"
        curl -sSfL -o "$MODS/sodium.jar" "$url" || { echo "game-test: could not download Sodium"; exit 2; }
    fi
    MOD_ARGS=("-Plh.mods=$MODS")
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
