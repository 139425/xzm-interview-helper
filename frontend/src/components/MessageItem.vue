<template>
  <article
    class="xzm-msg"
    :class="[`xzm-msg--${role}`, { 'is-streaming': isStreaming }]"
  >
    <!-- 用户消息：浅灰圆角矩形 -->
    <template v-if="role === 'user'">
      <div class="xzm-msg__user-bubble">
        <p class="xzm-msg__user-text">{{ content }}</p>
      </div>
    </template>

    <!-- 助手消息：纯文本（无头像） + 思考框 + 流式 markdown -->
    <template v-else>
      <header class="xzm-msg__role">
        <span class="xzm-msg__role-name">Assistant</span>
      </header>

      <ChatProcessTimeline
        v-if="pipelineStages.length"
        :stages="pipelineStages"
      />

      <ThinkingBox
        v-if="showThinkingBox"
        :stream="thinkingStream"
        :content="thinkingContent"
        :is-thinking="thinkingActive"
        :default-collapsed="thinkingDefaultCollapsed"
        :collapse-trigger="thinkingCollapseTrigger"
      />

      <div v-if="hasMainContent" class="xzm-msg__content">
        <StreamingMarkdown
          v-if="contentStream"
          :stream="contentStream"
          variant="content"
        />
        <StreamingMarkdown
          v-else-if="content"
          :stream="staticStream"
          variant="content"
        />
      </div>

      <!-- 操作按钮：完成态、hover 才显、仅 icon -->
      <footer v-if="!isStreaming && content" class="xzm-msg__actions" aria-label="消息操作">
        <button
          type="button"
          class="xzm-msg__action"
          aria-label="复制回复"
          title="复制"
          @click="copyMessage"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
          </svg>
        </button>
        <button
          type="button"
          class="xzm-msg__action"
          aria-label="在 Markdown 编辑器中打开"
          title="转 Markdown"
          @click="openMarkdown"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </button>
        <button
          type="button"
          class="xzm-msg__action"
          aria-label="重新生成回复"
          title="重新生成"
          @click="regenerate"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="23 4 23 10 17 10"/>
            <polyline points="1 20 1 14 7 14"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
        </button>
      </footer>
    </template>
  </article>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ThinkingBox from './ThinkingBox.vue'
import ChatProcessTimeline from './chat/ChatProcessTimeline.vue'
import StreamingMarkdown from './streaming/StreamingMarkdown.vue'
import { useStreamingMarkdown } from '../composables/useStreamingMarkdown'

const props = defineProps({
  role: {
    type: String,
    required: true,
    validator: (v) => ['user', 'assistant'].includes(v),
  },
  content: { type: String, default: '' },
  thinkingContent: { type: String, default: '' },
  contentStream: { type: Object, default: null },
  thinkingStream: { type: Object, default: null },
  isStreaming: { type: Boolean, default: false },
  isThinking: { type: Boolean, default: false },
  questionTitle: { type: String, default: '' },
  pipelineStages: { type: Array, default: () => [] },
})

const emit = defineEmits(['copy', 'open-md-editor', 'regenerate'])

const staticStream = props.role === 'assistant' ? useStreamingMarkdown({ variant: 'content' }) : null

watch(
  () => props.content,
  (val) => {
    if (staticStream && val && !props.contentStream) {
      staticStream.loadCompleted(val)
    }
  },
  { immediate: true }
)

const hasMainContent = computed(() => {
  if (props.contentStream) {
    const blocks = props.contentStream.blocks?.value || []
    return blocks.length > 0
  }
  return Boolean(props.content)
})

const showThinkingBox = computed(() => {
  if (props.thinkingStream) {
    const blocks = props.thinkingStream.blocks?.value || []
    return blocks.length > 0 || props.isThinking
  }
  return Boolean(props.thinkingContent)
})

const thinkingActive = computed(() => props.isStreaming && props.isThinking)

const thinkingDefaultCollapsed = computed(() => {
  if (props.isStreaming) return hasMainContent.value
  return true
})

const thinkingCollapseTrigger = ref(0)
let hasAutoCollapsed = false

