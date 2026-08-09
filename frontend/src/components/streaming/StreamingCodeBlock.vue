<template>
  <div class="xzm-code-block" :class="{ 'is-streaming': isStreaming }">
    <div class="xzm-code-block__header">
      <span class="xzm-code-block__lang">
        <span class="xzm-code-block__dot" :style="{ background: dotColor }"></span>
        {{ displayLang }}
      </span>
      <button
        v-if="!isStreaming"
        type="button"
        class="xzm-code-block__copy"
        :class="{ 'is-copied': copied }"
        :aria-label="copied ? '已复制' : '复制代码'"
        @click="handleCopy"
      >
        <svg
          v-if="!copied"
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
        <svg
          v-else
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <polyline points="20 6 9 17 4 12" />
        </svg>
        <span>{{ copied ? '已复制' : '复制' }}</span>
      </button>
      <span v-else class="xzm-code-block__streaming-tag">
        <span class="xzm-code-block__streaming-dot"></span>
        生成中
      </span>
    </div>

    <pre class="xzm-code-block__pre"><code
      v-if="isStreaming"
      class="xzm-code-block__code"
      v-text="code"
    ></code><code
      v-else
      class="xzm-code-block__code hljs"
      v-html="highlightedHtml"
    ></code></pre>
  </div>
</template>

<script setup>
import { ref, computed, shallowRef, watch } from 'vue'
// 使用 lib/common：内置 ~30 种常见语言（js/ts/python/java/go/rust/cpp/sql/json/html/css/bash...），无需动态注册
import hljs from 'highlight.js/lib/common'
import { decodeHtmlEntities, escapeHtml, sanitizeHtml } from '../../utils/markdownFormatter'

// ============== 语言别名 ==============
const LANG_ALIASES = {
  js: 'javascript',
  jsx: 'javascript',
  ts: 'typescript',
  tsx: 'typescript',
  py: 'python',
  py3: 'python',
  rb: 'ruby',
  sh: 'bash',
  shell: 'bash',
  zsh: 'bash',
  yml: 'yaml',
  md: 'markdown',
  rs: 'rust',
  kt: 'kotlin',
  cs: 'csharp',
  'c#': 'csharp',
  'c++': 'cpp',
  cc: 'cpp',
  cxx: 'cpp',
  hpp: 'cpp',
  h: 'c',
  pl: 'perl',
  dockerfile: 'docker',
  plaintext: 'plaintext',
  plain: 'plaintext',
  txt: 'plaintext',
  text: 'plaintext',
}

const DISPLAY_NAMES = {
  javascript: 'JavaScript',
  typescript: 'TypeScript',
  python: 'Python',
  java: 'Java',
  go: 'Go',
  rust: 'Rust',
  ruby: 'Ruby',
  php: 'PHP',
  swift: 'Swift',
  kotlin: 'Kotlin',
  scala: 'Scala',
  cpp: 'C++',
  c: 'C',
  csharp: 'C#',
  sql: 'SQL',
  html: 'HTML',
  css: 'CSS',
  scss: 'SCSS',
  less: 'Less',
  json: 'JSON',
  xml: 'XML',
  yaml: 'YAML',
  markdown: 'Markdown',
  bash: 'Bash',
  powershell: 'PowerShell',
  docker: 'Dockerfile',
  nginx: 'Nginx',
  perl: 'Perl',
  lua: 'Lua',
  r: 'R',
  plaintext: 'Plain Text',
}

// 颜色映射（语言徽标的 dot 颜色）
const LANG_COLORS = {
  javascript: '#F7DF1E',
  typescript: '#3178C6',
  python: '#3776AB',
  java: '#B07219',
  go: '#00ADD8',
  rust: '#DEA584',
  ruby: '#CC342D',
  php: '#777BB4',
  swift: '#FA7343',
  kotlin: '#7F52FF',
  cpp: '#F34B7D',
  c: '#555555',
  csharp: '#239120',
  sql: '#E38C00',
  html: '#E34F26',
  css: '#1572B6',
  json: '#999999',
  yaml: '#CB171E',
  markdown: '#083FA1',
  bash: '#4EAA25',
}

