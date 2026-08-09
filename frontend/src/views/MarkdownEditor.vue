<template>
  <div class="markdown-editor-container">
    <!-- 编辑器主体 -->
    <div class="editor-main" :class="{ 'preview-mode': showPreview }">
      <!-- 编辑区域 -->
      <div class="editor-pane" :style="{ width: showPreview ? leftPaneWidth + '%' : '100%' }">
        <input
          v-model="fileName"
          class="file-name-input"
          placeholder="未命名文档.md"
          @blur="saveToLocalStorage"
        />
        <div class="editor-tools">
            <el-button @click="insertText('**粗体**')" text size="small">B</el-button>
            <el-button @click="insertText('*斜体*')" text size="small">I</el-button>
            <el-button @click="insertText('`代码`')" text size="small">Code</el-button>
            <el-button @click="insertText('# ')" text size="small">H1</el-button>
            <el-button @click="insertText('## ')" text size="small">H2</el-button>
            <el-button @click="insertText('- ')" text size="small">List</el-button>
            <el-button @click="insertText('[链接](url)')" text size="small">Link</el-button>
            <el-button @click="insertText('![图片](url)')" text size="small">Image</el-button>
          </div>
        </div>
        <textarea
          ref="editorTextarea"
          v-model="markdownContent"
          class="markdown-textarea"
          placeholder="开始编写你的Markdown文档..."
          @input="handleInput"
          @scroll="syncScroll"
          @keydown="handleKeydown"
        ></textarea>
        <div class="word-count">
          字数: {{ wordCount }}
          <span class="save-status" :class="{ saved: isSaved }">
            {{ isSaved ? '• 已保存' : '' }}
          </span>
        </div>
      </div>

      <!-- 分割线 -->
      <div 
        v-if="showPreview" 
        class="resizer"
        @mousedown="startResize"
      ></div>

      <!-- 预览区域 -->
      <div v-if="showPreview" class="preview-pane" :style="{ width: (100 - leftPaneWidth) + '%' }">
        <div
          ref="previewContainer"
          class="markdown-preview"
          v-html="renderedMarkdown"
          @scroll="syncPreviewScroll"
        ></div>
      </div>
    </div>

    <!-- 悬浮操作按钮 - 右下角小圆形按钮 -->
    <div class="floating-buttons">
      <!-- 下载按钮 -->
      <el-tooltip content="下载文件" placement="left">
        <button
          @click="saveFile"
          class="fab-btn fab-download"
          title="下载文件"
        >
          <el-icon :size="18"><Download /></el-icon>
        </button>
      </el-tooltip>

      <!-- 重置按钮 -->
      <el-tooltip content="重置内容" placement="left">
        <button
          @click="resetContent"
          class="fab-btn fab-reset"
          title="重置内容"
        >
          <el-icon :size="18"><RefreshRight /></el-icon>
        </button>
      </el-tooltip>

      <!-- 切换预览按钮 -->
      <el-tooltip :content="showPreview ? '隐藏预览' : '显示预览'" placement="left">
        <button
          @click="togglePreview"
          class="fab-btn fab-preview"
          title="切换预览"
        >
          <el-icon :size="18"><View /></el-icon>
        </button>
      </el-tooltip>

      <!-- 返回按钮 -->
      <el-tooltip content="返回主页" placement="left">
        <button
          @click="goBack"
          class="fab-btn fab-back"
          title="返回主页"
        >
          <el-icon :size="18"><ArrowLeft /></el-icon>
        </button>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { renderMarkdown } from '@/utils/markdownFormatter'
