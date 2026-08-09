/**
 * markdownFormatter — 单 block 渲染工具
 *
 * 重构后语义：仅负责"已闭合的单个 block raw"→ 安全 HTML 的转换；
 * 流式增量编排已转移到 useStreamingMarkdown。
 *
 * 历史版本中的 fixMarkdownFormat 强行补 ```、全局 window.copyCodeBlock、
 * 内联 onclick="..." 等做法均已删除。
 */

import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { normalizeModelMarkdown } from './streamBuffer'

// marked v17 renderer API：只覆盖原始 HTML token，Markdown 自身生成的标签仍走默认 renderer。
// 模型输出的 <tag> 会以可见的转义文本呈现，而不是先被解释为受信 DOM。
const markdownRenderer = new marked.Renderer()
markdownRenderer.html = ({ text }) => escapeHtml(text)

marked.setOptions({
  breaks: true,
  gfm: true,
  renderer: markdownRenderer,
})

// 关闭 marked 默认对代码块的高亮，由 StreamingCodeBlock 单独处理
// （marked v17 不再有内建 highlight 选项，这里不调用即可）

/**
 * DOMPurify 消毒白名单配置（XSS 统一出口）
 * - 允许常规 Markdown 元素 + 表格标签 + 代码块 + a/img/blockquote
 * - URI 仅允许 http/https/mailto（以及相对路径、锚点），阻断 javascript:/data: 等
 * - 显式禁止 script/iframe 等危险标签；on* 事件属性由 DOMPurify 默认剥离，此处再加固
 */
const SANITIZE_CONFIG = {
  ALLOWED_TAGS: [
    'p', 'br', 'hr', 'span', 'div',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'strong', 'em', 'b', 'i', 'del', 's', 'sub', 'sup', 'mark', 'u', 'small',
    'ul', 'ol', 'li',
    'blockquote',
    'a', 'img',
    'pre', 'code', 'kbd', 'samp', 'var',
    'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption', 'colgroup', 'col',
  ],
  ALLOWED_ATTR: [
    'href', 'title', 'target', 'rel',
    'src', 'alt', 'width', 'height',
    'align', 'class', 'colspan', 'rowspan', 'start', 'type',
  ],
  // 仅放行 http/https/mailto，以及站内相对路径与锚点
  ALLOWED_URI_REGEXP: /^(?:https?:\/\/|mailto:|\/|#|\.\/|\.\.\/)/i,
  FORBID_TAGS: ['script', 'iframe', 'style', 'form', 'input', 'button', 'object', 'embed', 'link', 'meta'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'srcset'],
}

/**
 * 统一消毒出口：任意 HTML → 已消毒 HTML。
 * 若 DOMPurify 不可用或抛异常，降级为转义纯文本，绝不返回未消毒 HTML。
 */
export function sanitizeHtml(html) {
  if (!html) return ''
  try {
    return DOMPurify.sanitize(html, SANITIZE_CONFIG)
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[markdown] sanitize failed, fallback to escaped text', err)
    return escapeFallback(html)
  }
}

const HTML_ENTITIES = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#039;': "'",
  '&#x27;': "'",
  '&nbsp;': ' ',
}

/**
 * 解码 HTML 实体（marked 渲染代码块时会把内容 escape，需要还原成原字符串再交给 hljs）
 */
export function decodeHtmlEntities(text) {
  if (!text) return ''
  if (typeof document !== 'undefined') {
    const ta = document.createElement('textarea')
    ta.innerHTML = text
    return ta.value
  }
  return text.replace(/&amp;|&lt;|&gt;|&quot;|&#039;|&#x27;|&nbsp;/g, (m) => HTML_ENTITIES[m] || m)
}

/**
 * 转义 HTML 字符 —— 失败回退时使用
 */
export function escapeHtml(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/**
 * 失败回退：把任意 raw 转成 <pre> 转义文本
 */
export function escapeFallback(raw) {
  return `<pre class="xzm-fallback-pre">${escapeHtml(raw || '')}</pre>`
}

/**
 * 把"段落级"raw 渲染为 inline HTML（不包 <p>）
 */
export function renderInline(raw) {
  if (!raw) return ''
  try {
    return sanitizeHtml(marked.parseInline(raw))
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[markdown] parseInline failed', err)
    return escapeFallback(raw)
  }
}

/**
 * 把"已闭合的单 block raw"渲染为完整 HTML
 *
 * @param {string} raw
 * @param {string} kind — 'paragraph'|'list'|'heading'|'quote'|'hr'|'html'|'table'
 * @returns {string}
 */
export function renderMarkdown(raw, kind = 'paragraph') {
  if (!raw) return ''
  try {
    // code 与 table 在视图层用专用组件，不走这里；保险起见兜底处理
    if (kind === 'code') {
      return `<pre class="xzm-fallback-pre">${escapeHtml(raw)}</pre>`
    }
    return sanitizeHtml(marked.parse(normalizeModelMarkdown(raw)))
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[markdown] parse failed', err, raw.slice(0, 80))
    return escapeFallback(raw)
  }
}

// Backward-compatible name for existing block renderers. New v-html call sites should use the
// explicit renderMarkdown export so the sanitised rendering boundary is obvious in review.
export function renderBlock(raw, kind = 'paragraph') {
  return renderMarkdown(raw, kind)
}

export default {
  renderMarkdown,
  renderInline,
  renderBlock,
  sanitizeHtml,
  escapeFallback,
  decodeHtmlEntities,
  escapeHtml,
}
