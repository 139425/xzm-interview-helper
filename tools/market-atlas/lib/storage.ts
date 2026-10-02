import { env } from 'cloudflare:workers';
import { createSimulation, type SimState } from './simulator';
export type LearningState = { completed: string[]; quizResults: Record<string, { answers: number[]; correct: number; updatedAt: string }>; bookmarked: string[]; lastLesson: string };
export type WorkspaceState = { learning: LearningState; simulator: SimState; notes: { id: string; text: string; createdAt: string }[]; newsRead: string[] };
export function initialState(): WorkspaceState { return { learning: { completed: [], quizResults: {}, bookmarked: [], lastLesson: 'beginner-money' }, simulator: createSimulation(), notes: [], newsRead: [] }; }
export function database() { if (!env.DB) throw new Error('保存服务暂时不可用，请重试'); return env.DB; }
export function identity(request: Request) {
  const trusted = request.headers.get('oai-authenticated-user-id');
  if (trusted) return { userId: trusted, cookie: null };
  const existing = request.headers.get('Cookie')?.match(/(?:^|;\s*)market_atlas=([a-f0-9-]{36})/i)?.[1];
  const token = existing || crypto.randomUUID();
  return { userId: `guest:${token}`, cookie: existing ? null : `market_atlas=${token}; Path=/tools/market-atlas; HttpOnly; SameSite=Strict; Max-Age=31536000${new URL(request.url).protocol === 'https:' ? '; Secure' : ''}` };
}
export async function readState(userId: string) {
  const db = database(), stamp = new Date().toISOString();
  await db.prepare('INSERT OR IGNORE INTO workspaces (user_id, version, state, updated_at) VALUES (?, 1, ?, ?)').bind(userId, JSON.stringify(initialState()), stamp).run();
  const row = await db.prepare('SELECT version, state, updated_at FROM workspaces WHERE user_id = ?').bind(userId).first<{ version: number; state: string; updated_at: string }>();
  if (!row) throw new Error('暂时无法打开学习记录');
  return { version: row.version, state: JSON.parse(row.state) as WorkspaceState, updatedAt: row.updated_at };
}
export async function saveState(userId: string, version: number, state: WorkspaceState) {
  const encoded = JSON.stringify(state);
  if (encoded.length > 2000000) throw new Error('记录已超过存储上限，请先导出备份');
  const stamp = new Date().toISOString();
  const result = await database().prepare('UPDATE workspaces SET version = version + 1, state = ?, updated_at = ? WHERE user_id = ? AND version = ?').bind(encoded, stamp, userId, version).run();
  if (result.meta.changes !== 1) { const error = new Error('记录已在另一窗口更新，请刷新后重新确认操作'); (error as Error & { status: number }).status = 409; throw error; }
  return { version: version + 1, state, updatedAt: stamp };
}
export function jsonResponse(value: unknown, status = 200, cookie: string | null = null) {
  const headers = new Headers({ 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  if (cookie) headers.set('Set-Cookie', cookie);
  return new Response(JSON.stringify(value), { status, headers });
}
export function verifyOrigin(request: Request) { const origin = request.headers.get('Origin'); if (origin && origin !== new URL(request.url).origin) throw new Error('请从观市页面提交操作'); }

export async function cacheRead(key: string) {
  return database().prepare('SELECT payload, updated_at FROM market_cache WHERE key = ?').bind(key).first<{ payload: string; updated_at: string }>();
}
export async function cacheSave(key: string, value: unknown) {
  await database().prepare('INSERT INTO market_cache (key, payload, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at').bind(key, JSON.stringify(value), new Date().toISOString()).run();
}
