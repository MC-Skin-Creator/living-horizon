---
name: readme-writing
description: Write or change the README and the store description (Modrinth, CurseForge, Nexus Mods) of Living Horizon in the project's tone - short, plain sentences for players, features first. Use whenever README.md or the description block of store/listing.md is created, rewritten or edited, even for one sentence, and when a new feature should be presented to players.
---

# Writing the README

The README and the store description are read by players, not developers. They say what
the mod does, in plain words, and nothing else. Everything technical lives in
`docs/technical.md`.

## Steps

1. Read `README.md` and the description block of `store/listing.md` as they are now.
2. Check every fact against the code before writing it (a setting's name, what works on a
   server, when something happens). Setting and screen names come from
   `src/main/resources/assets/livinghorizon/lang/en_us.json`, exactly as the game shows them.
3. Write the change following the rules below.
4. Change the store description in the same commit, so both say the same thing. It is the
   README without *Useful commands*, the versions table, *Compatibility* and *More*. Keep the two summaries of `store/listing.md` in their
   limits (256 characters on Modrinth, 200 on CurseForge).
5. Commit as `docs(readme): ...`. Documentation gets no changelog entry.
6. Tell the user that the platform pages are not updated by any workflow: the description
   is pasted by hand on Modrinth, CurseForge and Nexus Mods. CurseForge gets the text that
   `node .github/scripts/store-description.js curseforge` prints: its editor breaks `<details>`.

## Structure

Keep this order. A new feature goes in the section it belongs to, never in a new one
unless it is as big as the ones below.

1. **Intro**, three or four sentences: the mod shows mobs and players far past the render
   distance; it draws them on the game's terrain and on **Voxy** and **Distant Horizons**
   terrain; it can also help performance.
2. **Features**, one `###` per feature, in this order: **Distant Mobs**, **Distant
   Players**, **Offline Players**, **Birds**, **Performance**, **Settings**. Each opens with one or two
   sentences: what it shows and what it is (light copies with no AI, only visual, needs the
   mod on the server). Then a `<details>` block, `<summary>How … work</summary>`, holds a
   plain bullet list of how it behaves, with its limits (guessed mobs may be gone when you
   arrive). Describe the game alone first; other mods come only as examples after it
   ("such as Voxy or Distant Horizons"), never as a feature of their own.
3. **Multiplayer**: the table of each feature in single player, LAN, server with the mod,
   server without the mod, then how to set up a server.
4. **Getting started**, **Useful commands**, **Requirements** (with the versions table),
   **Compatibility**.
5. **FAQ**: the questions players ask again and again (what is only visual, compatibility,
   modded mobs, servers, modpacks, AI and Modrinth). Grouped under `###` topics, each
   question a collapsible block: `<details>`, `<summary>` with the question in plain text,
   a blank line, the answer, a blank line, `</details>`. A short answer, a pointer to the
   section above rather than a copy of it. The store keeps it, with the mods named in place
   of *Compatibility*, no pointer to a README-only section, and every link absolute.
6. **Made with AI**, **More**.

## Tone

- **Short, plain, direct sentences.** One idea per sentence. Say what the feature does,
  then stop. If a sentence needs a comma and a colon and a dash, split it or cut it.
- **Concrete over clever.** No images, no metaphors, no made-up comparisons.
  - Bad: "A mob 200 blocks away is only a few pixels tall, so you hardly see the
    difference, and the game keeps its frame rate with as many entities as possible."
  - Good: "It costs much less to draw, so you can show many more entities."
- **Never state the obvious.** If it is how it should work anyway, do not say it.
  - Bad: "hidden behind its hills", "they hide behind buildings like anything else",
    "they never walk off a cliff", "Voxy looks the same with or without the mod".
- **Say the limits a player needs to know**, plainly: what is only visual, what is not in
  sync between players, what needs the mod on the server, what can crash the game.
  - Good: "Players who logged off are only visual. The world does not know they exist: you
    walk through them, they cannot block or push you, and mobs ignore them."
- **Do not list every detail.** "With their gear and their mount" is enough; no list of
  cape, elytra, armour.
- **Things that just work are not features.** Mobs load from afar on their own: the scan is
  only a line in *Useful commands*, never a feature or a step.
- English only, as everything in the repository.

## Leave out

- Class names, setting keys from `config/livinghorizon.json`, shaders, depth, buffers,
  passes or any rendering internals.
- Debug and developer commands (`/livinghorizon bird`, `debug`, `lod`, `impostors`, the
  bare `/livinghorizon`). *Useful commands* lists `config` and `scan` only.
- Anything about the easter eggs (see CLAUDE.md).
- Long lists of untested mods: link the `mod compatibility` label instead.
