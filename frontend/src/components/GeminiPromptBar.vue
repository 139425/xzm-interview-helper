<template>
  <div
    class="gemini-prompt-bar"
    :class="{
      'position-center': uiStore.promptBarPosition === 'center',
      'position-bottom': uiStore.promptBarPosition === 'bottom',
      'focused': isFocused,
      'is-dragging': isDragging
    }"
    @dragenter.prevent="isDragging = true"
    @dragover.prevent="isDragging = true"
    @dragleave.prevent="handleDragLeave"
    @drop.prevent="handleDrop"
  >
    <section v-if="ocrPreview.visible" class="ocr-preview" aria-label="图片识别结果">
      <header>
        <div><span>OCR</span><strong>{{ ocrPreview.filename || '剪贴板图片' }}</strong></div>
        <button type="button" aria-label="移除图片识别结果" @click="clearOcr">×</button>
      </header>
      <p v-if="ocrPreview.loading">正在本机服务器识别文字…</p>
      <textarea v-else v-model="ocrPreview.text" rows="4" aria-label="可编辑的图片识别文本"></textarea>
      <small v-if="ocrPreview.error">{{ ocrPreview.error }}</small>
      <small v-else>图片只做临时识别；你确认发送后，AI 才会读取这里的文字。</small>
    </section>
    <!-- 输入区域 -->
    <div class="input-wrapper">
      <el-input
        ref="inputRef"
        v-model="inputText"
        type="textarea"
        :placeholder="placeholder"
        :autosize="{ minRows: 1, maxRows: 5 }"
        :disabled="disabled"
        @focus="handleFocus"
        @blur="handleBlur"
        @keydown="handleKeyDown"
        @paste="handlePaste"
        class="prompt-input"
      />
    </div>
    
    <!-- 工具栏 -->
    <div class="toolbar">
      <!-- 左侧工具 -->
      <div class="toolbar-left">
        <!-- 添加按钮 -->
        <button 
          type="button"
          class="tool-btn"
          @click="handleAddAttachment"
          title="上传图片并识别文字"
        >
          <el-icon :size="20"><Plus /></el-icon>
        </button>
        <input ref="imageInput" class="visually-hidden" type="file" accept="image/png,image/jpeg,image/bmp" @change="handleFileInput">
        
        <!-- 模型选择器 - 已隐藏 -->
        <div class="model-selector">
          <button
            type="button"
            class="model-trigger"
            :class="{ 'is-open': showModelMenu }"
            :disabled="isStreaming"
            :aria-expanded="showModelMenu"
            aria-haspopup="listbox"
            aria-label="选择对话模型"
            @click.stop="toggleModelMenu"
          >
            <span class="model-mark" aria-hidden="true">{{ currentModel.provider === 'zhipu' ? 'GLM' : 'DS' }}</span>
            <span class="model-name">{{ currentModel.label }}</span>
            <el-icon :size="15" class="dropdown-icon">
              <ArrowDown />
            </el-icon>
          </button>

          <div
            v-if="showModelMenu"
            class="model-menu"
            role="listbox"
            aria-label="可用模型"
            @click.stop
          >
            <div class="model-menu-header">
              <span>选择模型</span>
              <span>{{ models.length }} 个可用</span>
            </div>
            <button
              v-for="model in models"
              :key="model.id"
              type="button"
              class="model-option"
              :class="{ active: currentModel.id === model.id }"
              role="option"
              :aria-selected="currentModel.id === model.id"
              @click="selectModel(model)"
            >
              <span class="model-option-mark" aria-hidden="true">{{ model.provider === 'zhipu' ? 'GLM' : 'DS' }}</span>
              <span class="model-option-copy">
                <span class="model-option-label">{{ model.label }}</span>
                <span class="model-option-description">{{ model.description }}</span>
              </span>
              <span v-if="currentModel.id === model.id" class="model-selected-mark" aria-hidden="true">✓</span>
            </button>
          </div>
        </div>
      </div>
      
      <!-- 右侧工具 -->
      <div class="toolbar-right">
        <!-- 提示词模式选择器 -->
        <el-dropdown @command="handlePromptModeChange" trigger="click">
          <button 
            class="prompt-mode-btn"
            :class="`mode-${promptMode}`"
            :title="getPromptModeTitle()"
          >
            <el-icon :size="16"><Document /></el-icon>
            <span class="mode-label">{{ getPromptModeLabel() }}</span>
            <el-icon :size="12" class="dropdown-arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="none" :class="{ 'is-active': promptMode === 'none' }">
                <el-icon><Close /></el-icon>
                <span>无</span>
              </el-dropdown-item>
              <el-dropdown-item command="simple" :class="{ 'is-active': promptMode === 'simple' }">
                <el-icon><Document /></el-icon>
                <span>简洁</span>
              </el-dropdown-item>
              <el-dropdown-item command="professional" :class="{ 'is-active': promptMode === 'professional' }">
                <el-icon><Star /></el-icon>
                <span>专业</span>
              </el-dropdown-item>
              <el-dropdown-item command="reasoning" :class="{ 'is-active': promptMode === 'reasoning' }">
                <el-icon><Compass /></el-icon>
                <span>推演</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button
          type="button"
          class="think-toggle-btn"
          :class="{ active: thinkingMode }"
          :disabled="isStreaming"
          :aria-pressed="thinkingMode"
          :title="thinkingMode ? '切换到非思考模式' : '切换到思考模式'"
          @click="toggleThinkingMode"
        >
          <el-icon :size="17"><Cpu /></el-icon>
          <span class="think-label">{{ thinkingMode ? '思考中' : '思考' }}</span>
        </button>
        
        <!-- 停止生成按钮：流式进行中显示 -->
        <button
          v-if="isStreaming"
          class="stop-btn"
          @click="handleStop"
          title="停止生成"
          aria-label="停止生成"
        >
          <el-icon :size="18"><VideoPause /></el-icon>
        </button>

        <!-- 发送按钮：非流式且有输入时显示 -->
        <button 
          v-else
          class="send-btn"
          @click="handleSend"
          :disabled="disabled || ocrPreview.loading || (!inputText.trim() && !ocrPreview.text.trim())"
          title="发送消息"
          v-show="inputText.trim() || ocrPreview.text.trim()"
        >
          <el-icon :size="20"><Promotion /></el-icon>
        </button>
        
        <!-- 语音输入按钮 -->
        <VoiceRecorder 
          v-if="enableVoice && !isStreaming"
          @voice-result="handleVoiceResult"
          class="voice-recorder-btn"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useUIStore } from '../stores/ui'
