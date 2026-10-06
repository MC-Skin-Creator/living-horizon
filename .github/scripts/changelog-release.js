#!/usr/bin/env node
/*
 * Dates the changelog for a release: renames the "Unreleased" section of
 * changelog/{en,fr}.md to "## <version> — <date>".
 *
 *   node .github/scripts/changelog-release.js <version> [YYYY-MM-DD]
 *
 * Run by release.yml on a stable release, right before the version commit, so the
 * dated changelog is part of the tagged commit. A file with no Unreleased section
 * is left alone (a release with nothing player-facing is legitimate), and a file
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
  if (new RegExp('^## ' + version.replace(/\./g, '\\.') + ' ', 'm').test(text)) {
    console.log(lang + ': ' + version + ' is already there.');
  } else if (UNRELEASED.test(text)) {
    fs.writeFileSync(file, text.replace(UNRELEASED, '## ' + version + ' — ' + date));
    console.log(lang + ': Unreleased -> ' + version + ' — ' + date);
  } else {
    console.log(lang + ': no Unreleased section, nothing to date.');
  }
}