import { 
  ArrowLeft, 
  View, 
  Document, 
  Download,
  RefreshRight
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const editorTextarea = ref(null)
const previewContainer = ref(null)
const markdownContent = ref('')
const fileName = ref('未命名文档.md')
const showPreview = ref(true)
const isSaved = ref(true)
const isScrolling = ref(false)
const leftPaneWidth = ref(50) // 左侧面板宽度百分比
const isResizing = ref(false)

// The same sanitised rendering boundary as chat/streaming output. This legacy Vue editor is not
// the primary static editor entry, but keeping it safe prevents a later route from resurrecting
// markdown-it's raw-HTML path after that dependency has been removed.
const renderedMarkdown = computed(() => {
  return renderMarkdown(markdownContent.value)
})

// 统计字数
const wordCount = computed(() => {
  if (!markdownContent.value) {
    return 0
  }
  // 统计汉字和英文单词
  const chineseWords = markdownContent.value.match(/[\u4e00-\u9fa5]/g) || []
  const englishWords = markdownContent.value.match(/[a-zA-Z0-9]+/g) || []
  return chineseWords.length + englishWords.length
})

// 返回主页
const goBack = () => {
  if (!isSaved.value) {
    if (confirm('文档未保存，确定要离开吗？')) {
      router.push('/')
    }
  } else {
    router.push('/')
  }
}

// 切换预览模式
const togglePreview = () => {
  showPreview.value = !showPreview.value
}

// 插入文本
const insertText = (text) => {
  const textarea = editorTextarea.value
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selectedText = markdownContent.value.substring(start, end)
  
  let insertedText = text
  
  // 特殊处理一些格式
  if (text.includes('**') && selectedText) {
    insertedText = `**${selectedText}**`
  } else if (text.includes('*') && selectedText && !text.includes('**')) {
    insertedText = `*${selectedText}*`
  } else if (text.includes('`') && selectedText) {
    insertedText = `\`${selectedText}\``
  } else if (text.includes('[链接]') && selectedText) {
    insertedText = `[${selectedText}](url)`
  }
  
  markdownContent.value = 
    markdownContent.value.substring(0, start) + 
    insertedText + 
    markdownContent.value.substring(end)
  
  // 设置光标位置
  nextTick(() => {
    textarea.focus()
    const newPosition = start + insertedText.length
    textarea.setSelectionRange(newPosition, newPosition)
  })
}

// 处理输入
const handleInput = () => {
  isSaved.value = false
  saveToLocalStorage()
}

// 处理键盘快捷键
const handleKeydown = (e) => {
  // Ctrl+S 保存
  if (e.ctrlKey && e.key === 's') {
    e.preventDefault()
    saveFile()
  }
  // Ctrl+B 粗体
  if (e.ctrlKey && e.key === 'b') {
    e.preventDefault()
    insertText('**粗体**')
  }
  // Ctrl+I 斜体
  if (e.ctrlKey && e.key === 'i') {
    e.preventDefault()
    insertText('*斜体*')
  }
  // Ctrl+K 行内代码
  if (e.ctrlKey && e.key === 'k') {
    e.preventDefault()
    insertText('`代码`')
  }
  // Ctrl+1 H1
  if (e.ctrlKey && e.key === '1') {
    e.preventDefault()
    insertText('# ')
  }
  // Ctrl+2 H2
  if (e.ctrlKey && e.key === '2') {
    e.preventDefault()
    insertText('## ')
  }
  // Ctrl+3 H3
  if (e.ctrlKey && e.key === '3') {
    e.preventDefault()
    insertText('### ')
  }
  // Ctrl+L 列表
  if (e.ctrlKey && e.key === 'l') {
    e.preventDefault()
    insertText('- ')
  }
  // Tab 缩进
  if (e.key === 'Tab') {
    e.preventDefault()
    insertText('  ')
  }
  // Shift+Tab 减少缩进
  if (e.shiftKey && e.key === 'Tab') {
    e.preventDefault()
    const textarea = editorTextarea.value
    const start = textarea.selectionStart
    const content = markdownContent.value

    // 找到当前行的开始位置
    let lineStart = start
    while (lineStart > 0 && content[lineStart - 1] !== '\n') {
      lineStart--
    }

    // 检查是否有两个空格可以移除
    if (content.substring(lineStart, lineStart + 2) === '  ') {
      markdownContent.value = content.substring(0, lineStart) + content.substring(lineStart + 2)
      // 调整光标位置
      nextTick(() => {
        textarea.focus()
        textarea.setSelectionRange(start - 2, start - 2)
      })
    }
  }
}

// 同步滚动
const syncScroll = () => {
  if (isScrolling.value || !showPreview.value) return
  
  isScrolling.value = true
  const textarea = editorTextarea.value
  const preview = previewContainer.value
  
  if (textarea && preview) {
    const scrollPercentage = textarea.scrollTop / (textarea.scrollHeight - textarea.clientHeight)
    preview.scrollTop = scrollPercentage * (preview.scrollHeight - preview.clientHeight)
  }
  
  setTimeout(() => {
    isScrolling.value = false
  }, 100)
}

const syncPreviewScroll = () => {
  if (isScrolling.value) return
  
  isScrolling.value = true
  const textarea = editorTextarea.value
  const preview = previewContainer.value
  
  if (textarea && preview) {
    const scrollPercentage = preview.scrollTop / (preview.scrollHeight - preview.clientHeight)
    textarea.scrollTop = scrollPercentage * (textarea.scrollHeight - textarea.clientHeight)
  }
  
  setTimeout(() => {
    isScrolling.value = false
  }, 100)
}

// 开始拖拽调节
const startResize = (e) => {
  isResizing.value = true
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

// 处理拖拽调节
const handleResize = (e) => {
  if (!isResizing.value) return
  
  const container = document.querySelector('.editor-main')
  if (container) {
    const containerRect = container.getBoundingClientRect()
    const newWidth = ((e.clientX - containerRect.left) / containerRect.width) * 100
    
    // 限制宽度在20%到80%之间
    if (newWidth >= 20 && newWidth <= 80) {
      leftPaneWidth.value = newWidth
    }
  }
}

// 停止拖拽调节
const stopResize = () => {
  isResizing.value = false
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
}

// 保存到本地存储
const saveToLocalStorage = () => {
  const data = {
    content: markdownContent.value,
    fileName: fileName.value,
    timestamp: Date.now()
  }
  localStorage.setItem('markdown-editor-draft', JSON.stringify(data))

  // 自动保存提示
  isSaved.value = true
  setTimeout(() => {
    if (isSaved.value) {
      // 如果用户没有继续输入，恢复未保存状态
      isSaved.value = false
    }
  }, 2000)
}

// 从本地存储加载
const loadFromLocalStorage = () => {
  const saved = localStorage.getItem('markdown-editor-draft')
  if (saved) {
    try {
      const data = JSON.parse(saved)
      markdownContent.value = data.content || ''
      fileName.value = data.fileName || '未命名文档.md'
    } catch (e) {
      console.error('Failed to load from localStorage:', e)
    }
  }
}

// 保存文件
const saveFile = () => {
  const blob = new Blob([markdownContent.value], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName.value.endsWith('.md') ? fileName.value : fileName.value + '.md'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  
  isSaved.value = true
  ElMessage.success('文件保存成功')
}

// 导出文件
const exportFile = () => {
  saveFile()
}

// 重置内容
const resetContent = () => {
  if (confirm('确定要重置内容吗？这将清空当前编辑的所有内容。')) {
    markdownContent.value = ''
    fileName.value = '未命名文档.md'
    isSaved.value = true
    localStorage.removeItem('markdown-editor-draft')
    ElMessage.success('内容已重置')
  }
}

// 监听内容变化
watch(markdownContent, () => {
  if (markdownContent.value) {
    isSaved.value = false
  }
}, { deep: true })

// 组件挂载
onMounted(() => {
  // 检查URL参数中是否有内容
  const urlParams = new URLSearchParams(window.location.search)
  const contentFromUrl = urlParams.get('content')
  
  if (contentFromUrl) {
    // 如果URL中有内容，使用URL中的内容
    markdownContent.value = decodeURIComponent(contentFromUrl)
    fileName.value = 'AI回复内容.md'
    isSaved.value = false
  } else {
    // 否则从本地存储加载
    loadFromLocalStorage()
  }
  
  // 添加键盘快捷键监听
  document.addEventListener('keydown', handleKeydown)
})

// 组件卸载
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  saveToLocalStorage()
})
</script>

<style scoped>
.markdown-editor-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
}

