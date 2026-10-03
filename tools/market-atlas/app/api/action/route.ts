import { identity, readState, saveState, jsonResponse, verifyOrigin, type WorkspaceState } from '@/lib/storage';
import { advanceSimulation, cancelOrder, createSimulation, placeOrder, ensure, type OrderInput, SCENARIOS } from '@/lib/simulator';
import { LESSONS } from '@/lib/data';

export async function POST(request: Request) {
  try {
    verifyOrigin(request);
    if (!request.headers.get('Content-Type')?.startsWith('application/json')) return jsonResponse({ error: '需要 JSON 数据' }, 415);
    const text = await request.text(); ensure(text.length < 2500000, '请求数据过大');
    const body = JSON.parse(text), user = identity(request), current = await readState(user.userId);
    ensure(Number.isInteger(body.version), '记录版本不正确');
    // A lost HTTP response can be retried with the original version and ID.
    // The engine verifies the payload fingerprint before returning the current record.
    if (body.action === 'trade' && current.state.simulator.processed.includes(body.payload?.id)) {
      placeOrder(current.state.simulator, body.payload as OrderInput);
      return jsonResponse(current, 200, user.cookie);
    }
    if (body.version !== current.version) return jsonResponse({ error: '记录已在另一窗口更新，请刷新后重新确认操作', conflict: true }, 409, user.cookie);
    const state: WorkspaceState = structuredClone(current.state), payload = body.payload || {};
    switch (body.action) {
      case 'trade': state.simulator = placeOrder(state.simulator, payload as OrderInput); break;
      case 'advance': state.simulator = advanceSimulation(state.simulator, payload.days); break;
      case 'cancel-order': state.simulator = cancelOrder(state.simulator, payload.id); break;
      case 'reset-simulation': {
        ensure(Number.isInteger(payload.capital) && payload.capital >= 10000 && payload.capital <= 1000000, '初始资金应在 1万–100万元之间');
        ensure(Object.hasOwn(SCENARIOS, payload.scenario), '请选择有效的市场场景');
        ensure(Number.isInteger(payload.seed) && payload.seed >= 0 && payload.seed < 2147483647, '场景种子不正确');
        state.simulator = createSimulation(payload.capital, payload.seed, payload.scenario); break;
      }
      case 'simulation-config': {
        ensure(Number.isFinite(payload.commissionRate) && payload.commissionRate >= 0 && payload.commissionRate <= .003, '佣金率需要在 0–0.3% 之间');
        ensure(Number.isFinite(payload.minCommission) && payload.minCommission >= 0 && payload.minCommission <= 20, '最低佣金需要在 0–20 元之间');
        ensure(Number.isFinite(payload.slippageBps) && payload.slippageBps >= 0 && payload.slippageBps <= 100, '滑点需要在 0–100 基点之间');
        ensure(!state.simulator.orders.some(o => o.state === 'open'), '请先取消未成交委托，再修改费用设置，避免冻结金额变化');
        Object.assign(state.simulator.config, { commissionRate: payload.commissionRate, minCommission: payload.minCommission, slippageBps: payload.slippageBps }); break;
      }
      case 'lesson-open': ensure(LESSONS.some(l => l.id === payload.id), '课程不存在'); state.learning.lastLesson = payload.id; state.learning.lastOpenedRevision = 3; break;
      case 'lesson-complete': ensure(LESSONS.some(l => l.id === payload.id), '课程不存在'); if (!state.learning.completed.includes(payload.id)) state.learning.completed.push(payload.id); (state.learning.readingVersions ??= {})[payload.id] = 3; break;
      case 'bookmark': {
        ensure(LESSONS.some(l => l.id === payload.id), '课程不存在');
        state.learning.bookmarked = state.learning.bookmarked.includes(payload.id) ? state.learning.bookmarked.filter(id => id !== payload.id) : [...state.learning.bookmarked, payload.id]; break;
      }
      case 'quiz': {
        const lesson = LESSONS.find(l => l.id === payload.id); ensure(lesson, '课程不存在');
        ensure(payload.revision === lesson.quizRevision, '课程练习已更新，请刷新页面后重新作答');
        ensure(Array.isArray(payload.answers) && payload.answers.length === lesson.quizzes.length && payload.answers.every((answer: number, i: number) => Number.isInteger(answer) && answer >= 0 && answer < lesson.quizzes[i].options.length), '请回答每一道题');
        const previous = state.learning.quizResults[payload.id];
        if (previous && previous.revision !== lesson.quizRevision) { ((state.learning.previousQuizResults ??= {})[payload.id] ??= []).push(previous); }
        state.learning.quizResults[payload.id] = { answers: payload.answers, correct: payload.answers.filter((a: number, i: number) => a === lesson.quizzes[i].answerIndex).length, updatedAt: new Date().toISOString(), revision: 3 }; break;
      }
      case 'note': {
        ensure(typeof payload.text === 'string' && payload.text.trim().length > 0 && payload.text.length <= 5000, '复盘内容需要在 1–5000 字之间');
        ensure(state.notes.length < 300, '已达到 300 条笔记，请先导出');
        ensure(typeof payload.id === 'string' && payload.id.length < 100, '笔记标识不正确');
        if (!state.notes.some(n => n.id === payload.id)) state.notes.unshift({ id: payload.id, text: payload.text.trim(), createdAt: new Date().toISOString() }); break;
      }
      case 'delete-note': state.notes = state.notes.filter(n => n.id !== payload.id); break;
      case 'news-read': ensure(typeof payload.id === 'string' && payload.id.length < 200, '消息标识不正确'); if (!state.newsRead.includes(payload.id)) state.newsRead.push(payload.id); state.newsRead = state.newsRead.slice(-500); break;
      default: return jsonResponse({ error: '未知操作' }, 400);
    }
    const result = await saveState(user.userId, current.version, state);
    return jsonResponse(result, 200, user.cookie);
  } catch (error) { return jsonResponse({ error: (error as Error).message || '暂时无法保存，请重试' }, (error as Error & { status?: number }).status || 400); }
}
