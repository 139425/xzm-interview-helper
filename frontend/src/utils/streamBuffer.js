/**
 * streamBuffer — 纯函数 markdown 流式切块器
 *
 * 输入：累积的原始流文本（每次 SSE 抵达后整体重传）
 * 输出：committedBlocks（已闭合可安全渲染） + pending（仍在写、纯文本展示）
 *
 * 设计目标：
 *  1) O(n) 单次扫描，不重复 substring 全文
 *  2) 不抛错 —— 任何异常降级为 paragraph 单 block 兜底
 *  3) 无 DOM / 无响应式依赖，便于 Node 单测
 *  4) 严格的"未闭合不提交"原则 —— 防止半成品 HTML
 */

const SP_MARKER_REGEX = /\{\{SP\}\}/g

/**
 * Explicit migration helper for legacy text that was known to use the old
 * placeholder protocol. New streams must preserve literal {{SP}} content.
 * @param {string} text
 * @returns {string}
 */
export function decodeSpaceMarker(text) {
  if (!text) return ''
  return text.replace(SP_MARKER_REGEX, ' ')
}

/**
 * 修复模型常见的编号型 ATX 标题漏空格，例如 `###a)` / `###1.`。
 * 范围刻意限制为“字母/数字/中文序号 + 分隔符”，避免把 #include、
 * issue 标签或普通井号文本误判成标题。
 */
export function normalizeModelMarkdown(text) {
  if (!text) return ''
  return text.replace(
    /^(#{1,6})(?=(?:[A-Za-z]|\d+)[.)、:：]|[一二三四五六七八九十]+[、.)：:])/gm,
    '$1 ',
  )
}

/**
 * 是否为 GFM 表格行（含 | 字符且非裸竖线）
 */
function isTableRow(line) {
  const trimmed = line.trim()
  if (!trimmed.includes('|')) return false
  // 至少有一个 | 出现在行内（不是首尾包裹空白）
  return /^\|?.+\|.*$/.test(trimmed)
}

/**
 * 是否为表格分隔行 |---|---|
 */
function isTableSeparatorRow(line) {
  const trimmed = line.trim()
  if (!trimmed.includes('|') || !trimmed.includes('-')) return false
  return /^\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)+\|?$/.test(trimmed)
}

/**
 * 是否为列表项首行
 */
function isListItemLine(line) {
  return /^\s*([-*+]|\d+\.)\s+\S/.test(line)
}

/**
 * 是否为代码栅栏行
 */