// hljs 已预注册的语言（lib/common 自带，下面 Set 仅作为缓存命中判断）
const langCache = shallowRef(new Map()) // { blockId-lang: highlightedHtml }

const props = defineProps({
  lang: { type: String, default: '' },
  code: { type: String, required: true },
  isStreaming: { type: Boolean, default: false },
  blockId: { type: String, default: '' },
})

const emit = defineEmits(['copied'])

const copied = ref(false)
const highlightedHtml = ref('')

const resolvedLang = computed(() => {
  const raw = (props.lang || '').toLowerCase().trim()
  return LANG_ALIASES[raw] || raw || 'plaintext'
})

const displayLang = computed(() => {
  return DISPLAY_NAMES[resolvedLang.value] || resolvedLang.value.toUpperCase() || 'CODE'
})

const dotColor = computed(() => {
  return LANG_COLORS[resolvedLang.value] || '#94A3B8'
})

// ============== 高亮 ==============
function highlight() {
  if (props.isStreaming) return // 流式中不高亮

  const cacheKey = `${props.blockId}::${resolvedLang.value}::${props.code.length}`
  const cached = langCache.value.get(cacheKey)
  if (cached) {
    highlightedHtml.value = cached
    return
  }

  const sourceCode = decodeHtmlEntities(props.code)

  try {
    const lang = resolvedLang.value
    let resultHtml
    if (lang !== 'plaintext' && hljs.getLanguage(lang)) {
      resultHtml = hljs.highlight(sourceCode, {
        language: lang,
        ignoreIllegals: true,
      }).value
    } else if (lang === 'plaintext') {
      resultHtml = escapeHtml(sourceCode)
    } else {
      // 未识别 → autodetect 一次
      resultHtml = hljs.highlightAuto(sourceCode).value
    }

    // 防御性消毒：hljs 输出本已转义，此处再经统一消毒出口，杜绝任何残留 XSS 向量
    const safeHtml = sanitizeHtml(resultHtml)
    highlightedHtml.value = safeHtml
    langCache.value.set(cacheKey, safeHtml)
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[code-block] highlight failed', err)
    highlightedHtml.value = escapeHtml(sourceCode)
  }
}

// 状态变化时重新高亮
watch(
  () => [props.code, props.isStreaming, resolvedLang.value],
  () => {
    if (!props.isStreaming) highlight()
  },
  { immediate: true }
)

// ============== 复制 ==============
async function handleCopy() {
  const text = props.code || ''
  let ok = false
  try {
    if (navigator?.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ok = true
    } else {
      ok = fallbackCopy(text)
    }
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[code-block] copy failed', err)
    ok = fallbackCopy(text)
  }

  if (ok) {
    copied.value = true
    emit('copied', text)
    setTimeout(() => { copied.value = false }, 1800)
  }
}

function fallbackCopy(text) {
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', 'readonly')
    ta.style.position = 'fixed'
    ta.style.left = '-99999px'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}
</script>

<style scoped>
.xzm-code-block {
  position: relative;
  margin: var(--xzm-space-4) 0;
  border-radius: var(--xzm-radius-md);
  overflow: hidden;
  background-color: var(--xzm-surface-1);
  border: 1px solid var(--xzm-border-color);
  box-shadow: none;
  max-width: 100%;
  box-sizing: border-box;
}

.xzm-code-block.is-streaming {
  border-color: var(--xzm-border-color-strong);
}

/* Header */
.xzm-code-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background-color: var(--xzm-surface-2);
  border-bottom: 1px solid var(--xzm-border-color);
  font-size: var(--xzm-fs-xs);
  font-family: var(--xzm-font-sans);
}

.xzm-code-block__lang {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--xzm-text-tertiary);
  font-weight: var(--xzm-fw-medium);
  letter-spacing: 0.02em;
}

.xzm-code-block__dot {
  width: 8px;
  height: 8px;
  border-radius: 9999px;
  display: inline-block;
}

