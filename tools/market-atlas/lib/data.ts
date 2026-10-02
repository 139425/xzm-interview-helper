import curriculum from './curriculum.json';
import research from './market-research.json';
export const LESSONS = curriculum.lessons;
export type Lesson = typeof LESSONS[number];
export const STAGES = curriculum.stages.map(stage => stage.title);
export const CURATED_NEWS = research.news;
export type NewsItem = { id: string; title: string; date: string; publisher: string; url: string; fact: string; explanation: string; question: string; tags: string[]; lesson?: string; curated?: boolean };
export type Quote = { symbol: string; name: string; last: number; prevClose: number; open: number; high: number; low: number; change: number; changePercent: number; sourceTime: string; source: string; stale?: boolean; volumeRaw?: string; turnoverWanYuanRaw?: string };
export const INITIAL_QUOTES: Quote[] = research.quoteSnapshots;
export const WATCHLIST = [
  { symbol: 'sh000001', name: '上证指数', index: true }, { symbol: 'sz399001', name: '深证成指', index: true },
  { symbol: 'sh600519', name: '贵州茅台', index: false }, { symbol: 'sz000001', name: '平安银行', index: false },
  { symbol: 'sh600036', name: '招商银行', index: false }, { symbol: 'sz300750', name: '宁德时代', index: false },
];
export function marketStatus(now = new Date()) {
  const formatter = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  const parts = Object.fromEntries(formatter.formatToParts(now).map(p => [p.type, p.value]));
  const date = `${parts.year}-${parts.month}-${parts.day}`, minutes = Number(parts.hour) * 60 + Number(parts.minute), weekday = new Date(date + 'T12:00:00Z').getUTCDay();
  const holidays = [['2026-01-01', '2026-01-03'], ['2026-02-15', '2026-02-23'], ['2026-04-04', '2026-04-06'], ['2026-05-01', '2026-05-05'], ['2026-06-19', '2026-06-21'], ['2026-09-25', '2026-09-27'], ['2026-10-01', '2026-10-07']];
  const holiday = holidays.some(([a, b]) => date >= a && date <= b);
  const verified = parts.year === '2026';
  const live = verified && !holiday && weekday !== 0 && weekday !== 6 && ((minutes >= 555 && minutes < 565) || (minutes >= 570 && minutes < 690) || (minutes >= 780 && minutes < 900) || (minutes >= 905 && minutes < 930));
  const phase = minutes >= 555 && minutes < 565 ? '开盘集合竞价' : minutes >= 570 && minutes < 690 || minutes >= 780 && minutes < 897 ? '连续竞价' : minutes >= 897 && minutes < 900 ? '收盘集合竞价' : minutes >= 905 && minutes < 930 ? '盘后固定价格时段' : '非成交时段';
  return { date, time: `${parts.hour}:${parts.minute}`, open: live, label: !verified ? '交易日历待核验 · 请查交易所' : date >= '2026-10-01' && date <= '2026-10-07' ? '国庆休市 · 10/8 开市' : holiday || weekday === 0 || weekday === 6 ? '休市 · 最近交易日快照' : live ? `${phase} · 延时未知` : `${phase} · 最近快照`, calendarVerifiedYear: 2026 };
}
