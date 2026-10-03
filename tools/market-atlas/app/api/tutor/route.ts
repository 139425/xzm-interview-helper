import { env } from 'cloudflare:workers';
import { answerWithTutor, validateTutorInput } from '@/lib/tutor';
import { database, identity, jsonResponse, verifyOrigin } from '@/lib/storage';
const active = new Set<string>();
async function budget(key: string, limit: number, message: string) {
  const db = database();
  await db.prepare('CREATE TABLE IF NOT EXISTS tutor_usage (bucket TEXT PRIMARY KEY, uses INTEGER NOT NULL, expires_at INTEGER NOT NULL)').run();
  const result = await db.prepare('INSERT INTO tutor_usage (bucket, uses, expires_at) VALUES (?, 1, ?) ON CONFLICT(bucket) DO UPDATE SET uses = uses + 1 WHERE uses < ? RETURNING uses').bind(key, Date.now() + 172800000, limit).first<{uses:number}>();
  if (!result) throw Object.assign(new Error(message), {status:429});
}
export async function POST(request: Request) {
  let userId: string | undefined;
  let acquired = false;
  let cookie: string | null | undefined;
  let answering = false;
  try {
    verifyOrigin(request);
    if (!request.headers.get('Content-Type')?.startsWith('application/json')) return jsonResponse({error:'需要JSON请求。'},415);
    const reader = request.body?.getReader();
    if (!reader) return jsonResponse({error:'请输入一个问题。'},400);
    const chunks: Uint8Array[] = []; let size = 0;
    while (true) { const {value,done} = await reader.read(); if (done) break; size += value.byteLength; if (size > 100000) { await reader.cancel(); return jsonResponse({error:'对话过长，请开启新对话。'},413); } chunks.push(value); }
    const bytes = new Uint8Array(size); let offset = 0; for (const chunk of chunks) { bytes.set(chunk,offset); offset += chunk.length; }
    const text = new TextDecoder().decode(bytes);
    const input = validateTutorInput(JSON.parse(text));
    const key = (env as Cloudflare.Env & {MARKET_ATLAS_DEEPSEEK_API_KEY?:string}).MARKET_ATLAS_DEEPSEEK_API_KEY;
    if (!key) return jsonResponse({error:'学习助手还未连接模型服务。课程阅读可以继续，稍后再试。'},503);
    const user = identity(request); userId = user.userId; cookie = user.cookie;
    if (active.has(userId) || active.size >= 4) return jsonResponse({error:'正在回答，请稍候再提问。'},429,user.cookie);
    active.add(userId); acquired = true;
    const day = new Date().toISOString().slice(0,10), minute = Math.floor(Date.now()/60000);
    await budget(`${userId}:m:${minute}`,6,'提问有点快，请一分钟后再试。');
    await budget(`${userId}:d:${day}`,60,'今天已问了60个问题，明天可以继续。');
    await budget(`global:d:${day}`,300,'今天助手的服务额度已用完，明天恢复。课程可以继续阅读。');
    answering = true;
    const answer = await answerWithTutor(input,key,request.signal);
    await database().prepare('DELETE FROM tutor_usage WHERE expires_at < ?').bind(Date.now()).run();
    return jsonResponse(answer,200,user.cookie);
  } catch (error) {
    const e = error as Error & {status?:number};
    const message = e.name === 'TimeoutError' || e.name === 'AbortError' ? '连接超时，问题仍保留在输入框，可以重试。' : e instanceof SyntaxError ? (answering ? '回答格式不完整，请重试。' : '问题格式不正确，请重试。') : e.message;
    return jsonResponse({error:message},e.status || (answering ? (e.name === 'TimeoutError' || e.name === 'AbortError' ? 504 : 502) : 400),cookie);
  } finally { if (userId && acquired) active.delete(userId); }
}
