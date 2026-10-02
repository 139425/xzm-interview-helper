import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { OrderInput, SimState } from '../lib/simulator';

// Load the real TypeScript engine without generating build files or requiring a test framework.
const {
  ASSETS, SIMULATOR_ASSUMPTIONS, account, advanceSimulation, calendarStatus, cancelOrder,
  cents, compound, createSimulation, currentCandle, drawdownSeries, feesFor, holdings,
  nextSession, orderPreview, placeOrder, orderBook, orderValidity,
} = await import(new URL('../lib/simulator.ts', import.meta.url).href) as typeof import('../lib/simulator');

const SYMBOL = 'HF1001';
function setQuote(sim: SimState, price: number, symbol = SYMBOL, spread = .2) {
  Object.assign(currentCandle(sim, symbol), { open: price, close: price, high: cents(price + spread), low: Math.max(.01, cents(price - spread)), volume: 2000000 });
}
function fixture(initialCash = 100000) {
  const sim = createSimulation(initialCash);
  sim.config.slippageBps = 0;
  setQuote(sim, 10);
  Object.assign(sim.candles[SYMBOL].at(-2)!, { open: 10, close: 10, high: 10.2, low: 9.8 });
  return sim;
}
function market(id: string, side: 'buy' | 'sell', qty: number): OrderInput {
  return { id, symbol: SYMBOL, side, qty, type: 'market' };
}
function assertLedger(sim: SimState) {
  const ledgerCash = sim.initialCash + sim.trades.reduce((sum, trade) => sum + (trade.side === 'buy' ? -1 : 1) * trade.qty * trade.price - trade.fees, 0);
  const report = account(sim);
  assert.equal(sim.cash, cents(ledgerCash));
  assert.equal(report.equity, cents(sim.cash + report.marketValue));
  assert.equal(report.profit, cents(report.realized + report.unrealized));
  assert.equal(report.availableCash, cents(report.cash - report.frozenCash));
  assert.ok(report.availableCash >= 0);
}

