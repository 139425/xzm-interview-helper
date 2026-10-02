import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { experimentResult } from '../lib/experiments.ts';

// Put this file in tests/experiments.test.ts. Expected values below come from
// independently stated financial identities or closed forms, not copied code.
type Control = { key: string; min: number; max: number; default: number };
type Course = { lessons: { id: string; experiment: { kind: string; controls: Control[] } }[] };
const course = JSON.parse(readFileSync(new URL('../lib/curriculum.json', import.meta.url), 'utf8')) as Course;
const defaults = (id: string): Record<string, number> => {
  const lesson = course.lessons.find(lesson => lesson.id === id);
  assert.ok(lesson, `Missing lesson ${id}`);
  return Object.fromEntries(lesson.experiment.controls.map(control => [control.key, control.default]));
};
const run = (id: string, override: Record<string, number> = {}) => experimentResult(id, { ...defaults(id), ...override });
const near = (actual: number, expected: number, label: string, tolerance = 1e-8) => {
  assert.ok(Number.isFinite(actual), `${label}: got non-finite ${actual}`);
  assert.ok(Math.abs(actual - expected) <= tolerance * Math.max(1, Math.abs(expected)), `${label}: ${actual} != ${expected}`);
};

test('equity: 1% ownership, 300 cash dividend, 70,000 retained company profit', () => {
  const r = run('equity');
  near(r.metrics[0].value, 1, 'ownership %');
  near(r.metrics[1].value, 300, 'shareholder dividend');
  near(r.metrics[2].value, 70000, 'company retained profit');
});

test('orderbook: 200 shares filled at 10.015; no fill has no average execution price', () => {
  const r = run('orderbook');
  near(r.metrics[0].value, 200, 'filled shares');
  near(r.metrics[1].value, 10.015, 'execution average');
  near(r.metrics[2].value, 100, 'unfilled shares');
  const noFill = run('orderbook', { limit: 9.98 });
  near(noFill.metrics[0].value, 0, 'no-fill quantity');
  assert.ok(Number.isNaN(noFill.metrics[1].value), 'No fill must display N/A, not a zero execution price');
  near(noFill.metrics[2].value, 300, 'no-fill unfilled quantity');
});

test('candle: red candle can still lose 5% versus previous close', () => {
  const r = run('candle');
  near(r.metrics[0].value, -5, 'day return %');
  near(r.metrics[1].value, 0.5, 'body height');
  near(r.metrics[2].value, 15, 'amplitude %');
});

test('statements: snapshot equity 600; simplified profit 300 and margin 30%', () => {
  const r = run('statements');
  near(r.metrics[0].value, 300, 'profit in ten-thousand CNY');
  near(r.metrics[1].value, 30, 'margin %');
  near(r.metrics[2].value, 600, 'equity in ten-thousand CNY');
  assert.equal(r.metrics[0].unit, '万元');
  assert.equal(r.metrics[2].unit, '万元');
});

test('cashflow: profit 500, operating cash 200, net cash change 100', () => {
  const r = run('cashflow');
  near(r.metrics[0].value, 500, 'profit');
  near(r.metrics[1].value, 200, 'operating cashflow');
  near(r.metrics[2].value, 100, 'net cash change');
  r.metrics.forEach(metric => assert.equal(metric.unit, '万元'));
});

test('cashflow: a 400 loan changes cash, not profit or operating cashflow', () => {
  const r = run('cashflow', { loan: 400 });
  near(r.metrics[0].value, 500, 'profit after loan');
  near(r.metrics[1].value, 200, 'operating cashflow after loan');
  near(r.metrics[2].value, 500, 'net cash after loan');
});

test('valuation: earnings growth with PE compression yields 28.8, losses disable PE', () => {
  const r = run('valuation');
  near(r.metrics[0].value, 15, 'current PE');
  near(r.metrics[1].value, 2.4, 'next hypothetical EPS');
  near(r.metrics[2].value, 28.8, 'next hypothetical price');
  for (const eps of [0, -1]) {
    const loss = run('valuation', { eps });
    assert.ok(Number.isNaN(loss.metrics[0].value), 'Non-positive EPS cannot have normal PE');
    assert.ok(Number.isNaN(loss.metrics[2].value), 'Non-positive EPS cannot produce this PE-based price');
  }
});

test('dividend: combined stock and cash value remains 1,000 across all valid dividend/bonus steps', () => {
  for (let d = 0; d <= 20; d++) for (let b = 0; b <= 10; b++) {
    const r = run('dividend', { dividend: d / 10, bonus: b / 10 });
    near(r.metrics[2].value, 1000, `wealth conservation d=${d / 10}, b=${b / 10}`);
  }
});

