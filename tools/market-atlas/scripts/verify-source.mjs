import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const manifest = JSON.parse(readFileSync(resolve(root, 'docs/source-manifest.json'), 'utf8'));
const adapted = new Set(manifest.adapted);
const differences = [], missing = [];
let identical = 0;
for (const [file, expected] of Object.entries(manifest.files)) {
  let actual;
  try { actual = createHash('sha256').update(readFileSync(resolve(root, file))).digest('hex'); }
  catch (error) { if (error.code !== 'ENOENT') throw error; missing.push(file); continue; }
  if (actual === expected) identical++;
  else differences.push(file);
}
const unexpected = differences.filter(file => !adapted.has(file));
console.log(JSON.stringify({ total: Object.keys(manifest.files).length, identical, adapted: differences, missing, unexpected }, null, 2));
if (missing.length || unexpected.length) process.exitCode = 1;
