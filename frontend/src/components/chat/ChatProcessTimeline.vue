<template>
  <section class="chat-process" aria-label="AI 回答流程" aria-live="polite">
    <ol class="chat-process__steps">
      <li
        v-for="(stage, index) in normalizedStages"
        :key="stage.phase"
        class="chat-process__step"
        :class="`is-${stage.status}`"
      >
        <span class="chat-process__node" aria-hidden="true">
          <svg v-if="stage.status === 'done'" viewBox="0 0 16 16">
            <path d="m3.2 8.2 3 3 6.6-6.5" />
          </svg>
          <span v-else-if="stage.status === 'running'" class="chat-process__pulse"></span>
          <span v-else-if="stage.status === 'degraded'">!</span>
          <span v-else-if="stage.status === 'stopped'">Ⅱ</span>
          <span v-else>{{ index + 1 }}</span>
        </span>
        <div class="chat-process__copy">
          <strong>{{ stageLabel(stage) }}</strong>
          <span v-if="stage.phase === 'retrieval' && ['done', 'degraded'].includes(stage.status)" class="chat-process__meta">
            {{ retrievalSummary(stage) }}
          </span>
        </div>
        <div v-if="stage.phase === 'retrieval' && stage.keywords?.length" class="chat-process__keywords">
          <span v-for="keyword in stage.keywords.slice(0, 6)" :key="keyword">{{ keyword }}</span>
        </div>
      </li>
    </ol>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  stages: { type: Array, default: () => [] },
})

const defaults = [
  { phase: 'retrieval', title: '检索相关信息', status: 'waiting' },
  { phase: 'thinking', title: '分析问题', status: 'waiting' },
  { phase: 'answer', title: '组织回答', status: 'waiting' },
]

const normalizedStages = computed(() => defaults.map((fallback) => ({
  ...fallback,
  ...(props.stages.find((stage) => stage?.phase === fallback.phase) || {}),
})))

function stageLabel(stage) {
  const labels = {
    retrieval: '检索相关信息',
    thinking: '分析问题',
    answer: '组织回答',
  }
  const baseLabel = stage.title || labels[stage.phase] || '处理'
  if (stage.status === 'running') return stage.title || '处理中'
  if (stage.status === 'degraded') return `${baseLabel}（已降级）`
  if (stage.status === 'stopped') return `${baseLabel}（已停止）`
  if (stage.status === 'skipped') return `${baseLabel}（未执行）`
  if (stage.status === 'error') return `${baseLabel}（失败）`
  return labels[stage.phase] || stage.title
}

function retrievalSummary(stage) {
  if (Number.isFinite(Number(stage.hitCount))) {
    return Number(stage.hitCount) > 0
      ? `已找到 ${stage.hitCount} 条相关资料`
      : '未命中资料，使用模型知识回答'
  }
  return '检索完成'
}
</script>

<style scoped>
.chat-process {
  width: min(100%, 760px);
  margin: 2px 0 12px;
  padding: 12px 14px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 12px;
  background:
    linear-gradient(100deg, color-mix(in srgb, var(--xzm-brand) 6%, transparent), transparent 45%),
    var(--xzm-surface-1);
}

.chat-process__steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chat-process__step {
  position: relative;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--xzm-text-tertiary);
}

.chat-process__step:not(:last-child)::after {
  position: absolute;
  top: 10px;
  right: -7px;
  width: 12px;
  height: 1px;
  background: var(--xzm-border-color);
  content: "";
}

.chat-process__node {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 50%;
  background: var(--xzm-surface-0);
  font: 650 10px/1 "JetBrains Mono", monospace;
}

.chat-process__node svg {
  width: 12px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.chat-process__step.is-running,
.chat-process__step.is-done {
  color: var(--xzm-text-primary);
}

.chat-process__step.is-running .chat-process__node {
  border-color: color-mix(in srgb, var(--xzm-brand) 55%, transparent);
  color: var(--xzm-brand);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--xzm-brand) 9%, transparent);
}

.chat-process__step.is-done .chat-process__node {
  border-color: color-mix(in srgb, var(--xzm-success) 45%, transparent);
  background: color-mix(in srgb, var(--xzm-success) 10%, var(--xzm-surface-0));
  color: var(--xzm-success);
}

.chat-process__step.is-error .chat-process__node {
  border-color: var(--xzm-danger);
  color: var(--xzm-danger);
}

.chat-process__step.is-degraded .chat-process__node {
  border-color: #d08a18;
  background: color-mix(in srgb, #d08a18 10%, var(--xzm-surface-0));
  color: #b46d06;
}

.chat-process__step.is-stopped,
.chat-process__step.is-skipped {
  opacity: .68;
}

.chat-process__pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: process-pulse 1.2s ease-in-out infinite;
}

.chat-process__copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.chat-process__copy strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-process__meta {
  overflow: hidden;
  color: var(--xzm-text-tertiary);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-process__keywords {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 3px 0 0 29px;
}

.chat-process__keywords span {
  max-width: 120px;
  overflow: hidden;
  padding: 2px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xzm-brand) 9%, transparent);
  color: var(--xzm-brand);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes process-pulse {
  50% { opacity: .35; transform: scale(.72); }
}

@media (max-width: 640px) {
  .chat-process__steps { grid-template-columns: 1fr; gap: 8px; }
  .chat-process__step:not(:last-child)::after { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .chat-process__pulse { animation: none; }
}
</style>