.editor-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.editor-pane {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e5e7eb;
  min-width: 20%;
  max-width: 80%;
}

.preview-pane {
  display: flex;
  flex-direction: column;
  min-width: 20%;
  max-width: 80%;
}

.resizer {
  width: 4px;
  background-color: #e5e7eb;
  cursor: col-resize;
  position: relative;
  transition: background-color 0.2s;
}

.resizer:hover {
  background-color: #3b82f6;
}

.resizer::before {
  content: '';
  position: absolute;
  top: 0;
  left: -2px;
  right: -2px;
  bottom: 0;
  background: transparent;
}

.file-name-input {
  display: block;
  width: calc(100% - 2rem);
  margin: 0.5rem auto;
  border: 1px solid transparent;
  background: #f3f4f6;
  font-size: 1rem;
  font-weight: 500;
  color: #374151;
  outline: none;
  padding: 0.5rem 0.75rem;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.file-name-input:focus {
  background-color: #ffffff;
  border: 1px solid #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.word-count {
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  color: #6b7280;
  text-align: right;
  border-top: 1px solid #e5e7eb;
  background-color: #f9fafb;
}

.save-status {
  font-size: 0.75rem;
  margin-left: 0.75rem;
  font-weight: 500;
  color: #6b7280;
  transition: all 0.3s ease;
}

.save-status.saved {
  color: #10b981;
}

.editor-tools {
  display: flex;
  gap: 0.25rem;
}

.editor-tools .el-button {
  padding: 0.25rem 0.5rem;
  font-size: 0.875rem;
  font-weight: 600;
}

.markdown-textarea {
  flex: 1;
  border: none;
  outline: none;
  padding: 1.5rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  background-color: #ffffff;
  color: #374151;
}

.markdown-textarea:focus {
  background-color: #fefefe;
}

.markdown-preview {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
  background-color: #ffffff;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  line-height: 1.6;
  color: #374151;
}

/* Markdown 样式 - 仿照 Typora */
.markdown-preview :deep(h1) {
  font-size: 2rem;
  font-weight: 700;
  margin: 2rem 0 1rem 0;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #e5e7eb;
  color: #1f2937;
}

.markdown-preview :deep(h2) {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 1.5rem 0 0.75rem 0;
  color: #1f2937;
}

.markdown-preview :deep(h3) {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 1.25rem 0 0.5rem 0;
  color: #374151;
}

.markdown-preview :deep(h4),
.markdown-preview :deep(h5),
.markdown-preview :deep(h6) {
  font-size: 1rem;
  font-weight: 600;
  margin: 1rem 0 0.5rem 0;
  color: #374151;
}

.markdown-preview :deep(p) {
  margin: 0.75rem 0;
  line-height: 1.7;
}

.markdown-preview :deep(blockquote) {
  margin: 1rem 0;
  padding: 0.75rem 1rem;
  background-color: #f9fafb;
  border-left: 4px solid #d1d5db;
  color: #6b7280;
  font-style: italic;
}

.markdown-preview :deep(code) {
  background-color: #f3f4f6;
  color: #ef4444;
  padding: 0.125rem 0.25rem;
  border-radius: 0.25rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.875rem;
}

.markdown-preview :deep(pre) {
  background-color: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  padding: 1rem;
  margin: 1rem 0;
  overflow-x: auto;
}

.markdown-preview :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
  border-radius: 0;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  margin: 0.75rem 0;
  padding-left: 1.5rem;
}

