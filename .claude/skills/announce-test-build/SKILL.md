---
name: announce-test-build
description: Write the Discord message that asks testers to try the latest build of develop (the newest GitHub pre-release), listing its changes and linking the release. Use when the user asks for a Discord message, announcement or call for testing about the latest develop version, a dev build or a pre-release.
---

# Announce a build of develop to test

Every push to `develop` publishes a GitHub pre-release `vX.Y.Z-dev.N`. This skill turns
the newest one into a Discord message that asks the community to test it. Nothing is
posted: the message is handed back to the user to paste.

## Steps

1. **Find the build.** `mcp__github__list_releases` on `mc-skin-creator/living-horizon`
   (`perPage: 3`, `fields: ["tag_name","html_url","published_at","prerelease"]`). Take the
   first release with `prerelease: true`. Its `html_url` is the link to put in the message,
   as is: the release page, never the compare view or a pull request.
2. **Check it matches `develop`.** Compare `git rev-parse origin/develop` (after a
   `git fetch origin develop`) with the release's commit if the user asks about a specific
   state; if `develop` moved after the release, say so in one line to the user.
3. **List the changes in player words.** Read the entry files of `changelog/unreleased/`
   on `origin/develop` (`git show origin/develop:changelog/unreleased/<file>`): each has
   `part`, `kind` (`new`, `improved`, `fixed`) and one line per language. Use the `fr:`
   line as the source for the French message, `en:` for an English one. Do not list pull
   request titles: they are developer words. Skip nothing the files carry, but merge related
   bullets (several stutter fixes become one line).
4. **Write the message in French** (the community channel is French; give an English
   version too only if asked), with this layout:

   ```
   ## 🧪 À tester : <tag>

   Une nouvelle version de développement est disponible. Dites-nous si quelque chose cloche !

   **Nouveautés**
   - …
   **Améliorations**
   - …
   **Corrections**
   - …

   🔗 <html_url>
   ```

   - one short line per change, starting with the benefit, no title/dash split, no
     internals (no shader, depth, buffer, class);
   - a change that is server-side starts with "Serveur :", a version limit goes at the end
     in parentheses;
   - say *créatures*, never *animaux*; setting names exactly as the French game shows them;
   - **Discord limits a message to 2000 characters**: count them and shorten until the
     message fits, merging bullets, never dropping the link;
   - no role ping unless the user asks for it.
5. **Easter eggs stay secret.** Never mention one; at most "easter egg" and a number. The
   changelog has none, so there is nothing to copy.
6. **Hand it back** in one fenced block ready to paste, followed by the character count and
   the release link on its own line.
