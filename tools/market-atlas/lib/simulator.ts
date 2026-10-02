export type Scenario = 'balanced' | 'bull' | 'bear' | 'volatile' | 'shock' | 'gap' | 'illiquid' | 'suspension';
export type TimeInForce = 'DAY' | 'CONDITIONAL';
export type Side = 'buy' | 'sell';
export type Candle = { day: number; date: string; open: number; high: number; low: number; close: number; volume: number; suspended?: boolean };
export type Asset = { symbol: string; name: string; sector: string; initial: number; volatility: number; color: string; description: string };
export const ASSETS: Asset[] = [
  { symbol: 'HF1001', name: '海风消费', sector: '消费', initial: 32, volatility: .018, color: '#488377', description: '虚构的日常消费企业，利润较平稳，仍受需求与成本影响。' },
  { symbol: 'XH1002', name: '星河科技', sector: '科技', initial: 68, volatility: .031, color: '#7889c8', description: '虚构的成长企业，市场预期变化大，波动更明显。' },
  { symbol: 'QH1003', name: '青禾医疗', sector: '医疗', initial: 45, volatility: .023, color: '#be935c', description: '虚构的医疗企业，研发、政策与需求共同影响价格。' },
  { symbol: 'YS1004', name: '远山制造', sector: '制造', initial: 21, volatility: .024, color: '#ac7297', description: '虚构的制造企业，订单、原材料和经济周期影响经营。' },
  { symbol: 'YQ1005', name: '云桥能源', sector: '能源', initial: 26, volatility: .027, color: '#75949b', description: '虚构的能源企业，对商品价格与宏观周期较敏感。' },
];
export const SCENARIOS = {
  balanced: { name: '平稳市场', drift: .0004, volatility: 1, description: '价格有涨有跌，没有单向保证。先体验交易规则与成本。' },
  bull: { name: '上行周期', drift: .005, volatility: .85, description: '总体偏上行，但个股仍可能下跌。观察现金拖累与追高。' },
  bear: { name: '下行周期', drift: -.005, volatility: 1.1, description: '总体偏下行，练习仓位与回撤，不把低价当成低风险。' },
  volatile: { name: '剧烈震荡', drift: 0, volatility: 2.1, description: '波动加大，频繁交易的成本与心理压力会更显眼。' },
  shock: { name: '突发冲击', drift: .0002, volatility: 1.2, description: '第 5 个模拟交易日发生共同冲击。观察相关性与流动性。' },
  gap: { name: '隔夜跳空', drift: 0, volatility: 1.1, description: '每 4 个交易日出现虚构隔夜消息，开盘可能直接越过昨日价格。限价控制价格，却不能保证成交。' },
  illiquid: { name: '流动性紧张', drift: 0, volatility: 1.5, description: '五档对手量显著缩小。体验部分成交、剩余冻结和多次交易成本。' },
  suspension: { name: '公司事件与停牌', drift: .0002, volatility: 1.1, description: '青禾医疗第 5–6 日因虚构重大事项停牌，第 7 日复牌并跳空。持仓仍计入资产，但无法成交。' },
} as const;
export const SIMULATOR_ASSUMPTIONS = {
  conditionalOrders: 'DAY 限价申报只在当前模拟交易日有效，推进市场时剩余部分到期并解冻。跨日条件委托由模拟器保留条件、每日重新申报；交易所普通申报仅当日有效，不存在这里所说的交易所 GTC 单。旧版挂单继续按跨日条件委托读取。',
  liquidity: '五档数量由虚构日成交量生成，同一标的同一方向全账户共享每日 1% 成交预算，已消耗的数量不会因再次下单重置。可以部分成交；限价余量继续冻结，教学五档即时成交单余量自动撤销。真实盘口会持续增加、撤销和成交，本模型每日只补充一次；卖出不补充买入预算，撤单不返还已成交消耗量。',
  pricing: '这是日级教学撮合。当前委托只检查当前模拟快照，不追溯当天已发生的高低点；跨日条件委托用下一日 OHLC 判断是否可能触及，再按虚构五档撮合。无法从日 K 线确定盘中先后、真实排队和触及后的成交量，触价不等于真实成交保证。每档价格在 OHLC、10% 范围和限价内；成交记录价格为多档成交均价，滑点已含在成交价。',
  priority: '跨日重新申报先按价格优先，再按同价委托的提交顺序排队；买价高者优先、卖价低者优先。这里仅比较本账户的虚构订单，不含真实市场的其他投资者队列。涨停买不到不代表不能卖；跌停卖不掉不代表不能买，对手方向有量仍可成交。',
  fees: '默认佣金万三、最低 5 元、卖出印花税万五、过户费十万分之一。最低佣金按同一订单同一模拟交易日合并计算，多档成交不重复收取；跨日条件重申报按新订单日计费。各券商实收及合并方式可能不同，以交割单为准。',
  scope: '仅练习普通主板 A 股：100 股买入、零股一次申报卖出、T+1 与 10% 涨跌幅。未实现集合竞价、连续竞价动态有效申报价格范围（价格笼子）、撤单禁区、盘后交易、除权除息与分红税、ST/新股/其他板块和资金可取日。腾讯真实行情仅供独立观察，不参与虚构订单撮合。',
  calendar: '2026 年使用已核验的沪深休市安排；其他年份为预估日历，仅跳过周末与固定日期的元旦、劳动节、国庆法定假日，未核验其他假期及调休安排。',
} as const;
export type Lot = { qty: number; cost: number; price: number; boughtDay: number };
export type SimOrder = {
  id: string; symbol: string; side: Side; qty: number; limit: number; day: number; reserved: number; reason: string;
  state: 'open' | 'filled' | 'cancelled' | 'expired'; error?: string; kind?: 'conditional' | 'day' | 'ioc';
  timeInForce?: TimeInForce; orderType?: 'market' | 'limit'; remaining?: number; filledQty?: number; mood?: string;
  lastAttemptDay?: number; lastAttemptDate?: string; attempts?: number;
  feeDay?: number; dayAmount?: number; dayCommission?: number; dayTransfer?: number; dayStamp?: number;
};
export type Trade = { id: string; orderId?: string; symbol: string; name: string; side: Side; qty: number; price: number; referencePrice: number; day: number; date: string; commission: number; transfer: number; stamp: number; fees: number; slippage: number; realized: number; reason: string; mood: string; executionLevels?: { qty: number; price: number }[] };
export type SimState = {
  seed: number; day: number; initialCash: number; cash: number; scenario: Scenario;
  config: { commissionRate: number; minCommission: number; slippageBps: number; stampRate: number; transferRate: number };
  candles: Record<string, Candle[]>; benchmark: { day: number; date: string; value: number }[];
  lots: Record<string, Lot[]>; orders: SimOrder[]; trades: Trade[];
  equityHistory: { day: number; date: string; equity: number; benchmark: number }[];
  events: { day: number; title: string; body: string; symbol?: string }[];
  processed: string[]; processedInputs?: Record<string, string>;
  modelVersion?: 2; sessionLiquidity?: Record<string, { day: number; buy: number; sell: number }>;
};
export const cents = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
export function ensure(condition: unknown, message: string): asserts condition { if (!condition) throw new Error(message); }

