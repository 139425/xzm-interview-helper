import { identity, readState, jsonResponse } from '@/lib/storage';
export async function GET(request: Request) {
  try { const user = identity(request); const response = jsonResponse({ format: 'market-atlas-backup', schemaVersion: 1, exportedAt: new Date().toISOString(), ...(await readState(user.userId)) }, 200, user.cookie); response.headers.set('Content-Disposition', 'attachment; filename=market-atlas-backup.json'); return response; }
  catch { return jsonResponse({ error: '暂时无法导出，已有记录仍保留' }, 503); }
}
