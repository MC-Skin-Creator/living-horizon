#!/usr/bin/env bash
#
# Prints the next mod version, derived from the conventional-commit subjects since
# the last stable tag. Prints nothing and exits 0 when no commit earns a release,
# which is how the release workflow decides to stay quiet.
#
#   feat!: / BREAKING CHANGE:  -> minor while below 1.0.0, major above
#   feat:                      -> minor
#   fix: / perf:               -> patch
#   anything else              -> no release
#
# With --min-patch, a patch bump is produced even when nothing earns a release.
# Kept for builds that must always carry a version, even with nothing release-worthy.
#
# The Minecraft version is deliberately absent: game compatibility is carried by
# the artifact name, never by the mod version.
set -euo pipefail

min_patch=false
case "${1:-}" in
    --min-patch) min_patch=true ;;
    "") ;;
    *) echo "usage: $0 [--min-patch]" >&2; exit 2 ;;
esac

# Stable tags only: a -dev. pre-release is never a baseline.
last_tag=$(git tag --list 'v*' --sort=-v:refname | grep -v -- '-dev\.' | head -n 1 || true)

if [ -n "$last_tag" ]; then
    base=${last_tag#v}
    range="${last_tag}..HEAD"
else
    # No release has been cut yet, so the baseline is the version the repository
    # already claims. Starting from 0.0.0 here would let the first release land
    # *below* the version sitting in the file.
    root=$(git rev-parse --show-toplevel)
    base=$(sed -n 's/^mod\.version = "\(.*\)"/\1/p' "$root/stonecutter.properties.toml" 2>/dev/null || true)
    base=${base:-0.0.0}
    range=""
fi

if ! [[ $base =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "last stable tag '$last_tag' is not a plain version" >&2
    exit 1
fi
major=${BASH_REMATCH[1]}
minor=${BASH_REMATCH[2]}
patch=${BASH_REMATCH[3]}

if [ -n "$range" ]; then
    subjects=$(git log --format='%s' "$range")
    bodies=$(git log --format='%b' "$range")
else
    subjects=$(git log --format='%s')
    bodies=$(git log --format='%b')
fi

bump=none
if grep -qE '^[a-zA-Z]+(\([^)]*\))?!:' <<<"$subjects" \
    || grep -qE '^BREAKING[ -]CHANGE:' <<<"$bodies"; then
    bump=breaking
elif grep -qE '^feat(\([^)]*\))?:' <<<"$subjects"; then
    bump=minor
elif grep -qE '^(fix|perf)(\([^)]*\))?:' <<<"$subjects"; then
    bump=patch
elif [ "$min_patch" = true ]; then
    bump=patch
fi

case $bump in
    breaking)
        # Below 1.0.0 there is no major to bump: 1.0.0 is cut by hand, when the mod
        # actually does what it promises, not by a commit message.
        if [ "$major" -eq 0 ]; then
            minor=$((minor + 1)); patch=0
        else
            major=$((major + 1)); minor=0; patch=0
        fi
        ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
    none) exit 0 ;;
esac

echo "${major}.${minor}.${patch}"
