<template>
  <div v-if="visible" class="modal-overlay" @click.self="closeModal">
    <div class="modal-container">
      <!-- 模态框头部 -->
      <div class="modal-header">
        <h3>编辑消息</h3>
        <button class="close-btn" @click="closeModal" title="返回">
          <el-icon :size="18"><Close /></el-icon>
        </button>
      </div>
      
      <!-- 模态框内容 -->
      <div class="modal-body">
        <textarea
          v-model="localText"
          placeholder="输入消息..."
          class="modal-textarea"
          @input="adjustTextareaHeight"
          ref="textareaRef"
        />
      </div>
      
      <!-- 模态框底部操作按钮 -->
      <div class="modal-footer">
        <div class="modal-actions">
          <VoiceRecorder @voice-result="handleVoiceResult" />
          <button
            class="send-button"
            :class="{ disabled: !localText.trim() }"
            :disabled="!localText.trim()"
            @click="sendMessage"
          >
            <div class="send-icon-container">
              <el-icon :size="12" class="arrow-up"><ArrowUp /></el-icon>
              <el-icon :size="18"><Position /></el-icon>
            </div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { Close, ArrowUp, Position } from '@element-plus/icons-vue'
import VoiceRecorder from './VoiceRecorder.vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  initialText: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['close', 'send', 'update-text'])

const localText = ref('')
const textareaRef = ref(null)

// 监听初始文本变化
watch(() => props.initialText, (newText) => {
  localText.value = newText
}, { immediate: true })

// 监听本地文本变化，实时同步到父组件
watch(localText, (newText) => {
  emit('update-text', newText)
})

// 监听模态框显示状态
watch(() => props.visible, (visible) => {
  if (visible) {
    nextTick(() => {
      if (textareaRef.value) {
        textareaRef.value.focus()
        adjustTextareaHeight()
      }
    })
  }
})

// 处理语音识别结果
const handleVoiceResult = (result) => {
  if (result && typeof result === 'string') {
    localText.value = localText.value + result
    nextTick(() => {
      adjustTextareaHeight()
    })
  }
}

// 调整文本框高度
const adjustTextareaHeight = () => {
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
    const scrollHeight = textareaRef.value.scrollHeight
    const maxHeight = 200 // 最大高度
    textareaRef.value.style.height = Math.min(scrollHeight, maxHeight) + 'px'
  }
}

// 发送消息
const sendMessage = () => {
  if (localText.value.trim()) {
    emit('send', localText.value.trim())
    closeModal()
  }
}

// 关闭模态框
const closeModal = () => {
  emit('close')
}

// 键盘事件处理
const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    closeModal()
  } else if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    sendMessage()
  }
}

// 组件挂载时添加键盘事件监听
onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

// 组件卸载时移除键盘事件监听
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-container {
  background: var(--bg-primary);
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.modal-header h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.modal-body {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
}

.modal-textarea {
  width: 100%;
  min-height: 120px;
  max-height: 200px;
  padding: 1rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-size: 1rem;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  font-family: inherit;
  overflow-y: auto;
}

.modal-textarea:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.modal-textarea::placeholder {
  color: var(--text-tertiary);
}

.modal-footer {
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.modal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.send-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background-color: var(--primary-color);
  color: white;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-button:hover:not(.disabled) {
  background-color: var(--primary-hover);
  transform: scale(1.05);
}

.send-button:active:not(.disabled) {
  transform: scale(0.95);
}

.send-button.disabled {
  background-color: #e2e8f0;
  color: #94a3b8;
  cursor: not-allowed;
}

.send-icon-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.arrow-up {
  margin-bottom: -4px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .modal-container {
    width: 95%;
    max-height: 90vh;
  }
  
  .modal-header {
    padding: 0.75rem 1rem;
  }
  
  .modal-body {
    padding: 1rem;
  }
  
  .modal-footer {
    padding: 0.75rem 1rem;
  }
  
  .modal-textarea {
    min-height: 100px;
  }
}

/* 暗色主题适配 */
@media (prefers-color-scheme: dark) {
  .modal-overlay {
    background-color: rgba(0, 0, 0, 0.7);
  }
}
</style>