<template>
  <div class="xzm-stream-md" :class="`xzm-stream-md--${variant}`">
    <TransitionGroup name="stream-block" tag="div" class="xzm-stream-md__blocks">
    <div v-for="block in blocks" :key="block.id" class="xzm-stream-md__block">
      <MermaidDiagram
        v-if="block.kind === 'code' && block.done && String(block.lang).toLowerCase() === 'mermaid'"
        :code="block.raw"
        :block-id="block.id"
      />
      <!-- 代码块（done & pending 都用这个组件） -->
      <StreamingCodeBlock
        v-else-if="block.kind === 'code'"
        :lang="block.lang || ''"
        :code="block.raw"
        :is-streaming="!block.done"
        :block-id="block.id"
      />

      <!-- 表格：done 才渲染；pending 表格显示骨架 -->
      <StreamingTable
        v-else-if="block.kind === 'table' && block.done"
        :raw="block.raw"
        :block-id="block.id"
      />
      <div
        v-else-if="block.kind === 'table' && !block.done"
        class="xzm-stream-md__pending xzm-stream-md__table-pending"
      >
        <span class="xzm-stream-md__pending-label">表格构建中…</span>
        <pre class="xzm-stream-md__pending-pre">{{ block.raw }}</pre>
        <StreamingCursor :variant="variant" />
      </div>

      <!-- 进行中段落：纯文本 + 光标 -->
      <p
        v-else-if="!block.done && (block.kind === 'paragraph' || block.kind === 'list' || block.kind === 'quote' || block.kind === 'heading')"
        class="xzm-stream-md__pending xzm-stream-md__pending-text"
      ><span class="xzm-stream-md__pending-text-inner" v-text="streamingTail(block.raw).stable"></span><span
        :key="`tail-${block.id}-${block.raw.length}`"
        class="xzm-stream-md__pending-tail"
        v-text="streamingTail(block.raw).tail"
      ></span><StreamingCursor :variant="variant" /></p>

      <!-- 已完成段落 / 列表 / 引用 / 标题 / 水平线 / html → marked 单 block 渲染 -->
      <div
        v-else-if="block.done"
        class="xzm-stream-md__rendered"
        v-memo="[block.raw, block.kind]"
        v-html="renderDoneBlock(block)"
      />
    </div>
    </TransitionGroup>

    <!-- 完全空内容时仍要显示一个光标占位（流刚开始） -->
    <span
      v-if="blocks.length === 0 && stream.isFinalized.value === false"
      class="xzm-stream-md__pending xzm-stream-md__empty"
    >
      <StreamingCursor :variant="variant" />
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import StreamingCodeBlock from './StreamingCodeBlock.vue'
import StreamingTable from './StreamingTable.vue'
import StreamingCursor from './StreamingCursor.vue'
import MermaidDiagram from './MermaidDiagram.vue'
import { renderMarkdown, escapeFallback } from '../../utils/markdownFormatter'
import { splitStreamingTail } from '../../utils/streamBuffer'

const props = defineProps({
  /**
   * useStreamingMarkdown 返回的实例
   */
  stream: {
    type: Object,
    required: true,
  },
  variant: {
    type: String,
    default: 'content',
    validator: (v) => ['content', 'thinking'].includes(v),
  },
})

const blocks = computed(() => {
  // stream.blocks 已经是 ComputedRef，这里再读取
  return props.stream.blocks?.value || props.stream.blocks || []
})

function renderDoneBlock(block) {
  try {
    return renderMarkdown(block.raw, block.kind)
  } catch (err) {
    /* eslint-disable no-console */
    console.warn('[stream-md] render done block failed', err)
    return escapeFallback(block.raw)
  }
}

function streamingTail(raw) {
  return splitStreamingTail(raw)
}
</script>

<style scoped>
.xzm-stream-md {
  --xzm-prose-color: var(--xzm-text-primary);
  --xzm-prose-link: var(--xzm-text-link);

  color: var(--xzm-prose-color);
  line-height: var(--xzm-lh-relaxed);
  font-size: var(--xzm-fs-base);
  word-wrap: break-word;
  overflow-wrap: break-word;
  max-width: 100%;
}

.xzm-stream-md--thinking {
  --xzm-prose-color: var(--xzm-text-secondary);
  font-size: var(--xzm-fs-sm);
  line-height: var(--xzm-lh-relaxed);
}
.xzm-stream-md__blocks { display: contents; }
.xzm-stream-md__block { min-width: 0; }
.stream-block-enter-active { transition: opacity 190ms ease-out, filter 190ms ease-out, transform 190ms ease-out; }
.stream-block-enter-from { opacity: 0; filter: blur(2px); transform: translateY(4px); }

/* 进行中段落（极克制透明度，节奏柔和） */
.xzm-stream-md__pending {
  margin: var(--xzm-space-3) 0;
  color: var(--xzm-text-secondary);
}
.xzm-stream-md__pending-text {
  white-space: pre-wrap;
  word-break: break-word;
  opacity: 0.95;
}
.xzm-stream-md__pending-text-inner { display: inline; }
.xzm-stream-md__pending-tail {
  display: inline;
  animation: xzm-stream-tail-reveal 180ms ease-out both;
}

@keyframes xzm-stream-tail-reveal {
  from { opacity: 0.28; filter: blur(1.5px); }
  to { opacity: 1; filter: blur(0); }
}

