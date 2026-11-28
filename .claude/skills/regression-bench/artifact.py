"""Builds a page to look at a compared run: for every game, profile and viewpoint, the reference,
this run and what the bench found, side by side, with a full-size view to flip between them.

    python artifact.py <run> [--lang fr]

It writes `LH_BENCH_HOME/results/<run>/_artifact/` (index.html and one picture per viewpoint,
the three panels in one file, so that a run of many profiles stays under the 255 files an
artifact may hold) and prints the folder. Publish it with the Artifact tool: `file_path` the
index.html, `files` every picture of `shots/` and `zoom/` at its path in the folder.
"""
from __future__ import annotations

import argparse
import html
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import bench  # noqa: E402

W, H = 854, 480

# The page's words, in the languages reports are written in (translations, like lang/).
LABELS = {
    "en": {
        "title": "Regression bench", "reference": "Reference", "this": "This run", "found": "What the bench found",
        "against_ref": "Compared with the validated reference", "against_run": "Compared with the run",
        "only_changed": "Changes only", "fps": "Frame rate", "ref_short": "ref.", "nothing": "nothing found",
        "same": "same", "changed": "changed", "failed": "failed", "new": "new", "missing": "missing",
        "px": "px in", "places": "place(s)", "log": "New lines in the game's log", "stats": "Numbers that changed",
        "zoom": "Enlarged", "help": "Click a picture to see it full size. In the full-size view, 1, 2 and 3 "
        "pick the panel, Space flips between the reference and this run, Escape closes.",
        "legend": "Third column: what changed in red, framed in yellow; in blue what moves by itself and is "
        "left out. Greyed out when nothing changed.", "flip": "Flip", "close": "Close", "noref": "no reference",
        "summary": "Summary", "played_in": "played in", "min": "min", "stopped": "stopped",
    },
    "fr": {
        "title": "Banc de régression", "reference": "Référence", "this": "Version testée", "found": "Différence",
        "against_ref": "Comparé à la référence validée", "against_run": "Comparé au run",
        "only_changed": "Changements seulement", "fps": "FPS", "ref_short": "réf.", "nothing": "rien trouvé",
        "same": "identique", "changed": "changé", "failed": "échec", "new": "nouveau", "missing": "manquant",
        "px": "px en", "places": "endroit(s)", "log": "Nouvelles lignes dans le log du jeu",
        "stats": "Chiffres qui ont changé", "zoom": "Agrandi", "help": "Cliquez sur une image pour la voir en "
        "grand. En grand : 1, 2 et 3 choisissent la colonne, Espace alterne référence et version testée, "
        "Échap ferme.", "legend": "Troisième colonne : en rouge ce qui a changé, encadré en jaune ; en bleu ce "
        "qui bouge tout seul et n'est pas compté. Grisée quand rien n'a changé.", "flip": "Alterner",
        "close": "Fermer", "noref": "pas de référence", "summary": "Résumé", "played_in": "joué en",
        "min": "min", "stopped": "arrêté",
    },
}


def panel(path: Path, dim: bool = False):
    from PIL import Image
    if not path.is_file():
        return Image.new("RGB", (W, H), (40, 42, 48))
    with Image.open(path) as picture:
        out = picture.convert("RGB").resize((W, H))
    if dim:
        out = out.convert("L").convert("RGB").point(lambda v: v * 0.35)
    return out


def found_panel(strip: Path, new: Path, changed: bool):
    """The third panel of the comparison's strip, as in the review sheet; the run dimmed otherwise."""
    from PIL import Image
    if changed and strip.is_file():
        with Image.open(strip) as picture:
            third = picture.convert("RGB")
            width = (third.width - 8) // 3
            return third.crop((2 * (width + 4), 0, third.width, third.height)).resize((W, H))
    return panel(new, dim=True)


