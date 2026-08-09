<template>
  <div class="xzm-stream-table" v-html="html"></div>
</template>

<script setup>
import { computed } from 'vue'
import { renderMarkdown, escapeFallback } from '../../utils/markdownFormatter'

const props = defineProps({
  raw: { type: String, required: true },
  blockId: { type: String, default: '' },
})

const html = computed(() => {
  if (!props.raw) return ''
  try {
    return renderMarkdown(props.raw, 'paragraph') // 让 marked 处理 GFM 表格
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[stream-table] render failed', err)
    return escapeFallback(props.raw)
  }
})
</script>

<style scoped>
.xzm-stream-table {
  margin: 12px 0;
  max-width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: var(--xzm-radius-md);
  border: 1px solid var(--xzm-border-color);
}

.xzm-stream-table :deep(table) {
  width: 100%;
  margin: 0;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--xzm-fs-sm);
  background-color: var(--xzm-glass-bg-soft);
}

.xzm-stream-table :deep(thead) {
  background: var(--xzm-grad-soft);
}

.xzm-stream-table :deep(th) {
  padding: 12px 16px;
  font-weight: var(--xzm-fw-semibold);
  text-align: left;
  color: var(--xzm-text-primary);
  border-bottom: 1px solid var(--xzm-border-color-strong);
  white-space: nowrap;
}

.xzm-stream-table :deep(td) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--xzm-border-color);
  color: var(--xzm-text-secondary);
  vertical-align: top;
  line-height: 1.6;
}

.xzm-stream-table :deep(tbody tr:last-child td) {
  border-bottom: none;
}

.xzm-stream-table :deep(tbody tr:nth-child(even)) {
  background-color: var(--xzm-hover-bg);
}

.xzm-stream-table :deep(tbody tr:hover) {
  background-color: var(--xzm-hover-bg-strong);
}

.xzm-stream-table :deep(code) {
  font-family: var(--xzm-font-mono);
  font-size: 0.9em;
  padding: 2px 6px;
  background-color: var(--xzm-surface-2);
  border-radius: var(--xzm-radius-xs);
  color: var(--xzm-accent-pink);
}

@media (max-width: 640px) {
  .xzm-stream-table :deep(th),
  .xzm-stream-table :deep(td) {
    padding: 8px 10px;
    font-size: var(--xzm-fs-xs);
  }
}
</style>
