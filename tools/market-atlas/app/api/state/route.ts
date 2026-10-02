import { identity, readState, jsonResponse } from '@/lib/storage';
export async function GET(request: Request) {
  const user = identity(request);
  try { return jsonResponse(await readState(user.userId), 200, user.cookie); }
  catch { return jsonResponse({ error: '学习记录暂时无法读取，请稍后重试。' }, 503); }
}
