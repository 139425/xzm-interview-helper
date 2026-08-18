import request, { baseURL } from '@/utils/request'

// SSE 帧前缀（与后端 Python zhipu_service / Java LongCatChatController 对齐）
const STREAM_PREFIX = {
  thinking: '[THINKING]',
  content: '[CONTENT]',
  error: '[ERROR]',
  done: '[DONE]',
  stage: '[STAGE]',
}
const STAGE_PHASES = new Set(['retrieval', 'thinking', 'answer'])
const STAGE_STATUSES = new Set([
  'waiting',
  'running',
  'done',
  'degraded',
  'error',
  'stopped',
  'skipped',
])

export function normalizeStageEvent(value) {
  if (!value || typeof value !== 'object') return null
  const phase = String(value.phase || '').trim().toLowerCase()
  const status = String(value.status || '').trim().toLowerCase()
  if (!STAGE_PHASES.has(phase) || !STAGE_STATUSES.has(status)) return null
  const normalized = {
    phase,
    status,
    title: String(value.title || '').trim().slice(0, 160),
  }
  if (Array.isArray(value.keywords)) {
    normalized.keywords = [...new Set(
      value.keywords
        .map((keyword) => String(keyword).trim().slice(0, 80))
        .filter(Boolean),
    )].slice(0, 6)
  }
  if (Number.isFinite(Number(value.hitCount))) {
    normalized.hitCount = Math.max(0, Math.min(100, Number(value.hitCount)))
  }
  if (Number.isFinite(Number(value.personalHitCount))) {
    normalized.personalHitCount = Math.max(0, Math.min(30, Number(value.personalHitCount)))
  }
  if (Array.isArray(value.sources)) {
    normalized.sources = value.sources.slice(0, 8).map((source) => ({
      id: Number(source?.id) || 0,
      title: String(source?.title || '').trim().slice(0, 255),
      sourceType: String(source?.sourceType || 'DOCUMENT').trim().slice(0, 32),
      score: Number(source?.score) || 0,
    })).filter((source) => source.title)
  }
  if (Array.isArray(value.publicSources)) {
    normalized.publicSources = value.publicSources.slice(0, 8).map((source) => ({
      title: String(source?.title || '公共知识库').trim().slice(0, 160),
      sourceType: 'PUBLIC_KNOWLEDGE',
      path: String(source?.path || '').trim().slice(0, 300),
      section: String(source?.section || '').trim().slice(0, 200),
    }))
  }
  return normalized
}

/**
 * 认证过期处理（与 request.js 语义一致，供流式 401 复用）
 * 清除本地凭证 → 标记过期 → 跳转登录页。
 */
function handleStreamAuthExpired() {
  try {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    sessionStorage.setItem('authExpired', 'true')
  } catch { /* noop */ }
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.replace('/login')
  }
}

async function createStreamHttpError(response, fallbackMessage) {
  let message = fallbackMessage
  try {
    const payload = await response.json()
    message = payload?.message || payload?.error || payload?.detail || message
  } catch {
    // Reverse proxies may return HTML; the bounded fallback remains actionable.
  }
  const error = new Error(message)
  error.status = response.status
  return error
}

/**
 * LongCat streams use POST JSON so messages and identity never appear in a URL.
 */
/**
 * 统一流式入口：fetch + ReadableStream 消费 SSE。
 *
 * @param {object}   opts
 * @param {string}   opts.path        Authenticated endpoint path, e.g. '/longcat/streamChat'
 * @param {object}   opts.body        JSON request payload
 * @param {AbortSignal} opts.signal   AbortController.signal，用于停止生成
 * @param {(t:string)=>void} opts.onThinking  收到 [THINKING] 增量
 * @param {(t:string)=>void} opts.onContent   收到 [CONTENT] 增量
 * @param {()=>void}         opts.onDone      收到 [DONE]（正常完成）
 * @param {(e:Error)=>void}  opts.onError     [ERROR] 帧 / 连接中断 / 网络错误
 * @returns {Promise<void>}
 */
