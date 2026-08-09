<template>
  <StreamingMarkdown
    :stream="staticStream"
    :variant="variant"
  />
</template>

<script setup>
import { watch } from 'vue'
import StreamingMarkdown from './streaming/StreamingMarkdown.vue'
import { useStreamingMarkdown } from '../composables/useStreamingMarkdown'

/**
 * StaticMarkdown — 用于非流式场景一次性渲染完整 Markdown
 *
 * 内部复用 StreamingMarkdown 渲染管线（保证样式与 chat 端一致），
 * 只是把整段 content 一次性 loadCompleted 进去。
 */
const props = defineProps({
  content: { type: String, default: '' },
  variant: {
    type: String,
    default: 'content',
    validator: (v) => ['content', 'thinking'].includes(v),
  },
})

const staticStream = useStreamingMarkdown({ variant: props.variant })

watch(
  () => props.content,
  (val) => {
    staticStream.loadCompleted(val || '')
  },
  { immediate: true }
)
</script>
