import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  createConversation: vi.fn(),
  resolveConversation: vi.fn(),
  getChatHistory: vi.fn(),
}))

vi.mock('../src/api/chat', () => ({ chatApi: api }))

import { useChatStore } from '../src/stores/chat'

describe('chat conversation URL identity', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('creates an opaque conversation id together with its memory id', async () => {
    api.createConversation.mockResolvedValue({
      conversationId: 'd8918f5c-4625-4b30-9c73-e60e64656666',
      memoryId: 188000001,
    })
    const store = useChatStore()

    await store.createNewChat()

    expect(store.currentConversationId).toBe('d8918f5c-4625-4b30-9c73-e60e64656666')
    expect(store.currentMemoryId).toBe(188000001)
  })

  it('resolves a direct URL id and restores that conversation', async () => {
    api.resolveConversation.mockResolvedValue({
      conversationId: 'd8918f5c-4625-4b30-9c73-e60e64656666',
      memoryId: 188000001,
    })
    api.getChatHistory.mockResolvedValue([
      { question: '解释索引', record: '索引用于加速查询。' },
    ])
    const store = useChatStore()

    const loaded = await store.loadChatByConversationId('d8918f5c-4625-4b30-9c73-e60e64656666')

    expect(loaded).toBe(true)
    expect(store.currentMemoryId).toBe(188000001)
    expect(store.messages).toHaveLength(2)
  })
})
