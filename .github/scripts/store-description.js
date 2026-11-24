#!/usr/bin/env node
// Prints the store description of store/listing.md for one platform.
// Usage: node .github/scripts/store-description.js [modrinth|curseforge]
// CurseForge's editor drops <details> and <summary>: each folded block is
// printed unfolded there, its summary line in bold.
const fs = require('fs');
const path = require('path');

const platform = process.argv[2] || 'modrinth';
const listing = fs.readFileSync(path.join(__dirname, '../../store/listing.md'), 'utf8');
const match = listing.match(/^```markdown\n([\s\S]*?)^```$/m);
if (!match) {
  console.error('No ```markdown block in store/listing.md');
  process.exit(1);
}
let text = match[1];
if (platform === 'curseforge') {
  text = text
    .replace(/^<details>\n<summary>(.*)<\/summary>\n/gm, '**$1**\n')
    .replace(/^<\/details>\n\n?/gm, '');
} else if (platform !== 'modrinth') {
  console.error(`Unknown platform: ${platform}`);
  process.exit(1);
}
process.stdout.write(text);