export async function createChatStream({ path, body, signal, onThinking, onContent, onStage, onDone, onError }) {
  const url = `${baseURL}${path}`
  const token = (() => {
    try { return localStorage.getItem('token') } catch { return null }
  })()

  const headers = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json',
  }
  if (token) headers.Authorization = `Bearer ${token}`

  let sawDone = false
  let sawError = false
  let reader = null
  let readerEnded = false
  let notifyDone = false

  const emitError = (err) => {
    if (sawError || sawDone || signal?.aborted) return
    sawError = true
    onError?.(err instanceof Error ? err : new Error(String(err || '流式接口错误')))
  }

  // 终止帧一旦出现，之后的任何载荷都不得再分发。
  const dispatchFrame = (payloadRaw) => {
    if (sawDone || sawError || signal?.aborted) return
    const payload = payloadRaw

    if (payload === STREAM_PREFIX.done) {
      sawDone = true
    } else if (payload.startsWith(STREAM_PREFIX.error)) {
      emitError(new Error(payload.slice(STREAM_PREFIX.error.length) || '流式接口返回错误'))
    } else if (payload.startsWith(STREAM_PREFIX.thinking)) {
      onThinking?.(payload.slice(STREAM_PREFIX.thinking.length))
    } else if (payload.startsWith(STREAM_PREFIX.stage)) {
      const rawStage = payload.slice(STREAM_PREFIX.stage.length)
      try {
        const stage = normalizeStageEvent(JSON.parse(rawStage))
        if (stage) onStage?.(stage)
      } catch {
        onStage?.({
          phase: 'retrieval',
          status: 'degraded',
          title: '流程状态解析失败，已继续回答',
        })
      }
    } else if (payload.startsWith(STREAM_PREFIX.content)) {
      onContent?.(payload.slice(STREAM_PREFIX.content.length))
    } else if (payload) {
      // The Java gateway emits a typed prefix for every payload.  Treating unknown/raw frames
      // as answer text would let a proxy regression or a future control frame silently enter the
      // persisted response, so protocol mismatches fail closed.
      emitError(new Error('流式响应包含不受支持的数据帧，请重试'))
    }
  }

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body || {}),
      signal,
    })

    if (response.status === 401) {
      handleStreamAuthExpired()
      emitError(new Error('登录已过期，请重新登录'))
      return
    }
    if (response.status === 403) {
      emitError(await createStreamHttpError(response, '没有权限发起此对话'))
      return
    }
    if (!response.ok || !response.body) {
      emitError(new Error(`流式请求失败：HTTP ${response.status}`))
      return
    }

    reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let dataLines = []

    const flushEvent = () => {
      if (dataLines.length === 0 || sawDone || sawError) {
        dataLines = []
        return
      }
      const payload = dataLines.join('\n')
      dataLines = []
      dispatchFrame(payload)
    }

    const processLine = (rawLine) => {
      let line = rawLine
      if (line.endsWith('\r')) line = line.slice(0, -1)
      if (line === '') {
        flushEvent()
      } else if (!line.startsWith(':') && line.startsWith('data:')) {
        let data = line.slice(5)
        if (data.startsWith(' ')) data = data.slice(1)
        dataLines.push(data)
      }
    }

    const drainLines = (flushTail = false) => {
      let newlineIndex
      while (!sawError && !sawDone && (newlineIndex = buffer.indexOf('\n')) >= 0) {
        processLine(buffer.slice(0, newlineIndex))
        buffer = buffer.slice(newlineIndex + 1)
      }
      if (flushTail && !sawError && !sawDone) {
        if (buffer) processLine(buffer)
        buffer = ''
        flushEvent()
      }
    }

    while (!sawError && !sawDone) {
      const { done, value } = await reader.read()
      if (done) {
        readerEnded = true
        buffer += decoder.decode()
        drainLines(true)
        break
      }
      buffer += decoder.decode(value, { stream: true })
      drainLines()
    }

    if (sawDone) {
      notifyDone = true
    } else if (!sawError && !signal?.aborted) {
      emitError(new Error('连接中断，回复可能不完整，请重试'))
    }
  } catch (err) {
    if (!(err && (err.name === 'AbortError' || signal?.aborted))) {
      emitError(err)
    }
  } finally {
    if (reader) {
      if (!readerEnded && (sawDone || sawError || signal?.aborted)) {
        try { await reader.cancel() } catch { /* noop */ }
      }
      try { reader.releaseLock() } catch { /* noop */ }
    }
  }

  // 完成回调延后到 reader 自然关闭并释放锁之后。
  if (notifyDone && !signal?.aborted) onDone?.()
}

