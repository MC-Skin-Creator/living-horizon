#!/usr/bin/env node
/*
 * Prepares the Patreon devlog post of a released version, from changelog/en.md.
 * Patreon's API cannot create posts, so the post is handed over to be pasted by
 * hand. No dependency and no AI: the text is what the pull requests wrote.
 *
 *   node .github/scripts/patreon-post.js --dry                   print the latest dated version's post
 *   node .github/scripts/patreon-post.js --version 0.5.0 --dry   print that version's post
 *   node .github/scripts/patreon-post.js --version 0.5.0 --out dir
 *                    write dir/patreon-post.txt and .html, and send them to Discord
 *
 * DISCORD_WEBHOOK_PATREON (secret, optional) is a private channel that receives the
 * post: the title, tags and collection to fill in, the body to copy, and the HTML file,
 * which keeps bold text and links when opened in a browser and copied from there.
 * Without it, the script says so and exits cleanly.
 *
 * The download links follow the release: a store is listed when its repository
 * variable (MODRINTH_ID, CURSEFORGE_ID, NEXUSMODS_ID) is set, as it is published then.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { parse, HEADINGS } = require('./announce-discord.js');

const ROOT = path.resolve(__dirname, '..', '..');
const PROJECT = 'Living Horizon';
const REPO = process.env.GITHUB_REPOSITORY || 'MC-Skin-Creator/living-horizon';
const TAGS = 'Minecraft, Living Horizon, Update, Dev Log';
const COLLECTION = 'Living Horizon - Devlogs';
// The easter egg is kept for players to find: an entry that gives it away stays out.
const SECRET = /easter egg|\bufo\b|flying saucer/i;

function stores(version) {
  const list = [['GitHub (release notes and files)', 'https://github.com/' + REPO + '/releases/tag/v' + version]];
  if (process.env.MODRINTH_ID) list.push(['Modrinth', 'https://modrinth.com/mod/living-horizon']);
  if (process.env.CURSEFORGE_ID) list.push(['CurseForge', 'https://www.curseforge.com/minecraft/mc-mods/living-horizon']);
  if (process.env.NEXUSMODS_ID) list.push(['Nexus Mods', 'https://www.nexusmods.com/minecraft/mods/' + process.env.NEXUSMODS_ID]);
  return list;
}

const count = (n, one, many) => n + ' ' + (n === 1 ? one : many);

function intro(release) {
  const n = kind => (release.sections.find(s => s.kind === kind) || { items: [] }).items.length;
  const parts = [];
  if (n('new')) parts.push(count(n('new'), 'new feature', 'new features'));
  if (n('improved')) parts.push(count(n('improved'), 'improvement', 'improvements'));
  if (n('fixed')) parts.push(count(n('fixed'), 'fix', 'fixes'));
  const list = parts.length > 1 ? parts.slice(0, -1).join(', ') + ' and ' + parts[parts.length - 1] : parts[0];
  return 'Hi everyone! ' + PROJECT + ' ' + release.version + ' is out, with ' + list
    + '. Animals, villagers, birds and other players keep living on your horizon; here is what changed.';
}

// Changelog Markdown to plain text, for the Discord copy and the .txt file.
const plain = s => s
  .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1 ($2)')
  .replace(/\*\*(.+?)\*\*/g, '$1')
  .replace(/\*(.+?)\*/g, '$1')
  .replace(/`(.+?)`/g, '$1');

const escape = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const html = s => escape(s)
  .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
  .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  .replace(/\*(.+?)\*/g, '<em>$1</em>')
  .replace(/`(.+?)`/g, '$1');

function post(release) {
  const sections = release.sections
    .map(s => ({ kind: s.kind, items: s.items.filter(i => !SECRET.test(i)) }))
    .filter(s => s.items.length);
  if (!sections.length) return null;
  const shown = { version: release.version, sections };
  const links = stores(release.version);
  const thanks = 'Thank you for supporting ' + PROJECT + '!';

  const text = [intro(shown), '']
    .concat(...sections.map(s => [HEADINGS.en[s.kind], ...s.items.map(i => '• ' + plain(i)), '']))
    .concat('Download', ...links.map(([name, url]) => '• ' + name + ': ' + url), '', thanks)
    .join('\n');

  const body = ['<p>' + escape(intro(shown)) + '</p>']
    .concat(...sections.map(s => ['<h3>' + HEADINGS.en[s.kind] + '</h3>', '<ul>',
      ...s.items.map(i => '  <li>' + html(i) + '</li>'), '</ul>']))
    .concat('<h3>Download</h3>', '<ul>',
      ...links.map(([name, url]) => '  <li>' + escape(name) + ': <a href="' + escape(url) + '">' + escape(url) + '</a></li>'),
      '</ul>', '<p>' + escape(thanks) + '</p>');
  const page = ['<!doctype html>', '<meta charset="utf-8">', '<title>' + escape(PROJECT + ' v' + release.version) + '</title>',
    '<!-- Select all, copy, and paste into the body of the Patreon post. -->', ...body, ''].join('\n');

  return { title: PROJECT + ' v' + release.version, text, page, dropped: release.sections.reduce((n, s) => n + s.items.length, 0) - sections.reduce((n, s) => n + s.items.length, 0) };
}