test('compound: end-year contributions match an independent annuity closed form', () => {
  const r = run('compound');
  const future = 10000 * 1.06 ** 10 + 2000 * (1.06 ** 10 - 1) / 0.06;
  near(future, 44270.066850190335, 'independent oracle');
  near(r.metrics[0].value, future, 'future wealth');
  near(r.metrics[1].value, 30000, 'total deposited capital');
  near(r.metrics[2].value, future - 30000, 'profit contribution');
});

test('compound: zero return equals capital; final-year deposit earns no prior-year return', () => {
  near(run('compound', { rate: 0, years: 30 }).metrics[0].value, 70000, 'zero-return wealth');
  near(run('compound', { rate: 20, years: 1 }).metrics[0].value, 14000, 'end-year contribution timing');
  near(run('compound', { rate: -20, years: 1, contribution: 0 }).metrics[0].value, 8000, 'negative-return wealth');
});

test('drawdown: +100% repairs a 50% loss; final point can set the maximum drawdown', () => {
  const half = run('drawdown', { loss: 50 });
  near(half.metrics[0].value, 50, '50% max drawdown');
  near(half.metrics[2].value, 100, '50% loss recovery');
  const endingLower = run('drawdown', { loss: 0, ending: -20 });
  near(endingLower.metrics[0].value, 100 / 3, 'ending point max drawdown');
  near(endingLower.metrics[2].value, 50, 'ending point recovery');
  const lowerStart = run('drawdown', { loss: 10, ending: 10 });
  near(lowerStart.metrics[0].value, 10, 'drawdown does not use global minimum before the peak');
  near(lowerStart.metrics[2].value, 100 / 9, '108 to previous peak 120');
});

test('position: 30% stock weight under -30% stock return yields -9% account return', () => {
  const r = run('position');
  near(r.metrics[0].value, -9, 'account return %');
  near(r.metrics[1].value, 91000, 'account wealth');
  near(r.metrics[2].value, 21000 / 91000 * 100, 'post-shock stock weight %');
});

test('diversification: equal volatility and half weights match correlation limiting cases', () => {
  near(run('diversification', { rho: 0 }).metrics[0].value, Math.sqrt(200), 'independent assets');
  near(run('diversification', { rho: 1 }).metrics[0].value, 20, 'perfect positive correlation');
  near(run('diversification', { rho: -1 }).metrics[0].value, 0, 'perfect negative correlation');
  near(run('diversification', { weight: 0, sigmaB: 35 }).metrics[0].value, 35, 'all B');
  near(run('diversification', { weight: 100, sigmaA: 35 }).metrics[0].value, 35, 'all A');
});

test('news: growth fact, surprise in percentage points, and explicitly hypothetical price return', () => {
  const r = run('news');
  near(r.metrics[0].value, 20, 'actual growth %');
  near(r.metrics[1].value, -10, 'surprise percentage points');
  near(r.metrics[2].value, -3, 'hypothetical price return %');
  assert.equal(r.metrics[1].unit, '百分点');
});

test('costs: default net 80.7300102 includes 0.2009998 transfer fees', () => {
  const r = run('costs');
  near(r.metrics[0].value, 100, 'quoted gross spread');
  near(r.metrics[1].value, 15.2499898, 'commissions and taxes including transfer');
  near(r.metrics[2].value, 80.7300102, 'unrounded net profit');
  const transfer = r.bars?.find(bar => bar.label === '双边过户费');
  assert.ok(transfer);
  near(transfer.value, -0.2009998, 'signed transfer fee');
  // Setting commissions, taxes, and slippage to zero still leaves the fixed transfer fee.
  near(run('costs', { commission: 0, minimum: 0, sellTax: 0, slippage: 0 }).metrics[2].value, 99.799, 'fixed transfer fee remains');
});

test('ETF: 5% premium; fee drag matches a geometric closed form; zero fee removes drag', () => {
  const r = run('etf');
  near(r.metrics[0].value, 5, 'premium %');
  const expectedNet = 10000 * 1.04475 ** 10;
  near(expectedNet, 15492.581809892878, 'independent net oracle');
  near(r.metrics[2].value, expectedNet, 'net balance');
  near(r.metrics[1].value, 10000 * 1.05 ** 10 - expectedNet, 'fee drag');
  near(run('etf', { fee: 0 }).metrics[1].value, 0, 'zero-fee drag');
});