function random(seed: number) {
  let value = seed >>> 0;
  return () => { value = (Math.imul(1664525, value) + 1013904223) >>> 0; return (value + 1) / 4294967297; };
}
function normal(rand: () => number) { return Math.sqrt(-2 * Math.log(rand())) * Math.cos(2 * Math.PI * rand()); }
function priceBounds(previous: number) {
  ensure(Number.isFinite(previous) && previous >= .01, '模拟行情缺少有效的前收盘价');
  return { upper: Math.max(cents(previous + .01), cents(previous * 1.1)), lower: Math.max(.01, Math.min(cents(previous - .01), cents(previous * .9))) };
}
const HOLIDAYS_2026 = [['01-01', '01-03'], ['02-15', '02-23'], ['04-04', '04-06'], ['05-01', '05-05'], ['06-19', '06-21'], ['09-25', '09-27'], ['10-01', '10-07']];
const FIXED_HOLIDAYS = [['01-01', '01-01'], ['05-01', '05-02'], ['10-01', '10-03']];
function sessionDate(value: string) {
  const date = new Date(value + 'T12:00:00Z');
  ensure(/^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value, '请输入有效的 YYYY-MM-DD 日期');
  return date;
}
export function calendarStatus(value: string) {
  const year = sessionDate(value).getUTCFullYear(), verified = year === 2026;
  return { year, verified, mode: verified ? 'verified' as const : 'estimated' as const, label: verified ? '2026 年已核验交易日历' : `${year} 年预估交易日历`, description: SIMULATOR_ASSUMPTIONS.calendar };
}
function isSession(date: Date) {
  const day = date.getUTCDay(), key = date.toISOString().slice(5, 10);
  const holidays = date.getUTCFullYear() === 2026 ? HOLIDAYS_2026 : FIXED_HOLIDAYS;
  return day !== 0 && day !== 6 && !holidays.some(([a, b]) => key >= a && key <= b);
}
export function nextSession(value: string, direction = 1) {
  ensure(direction === 1 || direction === -1, '交易日方向需要是 1 或 -1');
  const date = sessionDate(value);
  do { date.setUTCDate(date.getUTCDate() + direction); } while (!isSession(date));
  return date.toISOString().slice(0, 10);
}

function candle(previous: Candle, asset: Asset, day: number, date: string, seed: number, scenario: Scenario): Candle {
  const index = ASSETS.findIndex(a => a.symbol === asset.symbol), rand = random(seed + day * 7919 + (index + 1) * 104729);
  const common = normal(random(seed + day * 8191)), config = SCENARIOS[scenario];
  if (scenario === 'suspension' && asset.symbol === 'QH1003' && (day === 5 || day === 6)) return { day, date, open: previous.close, high: previous.close, low: previous.close, close: previous.close, volume: 0, suspended: true };
  const shock = scenario === 'shock' && day === 5 ? -.11 : 0;
  const gap = scenario === 'gap' && day > 0 && day % 4 === 0 ? (rand() > .5 ? .065 : -.065) : scenario === 'suspension' && asset.symbol === 'QH1003' && day === 7 ? -.07 : 0;
  const change = config.drift + (common * .62 + normal(rand) * .78) * asset.volatility * config.volatility + shock;
  const { upper, lower } = priceBounds(previous.close);
  const bound = (n: number) => cents(Math.max(lower, Math.min(upper, n)));
  const open = bound(previous.close * (1 + change * .3 + gap));
  const close = bound(previous.close * (1 + change + gap));
  const range = Math.abs(normal(rand)) * asset.volatility * .7;
  return { day, date, open, close, high: bound(Math.max(open, close) * (1 + range)), low: bound(Math.min(open, close) * (1 - range)), volume: Math.round((800000 + rand() * 4200000) * (1 + Math.abs(change) * 10) * (scenario === 'illiquid' ? .015 : 1)) };
}

