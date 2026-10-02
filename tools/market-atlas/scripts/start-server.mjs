import { spawn, spawnSync } from 'node:child_process';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import './sites-env.mjs';

const project = fileURLToPath(new URL('../', import.meta.url));
process.chdir(project);
const cli = fileURLToPath(new URL('../node_modules/wrangler/bin/wrangler.js', import.meta.url));
const config = 'dist/server/wrangler.json';
const state = resolve(process.env.MARKET_ATLAS_STATE_DIR || '.wrangler/state');
const port = Number(process.env.MARKET_ATLAS_PORT || 5174);
const host = process.env.MARKET_ATLAS_HOST || '127.0.0.1';
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid MARKET_ATLAS_PORT');
const publicOrigin = process.env.MARKET_ATLAS_PUBLIC_ORIGIN;
const publicOrigins = process.env.MARKET_ATLAS_PUBLIC_ORIGINS;
function validateOrigin(value, name) {
  const parsed = new URL(value);
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.origin !== value) {
    throw new Error(`${name} must contain HTTP(S) origins without paths or trailing slashes`);
  }
}
if (publicOrigin) validateOrigin(publicOrigin, 'MARKET_ATLAS_PUBLIC_ORIGIN');
if (publicOrigins) {
  if (!publicOrigin) throw new Error('MARKET_ATLAS_PUBLIC_ORIGINS requires MARKET_ATLAS_PUBLIC_ORIGIN');
  for (const value of publicOrigins.split(',')) validateOrigin(value.trim(), 'MARKET_ATLAS_PUBLIC_ORIGINS');
}
mkdirSync(state, { recursive: true });
// Idempotent schema creation preserves all existing learning/trading records.
const schema = readFileSync('drizzle/0000_sharp_wallflower.sql', 'utf8').replace(/CREATE TABLE /g, 'CREATE TABLE IF NOT EXISTS ');
const initialized = spawnSync(process.execPath, [cli, 'd1', 'execute', 'DB', '--local', '--config', config, '--persist-to', state, '--command', schema], { stdio: 'inherit' });
if (initialized.error) throw initialized.error;
if (initialized.status !== 0) process.exit(initialized.status || 1);
const server = spawn(process.execPath, [cli, 'dev', '--config', config, '--local', '--persist-to', state, '--ip', host, '--port', String(port), '--inspector-port', '0',
  ...(publicOrigin ? ['--var', `MARKET_ATLAS_PUBLIC_ORIGIN:${publicOrigin}`] : []),
  ...(publicOrigins ? ['--var', `MARKET_ATLAS_PUBLIC_ORIGINS:${publicOrigins}`] : [])], { stdio: 'inherit' });
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.kill(signal));
server.on('error', error => { console.error(error); process.exitCode = 1; });
server.on('exit', code => { process.exitCode = code || 0; });
