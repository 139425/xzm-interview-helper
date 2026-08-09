/**
 * streamingMarkdown.spec.js
 * 端到端：模拟 SSE chunk 序列 → useStreamingMarkdown 输出
 */
import { describe, it, expect } from 'vitest'
import { useStreamingMarkdown } from '../src/composables/useStreamingMarkdown'

describe('useStreamingMarkdown', () => {
  it('appendChunk 后 blocks 包含 pending', async () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.appendChunk('Hello')
    expect(s.blocks.value.length).toBeGreaterThan(0)
    expect(s.blocks.value[0].kind).toBe('paragraph')
    expect(s.blocks.value[0].done).toBe(false)
  })

  it('段落完成后变为 committed', async () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.appendChunk('Hello\n\nWorld')
    const committed = s.committedBlocks.value
    expect(committed).toHaveLength(1)
    expect(committed[0].done).toBe(true)
    expect(s.pending.value.kind).toBe('paragraph')
    expect(s.pending.value.raw.trim()).toBe('World')
  })

  it('finalize 把 pending 也提交', async () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.appendChunk('Hello\n\nWorld')
    s.finalize()
    expect(s.committedBlocks.value.length).toBeGreaterThanOrEqual(2)
    expect(s.isFinalized.value).toBe(true)
    expect(s.pending.value.kind).toBeNull()
  })

  it('代码块流式过程中只 pending，不提交', () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.appendChunk('```js\nconst a = ')
    expect(s.committedBlocks.value).toEqual([])
    expect(s.pending.value.kind).toBe('code')
    expect(s.pending.value.lang).toBe('js')

    s.appendChunk('1\n```\n')
    expect(s.committedBlocks.value).toHaveLength(1)
    expect(s.committedBlocks.value[0].kind).toBe('code')
  })

  it('reset 清空所有状态', () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.appendChunk('Hello')
    s.reset()
    expect(s.committedBlocks.value).toEqual([])
    expect(s.pending.value.kind).toBeNull()
    expect(s.getRaw()).toBe('')
    expect(s.isFinalized.value).toBe(false)
  })

  it('loadCompleted 一次性灌入历史内容并 finalize', () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.loadCompleted('# 标题\n\n正文段落')
    expect(s.isFinalized.value).toBe(true)
    expect(s.committedBlocks.value.length).toBeGreaterThanOrEqual(2)
  })

  it('fail 把 pending 兜底提交并标记 finalized', () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.appendChunk('Hello, ')
    s.fail(new Error('test'))
    expect(s.error.value).toBeInstanceOf(Error)
    expect(s.isFinalized.value).toBe(true)
    expect(s.committedBlocks.value.length).toBeGreaterThan(0)
  })

  it('已 finalize 后再 appendChunk 会自动 reset', () => {
    const s = useStreamingMarkdown({ scheduler: 'sync' })
    s.appendChunk('first')
    s.finalize()
    s.appendChunk('second')
    expect(s.getRaw()).toBe('second')
    expect(s.isFinalized.value).toBe(false)
  })
})