.xzm-stream-md__empty {
  display: inline-block;
  margin: 4px 0;
}

.xzm-stream-md__table-pending {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--xzm-border-color);
  border-radius: var(--xzm-radius-md);
  background-color: var(--xzm-surface-1);
  font-family: var(--xzm-font-mono);
  font-size: var(--xzm-fs-xs);
}
.xzm-stream-md__pending-label {
  font-family: var(--xzm-font-sans);
  font-size: var(--xzm-fs-xs);
  color: var(--xzm-text-tertiary);
  letter-spacing: 0.04em;
}
.xzm-stream-md__pending-pre {
  margin: 0;
  white-space: pre-wrap;
  color: var(--xzm-text-secondary);
  line-height: 1.5;
}

/* ============================================
   marked 渲染后的 prose 样式（V2 极简）
   ============================================ */
.xzm-stream-md :deep(p) {
  margin: var(--xzm-space-3) 0;
  line-height: var(--xzm-lh-relaxed);
}
.xzm-stream-md :deep(p:first-child) { margin-top: 0; }
.xzm-stream-md :deep(p:last-child) { margin-bottom: 0; }

.xzm-stream-md :deep(h1),
.xzm-stream-md :deep(h2),
.xzm-stream-md :deep(h3),
.xzm-stream-md :deep(h4),
.xzm-stream-md :deep(h5),
.xzm-stream-md :deep(h6) {
  margin: var(--xzm-space-6) 0 var(--xzm-space-3);
  font-weight: var(--xzm-fw-semibold);
  line-height: 1.45;
  letter-spacing: -0.01em;
  color: var(--xzm-text-primary);
}
.xzm-stream-md :deep(h1) { font-size: var(--xzm-fs-2xl); }
.xzm-stream-md :deep(h2) { font-size: var(--xzm-fs-xl); }
.xzm-stream-md :deep(h3) { font-size: var(--xzm-fs-lg); }
.xzm-stream-md :deep(h4) { font-size: var(--xzm-fs-md); }

.xzm-stream-md :deep(strong) { font-weight: var(--xzm-fw-semibold); color: var(--xzm-text-primary); }
.xzm-stream-md :deep(em) { font-style: italic; }
.xzm-stream-md :deep(del) { color: var(--xzm-text-tertiary); }

.xzm-stream-md :deep(ul),
.xzm-stream-md :deep(ol) {
  margin: var(--xzm-space-3) 0;
  padding-left: 26px;
}
.xzm-stream-md :deep(li) { margin: 6px 0; }
.xzm-stream-md :deep(ul ul),
.xzm-stream-md :deep(ol ol),
.xzm-stream-md :deep(ul ol),
.xzm-stream-md :deep(ol ul) {
  margin: 4px 0;
}

.xzm-stream-md :deep(blockquote) {
  margin: var(--xzm-space-4) 0;
  padding: var(--xzm-space-3) var(--xzm-space-4);
  border-left: 2px solid var(--xzm-border-color-strong);
  background-color: var(--xzm-surface-1);
  border-radius: 0 var(--xzm-radius-sm) var(--xzm-radius-sm) 0;
  color: var(--xzm-text-secondary);
}
.xzm-stream-md :deep(blockquote p) { margin: 4px 0; }

.xzm-stream-md :deep(hr) {
  margin: var(--xzm-space-8) 0;
  border: none;
  border-top: 1px solid var(--xzm-border-color);
}

.xzm-stream-md :deep(a) {
  color: var(--xzm-prose-link);
  text-decoration: none;
  border-bottom: 1px solid var(--xzm-border-color-hover);
  transition: color var(--xzm-duration-fast) var(--xzm-ease-out),
              border-color var(--xzm-duration-fast) var(--xzm-ease-out);
}
.xzm-stream-md :deep(a:hover) {
  color: var(--xzm-brand);
  border-bottom-color: var(--xzm-brand);
}

/* 行内代码（中性灰底，不再粉色） */
.xzm-stream-md :deep(:not(pre) > code) {
  font-family: var(--xzm-font-mono);
  font-size: 0.9em;
  padding: 1px 6px;
  background-color: var(--xzm-surface-2);
  color: var(--xzm-text-primary);
  border-radius: var(--xzm-radius-xs);
  border: 1px solid var(--xzm-border-color);
}

/* 兜底 pre */
.xzm-stream-md :deep(.xzm-fallback-pre) {
  margin: var(--xzm-space-3) 0;
  padding: 12px;
  background-color: var(--xzm-surface-2);
  border: 1px solid var(--xzm-border-color);
  border-radius: var(--xzm-radius-sm);
  font-family: var(--xzm-font-mono);
  font-size: var(--xzm-fs-sm);
  color: var(--xzm-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
}

@media (max-width: 640px) {
  .xzm-stream-md { font-size: var(--xzm-fs-sm); }
  .xzm-stream-md :deep(h1) { font-size: var(--xzm-fs-xl); }
  .xzm-stream-md :deep(h2) { font-size: var(--xzm-fs-lg); }
  .xzm-stream-md :deep(h3) { font-size: var(--xzm-fs-md); }
}
@media (prefers-reduced-motion: reduce) {
  .stream-block-enter-active { transition: none; }
  .xzm-stream-md__pending-tail { animation: none; }
}
</style>