export const chatApi = {
  async createConversation() {
    const response = await request.post('/record/conversations')
    return response.data
  },

  async resolveConversation(conversationId) {
    const response = await request.get(`/record/conversations/${encodeURIComponent(conversationId)}`)
    return response.data
  },

  /**
   * 获取对话历史
   * @param {number} memoryId - 会话ID
   * @returns {Promise} - 返回对话历史记录
   */
  async getChatHistory(memoryId) {
    const response = await request.get(`/record/history/${memoryId}`)
    return response.data
  },

  /**
   * 获取指定用户的对话历史
   * @param {number} memoryId - 会话ID
   * @param {number} userId - 用户ID
   * @returns {Promise} - 返回对话历史记录
   */
  async getChatHistoryByUser(memoryId, userId) {
    const response = await request.get(`/record/history/${memoryId}/user/${userId}`)
    return response.data
  },

  /**
   * 获取所有对话历史摘要
   * @returns {Promise} - 返回所有对话历史摘要列表
   */
  async getAllChatHistorySummaries() {
    try {
      const response = await request.get('/record/histories')
      return response.data
    } catch (error) {
      console.error('获取对话历史摘要失败:', error)
      return []
    }
  },

  /**
   * 获取指定用户的所有对话历史摘要
   * @param {number} userId - 用户ID
   * @returns {Promise} - 返回用户的对话历史摘要列表
   */
  async getAllChatHistorySummariesByUser(userId) {
    try {
      const response = await request.get(`/record/histories/user/${userId}`)
      return response.data
    } catch (error) {
      console.error('获取用户对话历史摘要失败:', error)
      return []
    }
  },

  /**
   * 分页获取指定用户的对话历史摘要
   * @param {number} userId - 用户ID
   * @param {number} pageNum - 页码（从1开始）
   * @param {number} pageSize - 每页数量
   * @returns {Promise} - 返回分页数据 { records, total, pageNum, pageSize, hasMore }
   */
  async getChatHistorySummariesByUserPaged(userId, pageNum = 1, pageSize = 10) {
    try {
      const response = await request.get(`/record/histories/user/${userId}/page`, {
        params: { pageNum, pageSize }
      })
      return response.data
    } catch (error) {
      console.error('分页获取用户对话历史摘要失败:', error)
      return { records: [], total: 0, pageNum, pageSize, hasMore: false }
    }
  },
  
  /**
   * 删除某一ID的所有历史记录
   * @param {number} memoryId - 会话ID
   */
  async deleteHistoryById(memoryId) {
    try {
      const response = await request.delete(`/record/history/${memoryId}`)
      return response.data
    } catch (error) {
      console.error('删除历史记录失败:', error)
      throw error
    }
  },

  /**
   * 清除指定用户的会话历史记录
   * @param {number} memoryId - 会话ID
   * @param {number} userId - 用户ID
   * @returns {Promise} - 返回清除结果
   */
  async clearChatHistoryByUser(memoryId, userId) {
    try {
      const response = await request.get(`/record/clear/${memoryId}/user/${userId}`)
      return response.data
    } catch (error) {
      console.error('清除用户历史记录失败:', error)
      throw error
    }
  },

  /**
   * 获取指定用户的会话消息数量
   * @param {number} memoryId - 会话ID
   * @param {number} userId - 用户ID
   * @returns {Promise} - 返回消息数量
   */
  async getChatMessageCountByUser(memoryId, userId) {
    try {
      const response = await request.get(`/record/count/${memoryId}/user/${userId}`)
      return response.data
    } catch (error) {
      console.error('获取用户消息数量失败:', error)
      return 0
    }
  },
  
  /**
   * 语音识别API
   * @param {string} audioBase64 - Base64编码的音频数据
   * @param {number} dataLen - 原始音频数据长度（字节）
   * @returns {Promise} - 返回识别结果
   */
  async voiceRecognition(audioBase64, dataLen) {
    try {
      // 使用FormData发送数据，匹配后端接口参数名
      const formData = new FormData()
      formData.append('Data', audioBase64)
      formData.append('DataLen', dataLen)
      
      const response = await request.post('/record/voice_Conversion', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      return response.data
    } catch (error) {
      console.error('语音识别失败:', error)
      throw error
    }
  },

  /**
   * LongCat 直接对话（非流式，无用户隔离）
   * @param {number} userMemoryId - 用户记忆ID
   * @param {string} message - 用户消息
   * @returns {Promise<string>} - 返回AI回复内容
   */
  async directChatLongCat(userMemoryId, message) {
    try {
      const response = await request.post('/longcat/directChat', { userMemoryId, message })
      return response.data
    } catch (error) {
      console.error('LongCat直接对话失败:', error)
      throw error
    }
  },

  /**
   * LongCat 直接对话（非流式，用户隔离）
   * @param {number} userMemoryId - 用户记忆ID
   * @param {string} message - 用户消息
   * @param {number} userId - 用户ID
   * @returns {Promise<string>} - 返回AI回复内容
   */
  async directChatLongCatByUser(userMemoryId, message, userId) {
    try {
      // The server derives identity from the verified JWT.  Keep this legacy
      // signature so callers do not break, but never send its userId argument.
      void userId
      const response = await request.post('/longcat/directChat', { userMemoryId, message })
      return response.data
    } catch (error) {
      console.error('LongCat直接对话（用户隔离）失败:', error)
      throw error
    }
  }
}
