<template>
  <transition name="xzm-question-nav-slide">
    <aside
      v-if="visible"
      class="xzm-question-nav xzm-glass--strong"
      role="dialog"
      aria-label="题目导航"
    >
      <header class="xzm-question-nav__header">
        <span class="xzm-question-nav__title">题目导航</span>
        <button
          type="button"
          class="xzm-btn xzm-btn--ghost xzm-btn--icon xzm-btn--sm"
          aria-label="关闭"
          @click="handleClose"
        >
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="6" y1="6" x2="18" y2="18" />
            <line x1="6" y1="18" x2="18" y2="6" />
          </svg>
        </button>
      </header>

      <div class="xzm-question-nav__search">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.35-4.35" />
        </svg>
        <input
          v-model="keyword"
          type="text"
          class="xzm-question-nav__input"
          placeholder="搜索题目"
          aria-label="搜索题目"
        />
      </div>

      <div class="xzm-question-nav__list xzm-scroll">
        <div v-if="filtered.length === 0" class="xzm-question-nav__empty">
          <span>没有匹配的题目</span>
        </div>
        <button
          v-for="(q, i) in filtered"
          :key="q.id"
          type="button"
          class="xzm-question-nav__item"
          :class="{ 'is-active': q.id === currentId }"
          @click="handleSelect(q.id)"
        >
          <span class="xzm-question-nav__index">{{ q.originalIndex + 1 }}</span>
          <span class="xzm-question-nav__text">{{ q.content }}</span>
        </button>
      </div>
    </aside>
  </transition>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  questions: { type: Array, default: () => [] },
  currentId: { type: String, default: '' },
})

const emit = defineEmits(['update:visible', 'select'])

const keyword = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return props.questions
    .map((q, idx) => ({ ...q, originalIndex: idx }))
    .filter((q) => !kw || (q.content || '').toLowerCase().includes(kw))
})

function handleClose() {
  emit('update:visible', false)
}

function handleSelect(id) {
  emit('select', id)
  emit('update:visible', false)
}
</script>

<style scoped>
.xzm-question-nav {
  position: fixed;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  width: 320px;
  max-height: 70vh;
  border-radius: var(--xzm-radius-lg);
  z-index: var(--xzm-z-drawer);
  overflow: hidden;
}

.xzm-question-nav__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--xzm-border-color);
}

.xzm-question-nav__title {
  font-size: var(--xzm-fs-sm);
  font-weight: var(--xzm-fw-semibold);
  color: var(--xzm-text-primary);
}

.xzm-question-nav__search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 12px;
  padding: 0 12px;
  height: 36px;
  border: 1px solid var(--xzm-border-color);
  border-radius: var(--xzm-radius-full);
  background-color: var(--xzm-surface-2);
  color: var(--xzm-text-tertiary);
  transition: border-color var(--xzm-duration-fast) var(--xzm-ease-out);
}
.xzm-question-nav__search:focus-within {
  border-color: var(--xzm-brand-400);
  background-color: var(--xzm-surface-1);
}

.xzm-question-nav__input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  color: var(--xzm-text-primary);
  font-family: inherit;
  font-size: var(--xzm-fs-sm);
  min-width: 0;
}
.xzm-question-nav__input::placeholder { color: var(--xzm-text-muted); }

.xzm-question-nav__list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 12px;
}

.xzm-question-nav__empty {
  padding: 20px;
  text-align: center;
  color: var(--xzm-text-tertiary);
  font-size: var(--xzm-fs-xs);
}

.xzm-question-nav__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 10px 10px;
  border: 1px solid transparent;
  border-radius: var(--xzm-radius-md);
  background-color: transparent;
  color: var(--xzm-text-primary);
  font-family: inherit;
  font-size: var(--xzm-fs-sm);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--xzm-duration-fast) var(--xzm-ease-out),
    border-color var(--xzm-duration-fast) var(--xzm-ease-out);
}
.xzm-question-nav__item:hover {
  background-color: var(--xzm-hover-bg);
  border-color: var(--xzm-border-color);
}
.xzm-question-nav__item.is-active {
  background-color: var(--xzm-thinking-bg-strong);
  border-color: var(--xzm-thinking-border);
}

.xzm-question-nav__index {
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

.xzm-question-nav__text {
  flex: 1;
  line-height: 1.5;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 动效 */
.xzm-question-nav-slide-enter-active,
.xzm-question-nav-slide-leave-active {
  transition:
    opacity var(--xzm-duration-normal) var(--xzm-ease-out),
    transform var(--xzm-duration-normal) var(--xzm-ease-out);
}
.xzm-question-nav-slide-enter-from,
.xzm-question-nav-slide-leave-to {
  opacity: 0;
  transform: translateY(-50%) translateX(16px);
}

@media (max-width: 768px) {
  .xzm-question-nav {
    right: 0;
    top: var(--xzm-top-bar-height);
    transform: none;
    width: 80%;
    max-width: 320px;
    max-height: calc(100vh - var(--xzm-top-bar-height) - 16px);
    border-radius: var(--xzm-radius-lg) 0 0 var(--xzm-radius-lg);
  }
  .xzm-question-nav-slide-enter-from,
  .xzm-question-nav-slide-leave-to {
    transform: translateX(100%);
  }
}
</style>