export function createSimulation(initialCash = 100000, seed = 261002, scenario: Scenario = 'balanced'): SimState {
  ensure(Number.isFinite(initialCash) && initialCash > 0 && Number.isSafeInteger(Math.round(initialCash * 100)), '初始资金必须是有效的正金额');
  ensure(Number.isSafeInteger(seed), '模拟种子需要是有效的整数');
  ensure(Object.hasOwn(SCENARIOS, scenario), '请选择有效的市场场景');
  initialCash = cents(initialCash);
  ensure(initialCash > 0, '初始资金至少为 0.01 元');
  const dates = ['2026-09-30'];
  for (let i = 0; i < 59; i++) dates.unshift(nextSession(dates[0], -1));
  const candles: Record<string, Candle[]> = {};
  ASSETS.forEach(asset => {
    const history: Candle[] = [{ day: -59, date: dates[0], open: asset.initial, close: asset.initial, high: asset.initial * 1.01, low: asset.initial * .99, volume: 1800000 }];
    for (let i = 1; i < 60; i++) history.push(candle(history[i - 1], asset, i - 59, dates[i], seed, 'balanced'));
    candles[asset.symbol] = history;
  });
  const state: SimState = { seed, day: 0, initialCash, cash: initialCash, scenario,
    config: { commissionRate: .0003, minCommission: 5, slippageBps: 8, stampRate: .0005, transferRate: .00001 },
    candles, benchmark: [{ day: 0, date: dates[59], value: 100 }], lots: Object.fromEntries(ASSETS.map(a => [a.symbol, []])), orders: [], trades: [],
    equityHistory: [{ day: 0, date: dates[59], equity: initialCash, benchmark: initialCash }], events: [], processed: [], processedInputs: {}, modelVersion: 2, sessionLiquidity: {} };
  return state;
}
export function currentCandle(sim: SimState, symbol: string) { const values = sim.candles[symbol]; ensure(values?.length, '找不到这个模拟标的'); return values[values.length - 1]; }
function validateConfig(sim: SimState) {
  ensure(Object.values(sim.config).every(value => Number.isFinite(value) && value >= 0), '费用与滑点参数需要是非负有限数值');
  ensure(sim.config.slippageBps <= 10000, '滑点不能超过 10000 个基点');
}
export function feesFor(sim: SimState, side: Side, qty: number, price: number) {
  validateConfig(sim);
  ensure((side === 'buy' || side === 'sell') && Number.isInteger(qty) && qty > 0 && Number.isFinite(price) && price > 0 && Number.isSafeInteger(Math.round(qty * price * 100)), '成交方向、数量或价格无效');
  const amount = cents(qty * price), commission = cents(Math.max(sim.config.minCommission, amount * sim.config.commissionRate)), transfer = cents(amount * sim.config.transferRate), stamp = side === 'sell' ? cents(amount * sim.config.stampRate) : 0;
  ensure([amount, commission, transfer, stamp, commission + transfer + stamp].every(value => Number.isSafeInteger(Math.round(value * 100))), '费用结果超出可精确核算的金额范围，请降低参数');
  return { amount, commission, transfer, stamp, total: cents(commission + transfer + stamp) };
}
export function holdings(sim: SimState, symbol: string) {
  const lots = sim.lots[symbol] || [], total = lots.reduce((n, l) => n + l.qty, 0), cost = cents(lots.reduce((n, l) => n + l.cost, 0));
  const frozen = sim.orders.filter(o => o.state === 'open' && o.side === 'sell' && o.symbol === symbol).reduce((n, o) => n + remainingQuantity(o), 0);
  const sellable = Math.max(0, lots.filter(l => l.boughtDay < sim.day).reduce((n, l) => n + l.qty, 0) - frozen);
  const value = cents(total * currentCandle(sim, symbol).close);
  return { symbol, total, cost, average: total ? cost / total : 0, value, sellable, frozen, todayBought: lots.filter(l => l.boughtDay === sim.day).reduce((n, l) => n + l.qty, 0), unrealized: cents(value - cost) };
}
export function account(sim: SimState) {
  const positions = ASSETS.map(a => ({ ...a, ...holdings(sim, a.symbol) })), marketValue = cents(positions.reduce((n, p) => n + p.value, 0));
  const frozenCash = cents(sim.orders.filter(o => o.state === 'open' && o.side === 'buy').reduce((n, o) => n + o.reserved, 0));
  const equity = cents(sim.cash + marketValue), fees = cents(sim.trades.reduce((n, t) => n + t.fees, 0)), realized = cents(sim.trades.reduce((n, t) => n + t.realized, 0)), unrealized = cents(positions.reduce((n, p) => n + p.unrealized, 0));
  let peak = sim.initialCash, maxDrawdown = 0;
  [...sim.equityHistory, { equity }].forEach(point => { peak = Math.max(peak, point.equity); maxDrawdown = Math.max(maxDrawdown, (peak - point.equity) / peak); });
  return { positions, marketValue, cash: sim.cash, frozenCash, availableCash: cents(sim.cash - frozenCash), equity, fees, realized, unrealized,
    profit: cents(equity - sim.initialCash), returnPercent: (equity / sim.initialCash - 1) * 100, maxDrawdown: maxDrawdown * 100,
    exposure: equity ? marketValue / equity * 100 : 0, benchmarkReturn: (sim.benchmark.at(-1)!.value / 100 - 1) * 100 };
}
function updateEquity(sim: SimState) {
  const point = { day: sim.day, date: sim.benchmark.at(-1)!.date, equity: account(sim).equity, benchmark: cents(sim.initialCash * sim.benchmark.at(-1)!.value / 100) };
  if (sim.equityHistory.at(-1)?.day === sim.day) sim.equityHistory[sim.equityHistory.length - 1] = point; else sim.equityHistory.push(point);
}
function checkedQuantity(sim: SimState, symbol: string, side: Side, qty: number, excludeOrder?: string) {
  ensure(ASSETS.some(a => a.symbol === symbol), '这个标的不属于教学模拟市场');
  ensure(Number.isInteger(qty) && qty > 0 && qty <= 1000000, '数量需要是 1–1000000 之间的整数');
  if (side === 'buy') ensure(qty % 100 === 0, '普通主板股票买入数量需要是 100 股的整数倍');
  else {
    const ownOrder = excludeOrder ? sim.orders.find(o => o.id === excludeOrder && o.state === 'open' && o.side === 'sell' && o.symbol === symbol) : undefined;
    const eligible = sim.lots[symbol].filter(l => l.boughtDay < sim.day).reduce((n, l) => n + l.qty, 0);
    const available = Math.max(0, eligible - holdings(sim, symbol).frozen + (ownOrder ? remainingQuantity(ownOrder) : 0));
    ensure(qty <= available, `可卖 ${available} 股。今天买入的股票下一个交易日才可卖（T+1）。`);
    ensure(qty % 100 === 0 || (available % 100 > 0 && qty % 100 === available % 100), '不足 100 股的零股需要一次性卖出，不可拆分');
  }
}
export function remainingQuantity(order: SimOrder) { return order.remaining ?? (order.state === 'filled' ? 0 : order.qty); }
export function orderValidity(order: SimOrder): TimeInForce { return order.timeInForce ?? 'CONDITIONAL'; }
function normalizeOrders(sim: SimState) {
  sim.orders.forEach(order => {
    order.remaining ??= order.state === 'filled' ? 0 : order.qty;
    order.filledQty ??= order.qty - order.remaining;
    order.timeInForce ??= 'CONDITIONAL';
    order.orderType ??= 'limit';
    order.kind ??= order.timeInForce === 'DAY' ? 'day' : 'conditional';
    if (order.state !== 'open') order.reserved = 0;
  });
  sim.modelVersion = 2;
  sim.sessionLiquidity ??= {};
  // Legacy accounts have fills but no depth-consumption index. Rebuild that index without touching the ledger.
  ASSETS.forEach(asset => {
    const buy = usedLiquidity(sim, asset.symbol, 'buy'), sell = usedLiquidity(sim, asset.symbol, 'sell');
    if (buy || sell) sim.sessionLiquidity![asset.symbol] = { day: sim.day, buy, sell };
  });
}
export type OrderInput = { id: string; symbol: string; side: Side; qty: number; type: 'market' | 'limit'; limit?: number; timeInForce?: TimeInForce; reason?: string; mood?: string };
function dailyBounds(sim: SimState, symbol: string) {
  const previous = sim.candles[symbol]?.at(-2)?.close;
  ensure(Number.isFinite(previous) && previous! > 0, '模拟行情缺少有效的前收盘价');
  return priceBounds(previous!);
}
function liquidityLimit(quote: Candle) { return Math.floor(quote.volume * .01 / 100) * 100; }
function usedLiquidity(sim: SimState, symbol: string, side: Side) {
  const stored = sim.sessionLiquidity?.[symbol];
  const recorded = sim.trades.reduce((sum, trade) => sum + (trade.day === sim.day && trade.symbol === symbol && trade.side === side ? trade.qty : 0), 0);
  return Math.max(stored?.day === sim.day ? stored[side] : 0, recorded);
}
function executionPrice(sim: SimState, symbol: string, side: Side, referencePrice: number, limit?: number) {
  const quote = currentCandle(sim, symbol), { upper, lower } = dailyBounds(sim, symbol);
  const minimum = Math.max(quote.low, lower, side === 'sell' ? limit ?? lower : lower);
  const maximum = Math.min(quote.high, upper, side === 'buy' ? limit ?? upper : upper);
  ensure(minimum <= maximum, '行情与限价之间没有可成交的价格');
  const impact = Math.max(.01, referencePrice * sim.config.slippageBps / 10000);
  return cents(Math.max(minimum, Math.min(maximum, referencePrice + (side === 'buy' ? impact : -impact))));
}
export type BookLevel = { level: number; price: number; qty: number };
function depth(sim: SimState, symbol: string, side: Side, referencePrice: number, limit?: number): BookLevel[] {
  const quote = currentCandle(sim, symbol), { upper, lower } = dailyBounds(sim, symbol);
  const cap = liquidityLimit(quote), used = usedLiquidity(sim, symbol, side);
  const locked = side === 'buy' ? referencePrice >= upper : referencePrice <= lower;
  if (quote.suspended || !cap || locked) return [];
  // At a directional lock, the opposite side may still trade against the queue at the limit price.
  const best = side === 'sell' && referencePrice >= upper ? upper : side === 'buy' && referencePrice <= lower ? lower : executionPrice(sim, symbol, side, referencePrice, limit);
  const weights = [.3, .25, .2, .15, .1], lots = cap / 100, quantities = weights.map(weight => Math.floor(lots * weight) * 100), result: BookLevel[] = [];
  const remainder = lots - quantities.reduce((sum, qty) => sum + qty, 0) / 100;
  const fractions = weights.map((weight, index) => ({ index, fraction: lots * weight % 1 })).sort((a, b) => b.fraction - a.fraction || a.index - b.index);
  for (let i = 0; i < remainder; i++) quantities[fractions[i].index] += 100;
  let consumed = used;
  for (let i = 0; i < 5; i++) {
    const raw = quantities[i];
    const taken = Math.min(raw, consumed); consumed -= taken;
    const price = cents(best + (side === 'buy' ? 1 : -1) * i * .01);
    const valid = price >= Math.max(quote.low, lower) && price <= Math.min(quote.high, upper);
    result.push({ level: i + 1, price: Math.max(.01, price), qty: valid ? raw - taken : 0 });
  }
  return result;
}
export function orderBook(sim: SimState, symbol: string) {
  const quote = currentCandle(sim, symbol), { upper, lower } = dailyBounds(sim, symbol);
  const bids = depth(sim, symbol, 'sell', quote.close), asks = depth(sim, symbol, 'buy', quote.close);
  return { bids, asks, upper, lower, suspended: !!quote.suspended, dayCapacity: liquidityLimit(quote),
    availableToBuy: asks.reduce((sum, level) => sum + level.qty, 0), availableToSell: bids.reduce((sum, level) => sum + level.qty, 0) };
}
function matches(sim: SimState, symbol: string, side: Side, qty: number, limit: number, referencePrice: number, rangeTouch = false) {
  let left = qty;
  return depth(sim, symbol, side, referencePrice, rangeTouch ? limit : undefined).flatMap(level => {
    const crosses = side === 'buy' ? level.price <= limit : level.price >= limit;
    const amount = crosses ? Math.min(left, level.qty) : 0;
    left -= amount;
    return amount > 0 ? [{ qty: amount, price: level.price }] : [];
  });
}
function feesAtAmount(sim: SimState, side: Side, amount: number) {
  if (!amount) return { commission: 0, transfer: 0, stamp: 0, total: 0 };
  const commission = cents(Math.max(sim.config.minCommission, amount * sim.config.commissionRate));
  const transfer = cents(amount * sim.config.transferRate), stamp = side === 'sell' ? cents(amount * sim.config.stampRate) : 0;
  return { commission, transfer, stamp, total: cents(commission + transfer + stamp) };
}
function dayCharges(order: SimOrder, day: number) {
  return order.feeDay === day ? { amount: order.dayAmount || 0, commission: order.dayCommission || 0, transfer: order.dayTransfer || 0, stamp: order.dayStamp || 0 } : { amount: 0, commission: 0, transfer: 0, stamp: 0 };
}
function reservation(sim: SimState, order: SimOrder) {
  if (order.side === 'sell' || order.state !== 'open') return 0;
  const previous = dayCharges(order, sim.day), amount = cents(remainingQuantity(order) * order.limit);
  const cumulative = feesAtAmount(sim, order.side, cents(previous.amount + amount));
  return cents(amount + Math.max(0, cumulative.total - previous.commission - previous.transfer - previous.stamp));
}
function fill(sim: SimState, order: SimOrder, levels: { qty: number; price: number }[], referencePrice: number) {
  const qty = levels.reduce((sum, level) => sum + level.qty, 0);
  if (!qty) return;
  ensure(sim.trades.length < 1000, '本局成交记录已达到上限，请导出复盘后开启新一局');
  const amount = cents(levels.reduce((sum, level) => sum + level.qty * level.price, 0)), price = amount / qty;
  const previous = dayCharges(order, sim.day), cumulative = feesAtAmount(sim, order.side, cents(previous.amount + amount));
  const commission = cents(cumulative.commission - previous.commission), transfer = cents(cumulative.transfer - previous.transfer), stamp = cents(cumulative.stamp - previous.stamp), total = cents(commission + transfer + stamp);
  ensure([amount, total].every(value => Number.isSafeInteger(Math.round(value * 100))), '成交金额超出可精确核算范围');
  ensure(qty <= remainingQuantity(order), '成交股数不能超过剩余委托');
  if (order.side === 'sell') {
    const eligible = sim.lots[order.symbol].filter(lot => lot.boughtDay < sim.day).reduce((sum, lot) => sum + lot.qty, 0);
    ensure(qty <= eligible - holdings(sim, order.symbol).frozen + remainingQuantity(order), 'T+1 可卖股数不足，不能成交');
  }
  let realized = 0;
  if (order.side === 'buy') {
    const available = account(sim).availableCash + order.reserved;
    ensure(amount + total <= available + .001, '可用现金不足，保留未成交订单');
    sim.cash = cents(sim.cash - amount - total);
    sim.lots[order.symbol].push({ qty, cost: cents(amount + total), price, boughtDay: sim.day });
  } else {
    let left = qty, allocatedCost = 0;
    const allocations = sim.lots[order.symbol].map(lot => {
      const taken = lot.boughtDay < sim.day ? Math.min(left, lot.qty) : 0;
      const portion = taken === lot.qty ? lot.cost : cents(lot.cost * taken / lot.qty);
      left -= taken; allocatedCost = cents(allocatedCost + portion);
      return { lot, taken, portion };
    });
    ensure(left === 0, '可卖股数发生变化，请重新检查订单');
    allocations.forEach(({ lot, taken, portion }) => { lot.qty -= taken; lot.cost = cents(lot.cost - portion); });
    sim.lots[order.symbol] = sim.lots[order.symbol].filter(lot => lot.qty > 0);
    sim.cash = cents(sim.cash + amount - total);
    realized = cents(amount - total - allocatedCost);
  }
  order.remaining = remainingQuantity(order) - qty; order.filledQty = (order.filledQty || 0) + qty;
  order.feeDay = sim.day; order.dayAmount = cents(previous.amount + amount);
  order.dayCommission = cumulative.commission; order.dayTransfer = cumulative.transfer; order.dayStamp = cumulative.stamp;
  if (!order.remaining) order.state = 'filled';
  order.reserved = reservation(sim, order);
  const usage = sim.sessionLiquidity![order.symbol];
  if (!usage || usage.day !== sim.day) sim.sessionLiquidity![order.symbol] = { day: sim.day, buy: 0, sell: 0 };
  sim.sessionLiquidity![order.symbol][order.side] += qty;
  sim.trades.push({ id: `${order.id}:${sim.day}:${order.filledQty}`, orderId: order.id, symbol: order.symbol, name: ASSETS.find(asset => asset.symbol === order.symbol)!.name,
    side: order.side, qty, price, referencePrice, day: sim.day, date: currentCandle(sim, order.symbol).date,
    commission, transfer, stamp, fees: total, slippage: cents(Math.max(0, (order.side === 'buy' ? price - referencePrice : referencePrice - price) * qty)), realized,
    reason: order.reason, mood: order.mood || (orderValidity(order) === 'CONDITIONAL' ? '条件委托' : '平静'), executionLevels: levels });
  updateEquity(sim);
}
function inputFingerprint(input: OrderInput, legacy = false) {
  const fields: unknown[] = [input.symbol, input.side, input.qty, input.type, input.type === 'limit' ? input.limit : null, (input.reason || '').slice(0, legacy ? 1000 : 300), (input.mood || '平静').slice(0, 40)];
  if (!legacy) fields.push(input.type === 'market' ? 'IOC' : input.timeInForce || 'DAY');
  return JSON.stringify(fields);
}
export function orderPreview(sim: SimState, input: OrderInput) {
  validateConfig(sim);
  const quote = currentCandle(sim, input.symbol), { upper, lower } = dailyBounds(sim, input.symbol), referencePrice = quote.close;
  const protection = input.type === 'limit' ? input.limit ?? quote.close : input.side === 'buy' ? upper : lower;
  const levels = matches(sim, input.symbol, input.side, input.qty, protection, referencePrice);
  const estimatedFilledQty = levels.reduce((sum, level) => sum + level.qty, 0);
  const executable = estimatedFilledQty ? levels.reduce((sum, level) => sum + level.qty * level.price, 0) / estimatedFilledQty : executionPrice(sim, input.symbol, input.side, referencePrice);
  const price = input.type === 'limit' ? protection : executable, fees = feesFor(sim, input.side, input.qty, price);
  return { price, executable, referencePrice, upper, lower, ...fees, estimatedFilledQty, remainingQty: input.qty - estimatedFilledQty,
    cashChange: cents(input.side === 'buy' ? -fees.amount - fees.total : fees.amount - fees.total),
    slippage: cents(Math.max(0, (input.side === 'buy' ? executable - referencePrice : referencePrice - executable) * estimatedFilledQty)) };
}
function attempt(sim: SimState, order: SimOrder, futureDay = false) {
  const quote = currentCandle(sim, order.symbol), { upper, lower } = dailyBounds(sim, order.symbol);
  order.lastAttemptDay = sim.day; order.lastAttemptDate = quote.date; order.attempts = (order.attempts || 0) + 1; delete order.error;
  if (quote.suspended) { order.error = '该标的今天停牌，无成交；限价余量按有效期保留'; return; }
  if (order.limit < lower || order.limit > upper) { order.error = '条件限价超出今日涨跌幅范围，本日未申报；下一交易日重新检查'; return; }
  const reached = order.side === 'buy' ? quote.low <= order.limit : quote.high >= order.limit;
  if (futureDay && !reached) { order.error = '今日价格未触及条件，余量继续等待'; return; }
  const referencePrice = futureDay ? order.side === 'buy' ? cents(Math.min(order.limit, quote.open)) : cents(Math.max(order.limit, quote.open)) : quote.close;
  // A price touch has no depth beyond the limit: reserve some synthetic volume at that tick.
  const levels = matches(sim, order.symbol, order.side, remainingQuantity(order), order.limit, referencePrice, futureDay);
  if (!levels.length) { order.error = quote.volume === 0 ? '今日没有成交量' : '当前没有满足限价的对手量，涨跌停方向或同日流动性可能限制成交'; return; }
  fill(sim, order, levels, referencePrice);
  if (order.state === 'open') order.error = `部分成交 ${order.filledQty} / ${order.qty} 股；剩余 ${order.remaining} 股继续冻结，触价不保证全部成交`;
}
export function placeOrder(source: SimState, input: OrderInput): SimState {
  const sim = structuredClone(source);
  ensure(typeof input.id === 'string' && input.id.length >= 8 && input.id.length < 100, '订单标识不正确');
  ensure((input.reason === undefined || typeof input.reason === 'string') && (input.mood === undefined || typeof input.mood === 'string'), '交易理由与心情需要是文本');
  const fingerprint = inputFingerprint(input);
  if (sim.processed.includes(input.id)) {
    const stored = sim.processedInputs && Object.hasOwn(sim.processedInputs, input.id) ? sim.processedInputs[input.id] : undefined;
    ensure(stored === undefined || stored === fingerprint || (input.timeInForce === undefined && stored === inputFingerprint(input, true)), '同一订单标识不能提交不同内容，请使用新的订单标识');
    return sim;
  }
  ensure(input.side === 'buy' || input.side === 'sell', '请选择买入或卖出');
  ensure(input.type === 'market' || input.type === 'limit', '请选择有效订单类型');
  ensure(input.timeInForce === undefined || input.timeInForce === 'DAY' || input.timeInForce === 'CONDITIONAL', '请选择 DAY 或跨日条件委托');
  normalizeOrders(sim); validateConfig(sim); checkedQuantity(sim, input.symbol, input.side, input.qty);
  ensure(sim.orders.length < 500 && sim.trades.length < 1000, '本局订单或成交记录已达到上限，请导出复盘后开启新一局');
  const preview = orderPreview(sim, input), quote = currentCandle(sim, input.symbol);
  const protection = input.type === 'limit' ? input.limit! : input.side === 'buy' ? preview.upper : preview.lower;
  ensure(Number.isFinite(protection) && protection >= preview.lower && protection <= preview.upper && Math.abs(protection * 100 - Math.round(protection * 100)) < .000001, `限价需要以 0.01 元为单位，且在今日 ${preview.lower.toFixed(2)}–${preview.upper.toFixed(2)} 范围内`);
  const order: SimOrder = { id: input.id, symbol: input.symbol, side: input.side, qty: input.qty, remaining: input.qty, filledQty: 0,
    limit: protection, day: sim.day, reserved: 0, reason: (input.reason || '').slice(0, 300), mood: (input.mood || '平静').slice(0, 40), state: 'open',
    orderType: input.type, timeInForce: input.timeInForce || 'DAY', kind: input.type === 'market' ? 'ioc' : input.timeInForce === 'CONDITIONAL' ? 'conditional' : 'day' };
  if (input.type === 'market') {
    // IOC can only spend on available depth; no cash is frozen for its cancelled remainder.
    const levels = matches(sim, input.symbol, input.side, input.qty, protection, quote.close);
    const amount = cents(levels.reduce((sum, level) => sum + level.qty * level.price, 0));
    const fee = feesAtAmount(sim, input.side, amount);
    ensure(input.side === 'sell' || amount + fee.total <= account(sim).availableCash, '可用现金不足，请减少委托数量');
  } else {
    order.reserved = reservation(sim, order);
    ensure(input.side === 'sell' || order.reserved <= account(sim).availableCash, '挂单所需冻结现金不足');
  }
  sim.orders.push(order); attempt(sim, order);
  if (input.type === 'market' && order.state === 'open') {
    order.state = 'cancelled'; order.reserved = 0;
    order.error = `五档即时成交 ${order.filledQty} 股，剩余 ${order.remaining} 股自动撤销；无成交部分不收费`;
  }
  sim.processed.push(input.id);
  Object.defineProperty(sim.processedInputs ||= {}, input.id, { value: fingerprint, enumerable: true, configurable: true, writable: true });
  return sim;
}
export function cancelOrder(source: SimState, id: string) {
  const sim = structuredClone(source), order = sim.orders.find(order => order.id === id);
  if (order?.state === 'cancelled' || order?.state === 'expired') return sim;
  ensure(order?.state === 'open', '这个订单已经成交或取消');
  normalizeOrders(sim); order.state = 'cancelled'; order.reserved = 0;
  order.error = `撤销剩余 ${remainingQuantity(order)} 股，已成交 ${order.filledQty} 股不会撤回`;
  return sim;
}
export function advanceSimulation(source: SimState, days = 1): SimState {
  validateConfig(source);
  ensure([1, 5, 20].includes(days), '请选择推进 1、5 或 20 个交易日');
  ensure(source.day + days <= 250, '本局已达到 250 个模拟交易日，请导出复盘后开启新一局');
  const sim = structuredClone(source); normalizeOrders(sim);
  for (let i = 0; i < days; i++) {
    sim.orders.filter(order => order.state === 'open' && orderValidity(order) === 'DAY').forEach(order => {
      order.state = 'expired'; order.reserved = 0; order.error = `DAY 委托在 ${currentCandle(sim, order.symbol).date} 日终到期，剩余 ${remainingQuantity(order)} 股已解冻`;
    });
    sim.day++; const date = nextSession(sim.benchmark.at(-1)!.date);
    ASSETS.forEach(asset => sim.candles[asset.symbol].push(candle(currentCandle(sim, asset.symbol), asset, sim.day, date, sim.seed, sim.scenario)));
    sim.sessionLiquidity = {};
    const config = SCENARIOS[sim.scenario], rand = random(sim.seed + sim.day * 8191), change = config.drift + normal(rand) * .012 * config.volatility + (sim.scenario === 'shock' && sim.day === 5 ? -.08 : 0);
    sim.benchmark.push({ day: sim.day, date, value: cents(sim.benchmark.at(-1)!.value * (1 + change)) });
    if (sim.scenario === 'shock' && sim.day === 5) sim.events.push({ day: sim.day, title: '虚构事件：共同风险突然升高', body: '多行业同日承压。分散持有能减少个股风险，但未必消除共同的市场风险。' });
    else if (sim.scenario === 'gap' && sim.day % 4 === 0) sim.events.push({ day: sim.day, title: '虚构事件：隔夜消息引发跳空', body: '开盘不必等于昨日收盘。买入限价不能保证买到，卖出限价不能保证及时退出。' });
    else if (sim.scenario === 'suspension' && sim.day === 5) sim.events.push({ day: sim.day, symbol: 'QH1003', title: '虚构事件：青禾医疗停牌', body: '公司筹划重大事项，第 5–6 日停止交易。持仓价值暂沿用停牌前价格；有持仓不等于现在能卖出。' });
    else if (sim.scenario === 'suspension' && sim.day === 7) sim.events.push({ day: sim.day, symbol: 'QH1003', title: '虚构事件：青禾医疗复牌', body: '停牌期间的消息在复牌时重新定价；本教学场景仍采用普通主板 10% 范围。' });
    else if (sim.day % 7 === 0) sim.events.push({ day: sim.day, title: '虚构事件：企业经营预期更新', body: '市场重新评估增长和成本。消息改变的是预期，价格方向不能只凭标题判断。', symbol: ASSETS[sim.day % ASSETS.length].symbol });
    const queued = sim.orders.map((order, sequence) => ({ order, sequence })).filter(({ order }) => order.state === 'open');
    queued.sort((a, b) => a.order.symbol.localeCompare(b.order.symbol) || a.order.side.localeCompare(b.order.side) || (a.order.side === 'buy' ? b.order.limit - a.order.limit : a.order.limit - b.order.limit) || a.sequence - b.sequence);
    for (const { order } of queued) {
      const required = reservation(sim, order);
      if (order.side === 'buy' && required > account(sim).availableCash + order.reserved + .001) {
        order.lastAttemptDay = sim.day; order.lastAttemptDate = date; order.attempts = (order.attempts || 0) + 1;
        order.error = '新交易日佣金所需现金不足，今日不重申报；原冻结保留，可撤销余量'; continue;
      }
      order.reserved = required;
      try { attempt(sim, order, true); } catch (error) { order.error = (error as Error).message; }
    }
    updateEquity(sim);
  }
  return sim;
}

