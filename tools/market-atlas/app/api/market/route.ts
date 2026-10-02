import { getQuotes, getNews, getHistory } from '@/lib/market';
import { jsonResponse } from '@/lib/storage';
export async function GET(request: Request) {
  const url = new URL(request.url);
  try {
    const type = url.searchParams.get('type'), refresh = url.searchParams.get('refresh') === '1';
    if (type === 'history') return jsonResponse(await getHistory(url.searchParams.get('symbol') || 'sh600519'));
    if (type === 'quotes') return jsonResponse(await getQuotes(refresh));
    if (type === 'news') return jsonResponse(await getNews(refresh));
    const [quotes, news] = await Promise.all([getQuotes(refresh), getNews(refresh)]);
    return jsonResponse({ quotes, news });
  } catch (error) { return jsonResponse({ error: (error as Error).message || '市场资料暂时不可用' }, 503); }
}