import { Plus, ArrowDown, Promotion, Document, Close, Star, Compass, Cpu, VideoPause } from '@element-plus/icons-vue'
import VoiceRecorder from './VoiceRecorder.vue'
import { ElMessage } from 'element-plus'
import { CHAT_MODELS } from '../config/models'
import { mediaApi } from '../api/career'

// Props
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '询问 AI 助手'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  currentModelId: {
    type: String,
    default: ''
  },
  thinkingMode: {
    type: Boolean,
    default: false
  },
  enableVoice: {
    type: Boolean,
    default: true
  },
  promptMode: {
    type: String,
    default: 'professional'  // 'none' | 'simple' | 'professional' | 'reasoning'
  },
  isStreaming: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits([
  'update:modelValue',
  'send',
  'stop',
  'change-model',
  'update:thinkingMode',
  'update:promptMode'
])

// Stores
const uiStore = useUIStore()

// 状态
const inputRef = ref(null)
const inputText = ref(props.modelValue)
const isFocused = ref(false)
const showModelMenu = ref(false)
const imageInput = ref(null)
const isDragging = ref(false)
const ocrPreview = ref({ visible: false, loading: false, filename: '', text: '', error: '' })

// 模型列表
const models = CHAT_MODELS
const currentModel = computed(() => {
  return models.find((model) => model.id === props.currentModelId) || models[0]
})

// 监听 props 变化
watch(() => props.modelValue, (newValue) => {
  inputText.value = newValue
})

// 监听输入变化
watch(inputText, (newValue) => {
  emit('update:modelValue', newValue)
})

// ========== 事件处理 ==========

// 聚焦
const handleFocus = () => {
  isFocused.value = true
  uiStore.promptBarFocused = true
}

// 失焦
const handleBlur = () => {
  isFocused.value = false
  uiStore.promptBarFocused = false
}

