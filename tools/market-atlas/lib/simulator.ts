export type Scenario = 'balanced' | 'bull' | 'bear' | 'volatile' | 'shock';
export type Side = 'buy' | 'sell';
export type Candle = { day: number; date: string; open: number; high: number; low: number; close: number; volume: number };
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
} as const;
export const SIMULATOR_ASSUMPTIONS = {
  conditionalOrders: '限价挂单是模拟器的条件委托：保留条件，并在每个模拟交易日重新尝试申报，直到成交或取消。交易所普通申报仅当日有效，这里不是交易所永久有效单。',
  liquidity: '单笔成交数量最多为成交当日模拟成交量的 1%（向下取整到 100 股）；数量不足时该日整笔不成交，条件委托继续等待。暂不模拟部分成交。',
  pricing: '日 K 线教学撮合不还原盘中先后顺序与真实盘口。成交价限制在当日 OHLC、涨跌幅和委托限价内；滑点比较成交时的无滑点参考价，已计入成交价，不应再次从净收益扣除。',
  calendar: '2026 年使用已核验的沪深休市安排；其他年份为预估日历，仅跳过周末与固定日期的元旦、劳动节、国庆法定假日，未核验其他假期及调休安排。',
} as const;
export type Lot = { qty: number; cost: number; price: number; boughtDay: number };
export type SimOrder = { id: string; symbol: string; side: Side; qty: number; limit: number; day: number; reserved: number; reason: string; state: 'open' | 'filled' | 'cancelled'; error?: string; kind?: 'conditional'; lastAttemptDay?: number; lastAttemptDate?: string; attempts?: number };
export type Trade = { id: string; symbol: string; name: string; side: Side; qty: number; price: number; referencePrice: number; day: number; date: string; commission: number; transfer: number; stamp: number; fees: number; slippage: number; realized: number; reason: string; mood: string };
export type SimState = {
  seed: number; day: number; initialCash: number; cash: number; scenario: Scenario;
  config: { commissionRate: number; minCommission: number; slippageBps: number; stampRate: number; transferRate: number };
  candles: Record<string, Candle[]>; benchmark: { day: number; date: string; value: number }[];
  lots: Record<string, Lot[]>; orders: SimOrder[]; trades: Trade[];
  equityHistory: { day: number; date: string; equity: number; benchmark: number }[];
  events: { day: number; title: string; body: string; symbol?: string }[];
  processed: string[]; processedInputs?: Record<string, string>;
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
  const shock = scenario === 'shock' && day === 5 ? -.11 : 0;
  const change = config.drift + (common * .62 + normal(rand) * .78) * asset.volatility * config.volatility + shock;
  const { upper, lower } = priceBounds(previous.close);
  const bound = (n: number) => cents(Math.max(lower, Math.min(upper, n)));
  const open = bound(previous.close * (1 + change * .3));
  const close = bound(previous.close * (1 + change));
  const range = Math.abs(normal(rand)) * asset.volatility * .7;
  return { day, date, open, close, high: bound(Math.max(open, close) * (1 + range)), low: bound(Math.min(open, close) * (1 - range)), volume: Math.round((800000 + rand() * 4200000) * (1 + Math.abs(change) * 10)) };
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
    equityHistory: [{ day: 0, date: dates[59], equity: initialCash, benchmark: initialCash }], events: [], processed: [], processedInputs: {} };
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
  const frozen = sim.orders.filter(o => o.state === 'open' && o.side === 'sell' && o.symbol === symbol).reduce((n, o) => n + o.qty, 0);
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
    const available = Math.max(0, eligible - holdings(sim, symbol).frozen + (ownOrder?.qty || 0));
    ensure(qty <= available, `可卖 ${available} 股。今天买入的股票下一个交易日才可卖（T+1）。`);
    ensure(qty % 100 === 0 || (available % 100 > 0 && qty % 100 === available % 100), '不足 100 股的零股需要一次性卖出，不可拆分');
  }
}
function execute(sim: SimState, id: string, symbol: string, side: Side, qty: number, price: number, referencePrice: number, reason: string, mood: string, excludedOrder?: string) {
  checkedQuantity(sim, symbol, side, qty, excludedOrder);
  const fee = feesFor(sim, side, qty, price), quote = currentCandle(sim, symbol), asset = ASSETS.find(a => a.symbol === symbol)!;
  let realized = 0;
  if (side === 'buy') {
    const available = account(sim).availableCash + (excludedOrder ? sim.orders.find(o => o.id === excludedOrder)?.reserved || 0 : 0);
    ensure(fee.amount + fee.total <= available + .001, `可用现金不足：需要 ¥${cents(fee.amount + fee.total).toFixed(2)}，可用 ¥${available.toFixed(2)}`);
    sim.cash = cents(sim.cash - fee.amount - fee.total);
    sim.lots[symbol].push({ qty, cost: cents(fee.amount + fee.total), price, boughtDay: sim.day });
  } else {
    let left = qty, allocatedCost = 0;
    const allocations = sim.lots[symbol].map(lot => {
      const taken = lot.boughtDay < sim.day ? Math.min(left, lot.qty) : 0;
      const portion = taken === lot.qty ? lot.cost : cents(lot.cost * taken / lot.qty);
      left -= taken; allocatedCost = cents(allocatedCost + portion);
      return { lot, taken, portion };
    });
    ensure(left === 0, '可卖股数发生变化，请重新检查订单');
    allocations.forEach(({ lot, taken, portion }) => { lot.qty -= taken; lot.cost = cents(lot.cost - portion); });
    sim.lots[symbol] = sim.lots[symbol].filter(l => l.qty > 0);
    sim.cash = cents(sim.cash + fee.amount - fee.total); realized = cents(fee.amount - fee.total - allocatedCost);
  }
  sim.trades.push({ id, symbol, name: asset.name, side, qty, price, referencePrice, day: sim.day, date: quote.date,
    commission: fee.commission, transfer: fee.transfer, stamp: fee.stamp, fees: fee.total,
    slippage: cents(Math.max(0, (side === 'buy' ? price - referencePrice : referencePrice - price) * qty)), realized, reason, mood });
  if (excludedOrder) sim.orders.find(o => o.id === excludedOrder)!.state = 'filled';
  updateEquity(sim);
}

