<template>
  <div class="thinking-content" v-if="content">
    <div class="thinking-header">
      <el-icon :size="16" class="thinking-icon"><Cpu /></el-icon>
      <span class="thinking-label">思考过程</span>
    </div>
    <div class="thinking-box" ref="thinkingBoxRef">
      {{ content }}
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { Cpu } from '@element-plus/icons-vue'

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

const thinkingBoxRef = ref(null)

// 监听内容变化，自动滚动到底部
watch(() => props.content, () => {
  nextTick(() => {
    if (thinkingBoxRef.value) {
      thinkingBoxRef.value.scrollTop = thinkingBoxRef.value.scrollHeight
    }
  })
})
</script>

<style scoped>
.thinking-content {
  margin-bottom: var(--gemini-spacing-md);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-sm);
  margin-bottom: var(--gemini-spacing-sm);
  color: var(--gemini-text-secondary);
  font-size: 0.875rem;
}

.thinking-icon {
  color: var(--gemini-accent-yellow);
}

.thinking-label {
  font-weight: 500;
}

.thinking-box {
  height: calc(1.5em * 5); /* 固定5行高度 */
  padding: var(--gemini-spacing-md);
  background-color: rgba(253, 214, 99, 0.1);
  border: 1px solid rgba(253, 214, 99, 0.3);
  border-radius: var(--gemini-radius-md);
  font-size: 0.875rem; /* 字体稍小 */
  line-height: 1.5;
  color: var(--gemini-text-secondary);
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-style: italic;
}

/* 滚动条样式 */
.thinking-box::-webkit-scrollbar {
  width: 4px;
}

.thinking-box::-webkit-scrollbar-track {
  background: transparent;
}

.thinking-box::-webkit-scrollbar-thumb {
  background: rgba(253, 214, 99, 0.3);
  border-radius: 2px;
}

.thinking-box::-webkit-scrollbar-thumb:hover {
  background: rgba(253, 214, 99, 0.5);
}

/* 亮色主题适配 */
[data-theme="light"] .thinking-box {
  background-color: rgba(253, 214, 99, 0.15);
  border-color: rgba(253, 214, 99, 0.4);
  color: #666;
}
</style>