test('conditional orders explicitly retry on each session rather than masquerading as exchange GTC orders', () => {
  const sim = createSimulation();
  const limit = orderPreview(sim, market('condition-preview', 'buy', 100)).lower;
  const queued = placeOrder(sim, { id: 'condition-persistent', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', timeInForce: 'CONDITIONAL', limit });
  const advanced = advanceSimulation(queued, 5);
  assert.match(SIMULATOR_ASSUMPTIONS.conditionalOrders, /交易所普通申报仅当日有效/);
  assert.equal(advanced.orders[0].kind, 'conditional');
  assert.equal(advanced.orders[0].day, 0);
  assert.equal(advanced.orders[0].lastAttemptDay, 5);
  assert.equal(advanced.orders[0].lastAttemptDate, currentCandle(advanced, SYMBOL).date);
  assert.equal(advanced.orders[0].attempts, 6);
  assert.equal(advanced.orders[0].state, 'open');
  assert.equal(queued.orders[0].attempts, 1);
  assertLedger(advanced);
});

test('zero-slippage conditional fills use the execution reference, not the future closing price', () => {
  let sim = createSimulation();
  sim.config.slippageBps = 0;
  sim = placeOrder(sim, { id: 'zero-slippage-condition', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', timeInForce: 'CONDITIONAL', limit: 28.60 });
  sim = advanceSimulation(sim);
  const trade = sim.trades[0];
  assert.equal(currentCandle(sim, SYMBOL).close, 28.84);
  assert.equal(trade.price, 28.60);
  assert.equal(trade.referencePrice, 28.60);
  assert.equal(trade.slippage, 0);
  assert.equal(trade.mood, '平静');
  assert.equal(trade.date, '2026-10-08');
  assert.equal(account(sim).frozenCash, 0);
  assertLedger(sim);
});

test('buy and sell conditional fills stay inside OHLC and record only actual adverse execution friction', () => {
  let buy = createSimulation(2000000, 2);
  buy = placeOrder(buy, { id: 'buy-high-boundary', symbol: 'XH1002', side: 'buy', qty: 100, type: 'limit', timeInForce: 'CONDITIONAL', limit: 62.83 });
  buy = advanceSimulation(buy);
  const buyTrade = buy.trades[0], buyCandle = currentCandle(buy, 'XH1002');
  assert.equal(buyCandle.high, 62.09);
  assert.equal(buyTrade.referencePrice, 62.05);
  assert.equal(buyTrade.price, 62.09);
  assert.equal(buyTrade.slippage, 4);
  assert.ok(buyTrade.price >= buyCandle.low && buyTrade.price <= buyCandle.high && buyTrade.price <= 62.83);
  assertLedger(buy);

  // Model an already-owned position so the sell boundary is independent of T+1.
  let sell = createSimulation(2000000, 7);
  const ownedPrice = currentCandle(sell, SYMBOL).close;
  sell.cash -= 100 * ownedPrice;
  sell.lots[SYMBOL] = [{ qty: 100, cost: 100 * ownedPrice, price: ownedPrice, boughtDay: -1 }];
  sell = placeOrder(sell, { id: 'sell-low-boundary', symbol: SYMBOL, side: 'sell', qty: 100, type: 'limit', timeInForce: 'CONDITIONAL', limit: 35.87 });
  sell = advanceSimulation(sell);
  const sellTrade = sell.trades[0], sellCandle = currentCandle(sell, SYMBOL);
  assert.equal(sellCandle.low, 35.89);
  assert.equal(sellTrade.referencePrice, 35.90);
  assert.equal(sellTrade.price, 35.89);
  assert.equal(sellTrade.slippage, 1);
  assert.ok(sellTrade.price >= sellCandle.low && sellTrade.price <= sellCandle.high && sellTrade.price >= 35.87);
});

test('market fills also respect the candle range when requested slippage would exceed it', () => {
  const sim = fixture(); sim.config.slippageBps = 1000;
  const bought = placeOrder(sim, market('market-high-clamped', 'buy', 100));
  assert.equal(bought.trades[0].price, 10.2);
  assert.equal(bought.trades[0].referencePrice, 10);
  assert.equal(bought.trades[0].slippage, 20);
  const sell = fixture(); sell.config.slippageBps = 1000;
  sell.lots[SYMBOL] = [{ qty: 100, cost: 1000, price: 10, boughtDay: -1 }];
  const sold = placeOrder(sell, market('market-low-clamped', 'sell', 100));
  assert.equal(sold.trades[0].price, 9.8);
  assert.equal(sold.trades[0].slippage, 20);
});

test('conditional orders partially fill, retain only remaining cash and release that remainder on cancellation', () => {
  const sim = createSimulation(2000000); sim.config.slippageBps = 0;
  const queued = placeOrder(sim, { id: 'liquidity-regression', symbol: SYMBOL, side: 'buy', qty: 42400, type: 'limit', timeInForce: 'CONDITIONAL', limit: 28.60 });
  const advanced = advanceSimulation(queued);
  assert.equal(currentCandle(advanced, SYMBOL).volume, 3140879);
  assert.equal(advanced.trades[0].qty, 9400);
  assert.equal(advanced.orders[0].remaining, 33000);
  assert.equal(advanced.orders[0].filledQty, 9400);
  assert.equal(advanced.orders[0].state, 'open');
  assert.equal(holdings(advanced, SYMBOL).total, 9400);
  assert.ok(advanced.orders[0].reserved < queued.orders[0].reserved);
  assert.match(advanced.orders[0].error!, /部分成交.*33000 股/);
  assert.match(SIMULATOR_ASSUMPTIONS.liquidity, /可以部分成交/);
  assertLedger(advanced);
  const cancelled = cancelOrder(advanced, queued.orders[0].id);
  assert.equal(account(cancelled).frozenCash, 0);
  assert.equal(cancelled.cash, advanced.cash);
  assert.equal(holdings(cancelled, SYMBOL).total, 9400);
  assertLedger(cancelled);
});

test('2026 sessions skip all verified holiday ranges and never treat make-up weekends as sessions', () => {
  const transitions = [
    ['2025-12-31', '2026-01-05'], ['2026-02-13', '2026-02-24'],
    ['2026-04-03', '2026-04-07'], ['2026-04-30', '2026-05-06'],
    ['2026-06-18', '2026-06-22'], ['2026-09-24', '2026-09-28'],
    ['2026-09-30', '2026-10-08'], ['2026-10-09', '2026-10-12'],
  ];
  transitions.forEach(([from, to]) => assert.equal(nextSession(from), to));
  assert.equal(nextSession('2026-09-28', -1), '2026-09-24');
  assert.equal(calendarStatus('2026-10-02').verified, true);
});

test('future calendars skip fixed holidays and explicitly identify other dates as estimates', () => {
  assert.equal(nextSession('2026-12-31'), '2027-01-04');
  assert.equal(nextSession('2027-09-30'), '2027-10-04');
  assert.equal(nextSession('2028-04-28'), '2028-05-03');
  assert.equal(calendarStatus('2027-01-04').mode, 'estimated');
  assert.match(calendarStatus('2027-01-04').label, /预估/);
  assert.match(SIMULATOR_ASSUMPTIONS.calendar, /未核验其他假期/);
  assert.throws(() => nextSession('2026-02-30'), /有效/);
  assert.throws(() => nextSession('2026-09-30', 0), /方向/);
});

test('cash, FIFO cost and realized/unrealized profit reconcile through partial and complete sales', () => {
  let sim = placeOrder(fixture(), market('fifo-buy-first', 'buy', 300));
  assert.equal(sim.lots[SYMBOL][0].cost, 3008.03);
  sim = advanceSimulation(sim); setQuote(sim, 10.5);
  sim = placeOrder(sim, market('fifo-buy-second', 'buy', 100));
  assert.equal(sim.lots[SYMBOL][1].cost, 1056.01);
  sim = advanceSimulation(sim); setQuote(sim, 11);
  sim = placeOrder(sim, market('fifo-sell-partial', 'sell', 200));
  assert.deepEqual(sim.lots[SYMBOL].map(lot => [lot.qty, lot.cost]), [[100, 1002.68], [100, 1056.01]]);
  assert.equal(sim.cash, 98127.84);
  assert.equal(sim.trades.at(-1)!.realized, 186.53);
  assert.equal(account(sim).unrealized, 141.31);
  assert.equal(account(sim).profit, 327.84);
  assertLedger(sim);
  sim = placeOrder(sim, market('fifo-sell-remaining', 'sell', 200));
  assert.equal(sim.lots[SYMBOL].length, 0);
  assert.equal(account(sim).unrealized, 0);
  assert.equal(account(sim).realized, 319.72);
  assertLedger(sim);
});

test('T+1 protects today additions while allowing yesterday positions to sell', () => {
  let sim = placeOrder(fixture(), market('tplus-buy-old', 'buy', 300));
  assert.throws(() => placeOrder(sim, market('tplus-sell-too-early', 'sell', 100)), /可卖 0 股/);
  sim = advanceSimulation(sim); setQuote(sim, 10.5);
  assert.equal(currentCandle(sim, SYMBOL).date, '2026-10-08');
  sim = placeOrder(sim, market('tplus-buy-new', 'buy', 100));
  assert.equal(holdings(sim, SYMBOL).sellable, 300);
  assert.equal(holdings(sim, SYMBOL).todayBought, 100);
  assert.throws(() => placeOrder(sim, market('tplus-sell-all', 'sell', 400)), /可卖 300 股/);
  sim = placeOrder(sim, market('tplus-sell-old', 'sell', 300));
  assert.equal(holdings(sim, SYMBOL).total, 100);
  assert.equal(holdings(sim, SYMBOL).sellable, 0);
  assert.equal(holdings(advanceSimulation(sim), SYMBOL).sellable, 100);
  assertLedger(sim);
});

test('pending buy cash remains part of equity and cancellation releases only that order', () => {
  const original = fixture(), snapshot = structuredClone(original);
  const one: OrderInput = { id: 'freeze-buy-first', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', limit: 9.8 };
  let sim = placeOrder(original, one);
  sim = placeOrder(sim, { ...one, id: 'freeze-buy-second', limit: 9.7 });
  assert.deepEqual(original, snapshot);
  assert.equal(account(sim).cash, 100000);
  assert.equal(account(sim).equity, 100000);
  assert.equal(account(sim).frozenCash, 1960.02);
  sim = cancelOrder(sim, one.id);
  assert.equal(account(sim).frozenCash, 975.01);
  assert.equal(account(sim).availableCash, 99024.99);
  assert.deepEqual(cancelOrder(sim, one.id), sim);
  assertLedger(sim);
});

test('sell orders reserve eligible shares and cannot oversell through overlapping orders', () => {
  let sim = advanceSimulation(placeOrder(fixture(), market('reserve-inventory', 'buy', 300)));
  const pending: OrderInput = { id: 'reserve-sell-first', symbol: SYMBOL, side: 'sell', qty: 100, type: 'limit', limit: 11 };
  sim = placeOrder(sim, pending);
  assert.equal(holdings(sim, SYMBOL).frozen, 100);
  assert.equal(holdings(sim, SYMBOL).sellable, 200);
  assert.throws(() => placeOrder(sim, market('reserve-sell-too-many', 'sell', 300)), /可卖 200 股/);
  sim = placeOrder(sim, { ...pending, id: 'reserve-sell-second', qty: 200 });
  assert.equal(holdings(sim, SYMBOL).sellable, 0);
  sim = cancelOrder(sim, pending.id);
  assert.equal(holdings(sim, SYMBOL).sellable, 100);
  assert.equal(holdings(sim, SYMBOL).frozen, 200);
  assertLedger(sim);
});

test('odd-lot remainder can sell alone or with board lots but cannot be split', () => {
  const sim = fixture(); sim.lots[SYMBOL] = [{ qty: 230, cost: 2300, price: 10, boughtDay: -1 }];
  assert.equal(holdings(placeOrder(sim, market('odd-sell-thirty', 'sell', 30)), SYMBOL).total, 200);
  assert.equal(holdings(placeOrder(sim, market('odd-sell-mixed', 'sell', 130)), SYMBOL).total, 100);
  assert.throws(() => placeOrder(sim, market('odd-sell-split', 'sell', 15)), /不可拆分/);
  assert.throws(() => placeOrder(sim, market('odd-sell-mixed-split', 'sell', 115)), /不可拆分/);
  assert.throws(() => placeOrder(sim, market('odd-buy-thirty', 'buy', 30)), /100 股/);
});

test('identical order retries are idempotent, conflicting reuse is rejected, and legacy IDs remain safe', () => {
  const input = market('idempotent-order', 'buy', 100), source = fixture();
  const first = placeOrder(source, input);
  assert.deepEqual(placeOrder(first, input), first);
  assert.equal(first.trades.length, 1);
  assert.equal(holdings(first, SYMBOL).total, 100);
  assert.throws(() => placeOrder(first, { ...input, qty: 200 }), /同一订单标识/);
  const legacy = structuredClone(first); delete legacy.processedInputs;
  assert.deepEqual(placeOrder(legacy, input), legacy);
  assert.equal(source.trades.length, 0);
  assertLedger(first);
});

test('order IDs matching JavaScript property names retain exact retry and conflict behavior', () => {
  for (const id of ['__proto__', 'constructor']) {
    const input = market(id, 'buy', 100), sim = placeOrder(fixture(), input);
    assert.deepEqual(placeOrder(sim, input), sim);
    assert.throws(() => placeOrder(sim, { ...input, qty: 200 }), /同一订单标识/);
    assert.equal(sim.trades.length, 1);
  }
});

test('insufficient cash and changed fees fail atomically, retaining the unfilled reservation', () => {
  const small = fixture(1000), snapshot = structuredClone(small);
  assert.throws(() => placeOrder(small, market('cash-not-enough', 'buy', 100)), /现金不足/);
  assert.deepEqual(small, snapshot);
  const queued = placeOrder(fixture(1100), { id: 'fee-change-condition', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', timeInForce: 'CONDITIONAL', limit: 9.99 });
  queued.config.minCommission = 500;
  const failed = advanceSimulation(queued);
  assert.equal(failed.trades.length, 0);
  assert.equal(failed.cash, 1100);
  assert.equal(holdings(failed, SYMBOL).total, 0);
  assert.equal(failed.orders[0].state, 'open');
  assert.equal(failed.orders[0].reserved, queued.orders[0].reserved);
  assert.match(failed.orders[0].error!, /现金不足/);
  assertLedger(failed);
});

test('fees apply buy/sell directions and bounded monetary values reject NaN or negative configuration', () => {
  const sim = fixture();
  assert.deepEqual(feesFor(sim, 'buy', 100, 10), { amount: 1000, commission: 5, transfer: .01, stamp: 0, total: 5.01 });
  assert.deepEqual(feesFor(sim, 'sell', 100, 10), { amount: 1000, commission: 5, transfer: .01, stamp: .5, total: 5.51 });
  sim.config.commissionRate = -.01;
  assert.throws(() => placeOrder(sim, market('negative-fee-order', 'buy', 100)), /非负/);
  assert.throws(() => createSimulation(0), /正金额/);
  assert.throws(() => createSimulation(Number.NaN), /正金额/);
  assert.throws(() => createSimulation(100000, Number.NaN), /种子/);
});

test('price-limit rounding retains the minimum tick at penny prices and rejects out-of-band limit orders', () => {
  const sim = fixture();
  assert.throws(() => placeOrder(sim, { id: 'price-outside-band', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', limit: 11.01 }), /限价/);
  assert.throws(() => placeOrder(sim, { id: 'price-invalid-tick', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', timeInForce: 'CONDITIONAL', limit: 9.991 }), /0.01/);
  setQuote(sim, .01, SYMBOL, .01);
  sim.candles[SYMBOL].at(-2)!.close = .01;
  const preview = orderPreview(sim, market('penny-price-preview', 'buy', 100));
  assert.equal(preview.lower, .01);
  assert.equal(preview.upper, .02);
});

test('涨停卖和跌停买仍可成交；反方向没有对手量则 IOC 撤销且不收费', () => {
  const upper = fixture(); setQuote(upper, 11, SYMBOL, 0);
  upper.lots[SYMBOL] = [{ qty: 100, cost: 1000, price: 10, boughtDay: -1 }];
  const blockedBuy = placeOrder(upper, market('upper-lock-buy', 'buy', 100));
  assert.equal(blockedBuy.trades.length, 0);
  assert.equal(blockedBuy.orders[0].state, 'cancelled');
  assert.equal(blockedBuy.cash, upper.cash);
  const validSell = placeOrder(upper, market('upper-valid-sell', 'sell', 100));
  assert.equal(validSell.trades[0].price, 11);
  const lower = fixture(); setQuote(lower, 9, SYMBOL, 0);
  lower.lots[SYMBOL] = [{ qty: 100, cost: 1000, price: 10, boughtDay: -1 }];
  const blockedSell = placeOrder(lower, market('lower-lock-sell', 'sell', 100));
  assert.equal(blockedSell.trades.length, 0);
  assert.equal(blockedSell.orders[0].state, 'cancelled');
  assert.equal(blockedSell.cash, lower.cash);
  const validBuy = placeOrder(lower, market('lower-valid-buy', 'buy', 100));
  assert.equal(validBuy.trades[0].price, 9);
  assert.equal(orderBook(upper, SYMBOL).asks.length, 0);
  assert.equal(orderBook(lower, SYMBOL).bids.length, 0);
});

test('seeded bulk and single-session advances agree and every generated OHLC remains bounded', () => {
  const source = createSimulation(100000, 42, 'shock');
  const bulk = advanceSimulation(source, 20);
  let single = source;
  for (let day = 0; day < 20; day++) single = advanceSimulation(single);
  assert.deepEqual(single, bulk);
  assert.equal(bulk.events.filter(event => event.day === 5).length, 1);
  ASSETS.forEach(asset => bulk.candles[asset.symbol].slice(60).forEach(quote => {
    const previous = bulk.candles[asset.symbol].find(candle => candle.day === quote.day - 1)!;
    assert.ok(quote.low <= quote.open && quote.open <= quote.high);
    assert.ok(quote.low <= quote.close && quote.close <= quote.high);
    assert.ok(quote.low >= cents(previous.close * .9) && quote.high <= cents(previous.close * 1.1));
  }));
});

test('compound and drawdown boundaries return finite educational calculations', () => {
  assert.deepEqual(drawdownSeries([0, 0]), [0, 0]);
  const dd = drawdownSeries([100, 80, 120, 60]);
  [0, -20, 0, -50].forEach((expected, index) => assert.ok(Math.abs(dd[index] - expected) < 1e-10));
  const zeroReturn = compound(1000, 100, 0, 1);
  assert.deepEqual(zeroReturn.at(-1), { year: 1, value: 2200, invested: 2200 });
  assert.ok(Math.abs(compound(1000, 0, 10, 1).at(-1)!.value - 1100) < 1e-8);
  assert.throws(() => compound(1000, 0, -101, 1), /参数无效/);
  assert.throws(() => compound(1000, 0, 5, 1, 100), /参数无效/);
  assert.throws(() => compound(1, 0, 10000, 1000), /超出数值范围/);
});


test('DAY is the default, expires before the next session and never fills retrospectively from the old low', () => {
  const source = fixture(), pending = placeOrder(source, { id: 'day-default-order', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', limit: 9.8 });
  // The day's low touched 9.8 before this snapshot. It must not be used to invent an earlier fill.
  assert.equal(pending.trades.length, 0);
  assert.equal(orderValidity(pending.orders[0]), 'DAY');
  assert.equal(account(pending).frozenCash, 985.01);
  const advanced = advanceSimulation(pending);
  assert.equal(advanced.orders[0].state, 'expired');
  assert.equal(advanced.trades.length, 0);
  assert.equal(advanced.cash, 100000);
  assert.equal(account(advanced).frozenCash, 0);
  assert.equal(advanced.orders[0].lastAttemptDay, 0);
  assertLedger(advanced);
});

function thinFixture() { const sim = fixture(); currentCandle(sim, SYMBOL).volume = 30000; return sim; }
test('large DAY limit consumes three levels, charges one minimum commission and freezes only 700 remaining shares', () => {
  const sim = placeOrder(thinFixture(), { id: 'thin-partial-day', symbol: SYMBOL, side: 'buy', qty: 1000, type: 'limit', limit: 10.1 });
  assert.deepEqual(sim.trades[0].executionLevels, [{ qty: 100, price: 10.01 }, { qty: 100, price: 10.02 }, { qty: 100, price: 10.03 }]);
  assert.equal(sim.trades[0].price, 10.02);
  assert.equal(sim.trades[0].commission, 5);
  assert.equal(sim.trades[0].fees, 5.03);
  assert.equal(sim.orders[0].filledQty, 300);
  assert.equal(sim.orders[0].remaining, 700);
  assert.equal(sim.orders[0].reserved, 7070.07);
  assert.equal(sim.cash, 96988.97);
  assert.equal(holdings(sim, SYMBOL).todayBought, 300);
  assert.equal(holdings(sim, SYMBOL).sellable, 0);
  assertLedger(sim);
  const cancelled = cancelOrder(sim, sim.orders[0].id);
  assert.equal(cancelled.cash, sim.cash);
  assert.equal(account(cancelled).frozenCash, 0);
  assert.equal(cancelled.orders[0].filledQty, 300);
  assertLedger(cancelled);
  const expired = advanceSimulation(sim);
  assert.equal(expired.orders[0].state, 'expired');
  assert.equal(expired.cash, sim.cash);
  assert.equal(holdings(expired, SYMBOL).sellable, 300);
  assertLedger(expired);
});

test('IOC remainder is cancelled, and repeated orders cannot refresh the same daily liquidity budget', () => {
  let sim = placeOrder(thinFixture(), market('shared-liquidity-one', 'buy', 200));
  assert.equal(sim.trades[0].qty, 200);
  assert.equal(orderBook(sim, SYMBOL).availableToBuy, 100);
  sim = placeOrder(sim, market('shared-liquidity-two', 'buy', 200));
  assert.equal(sim.trades[1].qty, 100);
  assert.equal(sim.orders[1].state, 'cancelled');
  assert.equal(sim.orders[1].remaining, 100);
  assert.equal(sim.orders[1].reserved, 0);
  const cash = sim.cash;
  sim = placeOrder(sim, market('shared-liquidity-three', 'buy', 100));
  assert.equal(sim.trades.length, 2);
  assert.equal(sim.cash, cash);
  assert.equal(sim.orders[2].filledQty, 0);
  assert.equal(orderBook(sim, SYMBOL).availableToBuy, 0);
  assert.equal(sim.sessionLiquidity![SYMBOL].buy, 300);
  assertLedger(sim);
});

test('a conditional partial buy completes on the next day with a new minimum fee and correct T+1 lots', () => {
  let sim = placeOrder(thinFixture(), { id: 'conditional-two-days', symbol: SYMBOL, side: 'buy', qty: 1000, type: 'limit', timeInForce: 'CONDITIONAL', limit: 10.1 });
  const firstCash = sim.cash;
  sim = advanceSimulation(sim);
  assert.equal(sim.orders[0].state, 'filled');
  assert.equal(sim.orders[0].remaining, 0);
  assert.equal(sim.trades.length, 2);
  assert.deepEqual(sim.trades.map(trade => [trade.day, trade.qty, trade.commission]), [[0, 300, 5], [1, 700, 5]]);
  assert.equal(account(sim).frozenCash, 0);
  assert.equal(holdings(sim, SYMBOL).sellable, 300);
  assert.equal(holdings(sim, SYMBOL).todayBought, 700);
  assert.ok(sim.cash < firstCash);
  assertLedger(sim);
});

test('partially filled sell freezes only remaining eligible shares and cancellation cannot restore sold inventory', () => {
  const source = thinFixture();
  source.lots[SYMBOL] = [{ qty: 1000, cost: 10000, price: 10, boughtDay: -1 }];
  const sim = placeOrder(source, { id: 'partial-sell-freeze', symbol: SYMBOL, side: 'sell', qty: 1000, type: 'limit', limit: 9.9 });
  assert.equal(sim.orders[0].filledQty, 300);
  assert.equal(sim.orders[0].remaining, 700);
  assert.equal(holdings(sim, SYMBOL).total, 700);
  assert.equal(holdings(sim, SYMBOL).frozen, 700);
  assert.equal(holdings(sim, SYMBOL).sellable, 0);
  assert.throws(() => placeOrder(sim, market('cannot-sell-frozen', 'sell', 100)), /可卖 0 股/);
  const cancelled = cancelOrder(sim, sim.orders[0].id);
  assert.equal(holdings(cancelled, SYMBOL).total, 700);
  assert.equal(holdings(cancelled, SYMBOL).sellable, 700);
  assert.equal(cancelled.cash, sim.cash);
});

test('new-day conditional priority uses price first and submission order for ties', () => {
  const make = () => { const sim = fixture(2000000); sim.scenario = 'illiquid'; sim.seed = 1; return sim; };
  const input = { symbol: SYMBOL, side: 'buy' as const, qty: 1000, type: 'limit' as const, timeInForce: 'CONDITIONAL' as const };
  let sim = placeOrder(make(), { ...input, id: 'priority-low-first', limit: 9.98 });
  sim = placeOrder(sim, { ...input, id: 'priority-high-second', limit: 9.99 });
  sim = advanceSimulation(sim);
  assert.equal(sim.orders[0].filledQty, 0);
  assert.equal(sim.orders[1].filledQty, 100);
  assert.equal(sim.trades[0].orderId, 'priority-high-second');
  assertLedger(sim);
  let tied = placeOrder(make(), { ...input, id: 'priority-tie-first', limit: 9.99 });
  tied = placeOrder(tied, { ...input, id: 'priority-tie-second', limit: 9.99 });
  tied = advanceSimulation(tied);
  assert.equal(tied.orders[0].filledQty, 100);
  assert.equal(tied.orders[1].filledQty, 0);
  assertLedger(tied);
});

test('old open conditional records migrate without resetting cash, lots, fills or their original reservations', () => {
  const legacy = fixture(); delete legacy.modelVersion; delete legacy.sessionLiquidity;
  legacy.orders = [{ id: 'legacy-open-limit', symbol: SYMBOL, side: 'buy', qty: 100, limit: 9.7, day: 0, reserved: 975.01, reason: '旧条件', state: 'open', kind: 'conditional', attempts: 1 }];
  const original = structuredClone(legacy);
  assert.equal(account(legacy).frozenCash, 975.01);
  assert.equal(orderValidity(legacy.orders[0]), 'CONDITIONAL');
  const migrated = advanceSimulation(legacy);
  assert.deepEqual(legacy, original);
  assert.equal(migrated.cash, legacy.cash);
  assert.deepEqual(migrated.lots, legacy.lots);
  assert.equal(migrated.orders[0].timeInForce, 'CONDITIONAL');
  assert.equal(migrated.orders[0].remaining, 100);
  assert.equal(migrated.orders[0].reserved, 975.01);
  assert.equal(migrated.orders[0].state, 'open');
  assert.equal(migrated.modelVersion, 2);
  assertLedger(migrated);
});

test('legacy seven-field payload fingerprints remain retryable but new DAY/conditional conflicts are rejected', () => {
  const input = market('fingerprint-legacy', 'buy', 100), first = placeOrder(fixture(), input);
  first.processedInputs![input.id] = JSON.stringify([input.symbol, input.side, input.qty, input.type, null, '', '平静']);
  assert.deepEqual(placeOrder(first, input), first);
  const limit: OrderInput = { id: 'fingerprint-validity', symbol: SYMBOL, side: 'buy', qty: 100, type: 'limit', limit: 9.8, timeInForce: 'DAY' };
  const day = placeOrder(fixture(), limit);
  assert.throws(() => placeOrder(day, { ...limit, timeInForce: 'CONDITIONAL' }), /同一订单标识/);
});

test('suspended stocks have no trades or fees; conditions survive and DAY instructions expire', () => {
  let sim = advanceSimulation(createSimulation(100000, 42, 'suspension'), 5);
  const symbol = 'QH1003', quote = currentCandle(sim, symbol);
  assert.equal(quote.suspended, true);
  assert.equal(quote.volume, 0);
  assert.equal(orderBook(sim, symbol).availableToBuy, 0);
  const stopped = placeOrder(sim, { id: 'suspended-ioc', symbol, side: 'buy', qty: 100, type: 'market' });
  assert.equal(stopped.cash, sim.cash);
  assert.equal(stopped.trades.length, 0);
  assert.equal(stopped.orders[0].state, 'cancelled');
  sim = placeOrder(sim, { id: 'suspended-day', symbol, side: 'buy', qty: 100, type: 'limit', limit: quote.close });
  sim = placeOrder(sim, { id: 'suspended-condition', symbol, side: 'buy', qty: 100, type: 'limit', timeInForce: 'CONDITIONAL', limit: quote.close });
  sim = advanceSimulation(sim);
  assert.equal(sim.orders[0].state, 'expired');
  assert.equal(sim.orders[1].state, 'open');
  assert.equal(sim.trades.length, 0);
  sim = advanceSimulation(sim);
  assert.equal(currentCandle(sim, symbol).suspended, undefined);
  assert.equal(sim.events.at(-1)!.title, '虚构事件：青禾医疗复牌');
  assertLedger(sim);
});

test('gap and low-liquidity scenes preserve bounded OHLC and expose their intended event and depth differences', () => {
  const normal = advanceSimulation(createSimulation(100000, 2), 5);
  const thin = advanceSimulation(createSimulation(100000, 2, 'illiquid'), 5);
  assert.ok(orderBook(thin, SYMBOL).dayCapacity < orderBook(normal, SYMBOL).dayCapacity / 10);
  const gap = advanceSimulation(createSimulation(100000, 2, 'gap'), 5);
  const day4 = gap.candles[SYMBOL].find(quote => quote.day === 4)!, day3 = gap.candles[SYMBOL].find(quote => quote.day === 3)!;
  assert.ok(Math.abs(day4.open / day3.close - 1) > .04);
  assert.ok(gap.events.some(event => event.day === 4 && event.title.includes('跳空')));
  assert.match(SIMULATOR_ASSUMPTIONS.scope, /价格笼子/);
  assert.match(SIMULATOR_ASSUMPTIONS.scope, /腾讯真实行情.*不参与/);
});


test('new-day minimum commission cannot create negative available cash when a partial condition has no free cash', () => {
  const source = thinFixture(); source.initialCash = source.cash = 10015.1;
  const queued = placeOrder(source, { id: 'fee-rebase-zero-cash', symbol: SYMBOL, side: 'buy', qty: 1000, type: 'limit', timeInForce: 'CONDITIONAL', limit: 10.01 });
  assert.equal(queued.orders[0].filledQty, 100);
  assert.equal(queued.cash, 9009.09);
  assert.equal(account(queued).availableCash, 0);
  const advanced = advanceSimulation(queued);
  assert.equal(advanced.trades.length, 1);
  assert.equal(advanced.cash, 9009.09);
  assert.equal(advanced.orders[0].state, 'open');
  assert.equal(advanced.orders[0].remaining, 900);
  assert.equal(advanced.orders[0].reserved, 9009.09);
  assert.equal(advanced.orders[0].lastAttemptDay, 1);
  assert.match(advanced.orders[0].error!, /佣金所需现金不足/);
  assert.equal(account(advanced).availableCash, 0);
  assertLedger(advanced);
  const cancelled = cancelOrder(advanced, advanced.orders[0].id);
  assert.equal(account(cancelled).availableCash, 9009.09);
  assertLedger(cancelled);
});

test('several conditions can rebase or skip new-day fees without spending cash frozen for another order', () => {
  const source = thinFixture(); source.initialCash = source.cash = 20030.2;
  const order = { symbol: SYMBOL, side: 'buy' as const, qty: 1000, type: 'limit' as const, timeInForce: 'CONDITIONAL' as const, limit: 10.01 };
  let sim = placeOrder(source, { ...order, id: 'fee-rebase-first' });
  sim = placeOrder(sim, { ...order, id: 'fee-rebase-second' });
  assert.equal(account(sim).availableCash, 0);
  sim = advanceSimulation(sim);
  assert.equal(sim.orders[0].state, 'open');
  assert.equal(sim.orders[0].remaining, 900);
  assert.equal(sim.orders[1].state, 'filled');
  assert.equal(sim.orders[1].remaining, 0);
  assert.equal(sim.cash, 9009.09);
  assert.equal(account(sim).frozenCash, 9009.09);
  assert.equal(account(sim).availableCash, 0);
  assertLedger(sim);
});

test('selling yesterday holdings does not regenerate the already used buy-side daily budget', () => {
  const source = thinFixture();
  source.lots[SYMBOL] = [{ qty: 300, price: 10, cost: 3000, boughtDay: -1 }];
  let sim = placeOrder(source, market('exhaust-buy-budget', 'buy', 300));
  sim = placeOrder(sim, market('exhaust-sell-old', 'sell', 300));
  assert.equal(sim.sessionLiquidity![SYMBOL].buy, 300);
  assert.equal(sim.sessionLiquidity![SYMBOL].sell, 300);
  const cash = sim.cash;
  sim = placeOrder(sim, market('cannot-refresh-budget', 'buy', 100));
  assert.equal(sim.cash, cash);
  assert.equal(sim.trades.length, 2);
  assert.equal(sim.orders.at(-1)!.filledQty, 0);
});

test('partial executions can contain odd quantities even though odd-lot submission cannot be split', () => {
  const source = thinFixture(); source.lots[SYMBOL] = [{ qty: 230, cost: 2300, price: 10, boughtDay: -1 }];
  let sim = placeOrder(source, market('odd-partial-one', 'sell', 30));
  assert.equal(sim.trades[0].qty, 30);
  // A new 100-share order can meet 70 shares left at bid one. The submission rule does not ban a 70-share fill.
  sim = placeOrder(sim, { id: 'odd-partial-level', symbol: SYMBOL, side: 'sell', qty: 100, type: 'limit', limit: 9.99 });
  assert.equal(sim.orders[1].filledQty, 70);
  assert.equal(sim.orders[1].remaining, 30);
  assert.equal(holdings(sim, SYMBOL).total, 130);
  assert.equal(holdings(sim, SYMBOL).frozen, 30);
});


test('legacy same-day fills consume depth before read-only previews and before any migration action', () => {
  const bought = placeOrder(thinFixture(), market('legacy-liquidity-first', 'buy', 300));
  const legacy = structuredClone(bought); delete legacy.modelVersion; delete legacy.sessionLiquidity;
  // Original engine market fills did not create order records or execution-depth fields.
  legacy.orders = []; delete legacy.trades[0].orderId; delete legacy.trades[0].executionLevels;
  const snapshot = structuredClone(legacy);
  assert.equal(orderBook(legacy, SYMBOL).availableToBuy, 0);
  assert.equal(orderPreview(legacy, market('legacy-liquidity-preview', 'buy', 300)).estimatedFilledQty, 0);
  assert.deepEqual(legacy, snapshot);
  const next = placeOrder(legacy, market('legacy-liquidity-second', 'buy', 300));
  assert.equal(next.trades.length, 1);
  assert.equal(next.cash, legacy.cash);
  assert.deepEqual(next.lots, legacy.lots);
  assert.equal(next.orders[0].filledQty, 0);
  assert.equal(next.orders[0].remaining, 300);
  assert.equal(next.sessionLiquidity![SYMBOL].buy, 300);
  assert.equal(next.sessionLiquidity![SYMBOL].sell, 0);
  assertLedger(next);
});

test('legacy consumption beyond the new shared cap is preserved, and only a new session replenishes it', () => {
  const initial = fixture();
  const legacy = placeOrder(initial, market('legacy-large-record', 'buy', 500));
  delete legacy.modelVersion; delete legacy.sessionLiquidity;
  currentCandle(legacy, SYMBOL).volume = 30000;
  assert.equal(orderBook(legacy, SYMBOL).dayCapacity, 300);
  assert.equal(orderBook(legacy, SYMBOL).availableToBuy, 0);
  const blocked = placeOrder(legacy, market('legacy-beyond-cap', 'buy', 100));
  assert.equal(blocked.trades.length, 1);
  assert.equal(blocked.trades[0].qty, 500);
  assert.equal(blocked.sessionLiquidity![SYMBOL].buy, 500);
  assert.equal(blocked.cash, legacy.cash);
  const newDay = advanceSimulation(blocked);
  assert.ok(orderBook(newDay, SYMBOL).availableToBuy > 0);
  assert.equal(newDay.trades.length, 1);
  assertLedger(newDay);
});

test('legacy sell-side depth is reconstructed independently and cannot be refreshed by a missing index', () => {
  const source = thinFixture(); source.lots[SYMBOL] = [{ qty: 600, cost: 6000, price: 10, boughtDay: -1 }];
  const legacy = placeOrder(source, market('legacy-sell-first', 'sell', 300));
  delete legacy.modelVersion; delete legacy.sessionLiquidity;
  assert.equal(orderBook(legacy, SYMBOL).availableToSell, 0);
  assert.equal(orderBook(legacy, SYMBOL).availableToBuy, 300);
  const blocked = placeOrder(legacy, market('legacy-sell-second', 'sell', 300));
  assert.equal(blocked.trades.length, 1);
  assert.equal(blocked.cash, legacy.cash);
  assert.deepEqual(blocked.lots, legacy.lots);
  assert.equal(blocked.sessionLiquidity![SYMBOL].sell, 300);
});