// Discord takes 2000 characters per message: the body is cut between lines.
function chunks(text, size) {
  const out = [];
  let current = '';
  for (const line of text.split('\n')) {
    if (current && current.length + line.length + 1 > size) { out.push(current); current = ''; }
    current += (current ? '\n' : '') + line;
  }
  if (current.trim()) out.push(current);
  return out;
}

async function send(url, made, version) {
  const hook = url + (url.includes('?') ? '&' : '?') + 'wait=true';
  // 4: no link previews, 4096: no notification sound for the body messages.
  const base = { username: PROJECT, allowed_mentions: { parse: [] } };
  const header = [
    'Patreon post ready for ' + PROJECT + ' ' + version + '.',
    'Title: ' + made.title,
    'Tags: ' + TAGS,
    'Collection: ' + COLLECTION,
    'Body: the next message(s), or open patreon-post.html in a browser and copy it to keep bold text and links.'
  ].join('\n');
  const form = new FormData();
  form.append('payload_json', JSON.stringify(Object.assign({}, base, {
    content: header, flags: 4,
    attachments: [{ id: 0, filename: 'patreon-post.html' }, { id: 1, filename: 'patreon-post.txt' }]
  })));
  form.append('files[0]', new Blob([made.page], { type: 'text/html' }), 'patreon-post.html');
  form.append('files[1]', new Blob([made.text], { type: 'text/plain' }), 'patreon-post.txt');
  const messages = [{ method: 'POST', body: form }]
    .concat(chunks(made.text, 1900).map(c => ({
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Object.assign({}, base, { content: c, flags: 4 | 4096 }))
    })));
  for (const m of messages) {
    const res = await fetch(hook, m);
    if (!res.ok) {
      console.log('::error::Discord answered ' + res.status + ' ' + (await res.text()).slice(0, 200));
      return 1;
    }
  }
  console.log('Patreon post for ' + version + ' sent to Discord.');
  return 0;
}

async function main(argv) {
  const dry = argv.includes('--dry');
  const arg = name => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] || '' : ''; };
  const wanted = arg('--version');
  const out = arg('--out');

  const file = path.join(ROOT, 'changelog', 'en.md');
  const releases = fs.existsSync(file) ? parse(fs.readFileSync(file, 'utf8')).filter(r => r.version) : [];
  // The latest dated section comes first; --version names one exactly.
  const release = wanted ? releases.find(r => r.version === wanted) : releases[0];
  if (!release || !release.sections.some(s => s.items.length)) {
    console.log('::notice::changelog/en.md has no section for ' + (wanted || 'any version') + ': no Patreon post.');
    return 0;
  }

  const made = post(release);
  if (!made) {
    console.log('::notice::Nothing in ' + release.version + ' to tell on Patreon: no post.');
    return 0;
  }
  if (made.dropped) console.log('::warning::' + made.dropped + ' entry(ies) left out of the Patreon post: they give the easter egg away.');
  if (out) {
    fs.mkdirSync(out, { recursive: true });
    fs.writeFileSync(path.join(out, 'patreon-post.txt'), made.text + '\n');
    fs.writeFileSync(path.join(out, 'patreon-post.html'), made.page);
  }
  if (dry) {
    console.log(['Title: ' + made.title, 'Tags: ' + TAGS, 'Collection: ' + COLLECTION, '', made.text].join('\n'));
    return 0;
  }
  const url = process.env.DISCORD_WEBHOOK_PATREON || '';
  if (!url) { console.log('No DISCORD_WEBHOOK_PATREON: the post was not sent to Discord.'); return 0; }
  return send(url, made, release.version);
}

main(process.argv.slice(2)).then(code => process.exit(code), e => { console.error(e); process.exit(1); });
