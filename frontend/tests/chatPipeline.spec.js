import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createChatStream } from '../src/api/chat'
import { useChatStream } from '../src/composables/useChatStream'

vi.mock('../src/api/chat', () => ({
  createChatStream: vi.fn(),
}))

describe('observable chat pipeline', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('token', 'test-token')
    vi.clearAllMocks()
  })

  it('preserves retrieval degraded status in the completed message snapshot', async () => {
    createChatStream.mockImplementation(async (options) => {
      options.onStage({
        phase: 'retrieval',
        status: 'degraded',
        title: '检索服务暂时不可用',
        keywords: ['CAS'],
        hitCount: 0,
      })
      options.onStage({ phase: 'thinking', status: 'done', title: '分析完成' })
      options.onStage({ phase: 'answer', status: 'running', title: '正在回答' })
      options.onContent('answer')
      options.onStage({ phase: 'answer', status: 'done', title: '回答完成' })
      options.onDone()
    })

    const stream = useChatStream()
    const result = await stream.send({
      memoryId: 1,
      message: 'hello',
      deepThinking: false,
    })

    expect(result.content).toBe('answer')
    expect(result.pipelineStages.find((stage) => stage.phase === 'retrieval')).toMatchObject({
      status: 'degraded',
      keywords: ['CAS'],
    })
    expect(stream.isStreaming.value).toBe(false)
  })

  it('does not fabricate retrieval or thinking completion when STAGE frames are missing', async () => {
    createChatStream.mockImplementation(async (options) => {
      options.onContent('answer without typed stage events')
      options.onDone()
    })

    const stream = useChatStream()
    const result = await stream.send({
      memoryId: 1,
      message: 'hello',
      deepThinking: false,
    })

    expect(result.pipelineStages).toEqual([
      expect.objectContaining({ phase: 'retrieval', status: 'skipped' }),
      expect.objectContaining({ phase: 'thinking', status: 'skipped' }),
      expect.objectContaining({ phase: 'answer', status: 'done' }),
    ])
    expect(stream.stages.value).toEqual(result.pipelineStages)
  })

  it('marks active work stopped and keeps partial content on user abort', async () => {
    createChatStream.mockImplementation((options) => new Promise((resolve) => {
      options.onContent('partial')
      options.signal.addEventListener('abort', resolve, { once: true })
    }))

    const stream = useChatStream()
    const promise = stream.send({ memoryId: 1, message: 'hello' })
    stream.stop()
    const result = await promise

    expect(result.stopped).toBe(true)
    expect(result.content).toBe('partial')
    expect(result.pipelineStages[0].status).toBe('stopped')
    expect(stream.isStreaming.value).toBe(false)
  })

  it('clears discarded stream blocks and stages before restoring another history', async () => {
    createChatStream.mockImplementation((options) => new Promise((resolve) => {
      options.onStage({
        phase: 'retrieval',
        status: 'done',
        title: '检索完成',
        keywords: ['stale-keyword'],
      })
      options.onContent('stale partial answer')
      options.signal.addEventListener('abort', resolve, { once: true })
    }))

    const stream = useChatStream()
    const pending = stream.send({ memoryId: 1, message: 'old conversation' })
    stream.cancel()
    stream.reset()
    const discarded = await pending

    expect(discarded.discarded).toBe(true)
    expect(discarded.content).toBe('stale partial answer')
    expect(stream.contentStream.getRaw()).toBe('')
    expect(stream.stages.value).toEqual([])
    expect(stream.isStreaming.value).toBe(false)
  })

  it('releases busy state and marks unfinished stages when the typed stream fails', async () => {
    createChatStream.mockImplementation(async (options) => {
      options.onStage({ phase: 'thinking', status: 'running', title: '分析中' })
      options.onContent('partial')
      options.onError(new Error('provider failed'))
    })

    const stream = useChatStream()
    await expect(stream.send({ memoryId: 1, message: 'hello' }))
      .rejects.toThrow('provider failed')

    expect(stream.contentStream.getRaw()).toBe('partial')
    expect(stream.isStreaming.value).toBe(false)
    expect(stream.stages.value.find((stage) => stage.phase === 'retrieval').status).toBe('error')
    expect(stream.stages.value.find((stage) => stage.phase === 'thinking').status).toBe('error')
    expect(stream.stages.value.find((stage) => stage.phase === 'answer').status).toBe('skipped')
  })
})
