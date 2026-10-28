#!/usr/bin/env bash
#
# Runs ./gradlew with the CI's options, the tasks given as arguments:
#
#   gradle.sh :1.21.11:build :1.21.11:buildAndCollect
#
# Fifty-odd runners download the same plugins and Minecraft files at once, and the
# Maven repositories behind them (Forge's most of all) sometimes drop a download or
# leave it hanging with no end: ForgeGradle's Mavenizer waits on a stalled transfer
# with no time limit of its own. So an attempt that runs past GRADLE_ATTEMPT_LIMIT is
# stopped, and one stopped that way or ended by a failed download is tried again,
# three times at most. Any other failure (a compile error, a test) ends it at once.
#
# The Mavenizer writes a download straight into its cache: one cut short (stopped, or
# dropped by the server) stays there as a truncated jar, and the next attempt fails on
# it ("zip END header not found"). Before trying again, any jar or zip of its folders
# that does not open is deleted, so that it is downloaded again.
#
# The limit stays well above a slow but healthy build: Loom setting up Minecraft from
# nothing has taken up to 17 minutes when the repositories were slow.
set -uo pipefail

limit=${GRADLE_ATTEMPT_LIMIT:-25m}
log="${RUNNER_TEMP:-/tmp}/gradle.log"
attempts=3

# Deletes the jars and zips of the Mavenizer's folders that do not open.
drop_partial() {
    python3 -I - "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/minecraftforge" .gradle/mavenizer <<'PY'
import sys, zipfile
from pathlib import Path
for root in map(Path, sys.argv[1:]):
    if not root.is_dir():
        continue
    for path in root.rglob("*"):
        if path.suffix not in (".jar", ".zip") or not path.is_file():
            continue
        try:
            zipfile.ZipFile(path).close()
        except (zipfile.BadZipFile, OSError):
            path.unlink()
            print(f"::warning::Deleted a download cut short: {path}")
PY
}

for attempt in $(seq 1 "$attempts"); do
    timeout --kill-after=1m "$limit" ./gradlew "$@" --stacktrace --configure-on-demand \
        -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64,JAVA_HOME_25_X64 \
        2>&1 | tee "$log"
    status=${PIPESTATUS[0]}
    [ "$status" -eq 0 ] && exit 0

    if [ "$status" -eq 124 ] || [ "$status" -eq 137 ]; then
        echo "::warning::Gradle ran past ${limit} on attempt ${attempt} of ${attempts}, most likely on a stalled download."
        # Killing the client leaves the daemon and the tools it started running, holding
        # the cache's locks: the next attempt would wait on them.
        ./gradlew --stop >/dev/null 2>&1 || true
        pkill -f mavenizer || true
    elif grep -qE "Could not (resolve|GET|HEAD|download)|could not resolve plugin artifact|Connection (reset|refused|timed out)|Read timed out|Remote host terminated|status code 5[0-9][0-9]|Returned 5[0-9][0-9]|RST_STREAM|IOException: closed|HttpTimeoutException|request timed out|zip END header not found" "$log"; then
        echo "::warning::A download failed on attempt ${attempt} of ${attempts}."
    else
        exit "$status"
    fi

    drop_partial
    [ "$attempt" -lt "$attempts" ] && sleep $((attempt * 30))
done

echo "::error::Gradle failed ${attempts} times on downloads; the Maven repositories may be down."
exit 1
