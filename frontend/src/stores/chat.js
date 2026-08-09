import { defineStore } from 'pinia'
import { chatApi } from '../api/chat'

export const useChatStore = defineStore('chat', {
  state: () => ({
    // 当前对话ID
    currentMemoryId: null,
    // 当前对话消息列表
    messages: [],
    // 是否正在加载
    loading: false
  }),

  actions: {
    // 创建新对话
    createNewChat() {
      // 生成一个随机的userMemoryId (1-1000000)
      this.currentMemoryId = Math.floor(Math.random() * 1000000) + 1
      this.messages = []
    },

    // 加载特定ID的对话历史
    async loadChatById(memoryId) {
      this.loading = true
      try {
        const history = await chatApi.getChatHistory(memoryId)
        this.currentMemoryId = memoryId
        this.messages = this.convertBackendHistoryToMessages(history || [])
        return this.messages.length > 0
      } catch (error) {
        console.error('加载对话历史失败:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    // 加载特定用户的特定ID对话历史（用户隔离）
    async loadChatByIdAndUser(memoryId, userId) {
      this.loading = true
      try {
        const history = await chatApi.getChatHistoryByUser(memoryId, userId)
        this.currentMemoryId = memoryId
        this.messages = this.convertBackendHistoryToMessages(history || [])
        return this.messages.length > 0
      } catch (error) {
        console.error('加载用户对话历史失败:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    // 将后端历史记录转换为前端消息格式
    convertBackendHistoryToMessages(history) {
      const messages = []
      history.forEach((record, index) => {
        if (record.question) {
          messages.push({
            id: `history-user-${index}-${Date.now()}`, // 生成唯一ID
            role: 'user',
            content: record.question
          })
        }

        // 添加AI回复（如果有）
        if (record.record) {
          messages.push({
            id: `history-assistant-${index}-${Date.now()}`, // 生成唯一ID
            role: 'assistant',
            content: record.record,
            thinkingContent: record.thinking || '' // 从后端获取思考内容
          })
        }
      })
      return messages
    },

    // 添加用户消息
    addUserMessage(content) {
      this.messages.push({
        id: `msg-user-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`, // 生成唯一字符串ID
        role: 'user',
        content
      })
    },

    // 添加助手消息
    addAssistantMessage(content, thinkingContent = '', pipelineStages = []) {
      this.messages.push({
        id: `msg-assistant-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`, // 生成唯一字符串ID
        role: 'assistant',
        content,
        thinkingContent,
        pipelineStages
      })
    }
  }
})