// 键盘事件
const handleKeyDown = (event) => {
  // Enter 发送，Shift+Enter 换行
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

// 发送消息
const handleSend = () => {
  if (props.disabled || ocrPreview.value.loading) return

  const typedText = inputText.value.trim()
  const recognizedText = ocrPreview.value.text.trim()
  if (!typedText && !recognizedText) return
  const message = recognizedText
    ? `${typedText}${typedText ? '\n\n' : ''}[用户确认的图片识别文本]\n${recognizedText}\n[/用户确认的图片识别文本]`
    : typedText
  inputText.value = ''
  clearOcr()
  emit('send', message)
  
  // 发送后聚焦输入框
  nextTick(() => {
    inputRef.value?.focus()
  })
}

// 停止生成
const handleStop = () => {
  emit('stop')
}

// 添加附件
const handleAddAttachment = () => {
  imageInput.value?.click()
}

async function recognizeImage(file) {
  if (!file || !file.type?.startsWith('image/')) {
    ElMessage.warning('请选择 PNG、JPG 或 BMP 图片')
    return
  }
  ocrPreview.value = { visible: true, loading: true, filename: file.name || '剪贴板图片', text: '', error: '' }
  try {
    const result = await mediaApi.ocr(file)
    ocrPreview.value = { ...ocrPreview.value, loading: false, text: result.text || '' }
    ElMessage.success('图片文字已识别，请检查并修改后发送')
  } catch (error) {
    ocrPreview.value = { ...ocrPreview.value, loading: false, error: error.response?.data?.message || '图片识别失败，请重试' }
  }
}

function handleFileInput(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) recognizeImage(file)
}

function handlePaste(event) {
  const image = Array.from(event.clipboardData?.items || []).find((item) => item.type.startsWith('image/'))
  if (!image) return
  event.preventDefault()
  recognizeImage(image.getAsFile())
}

function handleDrop(event) {
  isDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) recognizeImage(file)
}

function handleDragLeave(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) isDragging.value = false
}

function clearOcr() {
  ocrPreview.value = { visible: false, loading: false, filename: '', text: '', error: '' }
}

// 切换模型菜单
const toggleModelMenu = () => {
  if (props.isStreaming) return
  showModelMenu.value = !showModelMenu.value
}

// 选择模型
const selectModel = (model) => {
  emit('change-model', model.id)
  showModelMenu.value = false
}

const toggleThinkingMode = () => {
  if (props.isStreaming) return
  emit('update:thinkingMode', !props.thinkingMode)
}

// 语音识别结果
const handleVoiceResult = (text) => {
  inputText.value = text
  nextTick(() => {
    inputRef.value?.focus()
  })
}

// 提示词模式相关方法
const handlePromptModeChange = (mode) => {
  emit('update:promptMode', mode)
}

const getPromptModeLabel = () => {
  const labels = {
    'none': '无',
    'simple': '简洁',
    'professional': '专业',
    'reasoning': '推演'
  }
  return labels[props.promptMode] || '专业'
}

const getPromptModeTitle = () => {
  const titles = {
    'none': '无提示词模式 - AI 直接回答',
    'simple': '简洁模式 - 快速获得聚焦答案',
    'professional': '专业模式 - Java 技术专家深度回答',
    'reasoning': '推演模式 - 第一性原理、证据与工程结果'
  }
  return titles[props.promptMode] || '选择提示词模式'
}

// 聚焦输入框
const focus = () => {
  inputRef.value?.focus()
}

// 暴露方法
defineExpose({
  focus
})

// 点击外部关闭模型菜单
const handleClickOutside = (event) => {
  if (showModelMenu.value && !event.target.closest('.model-selector')) {
    showModelMenu.value = false
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('click', handleClickOutside)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('click', handleClickOutside)
  }
})
</script>

<style scoped>
/* 输入框容器 */
.gemini-prompt-bar {
  background-color: var(--gemini-bg-secondary);
  border: 1px solid var(--gemini-border-color);
  border-radius: var(--gemini-prompt-bar-border-radius);
  padding: var(--gemini-prompt-bar-padding);
  transition: all var(--gemini-transition-slow) var(--gemini-ease-in-out);
  box-shadow: var(--gemini-shadow-sm);
}

/* 聚焦状态 */
.gemini-prompt-bar.focused {
  border-color: var(--gemini-accent-blue);
  box-shadow: 0 0 0 3px rgba(138, 180, 248, 0.1);
}

.gemini-prompt-bar.is-dragging {
  border-color: var(--gemini-accent-blue);
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--gemini-accent-blue) 13%, transparent);
}

.ocr-preview {
  display: grid;
  gap: 8px;
  margin: 0 0 10px;
  padding: 11px;
  border: 1px solid color-mix(in srgb, var(--gemini-accent-blue) 30%, var(--gemini-border-color));
  border-radius: 13px;
  background: color-mix(in srgb, var(--gemini-accent-blue) 5%, var(--gemini-bg-secondary));
}

