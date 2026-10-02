import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const baseline = readFileSync(resolve(root, 'docs/source-manifest.json'));
const manifest = JSON.parse(baseline);
const enhancement = JSON.parse(readFileSync(resolve(root, 'docs/enhancement-manifest.json'), 'utf8'));
const digest = content => createHash('sha256').update(content).digest('hex');
if (digest(baseline) !== enhancement.baselineManifestSha256) throw new Error('The frozen migration manifest changed');
const adapted = new Set(manifest.adapted);
const approved = new Set(Object.keys(enhancement.files));
const differences = [], missing = [], changedEnhancements = [];
for (const [file, expected] of Object.entries(enhancement.files)) {
  if (!/^[a-f0-9]{64}$/.test(expected) || file.startsWith('/') || file.split('/').includes('..')) throw new Error(`Invalid enhancement entry: ${file}`);
  try { if (digest(readFileSync(resolve(root, file))) !== expected) changedEnhancements.push(file); }
  catch (error) { if (error.code !== 'ENOENT') throw error; missing.push(file); }
}
let identical = 0;
for (const [file, expected] of Object.entries(manifest.files)) {
  let actual;
  try { actual = digest(readFileSync(resolve(root, file))); }
  catch (error) { if (error.code !== 'ENOENT') throw error; missing.push(file); continue; }
  if (actual === expected) identical++;
  else differences.push(file);
}
const unexpected = differences.filter(file => !adapted.has(file) && !approved.has(file));
console.log(JSON.stringify({
  total: Object.keys(manifest.files).length,
  identical,
  migrationAdaptations: differences.filter(file => adapted.has(file) && !approved.has(file)),
  approvedEnhancements: differences.filter(file => approved.has(file)),
  enhancementFilesVerified: approved.size - changedEnhancements.length,
  missing: [...new Set(missing)], changedEnhancements, unexpected,
}, null, 2));
if (missing.length || unexpected.length || changedEnhancements.length) process.exitCode = 1;
