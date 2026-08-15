/**
 * streamBuffer.spec.js
 * 覆盖：基础切块 / 残缺代码块 / 残缺表格 / 列表 / {{SP}} 兜底 / 失败回退
 */
import { describe, it, expect } from 'vitest'
import {
  parseStream,
  finalizeBlocks,
  decodeSpaceMarker,
} from '../src/utils/streamBuffer'

describe('parseStream — 基础切块', () => {
  it('空输入返回空 committedBlocks 与空 pending', () => {
    const r = parseStream('')
    expect(r.committedBlocks).toEqual([])
    expect(r.pending.kind).toBeNull()
  })

  it('单段未结束 → pending 为 paragraph', () => {
    const r = parseStream('Hello')
    expect(r.committedBlocks).toEqual([])
    expect(r.pending.kind).toBe('paragraph')
    expect(r.pending.raw.trim()).toBe('Hello')
  })

  it('双换行触发 paragraph commit', () => {
    const r = parseStream('Hello\n\nWorld')
    expect(r.committedBlocks).toHaveLength(1)
    expect(r.committedBlocks[0].kind).toBe('paragraph')
    expect(r.pending.kind).toBe('paragraph')
    expect(r.pending.raw.trim()).toBe('World')
  })

  it('标题与水平线单行 commit', () => {
    const r = parseStream('# 标题\n\n正文')
    expect(r.committedBlocks[0].kind).toBe('heading')
    expect(r.pending.kind).toBe('paragraph')
  })

  it('未收到换行的流式标题保持 pending，避免完成态逐字重绘', () => {
    const r = parseStream('# 正在生成标题')
    expect(r.committedBlocks).toEqual([])
    expect(r.pending).toMatchObject({ kind: 'heading', raw: '# 正在生成标题' })
  })

  it('规范化模型漏写空格的编号型标题，但不改写代码预处理行', () => {
    const result = parseStream('###a) 常规回答\n\n#include <stdio.h>')

    expect(result.committedBlocks[0]).toMatchObject({
      kind: 'heading',
      raw: '### a) 常规回答\n',
    })
    expect(result.pending.raw).toContain('#include <stdio.h>')
  })
})

describe('parseStream — 代码块（核心修复）', () => {
  it('未闭合代码块 → pending kind=code，不提交', () => {
    const r = parseStream('```python\ndef foo():\n  return 1\n')
    expect(r.committedBlocks).toEqual([])
    expect(r.pending.kind).toBe('code')
    expect(r.pending.lang).toBe('python')
    expect(r.pending.raw).toContain('def foo')
  })

  it('已闭合代码块 → 一次 commit', () => {
    const r = parseStream('```js\nconst a = 1\n```\n')
    expect(r.committedBlocks).toHaveLength(1)
    expect(r.committedBlocks[0].kind).toBe('code')
    expect(r.committedBlocks[0].lang).toBe('js')
    expect(r.committedBlocks[0].raw).toContain('const a')
  })

  it('代码块内部双换行不会被误认为段落分隔', () => {
    const r = parseStream('```\nline1\n\nline2\n```\n')
    expect(r.committedBlocks).toHaveLength(1)
    expect(r.committedBlocks[0].kind).toBe('code')
  })

  it('代码块前后段落正确切分', () => {
    const r = parseStream('Hello\n\n```js\nx\n```\n\nWorld')
    expect(r.committedBlocks.map((b) => b.kind)).toEqual(['paragraph', 'code'])
    expect(r.pending.kind).toBe('paragraph')
  })
})

describe('parseStream — 表格', () => {
  it('表格首行后未跟分隔行 → 当 paragraph 处理（历史 bug）', () => {
    const r = parseStream('| a | b |\nplain text\n')
    // 表格"假候选"应降级为 paragraph
    expect(r.committedBlocks.some((b) => b.kind === 'table')).toBe(false)
  })

  it('完整表格 → commit table', () => {
    const r = parseStream('| h1 | h2 |\n|---|---|\n| a | b |\n\n')
    expect(r.committedBlocks.find((b) => b.kind === 'table')).toBeTruthy()
  })

  it('未闭合表格 → pending kind=table', () => {
    const r = parseStream('| h1 | h2 |\n|---|---|\n| a | b')
    expect(r.committedBlocks.some((b) => b.kind === 'table')).toBe(false)
    expect(r.pending.kind).toBe('table')
  })
})

describe('parseStream — 列表 / 引用', () => {
  it('无序列表收集为 list block', () => {
    const r = parseStream('- a\n- b\n- c\n\nnext')
    expect(r.committedBlocks[0].kind).toBe('list')
    expect(r.pending.kind).toBe('paragraph')
  })

  it('引用块收集为 quote', () => {
    const r = parseStream('> hello\n> world\n\nnext')
    expect(r.committedBlocks[0].kind).toBe('quote')
  })
})

describe('decodeSpaceMarker', () => {
  it('替换所有 {{SP}} 为空格', () => {
    expect(decodeSpaceMarker('a{{SP}}b{{SP}}c')).toBe('a b c')
  })
  it('空输入返回空字符串', () => {
    expect(decodeSpaceMarker('')).toBe('')
    expect(decodeSpaceMarker(null)).toBe('')
  })
  it('新流默认保留模型输出中的字面占位符文本', () => {
    expect(parseStream('a{{SP}}b').pending.raw).toBe('a{{SP}}b')
  })
})

describe('finalizeBlocks', () => {
  it('把 pending 也提交为最后一个 block', () => {
    const r = parseStream('Hello')
    const all = finalizeBlocks(r)
    expect(all).toHaveLength(1)
    expect(all[0].kind).toBe('paragraph')
    expect(all[0].done).toBe(true)
  })

  it('pending 为空时返回原 committedBlocks', () => {
    const r = parseStream('# heading\n\n')
    const all = finalizeBlocks(r)
    expect(all).toHaveLength(1)
    expect(all[0].kind).toBe('heading')
  })
})

describe('parseStream — 失败回退', () => {
  it('非字符串输入 → 安全返回空', () => {
    expect(parseStream(null).committedBlocks).toEqual([])
    expect(parseStream(undefined).committedBlocks).toEqual([])
    expect(parseStream(123).committedBlocks).toEqual([])
  })
})