.ocr-preview header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.ocr-preview header div { display: flex; min-width: 0; align-items: center; gap: 8px; }
.ocr-preview header span { padding: 2px 6px; border-radius: 5px; color: var(--gemini-accent-blue); background: color-mix(in srgb, var(--gemini-accent-blue) 12%, transparent); font-size: 9px; font-weight: 800; }
.ocr-preview header strong { overflow: hidden; color: var(--gemini-text-secondary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.ocr-preview header button { border: 0; color: var(--gemini-text-tertiary); background: transparent; font-size: 19px; cursor: pointer; }
.ocr-preview textarea { width: 100%; padding: 8px 10px; border: 1px solid var(--gemini-border-color); border-radius: 8px; outline: 0; color: var(--gemini-text-primary); background: var(--gemini-bg-primary); font: inherit; font-size: 12px; line-height: 1.55; resize: vertical; }
.ocr-preview textarea:focus { border-color: var(--gemini-accent-blue); }
.ocr-preview p,.ocr-preview small { margin: 0; color: var(--gemini-text-tertiary); font-size: 10px; }
.ocr-preview small:first-of-type { color: var(--gemini-accent-red); }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }

/* 居中位置 */
.gemini-prompt-bar.position-center {
  position: fixed;
  top: 50%;
  left: calc(var(--sidebar-width, 0px) / 2 + 50%);
  transform: translate(-50%, calc(-50% + 15vh));
  width: calc(90% - var(--sidebar-width, 0px));
  max-width: var(--gemini-prompt-bar-max-width);
  z-index: 100;
  transition:
    left 260ms cubic-bezier(0.4, 0.0, 0.2, 1),
    width 260ms cubic-bezier(0.4, 0.0, 0.2, 1);
}

/* 底部位置 */
.gemini-prompt-bar.position-bottom {
  position: fixed;
  bottom: var(--gemini-spacing-lg);
  left: calc(var(--sidebar-width, 0px) + var(--gemini-spacing-2xl));
  right: var(--gemini-spacing-2xl);
  width: auto;
  max-width: var(--gemini-content-max-width);
  margin-left: auto;
  margin-right: auto;
  z-index: 100;
  background-color: var(--gemini-bg-secondary);
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.08), 0 4px 20px rgba(0, 0, 0, 0.12);
  transition:
    left 260ms cubic-bezier(0.4, 0.0, 0.2, 1),
    right 260ms cubic-bezier(0.4, 0.0, 0.2, 1);
}

/* 输入区域 */
.input-wrapper {
  margin-bottom: var(--gemini-spacing-md);
}

/* 输入框样式覆盖 */
.prompt-input :deep(.el-textarea__inner) {
  background-color: transparent;
  border: none;
  color: var(--gemini-text-primary);
  font-size: 1.1rem;
  line-height: 1.5;
  padding: 0;
  resize: none;
  box-shadow: none;
}

.prompt-input :deep(.el-textarea__inner):focus {
  box-shadow: none;
}

.prompt-input :deep(.el-textarea__inner)::placeholder {
  color: var(--gemini-text-tertiary);
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gemini-spacing-md);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-md);
}

/* 工具按钮 */
.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--gemini-spacing-sm);
  padding: 6px 12px;
  border: none;
  border-radius: var(--gemini-radius-lg);
  background-color: var(--gemini-bg-tertiary);
  color: var(--gemini-text-secondary);
  font-size: 0.9rem;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
  white-space: nowrap;
}

.tool-btn:hover {
  background-color: var(--gemini-bg-hover);
  color: var(--gemini-text-primary);
}

.tool-btn:active {
  transform: scale(0.98);
}

/* 模型选择器 */
.model-selector {
  position: relative;
  display: inline-flex;
}

.model-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 6px 10px 6px 7px;
  border: 1px solid transparent;
  border-radius: var(--gemini-radius-lg);
  background-color: var(--gemini-bg-tertiary);
  color: var(--gemini-text-secondary);
  font-size: 0.9rem;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
  user-select: none;
}

.model-trigger:hover:not(:disabled),
.model-trigger.is-open {
  background-color: var(--gemini-bg-hover);
  border-color: var(--gemini-border-hover);
  color: var(--gemini-text-primary);
}

.model-trigger:focus-visible {
  outline: 2px solid var(--gemini-accent-blue);
  outline-offset: 2px;
}

