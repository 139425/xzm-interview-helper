import { CURATED_NEWS, INITIAL_QUOTES, WATCHLIST, marketStatus, type NewsItem, type Quote } from './data';
import { cacheRead, cacheSave } from './storage';

async function readRemote<T>(url: string, read: (response: Response) => Promise<T>, timeout = 7000): Promise<T> {
  const controller = new AbortController(), timer = setTimeout(() => controller.abort(), timeout);
  try { const response = await fetch(url, { signal: controller.signal, headers: { 'User-Agent': 'MarketAtlasEducational/1.0', 'Accept': 'text/html,application/json,*/*' } }); if (!response.ok) throw new Error(`上游返回 ${response.status}`); return await read(response); } finally { clearTimeout(timer); }
}
const today = () => marketStatus().date;
export function validDate(value: string) { if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false; const date = new Date(value + 'T00:00:00Z'); return Number.isFinite(date.valueOf()) && date.toISOString().slice(0, 10) === value && value <= today() && value >= '1990-01-01'; }
export function validQuote(q: Quote) { const stamp = q.sourceTime; return WATCHLIST.some(s => s.symbol === q.symbol) && [q.last, q.prevClose, q.open, q.high, q.low, q.change, q.changePercent].every(Number.isFinite) && q.last > 0 && q.prevClose > 0 && q.open > 0 && q.low > 0 && q.high >= Math.max(q.open, q.last, q.low) && q.low <= Math.min(q.open, q.last) && /^\d{14}$/.test(stamp) && validDate(`${stamp.slice(0, 4)}-${stamp.slice(4, 6)}-${stamp.slice(6, 8)}`) && Number(stamp.slice(8, 10)) < 24 && Number(stamp.slice(10, 12)) < 60 && Number(stamp.slice(12, 14)) < 60 && (q.volumeRaw === undefined || Number.isFinite(Number(q.volumeRaw)) && Number(q.volumeRaw) >= 0); }
async function safeCache<T>(key: string, valid: (value: unknown) => boolean) {
  try { const row = await cacheRead(key); if (!row || !Number.isFinite(Date.parse(row.updated_at))) return null; const value: unknown = JSON.parse(row.payload); return valid(value) ? { value: value as T, updatedAt: row.updated_at } : null; } catch { return null; }
}
export function parseQuotes(text: string): Quote[] {
  const quotes: Quote[] = [];
  for (const match of text.matchAll(/v_((?:sh|sz)\d{6})="([^"]*)"/g)) {
    const fields = match[2].split('~'), security = WATCHLIST.find(s => s.symbol === match[1]);
    if (!security || fields.length < 35) continue;
    const quote: Quote = { symbol: security.symbol, name: security.name, last: Number(fields[3]), prevClose: Number(fields[4]), open: Number(fields[5]), high: Number(fields[33]), low: Number(fields[34]), change: Number(fields[31]), changePercent: Number(fields[32]), sourceTime: fields[30], source: '腾讯公开行情', volumeRaw: fields[6], turnoverWanYuanRaw: fields[37] };
    if (validQuote(quote)) quotes.push(quote);
  }
  return quotes;
}
export async function getQuotes(refresh = false) {
  type SavedQuotes = { quotes: Quote[]; error: string | null };
  const cached = await safeCache<SavedQuotes>('quotes-v2', v => { const x = v as SavedQuotes; return Array.isArray(x?.quotes) && x.quotes.every(validQuote); });
  if (!refresh && cached && Date.now() - Date.parse(cached.updatedAt) < 60000) return { ...cached.value, fetchedAt: cached.updatedAt, status: marketStatus(), stale: !!cached.value.error, delay: '延时未知' };
  try {
    const buffer = await readRemote(`https://qt.gtimg.cn/q=${WATCHLIST.map(s => s.symbol).join(',')}`, r => r.arrayBuffer());
    const fresh = parseQuotes(new TextDecoder().decode(buffer));
    if (fresh.length < 2) throw new Error('上游报价字段不足');
    const old = cached?.value.quotes || INITIAL_QUOTES, missing = WATCHLIST.filter(s => !fresh.some(q => q.symbol === s.symbol));
    const quotes = WATCHLIST.flatMap(s => { const q = fresh.find(q => q.symbol === s.symbol) || old.find(q => q.symbol === s.symbol); return q ? [{ ...q, stale: !fresh.some(f => f.symbol === s.symbol) }] : []; });
    const error = missing.length ? `${missing.map(s => s.name).join('、')}本次未取得有效报价；对应标的使用旧快照或暂不显示。` : null;
    await cacheSave('quotes-v2', { quotes, error }).catch(() => undefined);
    return { quotes, fetchedAt: new Date().toISOString(), status: marketStatus(), stale: !!error, error, delay: '延时未知' };
  } catch {
    return { quotes: (cached?.value.quotes || INITIAL_QUOTES).map(q => ({ ...q, stale: true })), fetchedAt: cached?.updatedAt || null, status: marketStatus(), stale: true,
      error: '本次行情更新失败，保留最近成功快照。价格时间以每个标的原始时间为准。', delay: '延时未知' };
  }
}
const clean = (html: string) => html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '').replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;|&#160;/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/\s+/g, ' ').trim();
function meta(html: string, name: string) { const match = html.match(new RegExp(`<meta[^>]+name=["']${name}["'][^>]+content=["']([^"']*)["']`, 'i')) || html.match(new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]+name=["']${name}["']`, 'i')); return match?.[1] || ''; }
function explanation(title: string) {
  if (/财务|造假|信息披露/.test(title)) return { lesson: 'cashflow', tags: ['财报', '信息披露'], explanation: '先识别原文中的调查、涉嫌、拟处罚与最终决定，再核对公司公告。利润、现金流与应收款之间的差异，是继续查证的入口。', question: '这条消息是已确认的经营结果，还是正在进行的监管程序？' };
  if (/基金|REIT|不动产/.test(title)) return { lesson: 'etf', tags: ['基金', '制度'], explanation: '注册、募集、上市和基金收益是不同阶段。先看基金类型、资产范围与费用，再考虑它和直接持有股票有什么区别。', question: '这项变化影响产品供给，还是已经改变底层资产的现金流？' };
  if (/规则|监管|意见|改革|办法/.test(title)) return { lesson: 'news', tags: ['政策', '预期'], explanation: '分清征求意见、发布与正式生效。政策可能影响融资、成本或市场参与者，但不能从标题直接推导某只股票的价格方向。', question: '如果要影响公司价值，这项变化还需要经过哪些环节？' };
  return { lesson: 'news', tags: ['官方消息', '阅读练习'], explanation: '这是官方消息的阅读入口。查看原文确认对象、日期、文件状态与可验证的事实；教学提示不构成对市场方向的判断。', question: '原文说了什么事实？哪些判断只是读者自己的推测？' };
}
export async function getNews(refresh = false) {
  const base: NewsItem[] = CURATED_NEWS.map(n => ({ ...n, curated: true }));
  const cached = await safeCache<NewsItem[]>('news', v => Array.isArray(v) && v.every(n => typeof n.title === 'string' && validDate(n.date) && typeof n.url === 'string' && /^https:\/\//.test(n.url) && Array.isArray(n.tags))); 
  if (!refresh && cached && Date.now() - Date.parse(cached.updatedAt) < 600000) return { items: cached.value, fetchedAt: cached.updatedAt, stale: false, error: null };
  try {
    const homepage = await readRemote('https://www.csrc.gov.cn/', r => r.text());
    const urls = [...new Set([...homepage.matchAll(/(?:https:\/\/www\.csrc\.gov\.cn)?(\/csrc\/c100028\/c\d+\/content\.shtml)/g)].map(m => `https://www.csrc.gov.cn${m[1]}`))].slice(0, 5);
    if (!urls.length) throw new Error('无法识别官方新消息');
    const results = await Promise.allSettled(urls.map(async url => {
      const html = await readRemote(url, r => r.text(), 5000), title = clean(meta(html, 'ArticleTitle')), date = meta(html, 'PubDate').slice(0, 10);
      if (!title || !validDate(date) || /开除党籍|纪律审查|监察调查/.test(title)) throw new Error('文章元数据不完整');
      
      // Headlines are sourced; new body text is not republished as a fake summary.
      const fact = '已读取官方标题与发布日期；正文事实请通过原文链接核对。以下为按主题生成的阅读提示，并非逐篇人工解读。';
      return { id: url.match(/\/c(\d+)\/content/)![1], title, date, publisher: '中国证监会', url, fact, ...explanation(title), curated: false } as NewsItem;
    }));
    const dynamic = results.filter((r): r is PromiseFulfilledResult<NewsItem> => r.status === 'fulfilled').map(r => r.value);
    if (!dynamic.length) throw new Error('读取官方文章失败');
    const byUrl = new Map([...dynamic, ...base].map(n => [n.url, n]));
    const items = [...byUrl.values()].sort((a, b) => b.date.localeCompare(a.date));
    await cacheSave('news', items).catch(() => undefined);
    return { items, fetchedAt: new Date().toISOString(), stale: false, error: null };
  } catch {
    return { items: cached ? cached.value : base, fetchedAt: cached?.updatedAt || null, stale: true, error: '本次官方消息刷新未成功。保留已核验资料，发布日期仍显示在每条消息旁。' };
  }
}
type HistoricalData = { candles: { date: string; open: number; close: number; high: number; low: number; volume: number; day: number }[]; adjustment: string; volumeUnit: string };
function validHistory(value: unknown) { const data = value as HistoricalData; return Array.isArray(data?.candles) && data.candles.length >= 2 && data.candles.every((c, i, arr) => validDate(c.date) && [c.open, c.close, c.high, c.low, c.volume].every(Number.isFinite) && c.low > 0 && c.volume >= 0 && c.high >= Math.max(c.open, c.close) && c.low <= Math.min(c.open, c.close) && (i === 0 || c.date > arr[i - 1].date)) && typeof data.volumeUnit === 'string'; }
export async function getHistory(symbol: string) {
  const security = WATCHLIST.find(s => s.symbol === symbol); if (!security) throw new Error('请选择列表中的标的');
  const cached = await safeCache<HistoricalData>(`history-v2:${symbol}`, validHistory);
  if (cached && Date.now() - Date.parse(cached.updatedAt) < 1800000) return { ...cached.value, fetchedAt: cached.updatedAt, stale: false, source: '腾讯公开日K' };
  try {
    const json = await readRemote(`https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=${symbol},day,,,120,qfq`, r => r.json()) as { data?: Record<string, { qfqday?: string[][]; day?: string[][] }> };
    const record = json.data?.[symbol], rows = record?.qfqday || record?.day;
    if (!rows?.length) throw new Error('没有可用的历史数据');
    const candles = rows.map((r, i) => ({ date: r[0], open: Number(r[1]), close: Number(r[2]), high: Number(r[3]), low: Number(r[4]), volume: Number(r[5]), day: i }));
    const saved = { candles, adjustment: record?.qfqday ? '前复权' : '原始价格', volumeUnit: security.index ? '指数源口径' : '手（1手=100股；源值取整）' };
    if (!validHistory(saved)) throw new Error('历史序列字段无效');
    await cacheSave(`history-v2:${symbol}`, saved).catch(() => undefined);
    return { ...saved, fetchedAt: new Date().toISOString(), stale: false, source: '腾讯公开日K' };
  } catch {
    if (cached) return { ...cached.value, fetchedAt: cached.updatedAt, stale: true, error: '历史数据刷新失败，显示保存的序列', source: '腾讯公开日K' };
    throw new Error('历史行情暂时无法获取；不会用生成数据替代真实历史');
  }
}
