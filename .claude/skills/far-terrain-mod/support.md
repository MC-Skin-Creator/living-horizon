# How well each far-terrain mod is supported

The checklist of `SKILL.md`, filled in. A pull request that changes the support of one of these
mods updates its column in the same pull request.

Marks: ✅ done (for Voxy and Distant Horizons: shipped and in players' hands; Voxy cannot run
in the game tests, so its marks rest on players' games) · 🟡 written, not checked in game · ❌ not
done · — does not apply.

Lodistance: as in PR #311, not merged yet.

## 1. Link

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| Found by reflection, off for the session when something is missing | ✅ `VoxyLink` | ✅ `DhLink` | ✅ `LodistanceLink` |
| Line in `/livinghorizon lod` | ✅ | ✅ | ✅ |
| Fallbacks across its versions | ✅ (0.2.18's rename) | ✅ (2.3 to 3.3.4) | ❌ 0.8.7 only |
| Counters on our mixins into it | ✅ `renderOpaque` | ✅ its fades | — no mixin into it |
| Loaders and versions | several | several | Fabric 26.3 only (all it ships) |

## 2. Its world

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| Height, block, water of a column | ✅ | ✅ | 🟡 |
| Biome | ✅ | ✅ | ❌ it keeps none |
| Blocks of a column (walking) | ✅ | ✅ (top block only) | 🟡 up to three runs |
| First block along a ray | ✅ | ✅ | 🟡 |
| Drawn and clear distances | ✅ | ✅ | 🟡 |
| Area not built yet read again later | ✅ 20 s pause | ✅ cache emptied | ❌ regions not in memory read as nothing |
| World or dimension left without a freeze | ✅ | ✅ (#248) | 🟡 reads take no lock |
| The player's dimension in single player | ✅ | ✅ | 🟡 its world follows the client's level |
| Parallel reads | ✅ | ✅ | 🟡 |

## 3. Depth

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| Without a shader pack | ✅ Voxy writes it itself | ✅ merged | ✅ with Sodium (12 of 12 cows hidden) · 🟡 without Sodium (it drew nothing in the game tests) |
| Translucent step from 26.1 | ✅ | ✅ | 🟡 same code |
| Iris's shadow pass left out | ✅ | ✅ | — not under a pack yet |
| Under a shader pack | ✅ Photon | ✅ Iris, Oculus | ❌ left alone, says why |
| A pack's render scale | ✅ | ✅ | ❌ |
| Vulkan, 26.3's GPU path | — OpenGL only | ✅ | ❌ |
| View bobbing | ✅ | ✅ `DhFrame.inverse` | ✅ its depth does not depend on it |
| Far planes over the whole range (`DepthMathTest`) | ✅ | ✅ | ✅ moved both ways past half the far plane |
| *Skip Hidden Mobs* | ✅ | ✅ | ✅ |
| Debug depth view | ✅ | ✅ | ✅ |
| Its picture unchanged | ✅ | ✅ | ✅ screenshots identical |
| Window resize | ✅ | ✅ | 🟡 same code |
| *Lazy Far Depth* | ✅ | ✅ | ✅ same code |

## 4. Its rendering over our figures

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| Fade or composite after the entities | — | ✅ `optDhFade` | — draws before the entities |
| A pack's shadows through its depth | ✅ `optVoxyShadows` | — | — |

## 5. Fog

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| Distant mobs fogged like its terrain | ✅ `FarFog` | ✅ `DhFog` | 🟡 its ramp alone, without the game's atmospheric fog (`FarFog.lodistance`); within the game's render distance mobs are a little clearer than the game's terrain |

## 6. What the player sees

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| *Only Over Terrain* | ✅ | ✅ | 🟡 |
| Distant mobs walk | ✅ | ✅ | 🟡 |
| Birds find coasts, lakes, fields | ✅ | ✅ | 🟡 blocks only, no biome |
| Easter eggs that read the far world | ✅ | ✅ | 🟡 |

## 7. Tests, docs, texts

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| Game test scenario | ❌ cannot run on llvmpipe (GLSL 4.60) | ✅ `dhfog` | ✅ `lodistance`, part of it run |
| `DepthMathTest` case | ✅ | ✅ | ✅ |
| `docs/technical.md`, CLAUDE.md checklist | ✅ | ✅ | ✅ |
| Tooltips in every language, changelog | ✅ | ✅ | ✅ |

## 8. Telling the players

| Item | Voxy | Distant Horizons | Lodistance |
|---|---|---|---|
| README: *Compatibility*, *Requirements*, *Getting started*, FAQ | ✅ | ✅ | ✅ |
| Store description and platform relations | ✅ | ✅ | ✅ (to paste by hand) |
| `suggests` in the metadata | ✅ | ✅ | ✅ Fabric |

## Left to do

- **Lodistance**: run the `lodistance` scenario again with Sodium (its ground checks, the
  last depth change and the fog have not run in game); shader packs; Vulkan; areas
  not in its memory; its renderer without Sodium, which drew nothing in the game tests (its hook
  misses 26.3's indirect chunk path).
- **Voxy**: a game test, once something can run it.
