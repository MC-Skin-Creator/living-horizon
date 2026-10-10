#!/usr/bin/env node
/*
 * Checks the changelog of a pull request against its base branch:
 *
 * - entries only go into the Unreleased section: a released version never gains an
 *   entry, and no new dated section appears for a version that was never tagged
 *   (the release dates the Unreleased section by itself);
 * - the section headings are the ones the release scripts read: New, Improved,
 *   Fixed (Nouveautés, Améliorations, Corrections);
 * - en.md and fr.md have the same sections, headings and number of entries.
 *
 *   node .github/scripts/changelog-check.js <base-ref>
 */
'use strict';

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const base = process.argv[2];
if (!base) {
  console.error('usage: changelog-check.js <base-ref>');
  process.exit(2);
}

const ROOT = path.resolve(__dirname, '..', '..');
const HEADINGS = { en: ['New', 'Improved', 'Fixed'], fr: ['Nouveautés', 'Améliorations', 'Corrections'] };
const UNRELEASED = /^(Unreleased|Prochaine version)$/;
const git = args => execFileSync('git', args, { cwd: ROOT, encoding: 'utf8' });

// [{ version: '0.3.0' | null, kinds: [{ heading, entries }] }]
function parse(text) {
  const releases = [];
  for (const line of text.split(/\r?\n/)) {
    const h2 = /^## (.+?)\s*$/.exec(line);
    const h3 = /^### (.+?)\s*$/.exec(line);
    if (h2) {
      const dated = /^(\d+\.\d+\.\d+)\s+—\s+\d{4}-\d{2}-\d{2}$/.exec(h2[1]);
      releases.push({ title: h2[1], version: dated ? dated[1] : null, unreleased: UNRELEASED.test(h2[1]), kinds: [] });
    } else if (h3 && releases.length) {
      releases[releases.length - 1].kinds.push({ heading: h3[1], entries: 0 });
    } else if (/^- /.test(line) && releases.length) {
      const r = releases[releases.length - 1];
      if (!r.kinds.length) r.kinds.push({ heading: '(none)', entries: 0 });
      r.kinds[r.kinds.length - 1].entries++;
    }
  }
  return releases;
}

const entries = r => r.kinds.reduce((n, k) => n + k.entries, 0);
const tags = new Set(git(['tag', '--list', 'v*']).split('\n').map(t => t.trim().slice(1)));
const errors = [];
const files = {};

for (const lang of ['en', 'fr']) {
  const name = 'changelog/' + lang + '.md';
  const now = parse(fs.readFileSync(path.join(ROOT, name), 'utf8'));
  let before = [];
  try { before = parse(git(['show', base + ':' + name])); } catch (e) { /* new file */ }
  files[lang] = now;

  for (const r of now) {
    if (!r.version && !r.unreleased) errors.push(name + ': "## ' + r.title + '" is neither "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '" nor "## X.Y.Z — YYYY-MM-DD".');
    for (const k of r.kinds) {
      if (!HEADINGS[lang].includes(k.heading)) errors.push(name + ': "### ' + k.heading + '" under "' + r.title + '" must be one of ' + HEADINGS[lang].join(', ') + '.');
    }
    if (!r.version) continue;
    const old = before.find(o => o.version === r.version);
    if (old && entries(r) > entries(old)) {
      errors.push(name + ': ' + r.version + ' is already released and gained an entry. Add it under "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '" instead.');
    } else if (!old && !tags.has(r.version)) {
      errors.push(name + ': ' + r.version + ' was never released. Add the entries under "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '": the release dates it.');
    }
  }
  if (!now.some(r => r.unreleased)) errors.push(name + ': the "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '" section is missing.');
}

const shape = rs => rs.map(r => (r.version || 'unreleased') + ': ' + r.kinds.map(k => HEADINGS.en[HEADINGS.fr.indexOf(k.heading)] || k.heading).map((h, i) => h + ' ' + r.kinds[i].entries).join(', '));
const en = shape(files.en), fr = shape(files.fr);
for (let i = 0; i < Math.max(en.length, fr.length); i++) {
  if (en[i] !== fr[i]) errors.push('en.md and fr.md differ: "' + (en[i] || 'nothing') + '" in English, "' + (fr[i] || 'nothing') + '" in French.');
}

for (const e of errors) console.log('::error::' + e);
if (errors.length) process.exit(1);
console.log('The changelog is in order.');
