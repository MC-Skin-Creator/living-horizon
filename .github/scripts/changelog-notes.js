#!/usr/bin/env node
/*
 * Prints one released version's section of the changelog, as Markdown, for the
 * Modrinth and CurseForge version pages. Prints nothing when the version has no
 * section (a release with nothing player-facing).
 *
 *   node .github/scripts/changelog-notes.js <version> [en|fr]
 */
'use strict';

const fs = require('fs');
const path = require('path');

const version = process.argv[2];
const lang = process.argv[3] || 'en';
if (!version) {
  console.error('usage: changelog-notes.js <version> [en|fr]');
  process.exit(2);
}

const file = path.resolve(__dirname, '..', '..', 'changelog', lang + '.md');
const lines = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').split(/\r?\n/) : [];
const start = lines.findIndex(l => l.startsWith('## ' + version + ' '));
if (start >= 0) {
  let end = lines.findIndex((l, i) => i > start && l.startsWith('## '));
  if (end < 0) end = lines.length;
  const body = lines.slice(start + 1, end).join('\n').trim();
  if (/^- /m.test(body)) process.stdout.write(body + '\n');
}
