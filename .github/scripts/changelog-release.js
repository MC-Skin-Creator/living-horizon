#!/usr/bin/env node
/*
 * Dates the changelog for a release: renames the "Unreleased" section of
 * changelog/{en,fr}.md to "## <version> — <date>" and opens a new, empty
 * "Unreleased" section above it, where the next pull requests add their entries.
 *
 *   node .github/scripts/changelog-release.js <version> [YYYY-MM-DD]
 *
 * Run by release.yml on a stable release, right before the version commit, so the
 * dated changelog is part of the tagged commit. An Unreleased section with no entry
 * is left as it is (a release with nothing player-facing is legitimate), and a file
 * that already carries the version is never touched twice.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const version = process.argv[2];
const date = process.argv[3] || new Date().toISOString().slice(0, 10);
if (!/^\d+\.\d+\.\d+$/.test(version || '')) {
  console.error('usage: changelog-release.js <version> [YYYY-MM-DD]');
  process.exit(2);
}

const UNRELEASED = /^## (Unreleased|Prochaine version)[ \t]*$/m;
const root = path.resolve(__dirname, '..', '..');

for (const lang of ['en', 'fr']) {
  const file = path.join(root, 'changelog', lang + '.md');
  if (!fs.existsSync(file)) continue;
  const text = fs.readFileSync(file, 'utf8');
  const open = UNRELEASED.exec(text);
  if (new RegExp('^## ' + version.replace(/\./g, '\\.') + ' ', 'm').test(text)) {
    console.log(lang + ': ' + version + ' is already there.');
  } else if (!open) {
    console.log(lang + ': no Unreleased section, nothing to date.');
  } else {
    const rest = text.slice(open.index + open[0].length);
    const next = rest.search(/^## /m);
    const body = next < 0 ? rest : rest.slice(0, next);
    if (!/^- /m.test(body)) {
      console.log(lang + ': Unreleased is empty, nothing to date.');
      continue;
    }
    fs.writeFileSync(file, text.replace(UNRELEASED, open[0] + '\n\n## ' + version + ' — ' + date));
    console.log(lang + ': Unreleased -> ' + version + ' — ' + date);
  }
}