def build(run: Path, lang: str) -> Path:
    from PIL import Image
    t = LABELS[lang]
    summary = json.loads((run / "summary.json").read_text("utf-8"))
    config = bench.settings()
    against = bench.find_run(summary["against"]) if summary.get("against") else None
    out = run / "_artifact"
    (out / "shots").mkdir(parents=True, exist_ok=True)
    (out / "zoom").mkdir(exist_ok=True)
    tag = json.loads((run / "_run.json").read_text("utf-8")).get("tag", run.name) if (run / "_run.json").is_file() else run.name
    order = [n for n in config.get("nodes", []) if n in summary["cells"]] + \
            [n for n in summary["cells"] if n not in config.get("nodes", [])]
    cells, ref_tags = [], set()
    for node in order:
        for profile, cell in summary["cells"][node].items():
            base = (against / node / profile) if against else bench.HOME / "baselines" / node / profile
            approved = base / "approved.json"
            ref_tag = json.loads(approved.read_text("utf-8")).get("tag") if approved.is_file() else \
                (against.name if against else None)
            if ref_tag:
                ref_tags.add(ref_tag)
            ref_run = json.loads((base / "run.json").read_text("utf-8")) if (base / "run.json").is_file() else {}
            played = cell.get("played", {})
            shots = []
            for shot, verdict in cell.get("shots", {}).items():
                changed = verdict.get("verdict") in ("changed", "missing")
                new_pic = run / node / profile / f"{shot}-a.png"
                strip = run / "_diff" / node / profile / f"{shot}.png"
                sheet = Image.new("RGB", (W * 3, H))
                sheet.paste(panel(base / f"{shot}-a.png"), (0, 0))
                sheet.paste(panel(new_pic), (W, 0))
                sheet.paste(found_panel(strip, new_pic, changed), (2 * W, 0))
                name = f"{node}-{profile}-{shot}"
                sheet.save(out / "shots" / f"{name}.webp", "WEBP", quality=82, method=5)
                zoom = run / "_diff" / node / profile / f"{shot}-zoom.png"
                zoom_name = None
                if changed and zoom.is_file():
                    with Image.open(zoom) as picture:
                        picture.convert("RGB").save(out / "zoom" / f"{name}.webp", "WEBP", quality=88, method=5)
                    zoom_name = f"zoom/{name}.webp"
                shots.append({"shot": shot, "verdict": verdict.get("verdict"), "pixels": verdict.get("pixels", 0),
                              "places": verdict.get("places", 0), "img": f"shots/{name}.webp", "zoom": zoom_name})
            cells.append({"node": node, "profile": profile, "status": cell.get("status"), "ref": ref_tag,
                          "fps": (played.get("fps") or {}).get("mean"), "ref_fps": (ref_run.get("fps") or {}).get("mean"),
                          "minutes": played.get("minutes"), "stopped": played.get("stopped"),
                          "failures": played.get("failures", []), "log": cell.get("log", []),
                          "stats": cell.get("stats", []), "shots": shots})
    page = PAGE.replace("__TITLE__", html.escape(f"{t['title']} {tag}")) \
        .replace("__DATA__", json.dumps({"run": run.name, "tag": tag, "against": summary.get("against"),
                                         "refs": sorted(ref_tags), "cells": cells, "t": t, "W": W, "H": H},
                                        ensure_ascii=False).replace("</", "<\\/"))
    (out / "index.html").write_text(page, "utf-8")
    return out