/* Copy button —— ghost icon */
.xzm-code-block__copy {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background-color: transparent;
  border: 1px solid transparent;
  border-radius: var(--xzm-radius-sm);
  color: var(--xzm-text-tertiary);
  font-size: var(--xzm-fs-xs);
  font-family: inherit;
  cursor: pointer;
  transition:
    background-color var(--xzm-duration-fast) var(--xzm-ease-out),
    color var(--xzm-duration-fast) var(--xzm-ease-out),
    border-color var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-code-block__copy:hover {
  background-color: var(--xzm-hover-bg);
  border-color: var(--xzm-border-color);
  color: var(--xzm-text-primary);
}

.xzm-code-block__copy:focus-visible {
  outline: 2px solid var(--xzm-focus-ring);
  outline-offset: 2px;
}

.xzm-code-block__copy.is-copied {
  background-color: transparent;
  border-color: transparent;
  color: var(--xzm-success);
}

.xzm-code-block__streaming-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--xzm-fs-xs);
  color: var(--xzm-text-tertiary);
}

.xzm-code-block__streaming-dot {
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  background-color: var(--xzm-text-tertiary);
  animation: xzm-codeblock-pulse 1.6s ease-in-out infinite;
}
@keyframes xzm-codeblock-pulse {
  0%, 100% { opacity: 0.3; }
  50%      { opacity: 1; }
}

/* Code body */
.xzm-code-block__pre {
  margin: 0;
  padding: 16px;
  background-color: var(--xzm-surface-1);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  max-width: 100%;
}

.xzm-code-block__code {
  display: block;
  background: transparent;
  font-family: var(--xzm-font-mono);
  font-size: 13px;
  line-height: 1.65;
  color: var(--xzm-text-primary);
  white-space: pre;
  tab-size: 4;
}

/* hljs 配色（极简：单色基调 + 浅蓝/绿/灰强调） */
.xzm-code-block :deep(.hljs-keyword),
.xzm-code-block :deep(.hljs-selector-tag),
.xzm-code-block :deep(.hljs-section),
.xzm-code-block :deep(.hljs-link) {
  color: var(--xzm-brand);
  font-weight: var(--xzm-fw-medium);
}
.xzm-code-block :deep(.hljs-string),
.xzm-code-block :deep(.hljs-attr) {
  color: #047857;
}
.xzm-code-block :deep(.hljs-number),
.xzm-code-block :deep(.hljs-built_in),
.xzm-code-block :deep(.hljs-literal) {
  color: #B45309;
}
.xzm-code-block :deep(.hljs-comment),
.xzm-code-block :deep(.hljs-quote) {
  color: var(--xzm-text-tertiary);
  font-style: italic;
}
.xzm-code-block :deep(.hljs-type),
.xzm-code-block :deep(.hljs-class .hljs-title),
.xzm-code-block :deep(.hljs-title.class_) {
  color: #C2410C;
}
.xzm-code-block :deep(.hljs-function .hljs-title),
.xzm-code-block :deep(.hljs-title.function_) {
  color: #1D4ED8;
}
.xzm-code-block :deep(.hljs-variable),
.xzm-code-block :deep(.hljs-template-variable) {
  color: var(--xzm-text-primary);
}

/* 暗色主题适配 */
[data-theme="dark"] .xzm-code-block :deep(.hljs-keyword) { color: #818CF8; }
[data-theme="dark"] .xzm-code-block :deep(.hljs-string) { color: #6EE7B7; }
[data-theme="dark"] .xzm-code-block :deep(.hljs-number) { color: #FCD34D; }
[data-theme="dark"] .xzm-code-block :deep(.hljs-comment) { color: var(--xzm-text-tertiary); }
[data-theme="dark"] .xzm-code-block :deep(.hljs-type),
[data-theme="dark"] .xzm-code-block :deep(.hljs-title.class_) { color: #FCA5A5; }
[data-theme="dark"] .xzm-code-block :deep(.hljs-title.function_) { color: #93C5FD; }

/* 移动端 */
@media (max-width: 640px) {
  .xzm-code-block__pre { padding: 12px; }
  .xzm-code-block__code { font-size: 12px; }
  .xzm-code-block__header { padding: 6px 10px; }
  .xzm-code-block__copy span { display: none; }
}
</style>