.markdown-preview :deep(li) {
  margin: 0.25rem 0;
  line-height: 1.6;
}

.markdown-preview :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
  border: 1px solid #e5e7eb;
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  padding: 0.75rem;
  text-align: left;
  border: 1px solid #e5e7eb;
}

.markdown-preview :deep(th) {
  background-color: #f9fafb;
  font-weight: 600;
}

.markdown-preview :deep(a) {
  color: #3b82f6;
  text-decoration: none;
}

.markdown-preview :deep(a:hover) {
  text-decoration: underline;
}

.markdown-preview :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.5rem;
  margin: 1rem 0;
}

.markdown-preview :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 2rem 0;
}

/* 悬浮按钮样式 - 小圆形按钮 */
.floating-buttons {
  position: fixed;
  bottom: 20px;
  right: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 1000;
}

/* FAB 按钮基础样式 */
.fab-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: all 0.25s ease;
  outline: none;
}

.fab-btn:hover {
  transform: translateY(-2px) scale(1.1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
}

.fab-btn:active {
  transform: translateY(0) scale(1);
}

/* 下载按钮 - 蓝色 */
.fab-download {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
}

.fab-download:hover {
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
}

/* 重置按钮 - 橙色 */
.fab-reset {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
}

.fab-reset:hover {
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
}

/* 预览按钮 - 绿色 */
.fab-preview {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.fab-preview:hover {
  background: linear-gradient(135deg, #34d399, #10b981);
}

/* 返回按钮 - 灰色 */
.fab-back {
  background: linear-gradient(135deg, #6b7280, #4b5563);
  color: white;
}

.fab-back:hover {
  background: linear-gradient(135deg, #9ca3af, #6b7280);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .floating-buttons {
    flex-direction: row;
    gap: 8px;
    bottom: 12px;
    right: 12px;
  }

  .fab-btn {
    width: 36px;
    height: 36px;
  }

  .editor-main.preview-mode {
    flex-direction: column;
  }

  .editor-main.preview-mode .editor-pane,
  .preview-pane {
    width: 100% !important;
    height: 50%;
    min-width: unset;
    max-width: unset;
  }

  .resizer {
    display: none;
  }

  .editor-tools {
    display: none;
  }
}
</style>