test('review: zero-day returns are zero; day-20 excess is an absolute percentage-point difference', () => {
  const initial = run('review');
  near(initial.metrics[0].value, 0, 'initial return');
  near(initial.metrics[1].value, 0, 'initial excess');
  const day = run('review', { reviewDay: 20 });
  // Independently frozen path point: the educational path's day 20 is 89.4000391737972.
  near(day.metrics[0].value, -10.599960826202803, 'day-20 portfolio return %');
  near(day.metrics[1].value, -12.199960826202798, 'day-20 excess percentage points');
  assert.equal(day.metrics[1].unit, '百分点');
});

test('all 21 lessons route to 15 models and parameter corners produce valid metrics/curves', () => {
  assert.equal(course.lessons.length, 21);
  assert.equal(new Set(course.lessons.map(lesson => lesson.id)).size, 21);
  assert.equal(new Set(course.lessons.map(lesson => lesson.experiment.kind)).size, 15);
  let corners = 0;
  for (const lesson of course.lessons) {
    const controls = lesson.experiment.controls;
    for (let mask = 0; mask < 2 ** controls.length; mask++) {
      const values = Object.fromEntries(controls.map((control, i) => [control.key, mask & (1 << i) ? control.max : control.min]));
      const r = experimentResult(lesson.experiment.kind, values);
      corners++;
      r.metrics.forEach((metric, index) => {
        const expectedNA = (lesson.experiment.kind === 'valuation' && values.eps <= 0 && (index === 0 || index === 2))
          || (lesson.experiment.kind === 'orderbook' && index === 1 && r.metrics[0].value === 0);
        if (expectedNA) assert.ok(Number.isNaN(metric.value), `${lesson.id}: expected N/A for ${metric.label}`);
        else assert.ok(Number.isFinite(metric.value), `${lesson.id}: non-finite ${metric.label}`);
      });
      r.series?.forEach(series => {
        assert.ok(series.values.every(Number.isFinite), `${lesson.id}: non-finite chart values`);
        if (r.labels) assert.equal(series.values.length, r.labels.length, `${lesson.id}: series/labels length mismatch`);
      });
    }
  }
  assert.ok(corners >= 296, 'The original formula coverage must not shrink');
});

test('financial invariants: chronological drawdown, stock/cash wealth, volatility bounds and ETF fee identity', () => {
  // Max drawdown oracle checks every valid chronological peak-to-later-point pair,
  // rather than reproducing the implementation's running-maximum accumulator.
  for (let loss = 0; loss <= 80; loss++) for (let ending = -20; ending <= 40; ending++) {
    const path = [100, 120, 120 * (1 - loss / 100), 100 * (1 + ending / 100)];
    let expected = 0;
    for (let start = 0; start < path.length; start++) for (let end = start; end < path.length; end++) {
      expected = Math.max(expected, (path[start] - path[end]) / path[start]);
    }
    const r = run('drawdown', { loss, ending });
    near(r.metrics[0].value, expected * 100, 'chronological max drawdown');
    near(r.metrics[2].value, expected / (1 - expected) * 100, 'recovery from max drawdown');
  }
  for (let weight = 0; weight <= 100; weight += 5) for (let shock = -80; shock <= 80; shock += 5) {
    const r = run('position', { weight, shock, wealth: 100000 });
    const stocks = 100000 * weight / 100 * (1 + shock / 100);
    const cash = 100000 * (1 - weight / 100);
    near(r.metrics[1].value, stocks + cash, 'stock + cash identity');
    near(r.metrics[2].value, stocks / (stocks + cash) * 100, 'post-shock weight identity');
  }
  for (let weight = 0; weight <= 100; weight += 5) for (let rho = -10; rho <= 10; rho++) {
    const r = run('diversification', { weight, rho: rho / 10, sigmaA: 20, sigmaB: 35 });
    const w = weight / 100;
    assert.ok(r.metrics[0].value <= w * 20 + (1 - w) * 35 + 1e-9, 'volatility upper bound');
    assert.ok(r.metrics[0].value >= Math.abs(w * 20 - (1 - w) * 35) - 1e-9, 'volatility lower bound');
  }
  for (let eps = -20; eps <= 50; eps++) {
    const r = run('valuation', { eps: eps / 10 });
    assert.equal(Number.isFinite(r.metrics[0].value), eps > 0, 'PE applicability');
    assert.equal(Number.isFinite(r.metrics[2].value), eps > 0, 'PE-based price applicability');
  }
  for (let years = 1; years <= 20; years++) for (let fee = 0; fee <= 20; fee++) {
    const r = run('etf', { years, fee: fee / 10 });
    assert.ok(r.metrics[1].value >= -1e-8, 'Nonnegative fee cannot increase net balance');
    near(r.metrics[2].value, 10000 * (1.05 * (1 - fee / 1000)) ** years, 'geometric fee identity');
  }
});
