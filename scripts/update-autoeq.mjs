#!/usr/bin/env node
// Rebuild the bundled subset from official derived results, never measurements.
// Usage: node scripts/update-autoeq.mjs [fixed 40-character commit SHA]
import { mkdir, writeFile, rename, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createHash } from 'node:crypto';

const commit = process.argv[2] ?? '7ae0f56d53074872b028649617a22bbb4232feb7';
if (!/^[a-f0-9]{40}$/.test(commit)) throw new Error('Supply a fixed 40-character commit SHA.');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const destination = path.join(root, 'app/src/main/assets/autoeq');
const base = `https://raw.githubusercontent.com/jaakkopasanen/AutoEq/${commit}/`;
const urlFor = relative => base + relative.split('/').map(encodeURIComponent).join('/');
async function download(relative) {
  let failure;
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      const response = await fetch(urlFor(relative), { signal: AbortSignal.timeout(30_000) });
      if (response.ok) return await response.text();
      failure = new Error(`${response.status}: ${relative}`);
      if (response.status < 500 && response.status !== 429) throw failure;
    } catch (error) {
      failure = error;
      if (String(error).includes('404:')) throw error;
    }
    await new Promise(resolve => setTimeout(resolve, 500 * (attempt + 1)));
  }
  throw failure;
}

const [ranking, license] = await Promise.all([download('results/RANKING.md'), download('LICENSE')]);
if (!license.includes('MIT License')) throw new Error('Upstream LICENSE is not the expected MIT license.');
const candidates = ranking.split('\n').filter(line => line.startsWith('| [')).flatMap(line => {
  const cells = line.split('|');
  const match = cells[1].trim().match(/^\[(.*)\]\(\.\/(.*)\)$/);
  const score = Number(cells[2].trim());
  if (!match || !Number.isFinite(score) || score < 80) return [];
  const directory = decodeURIComponent(match[2]);
  const name = directory.split('/').at(-1);
  return [{ name, directory, score }];
});
if (!candidates.length) throw new Error('No eligible ranking entries; check upstream table format.');
const unique = [...new Map(candidates.map(entry => [entry.directory, entry])).values()];
const entries = new Array(unique.length);
let cursor = 0;
await Promise.all(Array.from({ length: 8 }, async () => {
  while (cursor < unique.length) {
    const index = cursor++;
    const { name, directory, score } = unique[index];
    const relative = `results/${directory}/${name} ParametricEQ.txt`;
    const text = await download(relative);
    if (!/^Preamp:/m.test(text) || !/^Filter \d+:/m.test(text)) throw new Error(`Invalid ParametricEQ: ${relative}`);
    entries[index] = {
      name,
      source: directory.split('/').slice(0, -1).join(' / '),
      score,
      path: relative,
      url: `https://github.com/jaakkopasanen/AutoEq/blob/${commit}/${relative.split('/').map(encodeURIComponent).join('/')}`,
      sha256: createHash('sha256').update(text).digest('hex'),
      text,
    };
    if ((index + 1) % 25 === 0) process.stdout.write(`Fetched ${index + 1}/${unique.length}\n`);
  }
}));
entries.sort((a, b) => a.name.localeCompare(b.name, 'en') || a.source.localeCompare(b.source, 'en'));
const generatedAt = new Date().toISOString();
const manifest = {
  version: 1,
  repository: 'https://github.com/jaakkopasanen/AutoEq',
  commit,
  generatedAt,
  license: 'MIT',
  selection: 'All unique results/RANKING.md entries with original Harman preference Score >= 80; derived ParametricEQ only.',
  rankingSha256: createHash('sha256').update(ranking).digest('hex'),
  entryCount: entries.length,
  entries,
};
const temp = `${destination}.building`;
await rm(temp, { recursive: true, force: true });
await mkdir(temp, { recursive: true });
await writeFile(path.join(temp, 'catalog.json'), JSON.stringify(manifest));
await writeFile(path.join(temp, 'LICENSE'), license);
await writeFile(path.join(temp, 'README.md'), `# Bundled AutoEq derived presets\n\nSource: https://github.com/jaakkopasanen/AutoEq\n\nFixed commit: ${commit}\n\nGenerated: ${generatedAt}\n\nLicense: MIT; original copyright and license are preserved in LICENSE.\n\nSelection: all ${entries.length} unique RANKING.md rows with original Harman preference Score >= 80. This is a bounded subset, not a complete headphone database and not a recommendation of sound quality or EQ benefit. Measurements, CSV, images and impulse responses are not bundled.\n\nEach catalog entry preserves the original derived ParametricEQ text, measurement source/type, upstream path/link and SHA-256. Text is not modified. The JSON packaging and Android loader are NeuMusic additions.\n\nRebuild: node scripts/update-autoeq.mjs ${commit}\n\nReview source/licensing and changed selection before updating to another fixed commit.\n`);
await mkdir(path.dirname(destination), { recursive: true });
// Only the three generated assets are owned by this script.
await mkdir(destination, { recursive: true });
for (const filename of ['catalog.json', 'LICENSE', 'README.md']) {
  await rename(path.join(temp, filename), path.join(destination, filename));
}
await rm(temp, { recursive: true, force: true });
process.stdout.write(`Bundled ${entries.length} entries (${Buffer.byteLength(JSON.stringify(manifest))} JSON bytes) at ${commit}\n`);