watch(hasMainContent, (val) => {
  if (props.isStreaming && val && !hasAutoCollapsed && showThinkingBox.value) {
    hasAutoCollapsed = true
    thinkingCollapseTrigger.value += 1
  }
})

watch(
  () => props.isStreaming,
  (val) => { if (!val) hasAutoCollapsed = false }
)

async function copyMessage() {
  const text = props.contentStream?.getRaw?.() || props.content || ''
  if (!text) return
  try {
    if (navigator?.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.setAttribute('readonly', 'readonly')
      ta.style.position = 'fixed'
      ta.style.left = '-99999px'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('已复制')
    emit('copy', text)
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[message-item] copy failed', err)
    ElMessage.error('复制失败，请手动选择')
  }
}

function openMarkdown() {
  const text = props.contentStream?.getRaw?.() || props.content || ''
  if (!text) return
  const title = props.questionTitle || ''
  localStorage.setItem('markdown-editor-content', text)
  localStorage.setItem('markdown-editor-title', title)
  emit('open-md-editor', text)
  window.open('/markdown-editor.html', '_blank', 'noopener')
}

function regenerate() {
  emit('regenerate')
}
</script>

<style scoped>
.xzm-msg {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 100%;
  margin-bottom: var(--xzm-space-8);
  width: 100%;
  animation: xzm-msg-in 180ms var(--xzm-ease-out) both;
}

@keyframes xzm-msg-in {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.xzm-msg--user { align-items: flex-end; }
.xzm-msg--assistant { align-items: stretch; }

/* ============== 用户气泡 ============== */
.xzm-msg__user-bubble {
  max-width: min(80%, 720px);
  padding: 12px 16px;
  background-color: var(--xzm-surface-2);
  border: 1px solid transparent;
  border-radius: var(--xzm-radius-lg);
  color: var(--xzm-text-primary);
}

.xzm-msg__user-text {
  margin: 0;
  font-size: var(--xzm-fs-base);
  line-height: var(--xzm-lh-relaxed);
  white-space: pre-wrap;
  word-break: break-word;
}

/* ============== 助手消息 ============== */
.xzm-msg__role {
  display: inline-flex;
  align-items: center;
  padding: 0;
  font-size: var(--xzm-fs-xs);
  color: var(--xzm-text-tertiary);
  font-weight: var(--xzm-fw-medium);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.xzm-msg__role-name { font-feature-settings: 'tnum' 1; }

.xzm-msg__content {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

/* ============== 操作条（hover-only） ============== */
.xzm-msg__actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 2px;
  padding: 0;
  align-self: flex-start;
  opacity: 0;
  transform: translateY(-1px);
  transition:
    opacity var(--xzm-duration-fast) var(--xzm-ease-out),
    transform var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-msg:hover .xzm-msg__actions,
.xzm-msg:focus-within .xzm-msg__actions {
  opacity: 1;
  transform: translateY(0);
}

.xzm-msg__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: var(--xzm-radius-sm);
  background-color: transparent;
  color: var(--xzm-text-tertiary);
  cursor: pointer;
  transition:
    color var(--xzm-duration-fast) var(--xzm-ease-out),
    background-color var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-msg__action:hover {
  color: var(--xzm-text-primary);
  background-color: var(--xzm-hover-bg);
}

.xzm-msg__action:focus-visible {
  outline: 2px solid var(--xzm-focus-ring);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .xzm-msg { margin-bottom: var(--xzm-space-6); }
  .xzm-msg__user-bubble { max-width: 88%; padding: 10px 14px; }
  .xzm-msg__user-text { font-size: var(--xzm-fs-sm); }
  /* Touch devices: always show actions with 44px targets */
  .xzm-msg__actions { opacity: 1; transform: none; }
  .xzm-msg__action { width: 44px; height: 44px; }
}

/* Touch devices regardless of width */
@media (hover: none) and (pointer: coarse) {
  .xzm-msg__actions { opacity: 1; transform: none; }
  .xzm-msg__action { width: 44px; height: 44px; }
}
</style>
