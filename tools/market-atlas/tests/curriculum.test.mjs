import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const curriculum = JSON.parse(readFileSync(new URL('../lib/curriculum.json', import.meta.url), 'utf8'));
const lessons = new Map(curriculum.lessons.map(lesson => [lesson.id, lesson]));
const originalContracts = {
  equity: '72d205bf89990791c795873294c65389d3883f236023fcd095cf0fa8f6f95348',
  orderbook: '6a19ac96edfdf5160b11237c428fd89d62cc84f978a03b52895a2792eb7b4247',
  candle: '5861307cb38e62c2892417d7483806ce00de908768d85e806883dfa22eaa8e54',
  statements: '04b78b6a971cea0a611fa0eec5fa0443f865de61519e66ecf5dbbd9fc3e2e4cf',
  cashflow: 'c40b362263fd2481c47cf408c77f42af209e9f5bf0e6fc5242dcb192ae9e8de8',
  valuation: '328b2d73bfcb5a211f11d818074586345cf0c11f35ecd52965595d5477d76028',
  dividend: '95fc02aa490002d3e5276baa1ce960fae6104f1d45ab38b84dc1650a9b1f60e2',
  compound: 'ab7b9e3a5f9a99ca6ab643b49a3955b0f4d32679420cd0491c157b33c002f9eb',
  drawdown: 'd83ac6f1972145ea0774b0a71dda5a34190843264f1626790fd691c1bfba9573',
  position: '97224a38438d34f26b33e8c2b983be1f68f3f46022a8c34cdb106526c6a31672',
  diversification: '8f3d8a972c62550f97b229d65d222043a0b09f6c43c21d7a64f30654df6fbd2b',
  news: 'ffddc4d7c40e89ea65d7a2a6e2dbae923bdb01fec8ae490e41b59c72879e7f11',
  costs: '2c1c96494aeff418daa2529555224b23b689f26a0418586342da9445f346dfa9',
  etf: 'b08d969c0ba75cce37f556d13521a7d47b412cb81d4785d6cb403119f9da3c93',
  review: 'b5e13a69907a816ea86a22bfd53d4e75d5c0dd68b85100685294d9d9b376fb11',
};
const beginners = ['beginner-money', 'beginner-accounts', 'beginner-opening', 'beginner-first-order', 'beginner-markets', 'beginner-protection'];

test('zero-basics path precedes the original path and every lesson appears in exactly one stage', () => {
  assert.equal(curriculum.schemaVersion, 2);
  assert.equal(lessons.size, curriculum.lessons.length);
  assert.deepEqual(curriculum.lessons.slice(0, 6).map(lesson => lesson.id), beginners);
  assert.deepEqual(curriculum.stages[0], { title: '新手起步', ids: beginners });
  const stageIds = curriculum.stages.flatMap(stage => stage.ids);
  assert.equal(new Set(stageIds).size, stageIds.length);
  assert.deepEqual(stageIds, curriculum.lessons.map(lesson => lesson.id));
  for (const stage of curriculum.stages) {
    for (const id of stage.ids) assert.equal(lessons.get(id)?.stage, stage.title);
  }
});

test('original experiments and the first two stored-quiz identities remain compatible', () => {
  for (const [id, expected] of Object.entries(originalContracts)) {
    const lesson = lessons.get(id);
    assert.ok(lesson, id);
    const contract = {
      kind: lesson.experiment.kind,
      controls: lesson.experiment.controls,
      formula: lesson.experiment.formula,
      quizzes: lesson.quizzes.slice(0, 2).map(({ question, options, answerIndex }) => ({ question, options, answerIndex })),
    };
    assert.equal(createHash('sha256').update(JSON.stringify(contract)).digest('hex'), expected, id);
  }
  const knownKinds = new Set(Object.keys(originalContracts));
  for (const id of beginners) assert.ok(knownKinds.has(lessons.get(id).experiment.kind), id);
});

test('reader content has complete sections, examples, actionable review and step-by-step diagrams', () => {
  for (const lesson of curriculum.lessons) {
    const guide = lesson.guide;
    assert.ok(lesson.minutes >= 15, lesson.id);
    assert.ok(guide.sections.length >= 6 && guide.sections.length <= 8, lesson.id);
    for (const section of guide.sections) {
      assert.ok(section.title.length > 0, lesson.id);
      assert.ok(section.paragraphs.length >= 3, `${lesson.id}/${section.title}`);
      assert.ok(section.paragraphs.join('').length >= 250, `${lesson.id}/${section.title}`);
      assert.ok(section.paragraphs.every(paragraph => paragraph.length >= 20 && /[。！？]/u.test(paragraph)), `${lesson.id}/${section.title}`);
    }
    assert.ok(guide.workedExamples.length >= 2, lesson.id);
    for (const example of guide.workedExamples) {
      assert.ok(example.steps.length >= 3, `${lesson.id}/${example.title}`);
      assert.ok(example.conclusion.length > 10, lesson.id);
    }
    assert.ok(guide.misconceptions.length >= 2 && guide.practiceTasks.length >= 3, lesson.id);
    assert.ok(guide.glossary.length >= 4, lesson.id);
    assert.equal(guide.diagram.details.length, guide.diagram.labels.length, lesson.id);
    for (const detail of guide.diagram.details) assert.ok((detail.match(/[。！？]/gu) ?? []).length >= 2, `${lesson.id}/${detail}`);
  }
});

