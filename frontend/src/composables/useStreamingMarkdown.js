/**
 * useStreamingMarkdown — 流式 markdown 编排 composable
 *
 * 职责：
 *  - 维护原始流缓冲 rawBuffer
 *  - 收到 chunk 后用 rAF 节流调度 parseStream
 *  - 把切块结果增量推到 blocks（committed + pending）
 *  - 暴露 appendChunk / finalize / fail / reset
 *
 * 性能要点：
 *  - shallowRef 持有 blocks 数组，避免深响应式
 *  - 同一帧内多个 chunk 合并到一次 parse
 *  - committedBlocks 仅追加（不修改已有 block 的 id/raw），子组件可 v-memo 锁定
 */

import { shallowRef, ref, computed } from 'vue'
import { parseStream, finalizeBlocks } from '../utils/streamBuffer'

const PENDING_ID = '__pending__'

export function useStreamingMarkdown(options = {}) {
  const {
    variant = 'content', // 'content' | 'thinking'
    scheduler = 'raf',   // 'raf' | 'sync'（测试用）
  } = options

  const committedBlocks = shallowRef([])
  const pending = shallowRef({ kind: null, raw: '' })
  const isFinalized = ref(false)
  const error = ref(null)

  let rawBuffer = ''
  let rafId = null
  let dirty = false
  let scheduleToken = 0

  function cancelScheduledFlush() {
    scheduleToken += 1
    if (rafId == null) return
    if (typeof cancelAnimationFrame === 'function') {
      try { cancelAnimationFrame(rafId) } catch { /* noop */ }
    }
    rafId = null
  }

  /**
   * blocks: 已提交 + （进行中 → 末尾追加 pending block，带 id = __pending__）
   */
  const blocks = computed(() => {
    const list = committedBlocks.value
    const p = pending.value
    if (!p || !p.kind || !p.raw) return list
    return list.concat({
      id: PENDING_ID,
      kind: p.kind,
      raw: p.raw,
      lang: p.lang,
      done: false,
    })
  })

  const hasPending = computed(() => {
    const p = pending.value
    return Boolean(p && p.kind && p.raw)
  })

  function flush() {
    rafId = null
    dirty = false
    try {
      const result = parseStream(rawBuffer)
      // 仅在长度变化时才赋新数组（减少不必要的响应式触发）
      if (result.committedBlocks.length !== committedBlocks.value.length) {
        committedBlocks.value = result.committedBlocks
      } else if (result.committedBlocks.length > 0) {
        // 长度相等但末尾 block raw 可能因解析重 commit 而变化（罕见）；做一次浅比较
        const last = result.committedBlocks[result.committedBlocks.length - 1]
        const prevLast = committedBlocks.value[committedBlocks.value.length - 1]
        if (!prevLast || prevLast.raw !== last.raw) {
          committedBlocks.value = result.committedBlocks
        }
      }
      pending.value = result.pending || { kind: null, raw: '' }
    } catch (err) {
      /* eslint-disable no-console */
      console.warn('[useStreamingMarkdown] flush failed', err)
      pending.value = { kind: 'paragraph', raw: rawBuffer }
    }
  }

  function schedule() {
    if (scheduler === 'sync') {
      flush()
      return
    }
    if (rafId != null) return
    if (typeof requestAnimationFrame === 'undefined') {
      flush()
      return
    }
    const token = scheduleToken
    rafId = requestAnimationFrame(() => {
      if (token !== scheduleToken) return
      flush()
    })
  }

  function appendChunk(text) {
    if (!text) return
    if (isFinalized.value) {
      // 已 finalize 后又来 chunk → 视为重启了一段流，自动重置
      reset()
    }
    rawBuffer += text
    dirty = true
    schedule()
  }

  function finalize() {
    cancelScheduledFlush()
    if (dirty) flush()
    // 把 pending 也提交
    const all = finalizeBlocks({
      committedBlocks: committedBlocks.value,
      pending: pending.value,
    })
    committedBlocks.value = all
    pending.value = { kind: null, raw: '' }
    isFinalized.value = true
  }

  function fail(err) {
    cancelScheduledFlush()
    // 网络错误可能发生在 rAF 执行前；先解析最新 rawBuffer，避免丢掉最后一批 chunk。
    if (dirty) flush()
    error.value = err || new Error('Stream failed')
    const all = finalizeBlocks({
      committedBlocks: committedBlocks.value,
      pending: pending.value,
    })
    committedBlocks.value = all
    pending.value = { kind: null, raw: '' }
    isFinalized.value = true
  }

  function reset() {
    cancelScheduledFlush()
    rawBuffer = ''
    committedBlocks.value = []
    pending.value = { kind: null, raw: '' }
    isFinalized.value = false
    error.value = null
    dirty = false
  }

  /**
   * 一次性把已有完整 markdown 文本作为"已完成"展示（用于历史会话渲染）
   */
  function loadCompleted(text) {
    reset()
    if (!text) return
    rawBuffer = text
    flush()
    finalize()
  }

  /**
   * 当前累积原文（调试 / 复制用）
   */
  function getRaw() {
    return rawBuffer
  }

  return {
    // 响应式状态
    blocks,
    committedBlocks,
    pending,
    hasPending,
    isFinalized,
    error,
    variant,

    // 操作
    appendChunk,
    finalize,
    fail,
    reset,
    loadCompleted,
    getRaw,
  }
}

export const STREAM_PENDING_ID = PENDING_ID
