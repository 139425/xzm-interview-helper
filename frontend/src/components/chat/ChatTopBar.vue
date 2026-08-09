<template>
  <header class="xzm-chat-topbar xzm-glass">
    <!-- 左：Sidebar toggle（移动端）/ 桌面端为空槽 -->
    <div class="xzm-chat-topbar__left">
      <button
        v-if="showSidebarToggle"
        type="button"
        class="xzm-btn xzm-btn--ghost xzm-btn--icon"
        :aria-label="sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
        @click="$emit('toggle-sidebar')"
      >
        <svg
          v-if="sidebarExpanded"
          viewBox="0 0 24 24" width="18" height="18"
          fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
        >
          <line x1="6" y1="6" x2="18" y2="18" />
          <line x1="6" y1="18" x2="18" y2="6" />
        </svg>
        <svg
          v-else
          viewBox="0 0 24 24" width="18" height="18"
          fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
        >
          <line x1="3" y1="6" x2="21" y2="6" />
          <line x1="3" y1="12" x2="21" y2="12" />
          <line x1="3" y1="18" x2="21" y2="18" />
        </svg>
      </button>
    </div>

    <!-- 中：题目下拉 -->
    <div class="xzm-chat-topbar__center">
      <el-popover
        v-model:visible="popoverVisible"
        placement="bottom"
        :width="popoverWidth"
        trigger="click"
        popper-class="xzm-question-popover"
        :disabled="!hasQuestions"
      >
        <template #reference>
          <button
            type="button"
            class="xzm-question-btn"
            :class="{ 'is-empty': !hasQuestions }"
            :disabled="!hasQuestions"
            @click.stop
          >
            <span class="xzm-question-btn__text">{{ currentLabel }}</span>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </button>
        </template>
        <div class="xzm-question-popover-content xzm-scroll">
          <div v-if="!hasQuestions" class="xzm-question-popover__empty">
            暂无题目，发送第一条消息开始
          </div>
          <button
            v-for="(q, index) in questions"
            :key="q.id"
            type="button"
            class="xzm-question-popover__item"
            :class="{ 'is-active': index === activeIndex }"
            @click.stop="handleSelect(q.id)"
          >
            <span class="xzm-question-popover__index">{{ index + 1 }}</span>
            <span class="xzm-question-popover__text">{{ truncate(q.content) }}</span>
          </button>
        </div>
      </el-popover>
    </div>

    <!-- 右：Tools + UserAvatar（主题切换已并入 UserAvatar 菜单） -->
    <div class="xzm-chat-topbar__right">
      <slot name="tools" />
      <slot name="user" />
    </div>
  </header>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElPopover } from 'element-plus'

const props = defineProps({
  questions: { type: Array, default: () => [] },
  activeIndex: { type: Number, default: -1 },
  sidebarExpanded: { type: Boolean, default: true },
  showSidebarToggle: { type: Boolean, default: false },
  popoverWidth: { type: Number, default: 440 },
})

const emit = defineEmits(['toggle-sidebar', 'select-question'])

const popoverVisible = ref(false)

const hasQuestions = computed(() => props.questions.length > 0)

const currentLabel = computed(() => {
  if (!hasQuestions.value) return '暂无题目'
  const idx = props.activeIndex >= 0 ? props.activeIndex : props.questions.length - 1
  const q = props.questions[idx]
  return q ? truncate(q.content, 60) : '暂无题目'
})

function truncate(text, max = 50) {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function handleSelect(id) {
  popoverVisible.value = false
  emit('select-question', id)
}
</script>

<style scoped>
.xzm-chat-topbar {
  position: sticky;
  top: 0;
  z-index: var(--xzm-z-sticky);
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(0, 2fr) minmax(120px, 1fr);
  align-items: center;
  height: var(--xzm-top-bar-height);
  margin: 12px;
  padding: 0 16px;
  border-radius: var(--xzm-radius-xl);
}

.xzm-chat-topbar__left {
  display: flex; align-items: center; gap: 12px;
  min-width: 0;
}

.xzm-chat-topbar__brand {
  font-family: var(--xzm-font-sans);
  font-size: var(--xzm-fs-md);
  font-weight: var(--xzm-fw-semibold);
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.xzm-chat-topbar__center {
  display: flex; justify-content: center; align-items: center;
  min-width: 0;
}

.xzm-chat-topbar__right {
  display: flex; align-items: center; gap: 8px;
  justify-content: flex-end;
  min-width: 0;
}

/* 题目按钮 */
.xzm-question-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 520px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--xzm-border-color);
  border-radius: var(--xzm-radius-full);
  background-color: var(--xzm-hover-bg);
  color: var(--xzm-text-secondary);
  font-family: inherit;
  font-size: var(--xzm-fs-xs);
  cursor: pointer;
  transition:
    background-color var(--xzm-duration-fast) var(--xzm-ease-out),
    border-color var(--xzm-duration-fast) var(--xzm-ease-out),
    color var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-question-btn:hover:not(:disabled) {
  background-color: var(--xzm-hover-bg-strong);
  border-color: var(--xzm-border-color-hover);
  color: var(--xzm-text-primary);
}

.xzm-question-btn.is-empty,
.xzm-question-btn:disabled {
  cursor: not-allowed;
  color: var(--xzm-text-muted);
}

.xzm-question-btn__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}

.xzm-question-popover-content {
  max-height: 360px;
  overflow-y: auto;
  padding: 4px;
}

.xzm-question-popover__empty {
  padding: 16px;
  text-align: center;
  color: var(--xzm-text-tertiary);
  font-size: var(--xzm-fs-xs);
}

.xzm-question-popover__item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: var(--xzm-radius-md);
  background-color: transparent;
  color: var(--xzm-text-primary);
  font-family: inherit;
  font-size: var(--xzm-fs-sm);
  text-align: left;
  cursor: pointer;
  transition: background-color var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-question-popover__item:hover {
  background-color: var(--xzm-hover-bg);
}

.xzm-question-popover__item.is-active {
  background-color: var(--xzm-hover-bg-strong);
}

.xzm-question-popover__index {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px; height: 22px;
  border-radius: 9999px;
  background: var(--xzm-grad-primary);
  color: var(--xzm-text-on-brand);
  font-size: var(--xzm-fs-xs);
  font-weight: var(--xzm-fw-semibold);
  line-height: 1;
}

.xzm-question-popover__text {
  flex: 1;
  line-height: 1.5;
  word-break: break-word;
}

@media (max-width: 768px) {
  .xzm-chat-topbar {
    grid-template-columns: auto 1fr auto;
    margin: 8px;
    padding: 0 10px;
  }
  .xzm-chat-topbar__brand { display: none; }
}
</style>
