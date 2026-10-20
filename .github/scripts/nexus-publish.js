#!/usr/bin/env node
/*
 * Publishes a released jar on Nexus Mods, through its v3 API. No dependency.
 *
 *   node .github/scripts/nexus-publish.js check
 *       says which account the key belongs to (never the key itself)
 *   node .github/scripts/nexus-publish.js file --jar <path> --name <file name>
 *       --version <version> [--description <text>] [--dry]
 *       uploads the jar as the newest version of the Nexus file of that name,
 *       or as a new file when the page has none of that name yet
 *   node .github/scripts/nexus-publish.js changelog --version <version>
 *       --notes <path> [--dry]
 *       adds that version's changelog to the page
 *
 * NEXUSMODS_TOKEN is the API key (a repository secret), NEXUSMODS_ID the number in the
 * page's URL (a repository variable), NEXUSMODS_GAME the game's domain (minecraft).
 *
 * The API cannot create a mod page: it is made once by hand. Every file, though, is
 * found by its name, so the first release creates one file per target and the next
 * ones add a version to it, archiving the previous one. No file id is kept anywhere.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const API = 'https://api.nexusmods.com';
const KEY = process.env.NEXUSMODS_TOKEN || '';
const PAGE = process.env.NEXUSMODS_ID || '';
const GAME = process.env.NEXUSMODS_GAME || 'minecraft';
const AGENT = 'living-horizon-release';

function args(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--dry') out.dry = true;
    else if (a.startsWith('--')) out[a.slice(2)] = argv[++i];
    else out._.push(a);
  }
  return out;
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

// Every call to Nexus itself. A busy server (429, 5xx) is tried again; anything else
// fails with Nexus's own explanation, which never contains the key.
async function api(method, url, body) {
  for (let attempt = 1; ; attempt++) {
    const res = await fetch(API + url, {
      method,
      headers: { apikey: KEY, 'User-Agent': AGENT, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (res.ok) return res.status === 204 ? null : res.json();
    const text = await res.text();
    if ((res.status === 429 || res.status >= 500) && attempt < 5) {
      console.log(`${method} ${url}: ${res.status}, trying again`);
      await sleep(attempt * 10000);
      continue;
    }
    throw new Error(`${method} ${url}: ${res.status} ${text}`);
  }
}

async function check() {
  const user = await api('GET', '/v1/users/validate.json');
  delete user.key;
  console.log(JSON.stringify(user, null, 2));
}

// The page's own id, which the file and changelog calls want, from its number in the URL.
async function page() {
  const { data } = await api('GET', `/v3/games/${GAME}/mods/${PAGE}`);
  return data.id;
}

// The way the official upload action sends a file: a multipart upload to the presigned
// URLs, then finalised, then waited for until Nexus has scanned it.
async function upload(file) {
  const bytes = fs.readFileSync(file);
  const { data } = await api('POST', '/v3/uploads/multipart', {
    filename: path.basename(file),
    size_bytes: String(bytes.length)
  });
  const parts = [];
  for (let i = 0; i < data.part_presigned_urls.length; i++) {
    const chunk = bytes.subarray(i * data.part_size_bytes, (i + 1) * data.part_size_bytes);
    const res = await fetch(data.part_presigned_urls[i], {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream', 'Content-Length': String(chunk.length) },
      body: chunk
    });
    if (!res.ok) throw new Error(`upload part ${i + 1}: ${res.status} ${await res.text()}`);
    const etag = res.headers.get('ETag');
    if (!etag) throw new Error(`upload part ${i + 1}: no ETag returned`);
    parts.push(`<Part><PartNumber>${i + 1}</PartNumber><ETag>${etag.replace(/"/g, '')}</ETag></Part>`);
  }
  const done = await fetch(data.complete_presigned_url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/xml' },
    body: `<CompleteMultipartUpload>${parts.join('')}</CompleteMultipartUpload>`
  });
  if (!done.ok) throw new Error(`complete upload: ${done.status} ${await done.text()}`);

  await api('POST', `/v3/uploads/${data.id}/finalise`);
  for (let attempt = 0; attempt < 40; attempt++) {
    const { data: state } = await api('GET', `/v3/uploads/${data.id}`);
    if (state.state === 'available') return data.id;
    await sleep(Math.min(2000 * Math.pow(1.5, attempt), 30000));
  }
  throw new Error(`upload ${data.id} still not available`);
}

async function file(opts) {
  for (const k of ['jar', 'name', 'version']) if (!opts[k]) throw new Error(`--${k} is missing`);
  const mod = await page();
  const { data } = await api('GET', `/v3/mods/${mod}/files`);
  const same = data.mod_files.filter(f => f.name === opts.name);
  const existing = same.find(f => f.is_active) || same[0];
  console.log(existing
    ? `${opts.name}: new version ${opts.version} of file ${existing.id}`
    : `${opts.name}: new file, version ${opts.version}`);
  if (opts.dry) return;

  const id = await upload(opts.jar);
  const common = {
    upload_id: id,
    name: opts.name,
    version: opts.version,
    description: opts.description || null,
    file_category: 'main',
    update_mod_version: true
  };
  if (existing) {
    await api('POST', `/v3/mod-files/${existing.id}/versions`, { ...common, archive_existing_file: true });
  } else {
    await api('POST', '/v3/mod-files', {
      ...common,
      mod_id: mod,
      primary_mod_manager_download: false,
      allow_mod_manager_download: true,
      show_requirements_pop_up: false
    });
  }
  console.log('published');
}

// Nexus shows a changelog as plain text: the Markdown of changelog/en.md loses its
// bold and its links become "text (url)".
function plain(markdown) {
  return markdown
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1 ($2)')
    .replace(/^#{3,4}\s+(.+)$/gm, '$1:')
    .trim();
}

async function changelog(opts) {
  for (const k of ['version', 'notes']) if (!opts[k]) throw new Error(`--${k} is missing`);
  const text = plain(fs.readFileSync(opts.notes, 'utf8'));
  if (!text) return console.log('No changelog for this version.');
  if (opts.dry) return console.log(text);
  await api('POST', `/v3/mods/${await page()}/changelogs`, { version: opts.version, changelog: text });
  console.log('changelog added');
}

async function main() {
  const opts = args(process.argv.slice(2));
  const command = opts._[0];
  if (!KEY) throw new Error('NEXUSMODS_TOKEN is not set');
  if (command === 'check') return check();
  if (!PAGE) throw new Error('NEXUSMODS_ID is not set');
  if (command === 'file') return file(opts);
  if (command === 'changelog') return changelog(opts);
  throw new Error('usage: nexus-publish.js check | file ... | changelog ...');
}

main().catch(e => {
  console.error(e.message);
  process.exitCode = 1;
});