function isCodeFenceLine(line) {
  return /^```/.test(line.trimStart())
}

/**
 * 提取代码栅栏的语言（```lang）
 */
function extractFenceLang(line) {
  const match = line.trimStart().match(/^```\s*([^\s]*)/)
  return match ? (match[1] || '').toLowerCase() : ''
}

/**
 * 是否为标题行
 */
function isHeadingLine(line) {
  return /^#{1,6}\s+\S/.test(line)
}

/**
 * 是否为引用行
 */
function isQuoteLine(line) {
  return /^\s*>/.test(line)
}

/**
 * 是否为水平线
 */
function isHrLine(line) {
  const t = line.trim()
  return /^(-{3,}|\*{3,}|_{3,})$/.test(t)
}

/**
 * 是否为缩进的列表续行（≥ 2 空格开头且非空）
 */
function isIndentedContinuation(line) {
  return /^[ \t]{2,}\S/.test(line)
}

/**
 * 当前行是否会"切断"列表（即列表必须 commit 后再处理这行）
 */
function isListBreaker(line) {
  if (line.trim() === '') return false // 列表内允许空行
  if (isListItemLine(line)) return false
  if (isIndentedContinuation(line)) return false
  // 同列起始且非列表项 → 视为列表结束
  return true
}

/**
 * @param {string} rawAccumulated
 * @param {object} [options]
 * @param {boolean} [options.decodeSpaceMarker=false]
 * @param {number} [options.maxLength=131072]
 * @returns {{
 *   committedBlocks: Array<{id:string,kind:string,raw:string,lang?:string,done:true}>,
 *   pending: {kind:string|null,raw:string,lang?:string},
 * }}
 */
export function parseStream(rawAccumulated, options = {}) {
  const {
    decodeSpaceMarker: doDecode = false,
    maxLength = 131072,
  } = options

  if (typeof rawAccumulated !== 'string' || !rawAccumulated) {
    return { committedBlocks: [], pending: { kind: null, raw: '' } }
  }

  let text = doDecode ? decodeSpaceMarker(rawAccumulated) : rawAccumulated
  text = normalizeModelMarkdown(text)
  if (text.length > maxLength) {
    text = text.slice(0, maxLength)
  }

  // 兜底 try/catch — 任何异常都降级为单段落
  try {
    return scan(text)
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[streamBuffer] parse failed, falling back to plain paragraph', err)
    return {
      committedBlocks: [],
      pending: { kind: 'paragraph', raw: text },
    }
  }
}

function scan(text) {
  const lines = text.split('\n')
  const blocks = []
  let blockId = 0

  let state = 'IDLE'
  let currentRaw = ''
  let currentLang = ''
  // 表格首行临时缓存（用来判断下一行是不是 |---| 分隔行）
  let pendingTableHeader = null

  function commit(kind) {
    if (!currentRaw) return
    blocks.push({
      id: `b${blockId++}`,
      kind,
      raw: currentRaw,
      ...(kind === 'code' ? { lang: currentLang } : null),
      done: true,
    })
    currentRaw = ''
    currentLang = ''
    pendingTableHeader = null
  }

  function flushTableCandidateAsParagraph() {
    // 表格"假候选"（首行后未跟 |---|）→ 当成 paragraph 提交
    if (pendingTableHeader != null) {
      currentRaw = pendingTableHeader
      pendingTableHeader = null
      commit('paragraph')
      currentRaw = ''
    }
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const isLast = i === lines.length - 1
    // line 不含末尾换行；除最后一行外都补一个 \n 还原原文
    const lineWithNewline = isLast ? line : line + '\n'

    // ------ 当前在代码块内 ------
    if (state === 'IN_CODE') {
      if (isCodeFenceLine(line)) {
        // 闭合代码块
        commit('code')
        state = 'IDLE'
      } else {
        currentRaw += lineWithNewline
      }
      continue
    }

    // ------ 当前在表格"候选"（已写入首行，等下一行决定） ------
    if (state === 'TABLE_CANDIDATE') {
      if (isTableSeparatorRow(line)) {
        // 确认是表格
        currentRaw = pendingTableHeader + lineWithNewline
        pendingTableHeader = null
        state = 'IN_TABLE'
      } else {
        // 不是表格 → 把首行降级为 paragraph，对当前行重新处理
        flushTableCandidateAsParagraph()
        state = 'IDLE'
        i-- // 重新处理这一行
      }
      continue
    }

    // ------ 当前在表格内 ------
    if (state === 'IN_TABLE') {
      if (isTableRow(line)) {
        currentRaw += lineWithNewline
      } else {
        // 表格结束（无论是空行还是其它）
        commit('table')
        state = 'IDLE'
        if (line.trim() !== '') {
          i-- // 非空行重新处理
        }
      }
      continue
    }

    // ------ 当前在引用 ------
    if (state === 'IN_QUOTE') {
      if (isQuoteLine(line)) {
        currentRaw += lineWithNewline
      } else if (line.trim() === '') {
        commit('quote')
        state = 'IDLE'
      } else {
        commit('quote')
        state = 'IDLE'
        i--
      }
      continue
    }

    // ------ 当前在列表 ------
    if (state === 'IN_LIST') {
      if (isListBreaker(line)) {
        commit('list')
        state = 'IDLE'
        i--
        continue
      }
      currentRaw += lineWithNewline
      continue
    }

    // ------ 当前在段落 ------
    if (state === 'IN_PARAGRAPH') {
      if (line.trim() === '') {
        commit('paragraph')
        state = 'IDLE'
      } else if (
        isCodeFenceLine(line) ||
        isHeadingLine(line) ||
        isHrLine(line) ||
        isQuoteLine(line) ||
        isListItemLine(line) ||
        (isTableRow(line) && /^\s*\|/.test(line))
      ) {
        commit('paragraph')
        state = 'IDLE'
        i--
      } else {
        currentRaw += lineWithNewline
      }
      continue
    }

    // ------ IDLE 状态：根据当前行决定进入哪个状态 ------
    // 跳过纯空行
    if (line.trim() === '') continue

    // 代码栅栏
    if (isCodeFenceLine(line)) {
      currentLang = extractFenceLang(line)
      currentRaw = ''
      state = 'IN_CODE'
      continue
    }

    // 标题（单行 commit）
    if (isHeadingLine(line)) {
      currentRaw = lineWithNewline
      commit('heading')
      continue
    }

    // 水平线（单行 commit）
    if (isHrLine(line)) {
      currentRaw = lineWithNewline
      commit('hr')
      continue
    }

    // 引用
    if (isQuoteLine(line)) {
      currentRaw = lineWithNewline
      state = 'IN_QUOTE'
      continue
    }

    // 列表
    if (isListItemLine(line)) {
      currentRaw = lineWithNewline
      state = 'IN_LIST'
      continue
    }

    // 表格候选（首行）：缓存下来，等下一行判断
    if (isTableRow(line) && /\|/.test(line)) {
      pendingTableHeader = lineWithNewline
      state = 'TABLE_CANDIDATE'
      continue
    }

    // 段落
    currentRaw = lineWithNewline
    state = 'IN_PARAGRAPH'
  }

  // 扫完后处理 pending
  let pending = { kind: null, raw: '' }
  if (state === 'TABLE_CANDIDATE') {
    // 候选状态：仍未确认 → 视为 paragraph 进行中
    pending = { kind: 'paragraph', raw: pendingTableHeader || '' }
  } else if (state === 'IN_CODE') {
    pending = { kind: 'code', raw: currentRaw, lang: currentLang }
  } else if (state === 'IN_TABLE') {
    pending = { kind: 'table', raw: currentRaw }
  } else if (state === 'IN_QUOTE') {
    pending = { kind: 'quote', raw: currentRaw }
  } else if (state === 'IN_LIST') {
    pending = { kind: 'list', raw: currentRaw }
  } else if (state === 'IN_PARAGRAPH') {
    pending = { kind: 'paragraph', raw: currentRaw }
  }

  return { committedBlocks: blocks, pending }
}

/**
 * 对 parseStream 输出做 finalize：把 pending 也作为最后一个 block 提交。
 * 流结束时由 useStreamingMarkdown.finalize() 调用。
 */
export function finalizeBlocks(parseResult) {
  const { committedBlocks, pending } = parseResult
  if (!pending || !pending.raw || !pending.kind) {
    return committedBlocks
  }
  const finalBlock = {
    id: `b${committedBlocks.length}`,
    kind: pending.kind,
    raw: pending.raw,
    ...(pending.kind === 'code' && pending.lang ? { lang: pending.lang } : null),
    done: true,
  }
  return [...committedBlocks, finalBlock]
}

/**
 * Keep most pending text stable and animate only its short trailing window.
 * Re-keying this tail on each streamed update gives new text a restrained fade
 * without making the whole paragraph flash or jump.
 */
export function splitStreamingTail(raw, maxTailLength = 14) {
  const text = String(raw || '')
  const tailLength = Math.max(1, Number(maxTailLength) || 14)
  if (text.length <= tailLength) {
    return { stable: '', tail: text }
  }
  const splitAt = text.length - tailLength
  return {
    stable: text.slice(0, splitAt),
    tail: text.slice(splitAt),
  }
}
