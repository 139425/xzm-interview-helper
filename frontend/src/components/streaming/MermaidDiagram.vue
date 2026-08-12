<template>
  <figure class="mermaid-card">
    <header><span>MERMAID</span><button type="button" @click="render">重新渲染</button></header>
    <div v-if="loading" class="mermaid-state">图表渲染中…</div>
    <div v-else-if="error" class="mermaid-state is-error">{{ error }}</div>
    <div v-else class="mermaid-output" v-html="safeSvg"></div>
  </figure>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import DOMPurify from 'dompurify'

const props = defineProps({ code: { type: String, required: true }, blockId: { type: String, default: '' } })
const safeSvg = ref('')
const loading = ref(true)
const error = ref('')
let renderVersion = 0

async function render() {
  const version = ++renderVersion
  loading.value = true
  error.value = ''
  try {
    const { default: mermaid } = await import('mermaid')
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'neutral', fontFamily: 'Noto Sans SC, Microsoft YaHei, sans-serif' })
    const id = `xzm-mermaid-${String(props.blockId || Date.now()).replace(/[^a-zA-Z0-9_-]/g, '')}-${version}`
    const result = await mermaid.render(id, props.code)
    if (version !== renderVersion) return
    safeSvg.value = DOMPurify.sanitize(result.svg, { USE_PROFILES: { svg: true, svgFilters: true }, FORBID_TAGS: ['script', 'foreignObject'] })
  } catch {
    if (version === renderVersion) error.value = '图表语法不完整，已保留原始 Mermaid 代码。'
  } finally {
    if (version === renderVersion) loading.value = false
  }
}
watch(() => props.code, render)
onMounted(render)
</script>

<style scoped>
.mermaid-card{margin:16px 0;border:1px solid var(--xzm-border-color);border-radius:12px;overflow:hidden;background:var(--xzm-surface-1)}.mermaid-card header{display:flex;justify-content:space-between;align-items:center;padding:8px 12px;border-bottom:1px solid var(--xzm-border-color);color:var(--xzm-text-tertiary);background:var(--xzm-surface-2);font-size:10px;font-weight:700;letter-spacing:.08em}.mermaid-card button{border:0;color:var(--xzm-brand);background:transparent;font:inherit;cursor:pointer}.mermaid-output{padding:18px;overflow:auto;text-align:center}.mermaid-output :deep(svg){max-width:100%;height:auto}.mermaid-state{padding:28px;color:var(--xzm-text-tertiary);text-align:center;font-size:11px}.mermaid-state.is-error{color:var(--xzm-danger)}
</style>