.model-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.model-mark,
.model-option-mark {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 30px;
  height: 24px;
  border-radius: 7px;
  background: rgba(138, 180, 248, 0.12);
  color: var(--gemini-accent-blue);
  font-size: 0.63rem;
  font-weight: 750;
  letter-spacing: 0.04em;
}

.model-name {
  font-size: 0.875rem;
  font-weight: 620;
  white-space: nowrap;
}

.thinking-badge {
  padding: 2px 6px;
  border: 1px solid rgba(253, 214, 99, 0.4);
  border-radius: 999px;
  color: var(--gemini-accent-yellow);
  font-size: 0.65rem;
  line-height: 1.25;
}

.dropdown-icon {
  transition: transform var(--gemini-transition-fast);
}

.model-trigger.is-open .dropdown-icon {
  transform: rotate(180deg);
}

/* 模型菜单 */
.model-menu {
  position: absolute;
  top: auto;
  bottom: calc(100% + 12px);
  left: 0;
  width: min(340px, calc(100vw - 32px));
  padding: 7px;
  background-color: var(--gemini-bg-secondary);
  border: 1px solid var(--gemini-border-color);
  border-radius: 14px;
  box-shadow: 0 18px 46px rgba(0, 0, 0, 0.34);
  overflow-y: auto;
  max-height: min(420px, calc(100vh - 180px));
  z-index: 1000;
  animation: gemini-scale-in var(--gemini-transition-fast);
  transform-origin: left bottom;
}

.model-menu-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 9px 9px;
  color: var(--gemini-text-tertiary);
  font-size: 0.68rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.model-option {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 10px;
  min-height: 58px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  color: var(--gemini-text-primary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
}

.model-option-copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.model-option-label {
  overflow: hidden;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-option-description {
  color: var(--gemini-text-tertiary);
  font-size: 0.75rem;
  white-space: nowrap;
}

.model-option:hover {
  background-color: var(--gemini-bg-hover);
  border-color: var(--gemini-border-color);
}

.model-option.active {
  border-color: rgba(138, 180, 248, 0.38);
  background: rgba(138, 180, 248, 0.1);
  color: var(--gemini-text-primary);
}

.model-option.active .model-option-description {
  color: var(--gemini-text-secondary);
}

.model-selected-mark {
  flex: 0 0 auto;
  color: var(--gemini-accent-yellow);
  font-size: 0.68rem;
  font-weight: 700;
}

.model-selected-mark {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--gemini-accent-blue);
  color: var(--gemini-bg-primary);
}

/* 语音录制按钮 */
.voice-recorder-btn {
  flex-shrink: 0;
}

/* 提示词模式选择器 */
.prompt-mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--gemini-border-color);
  border-radius: var(--gemini-radius-lg);
  background-color: var(--gemini-bg-tertiary);
  color: var(--gemini-text-primary);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
  white-space: nowrap;
}

.prompt-mode-btn:hover {
  background-color: var(--gemini-bg-hover);
  border-color: var(--gemini-accent-blue);
}

.prompt-mode-btn .mode-label {
  font-weight: 500;
}

.prompt-mode-btn .dropdown-arrow {
  opacity: 0.6;
  transition: transform var(--gemini-transition-fast);
}

.prompt-mode-btn:hover .dropdown-arrow {
  transform: translateY(2px);
}

/* 不同模式的样式 - 使用更明显的颜色对比 */
.prompt-mode-btn.mode-none {
  border-color: var(--gemini-border-hover);
  color: var(--gemini-text-secondary);
  background-color: var(--gemini-bg-tertiary);
}

.prompt-mode-btn.mode-simple {
  border-color: var(--gemini-accent-green);
  color: var(--gemini-accent-green);
  background-color: var(--gemini-bg-tertiary);
}

.prompt-mode-btn.mode-professional {
  border-color: var(--gemini-accent-blue);
  color: var(--gemini-accent-blue);
  background-color: var(--gemini-bg-tertiary);
}

.prompt-mode-btn.mode-reasoning {
  border-color: color-mix(in srgb, var(--gemini-accent-yellow) 72%, transparent);
  color: var(--gemini-accent-yellow);
  background-color: color-mix(in srgb, var(--gemini-accent-yellow) 7%, var(--gemini-bg-tertiary));
}

.think-toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  padding: 6px 11px;
  border: 1px solid var(--gemini-border-color);
  border-radius: var(--gemini-radius-lg);
  background: var(--gemini-bg-tertiary);
  color: var(--gemini-text-secondary);
  font-size: 0.8rem;
  font-weight: 620;
  cursor: pointer;
  transition:
    color var(--gemini-transition-fast),
    border-color var(--gemini-transition-fast),
    background-color var(--gemini-transition-fast);
}

