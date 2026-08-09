<template>
  <section
    class="xzm-thinking"
    :class="{ 'is-active': isThinking, 'is-collapsed': isCollapsed }"
    role="region"
    aria-label="AI 思考过程"
  >
    <button
      type="button"
      class="xzm-thinking__header"
      :aria-expanded="!isCollapsed"
      @click="toggle"
    >
      <span class="xzm-thinking__icon" :class="{ 'is-spin': isThinking }">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3.2 1.9" />
        </svg>
      </span>
      <span class="xzm-thinking__title">{{ titleText }}</span>
      <span v-if="elapsedSeconds > 0 && !isThinking" class="xzm-thinking__elapsed">
        已思考 {{ elapsedLabel }}
      </span>
      <span class="xzm-thinking__chevron" :class="{ 'is-rotated': !isCollapsed }" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5">
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </span>
    </button>

    <transition name="xzm-thinking-fold">
      <div v-show="!isCollapsed" class="xzm-thinking__body">
        <div ref="contentRef" class="xzm-thinking__content xzm-scroll">
          <!-- 流式中：直接挂载 stream -->
          <StreamingMarkdown
            v-if="stream"
            :stream="stream"
            variant="thinking"
          />
          <!-- 历史：用本地一次性 stream（loadCompleted） -->
          <StreamingMarkdown
            v-else-if="content"
            :stream="staticStream"
            variant="thinking"
          />
          <span v-else class="xzm-thinking__placeholder">正在准备思考…</span>
        </div>
      </div>
    </transition>
  </section>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import StreamingMarkdown from './streaming/StreamingMarkdown.vue'
import { useStreamingMarkdown } from '../composables/useStreamingMarkdown'

const props = defineProps({
  /**
   * 流式实例（来自 useChatStream.thinkingStream）
   * 与 content 二选一
   */
  stream: { type: Object, default: null },
  /**
   * 历史思考内容（已完成）
   */
  content: { type: String, default: '' },

  isThinking: { type: Boolean, default: false },
  defaultCollapsed: { type: Boolean, default: false },
  collapseTrigger: { type: Number, default: 0 },
  elapsedSeconds: { type: Number, default: 0 },
})

const isCollapsed = ref(props.defaultCollapsed)
const contentRef = ref(null)

const titleText = computed(() => (props.isThinking ? '思考中…' : '思考过程'))

const elapsedLabel = computed(() => {
  const s = Math.max(0, Math.floor(props.elapsedSeconds))
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m} 分 ${r} 秒`
})

// 静态内容（历史）→ 一次性灌入 stream
const staticStream = useStreamingMarkdown({ variant: 'thinking' })

watch(
  () => props.content,
  (val) => {
    if (val && !props.stream) {
      staticStream.loadCompleted(val)
    }
  },
  { immediate: true }
)

function toggle() {
  isCollapsed.value = !isCollapsed.value
}

// 自动滚动到底部
function scrollToBottom() {
  nextTick(() => {
    const el = contentRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(
  () => (props.stream ? props.stream.blocks?.value?.length : 0),
  () => { if (!isCollapsed.value) scrollToBottom() }
)

watch(
  () => props.collapseTrigger,
  (next, prev) => { if (next !== prev) isCollapsed.value = true }
)

watch(
  () => props.defaultCollapsed,
  (val) => { if (val) isCollapsed.value = true }
)

onMounted(() => {
  if (!isCollapsed.value) scrollToBottom()
})
</script>

<style scoped>
.xzm-thinking {
  margin: 8px 0 12px;
  border: 1px solid var(--xzm-border-color);
  background-color: var(--xzm-surface-1);
  border-radius: var(--xzm-radius-lg);
  overflow: hidden;
  transition:
    border-color var(--xzm-duration-fast) var(--xzm-ease-out),
    background-color var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-thinking.is-active {
  border-color: var(--xzm-border-color-strong);
}

.xzm-thinking__header {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  background: transparent;
  border: none;
  color: var(--xzm-text-secondary);
  cursor: pointer;
  font-size: var(--xzm-fs-sm);
  font-weight: var(--xzm-fw-medium);
  text-align: left;
  transition: background-color var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-thinking__header:hover { background-color: var(--xzm-hover-bg); }
.xzm-thinking__header:focus-visible {
  outline: 2px solid var(--xzm-focus-ring);
  outline-offset: -2px;
}

.xzm-thinking__icon {
  display: inline-flex;
  align-items: center;
  color: var(--xzm-text-tertiary);
}
.xzm-thinking__icon.is-spin {
  animation: xzm-think-spin 1.2s linear infinite;
}
@keyframes xzm-think-spin {
  to { transform: rotate(360deg); }
}

.xzm-thinking__title { flex-shrink: 0; }

.xzm-thinking__elapsed {
  font-size: var(--xzm-fs-xs);
  color: var(--xzm-text-tertiary);
  font-weight: var(--xzm-fw-regular);
}

.xzm-thinking__chevron {
  margin-left: auto;
  display: inline-flex;
  color: var(--xzm-text-tertiary);
  transition: transform var(--xzm-duration-normal) var(--xzm-ease-out);
}
.xzm-thinking__chevron.is-rotated { transform: rotate(0); }
.xzm-thinking__chevron:not(.is-rotated) { transform: rotate(-90deg); }

.xzm-thinking__body {
  border-top: 1px solid var(--xzm-border-color);
  background-color: var(--xzm-surface-1);
}

.xzm-thinking__content {
  padding: 12px 14px;
  max-height: 320px;
  overflow-y: auto;
  font-size: var(--xzm-fs-sm);
  line-height: var(--xzm-lh-relaxed);
  color: var(--xzm-text-secondary);
}

.xzm-thinking__placeholder {
  font-style: italic;
  color: var(--xzm-text-tertiary);
}

/* 折叠动效 */
.xzm-thinking-fold-enter-active,
.xzm-thinking-fold-leave-active {
  transition:
    max-height var(--xzm-duration-normal) var(--xzm-ease-out),
    opacity var(--xzm-duration-fast) var(--xzm-ease-out);
  overflow: hidden;
}
.xzm-thinking-fold-enter-from,
.xzm-thinking-fold-leave-to {
  max-height: 0;
  opacity: 0;
}
.xzm-thinking-fold-enter-to,
.xzm-thinking-fold-leave-from {
  max-height: 320px;
  opacity: 1;
}

@media (max-width: 640px) {
  .xzm-thinking__content { max-height: 240px; padding: 10px 12px; }
}

@media (prefers-reduced-motion: reduce) {
  .xzm-thinking__icon.is-spin { animation: none; }
}
</style>