PAGE = r"""<title>__TITLE__</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>
/* Layout: a light-table of frames — a summary strip of games, then per game three columns of pictures. */
:root{--bg:#f3f4f2;--fg:#1b1f1d;--mute:#5f6863;--card:#ffffff;--line:#d6dad6;--accent:#2f6f4f;
--same:#2f7d4b;--changed:#b4600c;--failed:#b3262e;--new:#3a64c4;--shadow:rgba(20,30,25,.08);
--sans:"IBM Plex Sans",system-ui,-apple-system,"Segoe UI",sans-serif;--mono:"IBM Plex Mono",ui-monospace,Consolas,monospace}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#121513;--fg:#e3e7e4;--mute:#97a19b;--card:#1a1e1c;--line:#2c322f;--accent:#6fbf93;--same:#5cc184;--changed:#e39a4a;--failed:#ef6a70;--new:#7fa2ef;--shadow:rgba(0,0,0,.3);color-scheme:dark}}
:root[data-theme="dark"]{--bg:#121513;--fg:#e3e7e4;--mute:#97a19b;--card:#1a1e1c;--line:#2c322f;--accent:#6fbf93;--same:#5cc184;--changed:#e39a4a;--failed:#ef6a70;--new:#7fa2ef;--shadow:rgba(0,0,0,.3);color-scheme:dark}
body{background:var(--bg);color:var(--fg);font-family:var(--sans);line-height:1.45}
.wrap{max-width:1600px;margin:0 auto;padding-block:20px 48px;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px))}
header{display:flex;flex-wrap:wrap;gap:12px 24px;align-items:end;justify-content:space-between;margin-bottom:16px}
h1{font-size:1.5rem;font-weight:600;margin:0;text-wrap:balance}
.sub{color:var(--mute);margin:4px 0 0;font-size:.9rem}
.mono{font-family:var(--mono);font-variant-numeric:tabular-nums}
.toggle{display:flex;align-items:center;gap:8px;font-size:.9rem;cursor:pointer;user-select:none}
.toggle input{width:16px;height:16px;accent-color:var(--accent)}
.help{color:var(--mute);font-size:.85rem;margin:0 0 4px;max-width:75ch}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px;margin:16px 0 28px}
.chip{display:flex;flex-direction:column;gap:2px;background:var(--card);border:1px solid var(--line);border-left:4px solid var(--c);border-radius:6px;padding:8px 10px;text-decoration:none;color:inherit}
.chip:hover,.chip:focus-visible{box-shadow:0 2px 8px var(--shadow);outline:none}
.chip b{font-weight:600;font-size:.95rem}.chip span{font-size:.8rem;color:var(--mute)}
.st{color:var(--c);font-weight:600}
.s-same{--c:var(--same)}.s-changed,.s-missing{--c:var(--changed)}.s-failed{--c:var(--failed)}.s-new{--c:var(--new)}
section.cell{margin:0 0 36px;scroll-margin-top:12px}
.cellhead{display:flex;flex-wrap:wrap;gap:6px 18px;align-items:baseline;border-bottom:1px solid var(--line);padding-bottom:6px;margin-bottom:10px}
.cellhead h2{font-size:1.15rem;margin:0;font-weight:600}
.cellhead .meta{font-size:.85rem;color:var(--mute)}
.cols{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;font-size:.78rem;text-transform:uppercase;letter-spacing:.06em;color:var(--mute);margin-bottom:4px;position:sticky;top:env(safe-area-inset-top,0px);background:var(--bg);padding:6px 0;z-index:2}
.row{margin:0 0 14px}
.rowlabel{display:flex;gap:10px;align-items:baseline;font-size:.88rem;margin-bottom:4px}
.rowlabel .name{font-family:var(--mono);font-weight:500}
.frames{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.frame{aspect-ratio:854/480;max-width:100%;background-repeat:no-repeat;background-size:300% 100%;border:1px solid var(--line);border-radius:4px;cursor:zoom-in;padding:0;background-color:var(--card)}
.frame:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.row.is-changed .frame{border-color:var(--changed)}
.zoom{margin-top:6px}.zoom img{display:block;max-width:100%;border:1px solid var(--line);border-radius:4px;image-rendering:pixelated}
details{margin:6px 0 10px;font-size:.85rem}details pre{white-space:pre-wrap;overflow-x:auto;background:var(--card);border:1px solid var(--line);padding:8px;border-radius:4px;font-family:var(--mono);font-size:.78rem}
.lb{position:fixed;inset:0;background:rgba(8,10,9,.92);z-index:20;display:flex;flex-direction:column;gap:10px;padding:max(12px,env(safe-area-inset-top,0px)) max(12px,env(safe-area-inset-right,0px)) max(12px,env(safe-area-inset-bottom,0px)) max(12px,env(safe-area-inset-left,0px))}
.lbbar{display:flex;flex-wrap:wrap;gap:8px;align-items:center;color:#e3e7e4}
.lbbar .t{font-family:var(--mono);margin-right:auto}
.lbbar button{font:inherit;font-size:.88rem;background:#232826;color:#e3e7e4;border:1px solid #3a413d;border-radius:5px;padding:6px 12px;cursor:pointer}
.lbbar button[aria-pressed="true"]{background:#2f6f4f;border-color:#6fbf93}
.lbbar button:focus-visible{outline:2px solid #6fbf93}
.lbimg{flex:1;min-height:0;background-repeat:no-repeat;background-position:center;background-size:contain}
@media (max-width:640px){.frames,.cols{gap:4px}.cols{font-size:.68rem}}
@media (prefers-reduced-motion:no-preference){.chip{transition:box-shadow .15s}}
</style>
<div class="wrap">
  <header>
    <div>
      <h1 id="h"></h1>
      <p class="sub" id="sub"></p>
    </div>
    <label class="toggle"><input type="checkbox" id="only"> <span id="onlyl"></span></label>
  </header>
  <p class="help" id="help"></p>
  <p class="help" id="legend"></p>
  <nav class="grid" id="grid"></nav>
  <main id="cells"></main>
</div>
<div class="lb" id="lb" hidden>
  <div class="lbbar">
    <span class="t" id="lbt"></span>
    <button type="button" data-p="0" id="b0"></button>
    <button type="button" data-p="1" id="b1"></button>
    <button type="button" data-p="2" id="b2"></button>
    <button type="button" id="flip"></button>
    <button type="button" id="close"></button>
  </div>
  <div class="lbimg" id="lbimg"></div>
</div>
<script>
const D = __DATA__;
const t = D.t;
const $ = id => document.getElementById(id);
const esc = s => String(s ?? "").replace(/[&<>"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const word = v => t[v] || v;
$("h").textContent = `${t.title} · ${D.tag}`;
$("sub").textContent = D.against ? `${t.against_run} ${D.against}` : `${t.against_ref} ${D.refs.join(", ") || "—"}`;
$("onlyl").textContent = t.only_changed; $("help").textContent = t.help; $("legend").textContent = t.legend;
["reference","this","found"].forEach((k,i) => $("b"+i).textContent = (i+1) + " · " + (k === "this" ? D.tag : t[k]));
$("flip").textContent = t.flip; $("close").textContent = t.close;

const fps = c => c.fps == null ? "" : `${t.fps} ${Math.round(c.fps)}` + (c.ref_fps != null ? ` (${t.ref_short} ${Math.round(c.ref_fps)}, ${((c.fps/c.ref_fps-1)*100).toFixed(0).replace(/^(?!-)/,"+")} %)` : "");
const id = c => `c-${c.node}-${c.profile}`.replace(/[^A-Za-z0-9-]/g, "-");

$("grid").innerHTML = D.cells.map(c => {
  const n = c.shots.filter(s => s.verdict !== "same").length;
  return `<a class="chip s-${esc(c.status)}" href="#${id(c)}"><b>${esc(c.node)} · ${esc(c.profile)}</b>
    <span><span class="st">${esc(word(c.status))}</span>${n ? ` · ${n}/${c.shots.length}` : ""}</span>
    <span class="mono">${esc(fps(c))}</span></a>`;
}).join("");

$("cells").innerHTML = D.cells.map(c => {
  const rows = c.shots.map((s, i) => {
    const changed = s.verdict !== "same";
    const label = changed && s.pixels ? `${s.pixels} ${t.px} ${s.places} ${t.places}` : (changed ? "" : t.nothing);
    const frames = [0,1,2].map(p => `<button type="button" class="frame" aria-label="${esc(c.node)} ${esc(s.shot)} ${p+1}"
      data-c="${esc(id(c))}" data-i="${i}" data-p="${p}" style="background-image:url('${esc(s.img)}');background-position:${p*50}% 0"></button>`).join("");
    const zoom = s.zoom ? `<details class="zoom"><summary>${t.zoom}</summary><img src="${esc(s.zoom)}" alt="" loading="lazy"></details>` : "";
    return `<div class="row ${changed ? "is-changed" : "is-same"}"><div class="rowlabel"><span class="name">${esc(s.shot)}</span>
      <span class="st s-${esc(s.verdict)}">${esc(word(s.verdict))}</span><span class="meta mono">${esc(label)}</span></div>
      <div class="frames">${frames}</div>${zoom}</div>`;
  }).join("");
  const extra = (c.log.length ? `<details><summary>${t.log} (${c.log.length})</summary><pre>${esc(c.log.join("\n"))}</pre></details>` : "")
    + (c.stats.length ? `<details><summary>${t.stats} (${c.stats.length})</summary><pre>${esc(c.stats.map(r => JSON.stringify(r)).join("\n"))}</pre></details>` : "")
    + (c.failures.length ? `<details open><summary>${word("failed")}</summary><pre>${esc(c.failures.join("\n"))}</pre></details>` : "");
  return `<section class="cell" id="${id(c)}" data-changed="${c.status !== "same"}">
    <div class="cellhead"><h2>${esc(c.node)} · ${esc(c.profile)}</h2><span class="st s-${esc(c.status)}">${esc(word(c.status))}</span>
    <span class="meta mono">${esc(fps(c))}</span><span class="meta">${esc(c.ref ? t.reference + " " + c.ref : t.noref)}${c.minutes ? ` · ${t.played_in} ${c.minutes} ${t.min}` : ""}${c.stopped ? ` · ${t.stopped}: ${esc(c.stopped)}` : ""}</span></div>
    <div class="cols"><span>${t.reference}${c.ref ? " " + esc(c.ref) : ""}</span><span>${esc(D.tag)}</span><span>${t.found}</span></div>
    ${extra}${rows}</section>`;
}).join("");

function applyFilter() {
  const only = $("only").checked;
  document.querySelectorAll(".row.is-same").forEach(r => r.hidden = only);
  document.querySelectorAll("section.cell").forEach(s => s.hidden = only && s.dataset.changed === "false");
  try { localStorage.setItem("only", only ? "1" : ""); } catch (e) {}
}
try { $("only").checked = localStorage.getItem("only") === "1"; } catch (e) {}
$("only").addEventListener("change", applyFilter); applyFilter();

let cur = null, last = null;
function show(p) {
  cur.p = p;
  const s = cur.s;
  $("lbimg").style.backgroundImage = "none";
  const img = new Image();
  img.onload = () => {
    const cv = document.createElement("canvas"); cv.width = D.W; cv.height = D.H;
    cv.getContext("2d").drawImage(img, p * D.W, 0, D.W, D.H, 0, 0, D.W, D.H);
    $("lbimg").style.backgroundImage = `url(${cv.toDataURL()})`;
  };
  img.src = s.img;
  [0,1,2].forEach(i => $("b"+i).setAttribute("aria-pressed", String(i === p)));
  $("lbt").textContent = `${cur.c.node} · ${cur.c.profile} · ${s.shot} — ${word(s.verdict)}`;
}
function open(c, i, p) { last = document.activeElement; cur = {c, s: c.shots[i]}; $("lb").hidden = false; show(p); $("close").focus(); }
function close() { $("lb").hidden = true; cur = null; last && last.focus(); }
document.addEventListener("click", e => {
  const f = e.target.closest(".frame");
  if (f) { open(D.cells.find(c => id(c) === f.dataset.c), +f.dataset.i, +f.dataset.p); return; }
  const b = e.target.closest(".lbbar button[data-p]");
  if (b && cur) show(+b.dataset.p);
});
$("flip").addEventListener("click", () => cur && show(cur.p === 0 ? 1 : 0));
$("close").addEventListener("click", close);
$("lbimg").addEventListener("click", close);
document.addEventListener("keydown", e => {
  if (!cur) return;
  if (e.key === "Escape") close();
  else if (e.key === " ") { e.preventDefault(); show(cur.p === 0 ? 1 : 0); }
  else if (["1","2","3"].includes(e.key)) show(+e.key - 1);
});
</script>
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("run", help="a run of results/ (or last)")
    parser.add_argument("--lang", default="fr", choices=sorted(LABELS))
    args = parser.parse_args()
    print(build(bench.find_run(args.run), args.lang))


if __name__ == "__main__":
    main()
