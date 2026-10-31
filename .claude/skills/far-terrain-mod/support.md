# How well each far-terrain mod is supported

The checklist of `SKILL.md`, filled in. A pull request that changes the support of one of these
mods updates its column in the same pull request.

Marks: ✅ done (for Voxy and Distant Horizons: shipped and in players' hands; Voxy cannot run
in the game tests, so its marks rest on players' games) · 🟡 written, not checked in game · ❌ not
done · — does not apply.

Lodistance: as in PR #311. Far View: as in PR #310, not merged yet.

## 1. Link

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| Found by reflection, off for the session when something is missing | ✅ `VoxyLink` | ✅ `DhLink` | ✅ `LodistanceLink` | 🟡 `FarViewLink` |
| Line in `/livinghorizon lod` | ✅ | ✅ | ✅ | 🟡 |
| Fallbacks across its versions | ✅ (0.2.18's rename) | ✅ (2.3 to 3.3.4) | ❌ 0.8.7 only | 🟡 0.1.5 and 0.1.6 (no far mobs, no *Seamless*) to 0.1.7 |
| Counters on our mixins into it | ✅ `renderOpaque` | ✅ its fades | — no mixin into it | — no mixin into it |
| Loaders and versions | several | several | Fabric 26.3 only (all it ships) | Forge and NeoForge 1.20.1 to 1.21.4 (all it ships) |

## 2. Its world

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| Height, block, water of a column | ✅ | ✅ | 🟡 | 🟡 `FarViewSourceTest` |
| Biome | ✅ | ✅ | ❌ it keeps none | ❌ it keeps none |
| Blocks of a column (walking) | ✅ | ✅ (top block only) | 🟡 up to three runs | 🟡 top block; ground and leaves under trees |
| First block along a ray | ✅ | ✅ | 🟡 | 🟡 `FarViewSourceTest` |
| Drawn and clear distances | ✅ | ✅ | 🟡 | 🟡 its distance and haze settings |
| Area not built yet read again later | ✅ 20 s pause | ✅ cache emptied | ❌ regions not in memory read as nothing | 🟡 its grid copied every second |
| World or dimension left without a freeze | ✅ | ✅ (#248) | 🟡 reads take no lock | 🟡 reads take no lock and never call it |
| The player's dimension in single player | ✅ | ✅ | 🟡 its world follows the client's level | 🟡 its grid follows the client's level |
| Parallel reads | ✅ | ✅ | 🟡 | 🟡 a copy of its grid, read without locks |

## 3. Depth

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| Without a shader pack | ✅ Voxy writes it itself | ✅ merged | ✅ with Sodium (12 of 12 cows hidden) · 🟡 without Sodium (it drew nothing in the game tests) | 🟡 Far View writes it itself with the game's projection · with *Seamless*, lines of sight only |
| Translucent step from 26.1 | ✅ | ✅ | 🟡 same code | — 1.21.4 at most |
| Iris's shadow pass left out | ✅ | ✅ | — not under a pack yet | — 1.21.4 at most |
| Under a shader pack | ✅ Photon | ✅ Iris, Oculus | ❌ left alone, says why | 🟡 it draws in Iris's terrain phase, the game's projection |
| A pack's render scale | ✅ | ✅ | ❌ | ❌ not looked at |
| Vulkan, 26.3's GPU path | — OpenGL only | ✅ | ❌ | — 1.21.4 at most |
| View bobbing | ✅ | ✅ `DhFrame.inverse` | ✅ its depth does not depend on it | 🟡 the game's own projection |
| Far planes over the whole range (`DepthMathTest`) | ✅ | ✅ | ✅ moved both ways past half the far plane | — nothing converted · ❌ *Seamless* |
| *Skip Hidden Mobs* | ✅ | ✅ | ✅ | 🟡 queries; lines of sight with *Seamless* |
| Debug depth view | ✅ | ✅ | ✅ | 🟡 the game's depth |
| Its picture unchanged | ✅ | ✅ | ✅ screenshots identical | 🟡 nothing of it written |
| Window resize | ✅ | ✅ | 🟡 same code | — nothing of ours |
| *Lazy Far Depth* | ✅ | ✅ | ✅ same code | — nothing merged |

## 4. Its rendering over our figures

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| Fade or composite after the entities | — | ✅ `optDhFade` | — draws before the entities | — draws before the entities; its mob boxes are left to it (`drawsMob`) |
| A pack's shadows through its depth | ✅ `optVoxyShadows` | — | — | — |

## 5. Fog

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| Distant mobs fogged like its terrain | ✅ `FarFog` | ✅ `DhFog` | 🟡 its ramp alone, without the game's atmospheric fog (`FarFog.lodistance`); within the game's render distance mobs are a little clearer than the game's terrain | 🟡 its haze is the game's terrain fog, which the entities share |

## 6. What the player sees

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| *Only Over Terrain* | ✅ | ✅ | 🟡 | 🟡 |
| Distant mobs walk | ✅ | ✅ | 🟡 | 🟡 |
| Birds find coasts, lakes, fields | ✅ | ✅ | 🟡 blocks only, no biome | 🟡 blocks only, no biome |
| Easter eggs that read the far world | ✅ | ✅ | 🟡 | 🟡 |

## 7. Tests, docs, texts

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| Game test scenario | ❌ cannot run on llvmpipe (GLSL 4.60) | ✅ `dhfog` | ✅ `lodistance`, part of it run | ❌ Forge and NeoForge only: no client game tests there |
| `DepthMathTest` case | ✅ | ✅ | ✅ | — nothing converted |
| `docs/technical.md`, CLAUDE.md checklist | ✅ | ✅ | ✅ | ✅ |
| Tooltips in every language, changelog | ✅ | ✅ | ✅ | ✅ |

## 8. Telling the players

| Item | Voxy | Distant Horizons | Lodistance | Far View |
|---|---|---|---|---|
| README: *Compatibility*, *Requirements*, *Getting started*, FAQ | ✅ | ✅ | ✅ | ❌ waits for a check in game |
| Store description and platform relations | ✅ | ✅ | ✅ (to paste by hand) | ❌ |
| `suggests` in the metadata | ✅ | ✅ | ✅ Fabric | ❌ |

## Left to do

- **Lodistance**: run the `lodistance` scenario again with Sodium (its ground checks, the
  last depth change and the fog have not run in game); shader packs; Vulkan; areas
  not in its memory; its renderer without Sodium, which drew nothing in the game tests (its hook
  misses 26.3's indirect chunk path).
- **Far View**: everything in game, by hand on `1.21.4-neoforge` (the client game tests need the
  Fabric API): mobs on its terrain and behind its hills, with and without a shader pack, its
  mob boxes alone; its *Seamless* option hides a mob only whole, by lines of sight (moving its
  squeezed depth to the real one around the entities, as for Lodistance, would hide it pixel by
  pixel); no biome; then the README and the store.
- **Voxy**: a game test, once something can run it.
