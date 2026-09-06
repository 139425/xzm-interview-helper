<template>
  <el-popover v-model:visible="open" placement="right-start" :width="288"
    :fallback-placements="['bottom-start', 'right-start', 'left-start']"
    trigger="click" popper-class="workspace-picker-popper" @show="focusSearch" @hide="restoreFocus">
    <template #reference>
      <button ref="trigger" type="button" class="workspace-picker-trigger" :class="{ 'is-collapsed': collapsed }"
        aria-label="全部工作区" :aria-expanded="open" aria-haspopup="dialog">
        <el-icon v-if="collapsed" :size="18"><Grid /></el-icon>
        <template v-else><span>工作区</span><span class="workspace-picker-more">全部 <el-icon :size="13"><ArrowDown /></el-icon></span></template>
      </button>
    </template>
    <div ref="panel" class="workspace-picker" role="dialog" aria-label="选择工作区" @keydown="handleKey">
      <label class="workspace-picker-search"><el-icon :size="16"><Search /></el-icon>
        <input ref="search" v-model="query" aria-label="搜索工作区" placeholder="搜索工作区" autocomplete="off" />
      </label>
      <div class="workspace-picker-list">
        <button v-for="item in filtered" :key="item.id" type="button" class="workspace-option"
          :class="{ active: item.id === activeMode }" :aria-current="item.id === activeMode ? 'page' : undefined"
          :aria-label="item.label" @click="select(item)">
          <el-icon :size="19"><component :is="item.icon" /></el-icon>
          <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
          <span v-if="item.id === activeMode" class="workspace-check" aria-hidden="true">✓</span>
        </button>
        <p v-if="!filtered.length" class="workspace-picker-empty">没有匹配的工作区</p>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElPopover } from 'element-plus'
import { ArrowDown, Grid, Search } from '@element-plus/icons-vue'
const props = defineProps({ items: { type: Array, required: true }, activeMode: String, collapsed: Boolean })
const emit = defineEmits(['select', 'open-change'])
const open = ref(false), query = ref(''), trigger = ref(null), search = ref(null), panel = ref(null)
const filtered = computed(() => props.items.filter(item => `${item.label} ${item.description}`.toLowerCase().includes(query.value.trim().toLowerCase())))
async function focusSearch() { emit('open-change', true); await nextTick(); search.value?.focus({ preventScroll: true }) }
function restoreFocus() { emit('open-change', false); query.value = ''; if (document.activeElement === document.body || panel.value?.contains(document.activeElement)) trigger.value?.focus({ preventScroll: true }) }
function select(item) { open.value = false; emit('select', item) }
function handleKey(event) {
  if (event.key === 'Escape') { event.stopPropagation(); event.preventDefault(); open.value = false; return }
  const buttons = [...(panel.value?.querySelectorAll('.workspace-option') || [])]
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault(); event.stopPropagation(); const index = buttons.indexOf(document.activeElement)
    const next = event.key === 'ArrowDown' ? (index + 1) % buttons.length : (index < 0 ? buttons.length - 1 : (index - 1 + buttons.length) % buttons.length)
    buttons[next]?.focus(); return
  }
  if (event.key !== 'Tab') return
  // The popover also handles Tab; keep this dialog's focus loop authoritative.
  event.stopPropagation()
  if (event.shiftKey && document.activeElement === search.value) { event.preventDefault(); (buttons.at(-1) || search.value)?.focus() }
  else if (!event.shiftKey && document.activeElement === (buttons.at(-1) || search.value)) { event.preventDefault(); search.value?.focus() }
}
</script>

<style scoped>
.workspace-picker-trigger { display: flex; width: 100%; min-height: 28px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px; border: 0; border-radius: 6px; background: transparent; color: var(--xzm-text-secondary); font-size: 11px; cursor: pointer; transition: background 120ms; }
.workspace-picker-trigger:hover, .workspace-picker-trigger[aria-expanded='true'] { background: var(--xzm-hover-bg); color: var(--xzm-text-primary); }
.workspace-picker-more { display: flex; gap: 4px; align-items: center; color: var(--xzm-text-secondary); }
.workspace-picker-trigger.is-collapsed { width: 40px; height: 34px; justify-content: center; padding: 0; }
.workspace-picker { color: var(--xzm-text-primary); }
.workspace-picker-search { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--xzm-border-color-strong); border-radius: 8px; color: var(--xzm-text-secondary); background: var(--xzm-surface-control); }
.workspace-picker-search:focus-within { border-color: var(--xzm-brand); box-shadow: 0 0 0 2px var(--xzm-focus-ring-soft); }
.workspace-picker-search input { width: 100%; min-width: 0; padding: 0; border: 0; outline: 0; color: var(--xzm-text-primary); background: transparent; font: inherit; font-size: 13px; }
.workspace-picker-list { max-height: min(480px, calc(100dvh - 130px)); overflow-y: auto; overscroll-behavior: contain; padding-top: 8px; }
.workspace-option { display: flex; align-items: center; width: 100%; gap: 10px; padding: 10px; border: 0; border-radius: 8px; text-align: left; color: var(--xzm-text-secondary); background: transparent; cursor: pointer; transition: background 120ms; }
.workspace-option > span:not(.workspace-check) { display: grid; gap: 3px; min-width: 0; }
.workspace-option strong { color: var(--xzm-text-primary); font-size: 13px; font-weight: 550; }
.workspace-option small { color: var(--xzm-text-secondary); font-size: 11px; }
.workspace-option:hover { background: var(--xzm-hover-bg); }
.workspace-option.active { color: var(--xzm-brand); background: var(--xzm-brand-soft); }
.workspace-check { margin-left: auto; }
.workspace-picker-empty { padding: 22px 8px; text-align: center; font-size: 13px; color: var(--xzm-text-secondary); }
@media (pointer: coarse) { .workspace-picker-trigger { min-height: 36px; } }
</style>