export function drawdownSeries(values: number[]) {
  ensure(values.every(value => Number.isFinite(value) && value >= 0), '净值需要是非负有限数值');
  let peak = 0; return values.map(value => { peak = Math.max(peak, value); return peak ? (value / peak - 1) * 100 : 0; });
}
export function compound(initial: number, contribution: number, annualPercent: number, years: number, feePercent = 0) {
  ensure(Number.isFinite(initial) && initial >= 0 && Number.isFinite(contribution) && contribution >= 0 && Number.isFinite(annualPercent) && annualPercent > -100 && Number.isFinite(feePercent) && feePercent >= 0 && feePercent < 100 && Number.isInteger(years) && years >= 0 && years <= 1000, '复利参数无效：金额不得为负，年收益需大于 -100%，年费率需在 0–100% 之间，年数需为 0–1000 的整数');
  const monthly = Math.pow(1 + annualPercent / 100, 1 / 12) - 1, fee = Math.pow(1 - feePercent / 100, 1 / 12);
  let value = initial, invested = initial; const series = [{ year: 0, value, invested }];
  for (let month = 1; month <= years * 12; month++) {
    value = value * (1 + monthly) * fee + contribution; invested += contribution;
    ensure(Number.isFinite(value) && Number.isFinite(invested), '复利结果超出数值范围，请降低收益率、金额或年数');
    if (month % 12 === 0) series.push({ year: month / 12, value, invested });
  }
  return series;
}
