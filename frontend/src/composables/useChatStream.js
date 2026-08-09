/**
 * useChatStream — 把 SSE 流接入两条 useStreamingMarkdown 实例。
 *
 * 每次 send 都持有独立请求 context。所有回调只有在 context 仍为活动请求时
 * 才能修改共享的流/响应式状态，避免旧 Promise 在新请求启动后清理新请求。
 */

import { ref, computed } from 'vue'
import { createChatStream } from '../api/chat'
import { useStreamingMarkdown } from './useStreamingMarkdown'

const STREAM_PREFIX = {
  thinking: '[THINKING]',
  content: '[CONTENT]',
  error: '[ERROR]',
  done: '[DONE]',
  stage: '[STAGE]',
}

/**
 * 解析后端 SSE 单帧（保留为纯函数，便于单测）
 * @returns {{kind:'thinking'|'content'|'error'|'done'|'stage'|'empty', value:string}}
 */
export function parseStreamChunk(raw) {
  if (!raw) return { kind: 'empty', value: '' }
  if (raw.startsWith(STREAM_PREFIX.thinking)) {
    return { kind: 'thinking', value: raw.slice(STREAM_PREFIX.thinking.length) }
  }
  if (raw.startsWith(STREAM_PREFIX.content)) {
    return { kind: 'content', value: raw.slice(STREAM_PREFIX.content.length) }
  }
  if (raw.startsWith(STREAM_PREFIX.error)) {
    return { kind: 'error', value: raw.slice(STREAM_PREFIX.error.length) }
  }
  if (raw === STREAM_PREFIX.done) {
    return { kind: 'done', value: '' }
  }
  if (raw.startsWith(STREAM_PREFIX.stage)) {
    return { kind: 'stage', value: raw.slice(STREAM_PREFIX.stage.length) }
  }
  return { kind: 'error', value: 'Unsupported untyped stream frame' }
}

function hasToken() {
  try {
    return Boolean(localStorage.getItem('token'))
  } catch {
    return false
  }
}