.think-toggle-btn:hover:not(:disabled) {
  color: var(--gemini-text-primary);
  border-color: var(--gemini-border-hover);
  background: var(--gemini-bg-hover);
}

.think-toggle-btn.active {
  color: var(--gemini-accent-yellow);
  border-color: color-mix(in srgb, var(--gemini-accent-yellow) 58%, transparent);
  background: color-mix(in srgb, var(--gemini-accent-yellow) 11%, var(--gemini-bg-tertiary));
}

.think-toggle-btn:focus-visible {
  outline: 2px solid var(--gemini-accent-yellow);
  outline-offset: 2px;
}

.think-toggle-btn:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

/* 下拉菜单样式 - 通过 teleport 渲染，需要全局样式 */
:deep(.el-dropdown-menu) {
  background-color: var(--gemini-bg-secondary) !important;
  border: 1px solid var(--gemini-border-color) !important;
  border-radius: var(--gemini-radius-lg) !important;
  box-shadow: var(--gemini-shadow-lg) !important;
  padding: 4px 0 !important;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--gemini-text-primary) !important;
  background-color: transparent !important;
  padding: 8px 16px !important;
}

:deep(.el-dropdown-menu__item:hover),
:deep(.el-dropdown-menu__item:focus) {
  background-color: var(--gemini-bg-hover) !important;
  color: var(--gemini-text-primary) !important;
}

/* 下拉菜单激活项样式 */
:deep(.el-dropdown-menu__item.is-active) {
  background-color: var(--gemini-bg-hover) !important;
  color: var(--gemini-accent-blue) !important;
  font-weight: 500;
}

:deep(.el-dropdown-menu__item .el-icon) {
  color: inherit !important;
}

:deep(.el-dropdown-menu__item span) {
  color: inherit !important;
}

/* 发送按钮 */
.send-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--gemini-radius-full);
  background: linear-gradient(135deg, var(--gemini-accent-blue) 0%, #6b9ff8 100%);
  color: white;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(138, 180, 248, 0.4);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 停止生成按钮 */
.stop-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--gemini-radius-full);
  background: var(--gemini-bg-tertiary);
  color: var(--gemini-text-primary);
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
  flex-shrink: 0;
}

.stop-btn:hover {
  background: var(--gemini-bg-hover);
  transform: scale(1.05);
}

.stop-btn:active {
  transform: scale(0.95);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .gemini-prompt-bar.position-center {
    width: 95%;
    transform: translate(-50%, calc(-50% + 10vh));
  }
  
  .gemini-prompt-bar.position-bottom {
    left: var(--gemini-spacing-md);
    right: var(--gemini-spacing-md);
    bottom: var(--gemini-spacing-md);
    max-width: none;
  }
  
  .tool-text {
    display: none;
  }
  
  .tool-btn {
    padding: 8px;
  }
  
  .prompt-input :deep(.el-textarea__inner) {
    font-size: 1rem;
  }
  
  /* 工具栏在窄屏下换行显示 */
  .toolbar {
    flex-wrap: wrap;
    gap: var(--gemini-spacing-sm);
  }
  
  .toolbar-right {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
  
  /* 隐藏提示词模式的文字标签 */
  .prompt-mode-btn .mode-label {
    display: none;
  }
  
  .prompt-mode-btn {
    padding: 6px 10px;
  }

  .think-toggle-btn {
    width: 38px;
    padding: 0;
  }

  .think-label {
    display: none;
  }
}

@media (max-width: 480px) {
  .toolbar {
    gap: var(--gemini-spacing-xs, 4px);
  }
  
  .toolbar-left,
  .toolbar-right {
    gap: var(--gemini-spacing-xs, 4px);
  }
  
  .model-trigger {
    max-width: 210px;
    padding: 5px 8px 5px 6px;
  }
  
  .model-name {
    overflow: hidden;
    font-size: 0.75rem;
    text-overflow: ellipsis;
  }
  
  /* 超窄屏下按钮更紧凑 */
  .tool-btn {
    padding: 6px;
  }
  
  .send-btn,
  .stop-btn {
    width: 36px;
    height: 36px;
  }
  
  .prompt-mode-btn {
    padding: 4px 8px;
  }
  
  /* 隐藏下拉箭头 */
  .prompt-mode-btn .dropdown-arrow {
    display: none;
  }
}
</style>
