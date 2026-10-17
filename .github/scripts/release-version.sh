#!/usr/bin/env bash
#
# Prints the version a push of HEAD to the given branch releases, or nothing when it
# releases none. The release workflow runs it on the pushed commit; the pull request
# build runs it on the merge GitHub prepares, so both stamp the same number on the
# jars and the release can take the pull request's jars instead of building again.
#
#   release-version.sh develop   -> X.Y.Z-dev.N, N the number of commits in HEAD
#   release-version.sh main      -> X.Y.Z from next-version.sh, or nothing
#
# N counts commits rather than runs because a pull request's merge and the merge
# commit that lands it have the same history, so the same count, and develop only
# ever grows: each dev build gets a higher number than the one before.
set -euo pipefail

here=$(dirname "$0")

case "${1:-}" in
    develop)
        # Every merge into develop gets a build, even one carrying no release-worthy
        # commit. The -dev. suffix sorts below the release it previews.
        base=$("$here/next-version.sh" --min-patch)
        echo "${base}-dev.$(git rev-list --count HEAD)"
        ;;
    main)
        "$here/next-version.sh"
        ;;
    *)
        echo "usage: $0 develop|main" >&2
        exit 2
        ;;
esac