export function useChatStream() {
  const contentStream = useStreamingMarkdown({ variant: 'content' })
  const thinkingStream = useStreamingMarkdown({ variant: 'thinking' })

  const isLoading = ref(false)
  const isStreaming = ref(false)
  const isThinking = ref(false)
  const lastError = ref(null)
  const lastPayload = ref(null)
  const stages = ref([])

  let nextRequestId = 0
  let activeContext = null

  const hasContent = computed(() => contentStream.blocks.value.length > 0)
  const hasThinking = computed(() => thinkingStream.blocks.value.length > 0)

  function resetStreams() {
    contentStream.reset()
    thinkingStream.reset()
    isLoading.value = false
    isStreaming.value = false
    isThinking.value = false
    lastError.value = null
    stages.value = []
  }

  function isActive(context) {
    return activeContext === context && !context.discarded && !context.settled
  }

  /** 只允许拥有当前活动槽位的 context 释放连接状态。 */
  function releaseConnection(context) {
    if (activeContext !== context) return
    activeContext = null
    isLoading.value = false
    isStreaming.value = false
    isThinking.value = false
  }

  function cloneStages(source = stages.value) {
    return source.map((stage) => ({
      ...stage,
      keywords: Array.isArray(stage.keywords) ? [...stage.keywords] : stage.keywords,
    }))
  }

  function buildResult(context, flags = {}) {
    const useSnapshot = context.discarded
    return {
      content: useSnapshot ? context.snapshotContent : contentStream.getRaw(),
      thinking: useSnapshot ? context.snapshotThinking : thinkingStream.getRaw(),
      pipelineStages: useSnapshot ? context.snapshotStages : cloneStages(),
      cancelled: false,
      discarded: false,
      stopped: false,
      ...flags,
    }
  }

  /**
   * 丢弃用于新会话、卸载或被后续 send 取代的请求。
   * 先快照旧流再 settle；绝不 finalize/reset 共享流，以免触碰后续请求。
   */
  function discardContext(context) {
    if (!context || context.settled) return
    context.discarded = true
    context.snapshotContent = contentStream.getRaw()
    context.snapshotThinking = thinkingStream.getRaw()
    context.snapshotStages = cloneStages()
    try { context.controller.abort() } catch { /* noop */ }
    context.settled = true
    releaseConnection(context)
    context.resolve(buildResult(context, { cancelled: true, discarded: true }))
  }

  /**
   * 发送一条消息并启动流式对话。
   * @returns {Promise<{content:string, thinking:string, cancelled?:boolean, discarded?:boolean, stopped?:boolean}>}
   */
  function send(payload) {
    const {
      memoryId,
      message,
      promptMode = 'professional',
      deepThinking = false,
      provider = 'deepseek',
      modelName = 'deepseek-v4-flash',
    } = payload || {}

    if (!hasToken()) {
      const err = new Error('请先登录后再发起对话')
      lastError.value = err
      return Promise.reject(err)
    }

    // 新请求取得活动槽位前，旧请求必须先成为 discarded。
    if (activeContext) discardContext(activeContext)

    lastPayload.value = { ...payload }
    resetStreams()
    stages.value = [
      { phase: 'retrieval', title: '检索相关信息', status: 'running', keywords: [], hitCount: null },
      { phase: 'thinking', title: '分析问题', status: 'waiting' },
      { phase: 'answer', title: '组织回答', status: 'waiting' },
    ]

    let resolveRequest
    let rejectRequest
    const requestPromise = new Promise((resolve, reject) => {
      resolveRequest = resolve
      rejectRequest = reject
    })

    const context = {
      id: ++nextRequestId,
      controller: new AbortController(),
      userAborted: false,
      discarded: false,
      settled: false,
      snapshotContent: '',
      snapshotThinking: '',
      snapshotStages: [],
      receivedStagePhases: new Set(),
      resolve: resolveRequest,
      reject: rejectRequest,
    }
    activeContext = context
    isLoading.value = true
    isStreaming.value = true

    const finishSuccess = () => {
      if (!isActive(context)) return
      context.settled = true
      thinkingStream.finalize()
      contentStream.finalize()
      const hasAnswerContent = Boolean(contentStream.getRaw())
      stages.value = stages.value.map((stage) => {
        if (!['running', 'waiting'].includes(stage.status)) return stage
        if (context.receivedStagePhases.has(stage.phase)) {
          return { ...stage, status: 'done' }
        }
        if (stage.phase === 'answer' && hasAnswerContent) {
          return { ...stage, status: 'done' }
        }
        return { ...stage, status: 'skipped' }
      })
      const result = buildResult(context)
      releaseConnection(context)
      context.resolve(result)
    }

    const finishStopped = () => {
      if (!isActive(context) || !context.userAborted) return
      context.settled = true
      thinkingStream.finalize()
      contentStream.finalize()
      stages.value = stages.value.map((stage) => {
        if (stage.status === 'running') return { ...stage, status: 'stopped' }
        if (stage.status === 'waiting') return { ...stage, status: 'skipped' }
        return stage
      })
      const result = buildResult(context, { stopped: true })
      releaseConnection(context)
      context.resolve(result)
    }

    const finishError = (err) => {
      if (!isActive(context)) return
      context.settled = true
      const error = err instanceof Error ? err : new Error(String(err || '流式连接错误'))
      lastError.value = error
      stages.value = stages.value.map((stage) => (
        stage.status === 'running'
          ? { ...stage, status: 'error' }
          : stage.status === 'waiting'
            ? { ...stage, status: 'skipped' }
            : stage
      ))
      thinkingStream.fail(error)
      contentStream.fail(error)
      releaseConnection(context)
      context.reject(error)
    }

    const path = deepThinking
      ? '/longcat/streamThinkChat'
      : '/longcat/streamChat'

    createChatStream({
      path,
      body: { userMemoryId: memoryId, message, promptMode, provider, modelName },
      signal: context.controller.signal,
      onThinking: (text) => {
        if (!isActive(context)) return
        isThinking.value = true
        thinkingStream.appendChunk(text)
      },
      onStage: (event) => {
        if (!isActive(context) || !event?.phase) return
        context.receivedStagePhases.add(event.phase)
        const index = stages.value.findIndex((stage) => stage.phase === event.phase)
        const nextStage = {
          ...(index >= 0 ? stages.value[index] : {}),
          ...event,
          phase: event.phase,
        }
        if (index >= 0) {
          stages.value = stages.value.map((stage, itemIndex) => (
            itemIndex === index ? nextStage : stage
          ))
        } else {
          stages.value = [...stages.value, nextStage]
        }
      },
      onContent: (text) => {
        if (!isActive(context)) return
        if (isThinking.value) {
          isThinking.value = false
          thinkingStream.finalize()
        }
        contentStream.appendChunk(text)
      },
      onDone: () => {
        if (isActive(context)) finishSuccess()
      },
      onError: (err) => {
        if (isActive(context)) finishError(err)
      },
    })
      .then(() => {
        if (context.settled || context.discarded || activeContext !== context) return
        if (context.userAborted) finishStopped()
        else finishError(new Error('连接中断，回复可能不完整，请重试'))
      })
      .catch((err) => {
        if (context.settled || context.discarded || activeContext !== context) return
        if (context.userAborted) finishStopped()
        else finishError(err)
      })

    return requestPromise
  }

  /** 用户主动停止：保留部分内容，并在底层 abort 完成后正常 resolve。 */
  function stop() {
    const context = activeContext
    if (!context || context.settled) return
    context.userAborted = true
    try { context.controller.abort() } catch { /* noop */ }
  }

  function regenerate() {
    if (!lastPayload.value) return undefined
    return send(lastPayload.value)
  }

  /** 新会话/卸载取消：旧请求返回明确的 discarded 结果。 */
  function cancel() {
    if (activeContext) discardContext(activeContext)
  }

  function reset() {
    if (activeContext) discardContext(activeContext)
    resetStreams()
  }

  return {
    contentStream,
    thinkingStream,
    isLoading,
    isStreaming,
    isThinking,
    lastError,
    hasContent,
    hasThinking,
    lastPayload,
    stages,
    send,
    stop,
    regenerate,
    cancel,
    reset,
  }
}
