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
# The limit stays well above a slow but healthy build: Loom setting up Minecraft from
# nothing has taken up to 17 minutes when the repositories were slow.
set -uo pipefail

limit=${GRADLE_ATTEMPT_LIMIT:-25m}
log="${RUNNER_TEMP:-/tmp}/gradle.log"
attempts=3

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
    elif grep -qE "Could not (resolve|GET|HEAD|download)|could not resolve plugin artifact|Connection (reset|refused|timed out)|Read timed out|Remote host terminated|status code 5[0-9][0-9]|Returned 5[0-9][0-9]|RST_STREAM|IOException: closed" "$log"; then
        echo "::warning::A download failed on attempt ${attempt} of ${attempts}."
    else
        exit "$status"
    fi

    [ "$attempt" -lt "$attempts" ] && sleep $((attempt * 30))
done

echo "::error::Gradle failed ${attempts} times on downloads; the Maven repositories may be down."
exit 1