test('every question has a unique correct index and a distinct explanation for each option', () => {
  for (const lesson of curriculum.lessons) {
    assert.ok(lesson.quizzes.length >= 6, lesson.id);
    assert.equal(new Set(lesson.quizzes.map(quiz => quiz.question)).size, lesson.quizzes.length, lesson.id);
    for (const quiz of lesson.quizzes) {
      assert.ok(Number.isInteger(quiz.answerIndex) && quiz.answerIndex >= 0 && quiz.answerIndex < quiz.options.length, quiz.question);
      assert.equal(quiz.optionExplanations.length, quiz.options.length, quiz.question);
      assert.equal(new Set(quiz.optionExplanations).size, quiz.options.length, quiz.question);
      assert.ok(quiz.optionExplanations.every(explanation => explanation.length >= 10), quiz.question);
      assert.ok(['入门', '应用', '判断'].includes(quiz.difficulty), quiz.question);
    }
  }
});

test('rule sources are primary HTTPS pages, dated, scoped and mapped to visible links', () => {
  const officialDomains = ['csrc.gov.cn', 'sac.net.cn', 'sse.com.cn', 'szse.cn', 'bse.cn', 'chinaclear.cn', 'mof.gov.cn', 'chinatax.gov.cn', 'spp.gov.cn', 'sec.gov', 'investor.gov', 'finra.org'];
  for (const lesson of curriculum.lessons) {
    assert.ok(lesson.guide.sourceNotes.length >= 2, lesson.id);
    const displayed = new Set(lesson.sources.map(source => source.url));
    for (const source of lesson.guide.sourceNotes) {
      const url = new URL(source.url);
      assert.equal(url.protocol, 'https:', lesson.id);
      assert.ok(officialDomains.some(domain => url.hostname === domain || url.hostname.endsWith(`.${domain}`)), source.url);
      assert.ok(url.pathname.length > 1, source.url);
      assert.equal(source.asOf, curriculum.researchedAt, source.url);
      assert.ok(source.jurisdiction.length > 4 && source.supports.length > 8, source.url);
      assert.ok(displayed.has(source.url), source.url);
    }
  }
});

function answerFor(id, questionFragment) {
  const quiz = lessons.get(id).quizzes.find(quiz => quiz.question.includes(questionFragment));
  assert.ok(quiz, `${id}: ${questionFragment}`);
  return quiz.options[quiz.answerIndex];
}

test('independent calculations validate the money, market, ownership and accounting answers', () => {
  assert.equal(answerFor('beginner-money', '余额 50,000'), `${(50000 - 12000 - 18000).toLocaleString('en-US')} 元`);
  assert.equal(answerFor('beginner-money', '8,000 元股票'), `${(8000 * 0.25).toLocaleString('en-US')} 元`);
  assert.equal(answerFor('beginner-first-order', '静态卖一'), '100 股');
  assert.equal(answerFor('beginner-markets', '前 19'), `${((19 * 80000 + 200000) / 20).toLocaleString('en-US')} 元`);
  assert.equal(answerFor('equity', '10,000 股中持有 200'), `${120000 * 0.25 * (200 / 10000)} 元`);
  assert.equal(answerFor('orderbook', '均价是多少'), `${(100 * 10.01 + 100 * 10.02) / 200} 元`);
  assert.equal(answerFor('candle', '日内高低跨度'), `${12 - 10} 元`);
  assert.equal(answerFor('statements', '简化净利润率'), `${((1000 - 900) / 1000) * 100}%`);
  assert.equal(answerFor('cashflow', '设备支出 400').replace('−', '-'), `${200 - 400 + 100} 万`);
  assert.equal(answerFor('valuation', 'EPS 从 1'), `${1.2 * 15} 元`);
});

test('independent calculations validate compound, recovery, concentration, fee and ETF answers', () => {
  const compound = (10000 * 1.1 + 1000) * 1.1 + 1000;
  assert.equal(answerFor('compound', '两年末资产'), `${compound.toLocaleString('en-US')} 元`);
  assert.equal(answerFor('drawdown', '损失 50%'), `${((1 - 0.5) / 0.5) * 100}%`);
  assert.equal(answerFor('position', '股票占 20%'), `${(100000 * 0.2 * 0.25).toLocaleString('en-US')} 元`);
  assert.equal(answerFor('diversification', '相关系数 0'), `${(Math.sqrt(2 * 0.5 ** 2 * 0.2 ** 2) * 100).toFixed(2)}%`);
  assert.equal(answerFor('news', '实际增幅 20%'), '−20 个百分点');
  assert.equal(answerFor('costs', '卖出成交额 10,100'), `${10100 * 0.0005} 元`);
  assert.equal(answerFor('costs', '过户费率 0.001%'), `${20100 * 0.00001} 元`);
  assert.equal(answerFor('costs', '毛利 100 元'), `${(100 - 10 - 5.05 - 0.201).toFixed(3)} 元`);
  assert.equal(answerFor('etf', '第一年余额'), `${(10000 * 1.05 * 0.99).toLocaleString('en-US')} 元`);
  assert.equal(answerFor('review', '期末 108,000'), '8%');
  assert.equal(answerFor('dividend', '最终股息税负'), `${50 * 0.5 * 0.2} 元`);
});

test('important boundaries remain explicit rather than silently becoming nationwide guarantees', () => {
  const text = id => JSON.stringify(lessons.get(id).guide);
  assert.match(text('beginner-opening'), /70 周岁及以上.*优先/u);
  assert.match(text('beginner-opening'), /全国通用.*C3/u);
  assert.match(text('beginner-markets'), /已有科创板.*免于/u);
  assert.match(text('beginner-markets'), /2026.*10%/u);
  assert.match(text('beginner-first-order'), /可用.*可取/u);
  assert.match(text('dividend'), /派发.*暂不扣/u);
  assert.match(text('equity'), /交易类强制退市.*不进入/u);
  assert.match(text('etf'), /不把所有 ETF.*T\+0/u);
  assert.match(text('beginner-protection'), /12386.*不保证退款/u);
});