export type OrderInput = { id: string; symbol: string; side: Side; qty: number; type: 'market' | 'limit'; limit?: number; reason?: string; mood?: string };
function dailyBounds(sim: SimState, symbol: string) {
  const previous = sim.candles[symbol]?.at(-2)?.close;
  ensure(Number.isFinite(previous) && previous! > 0, '模拟行情缺少有效的前收盘价');
  return priceBounds(previous!);
}
function liquidityLimit(quote: Candle) { return Math.floor(quote.volume * .01 / 100) * 100; }
function executionPrice(sim: SimState, symbol: string, side: Side, referencePrice: number, limit?: number) {
  const quote = currentCandle(sim, symbol), { upper, lower } = dailyBounds(sim, symbol);
  const minimum = Math.max(quote.low, lower, side === 'sell' ? limit ?? lower : lower);
  const maximum = Math.min(quote.high, upper, side === 'buy' ? limit ?? upper : upper);
  ensure(minimum <= maximum, '行情与限价之间没有可成交的价格');
  return cents(Math.max(minimum, Math.min(maximum, cents(referencePrice * (1 + (side === 'buy' ? 1 : -1) * sim.config.slippageBps / 10000)))));
}
function inputFingerprint(input: OrderInput) {
  return JSON.stringify([input.symbol, input.side, input.qty, input.type, input.type === 'limit' ? input.limit : null, (input.reason || '').slice(0, 1000), (input.mood || '平静').slice(0, 40)]);
}
export function orderPreview(sim: SimState, input: OrderInput) {
  validateConfig(sim);
  const quote = currentCandle(sim, input.symbol), { upper, lower } = dailyBounds(sim, input.symbol), referencePrice = quote.close;
  const executable = executionPrice(sim, input.symbol, input.side, referencePrice);
  const price = input.type === 'limit' ? input.limit ?? quote.close : executable;
  const fees = feesFor(sim, input.side, input.qty, price);
  return { price, executable, referencePrice, upper, lower, ...fees, cashChange: cents(input.side === 'buy' ? -fees.amount - fees.total : fees.amount - fees.total), slippage: cents(Math.max(0, (input.side === 'buy' ? executable - referencePrice : referencePrice - executable) * input.qty)) };
}
export function placeOrder(source: SimState, input: OrderInput): SimState {
  const sim = structuredClone(source);
  ensure(typeof input.id === 'string' && input.id.length >= 8 && input.id.length < 100, '订单标识不正确');
  ensure((input.reason === undefined || typeof input.reason === 'string') && (input.mood === undefined || typeof input.mood === 'string'), '交易理由与心情需要是文本');
  const fingerprint = inputFingerprint(input);
  if (sim.processed.includes(input.id)) {
    const stored = sim.processedInputs && Object.hasOwn(sim.processedInputs, input.id) ? sim.processedInputs[input.id] : undefined;
    ensure(stored === undefined || stored === fingerprint, '同一订单标识不能提交不同内容，请使用新的订单标识');
    return sim;
  }
  ensure(input.side === 'buy' || input.side === 'sell', '请选择买入或卖出');
  ensure(input.type === 'market' || input.type === 'limit', '请选择有效订单类型');
  checkedQuantity(sim, input.symbol, input.side, input.qty);
  const preview = orderPreview(sim, input), quote = currentCandle(sim, input.symbol);
  ensure(input.qty <= liquidityLimit(quote), '单笔订单超过场景成交量的 1%，请减少数量。模拟器用这个上限表示有限流动性。');
  const reason = (input.reason || '').slice(0, 1000), mood = (input.mood || '平静').slice(0, 40);
  if (input.type === 'market') {
    ensure(!(input.side === 'buy' && quote.close >= preview.upper) && !(input.side === 'sell' && quote.close <= preview.lower), '价格达到方向上的涨跌停。教学场景保守假设没有可成交的对手单，请等待下一交易日。');
    execute(sim, input.id, input.symbol, input.side, input.qty, preview.executable, preview.referencePrice, reason, mood);
  } else {
    const price = input.limit!;
    ensure(Number.isFinite(price) && price >= preview.lower && price <= preview.upper && Math.abs(price * 100 - Math.round(price * 100)) < .000001, `限价需要以 0.01 元为单位，且在今日 ${preview.lower.toFixed(2)}–${preview.upper.toFixed(2)} 范围内`);
    const crosses = input.side === 'buy' ? price >= preview.executable : price <= preview.executable;
    if (crosses && !(input.side === 'buy' && quote.close >= preview.upper) && !(input.side === 'sell' && quote.close <= preview.lower)) execute(sim, input.id, input.symbol, input.side, input.qty, preview.executable, preview.referencePrice, reason, mood);
    else {
      const reserved = input.side === 'buy' ? cents(preview.amount + preview.total) : 0;
      ensure(input.side === 'sell' || reserved <= account(sim).availableCash, '挂单所需冻结现金不足');
      sim.orders.push({ id: input.id, symbol: input.symbol, side: input.side, qty: input.qty, limit: price, day: sim.day, reserved, reason, state: 'open', kind: 'conditional', lastAttemptDay: sim.day, lastAttemptDate: quote.date, attempts: 1 });
    }
  }
  sim.processed.push(input.id);
  Object.defineProperty(sim.processedInputs ||= {}, input.id, { value: fingerprint, enumerable: true, configurable: true, writable: true });
  return sim;
}
export function cancelOrder(source: SimState, id: string) {
  const sim = structuredClone(source), order = sim.orders.find(o => o.id === id);
  if (order?.state === 'cancelled') return sim;
  ensure(order?.state === 'open', '这个订单已经成交或取消'); order.state = 'cancelled'; return sim;
}
export function advanceSimulation(source: SimState, days = 1): SimState {
  validateConfig(source);
  ensure([1, 5, 20].includes(days), '请选择推进 1、5 或 20 个交易日');
  ensure(source.day + days <= 250, '本局已达到 250 个模拟交易日，请导出复盘后开启新一局');
  const sim = structuredClone(source);
  for (let i = 0; i < days; i++) {
    sim.day++; const date = nextSession(sim.benchmark.at(-1)!.date);
    ASSETS.forEach(asset => sim.candles[asset.symbol].push(candle(currentCandle(sim, asset.symbol), asset, sim.day, date, sim.seed, sim.scenario)));
    const config = SCENARIOS[sim.scenario], rand = random(sim.seed + sim.day * 8191), change = config.drift + normal(rand) * .012 * config.volatility + (sim.scenario === 'shock' && sim.day === 5 ? -.08 : 0);
    sim.benchmark.push({ day: sim.day, date, value: cents(sim.benchmark.at(-1)!.value * (1 + change)) });
    if (sim.scenario === 'shock' && sim.day === 5) sim.events.push({ day: sim.day, title: '虚构事件：共同风险突然升高', body: '多行业同日承压。分散持有能减少个股风险，但未必消除共同的市场风险。' });
    else if (sim.day % 7 === 0) sim.events.push({ day: sim.day, title: '虚构事件：企业经营预期更新', body: '市场重新评估增长和成本。消息改变的是预期，价格方向不能只凭标题判断。', symbol: ASSETS[sim.day % ASSETS.length].symbol });
    for (const order of sim.orders.filter(o => o.state === 'open')) {
      const quote = currentCandle(sim, order.symbol), { upper, lower } = dailyBounds(sim, order.symbol);
      order.kind = 'conditional'; order.lastAttemptDay = sim.day; order.lastAttemptDate = date; order.attempts = (order.attempts ?? 1) + 1; delete order.error;
      if (order.limit < lower || order.limit > upper) { order.error = '条件委托今日限价超出涨跌幅范围，本日未申报；保留条件，下一交易日重新检查'; continue; }
      if (order.qty > liquidityLimit(quote)) { order.error = `今日成交量的 1% 仅支持 ${liquidityLimit(quote)} 股，整笔不成交；暂不模拟部分成交，下一交易日重试`; continue; }
      const reached = order.side === 'buy' ? quote.low <= order.limit : quote.high >= order.limit;
      const locked = order.side === 'buy' ? quote.low >= upper : quote.high <= lower;
      if (reached && !locked) {
        const referencePrice = order.side === 'buy' ? cents(Math.min(order.limit, quote.open)) : cents(Math.max(order.limit, quote.open));
        try {
          const price = executionPrice(sim, order.symbol, order.side, referencePrice, order.limit);
          execute(sim, order.id, order.symbol, order.side, order.qty, price, referencePrice, order.reason, '条件委托', order.id);
        } catch (error) { order.error = (error as Error).message; }
      } else if (locked) {
        order.error = '全天方向涨跌停：教学模型保守假设无对手盘，今日不成交';
      }
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
