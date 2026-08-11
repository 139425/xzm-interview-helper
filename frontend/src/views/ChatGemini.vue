<template>
  <div
    class="xzm-chat-page"
    :style="{
      '--xzm-sidebar-current-width': `${uiStore.sidebarWidth}px`,
      '--sidebar-width': `${uiStore.sidebarWidth}px`,
    }"
  >
    <!-- 背景渐变层 -->
    <div class="xzm-chat-page__bg" aria-hidden="true"></div>

    <!-- 侧边栏（沿用 GeminiSidebar，主题已通过 token 桥接） -->
    <GeminiSidebar
      ref="sidebarRef"
      :mode="uiStore.currentMode"
      @new-chat="handleNewChat"
      @mode-change="handleModeChange"
      @interview-select="handleInterviewSelect"
      @history-selecting="handleHistorySelecting"
    />

    <main
      class="xzm-chat-page__main"
      :style="{ marginLeft: `${uiStore.sidebarWidth}px` }"
    >
      <ChatTopBar
        :questions="userQuestions"
        :active-index="currentQuestionIndex"
        :sidebar-expanded="uiStore.sidebarExpanded"
        :show-sidebar-toggle="uiStore.isMobile"
        :popover-width="uiStore.isMobile ? 320 : 440"
        @toggle-sidebar="uiStore.toggleSidebar"
        @select-question="scrollToMessage"
      >
        <template #tools>
          <ChatToolbar
            v-if="uiStore.isMobile"
            is-mobile
            @open-md-editor="openMarkdownEditor"
            @open-code-editor="openCodeEditor"
            @open-resume-editor="openResumeEditor"
            @open-wechat-modal="showWechatModal = true"
            @scroll-to-bottom="forceScrollToBottom"
            @toggle-question-nav="toggleQuestionNav"
            @open-network-review="openNetworkReview"
            @open-html-showcase="openHtmlShowcase"
          />
        </template>
        <template #user>
          <UserAvatar />
        </template>
      </ChatTopBar>

      <section class="xzm-chat-page__content">
        <!-- 欢迎页（V2 极简：仅居中问候） -->
        <WelcomeSection
          v-if="uiStore.showWelcome && chatStore.messages.length === 0 && !chatStream.isStreaming.value"
        />

        <!-- 消息流 -->
        <div
          v-if="hasMessages || chatStream.isStreaming.value"
          ref="messagesContainer"
          class="xzm-chat-page__messages xzm-scroll"
          role="log"
          aria-live="polite"
          aria-relevant="additions text"
          aria-label="对话消息"
          @scroll="handleScroll"
        >
          <div class="xzm-chat-page__messages-inner">
            <div
              v-for="(message, idx) in chatStore.messages"
              :key="message.id"
              :data-message-id="message.id"
              class="xzm-chat-page__message-row"
            >
              <MessageItem
                :role="message.role"
                :content="message.content"
                :thinking-content="message.thinkingContent || ''"
                :pipeline-stages="message.pipelineStages || []"
                :is-streaming="false"
                :question-title="getQuestionTitle(idx)"
                @open-md-editor="handleOpenMarkdownEditor"
                @regenerate="handleRegenerate(message.id)"
              />
            </div>

            <!-- 当前流式消息 -->
            <div
              v-if="chatStream.isStreaming.value || streamingHasContent"
              class="xzm-chat-page__message-row"
            >
              <MessageItem
                role="assistant"
                content=""
                :content-stream="chatStream.contentStream"
                :thinking-stream="chatStream.thinkingStream"
                :pipeline-stages="chatStream.stages.value"
                :is-streaming="chatStream.isStreaming.value"
                :is-thinking="chatStream.isThinking.value"
              />
            </div>
          </div>
        </div>

        <QuestionNav
          v-model:visible="showQuestionNav"
          :questions="userQuestions"
          :current-id="currentQuestionId"
          @select="scrollToMessage"
        />
      </section>

      <!-- 微信二维码弹窗 -->
      <el-dialog v-model="showWechatModal" title="扫码加微信" width="90%" style="max-width: 380px">
        <div class="xzm-wechat-modal">
          <img
            v-if="wechatQrUrl"
            :src="wechatQrUrl"
            alt="微信二维码"
            class="xzm-wechat-modal__qr"
          />
          <p class="xzm-wechat-modal__caption">{{ wechatQrUrl ? '扫描添加微信' : '暂未配置联系二维码' }}</p>
        </div>
      </el-dialog>

      <!-- 输入区 -->
      <GeminiPromptBar
        ref="promptBarRef"
        v-model="userInput"
        :disabled="chatStream.isStreaming.value"
        v-model:prompt-mode="promptMode"
        v-model:thinking-mode="deepThinkingEnabled"
        :current-model-id="currentModel.id"
        :enable-voice="true"
        :is-streaming="chatStream.isStreaming.value"
        @send="handleSend"
        @stop="handleStop"
        @change-model="handleChangeModel"
      />
    </main>

    <!-- 桌面工具栏（放在根层级，避免被 overflow:hidden 裁切） -->
    <ChatToolbar
      v-if="!uiStore.isMobile"
      @open-md-editor="openMarkdownEditor"
      @open-code-editor="openCodeEditor"
      @open-resume-editor="openResumeEditor"
      @open-wechat-modal="showWechatModal = true"
      @scroll-to-bottom="forceScrollToBottom"
      @toggle-question-nav="toggleQuestionNav"
      @open-network-review="openNetworkReview"
      @open-html-showcase="openHtmlShowcase"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUIStore } from '../stores/ui'
import { useChatStore } from '../stores/chat'
import { useUserStore } from '../stores/user'
import { useChatStream } from '../composables/useChatStream'
import {
  CHAT_MODEL_STORAGE_KEY,
  DEFAULT_CHAT_MODEL_ID,
  getChatModel
} from '../config/models'

import GeminiSidebar from '../components/GeminiSidebar.vue'
import GeminiPromptBar from '../components/GeminiPromptBar.vue'
import WelcomeSection from '../components/WelcomeSection.vue'
import UserAvatar from '../components/UserAvatar.vue'
import MessageItem from '../components/MessageItem.vue'
import ChatTopBar from '../components/chat/ChatTopBar.vue'
import ChatToolbar from '../components/chat/ChatToolbar.vue'
import QuestionNav from '../components/chat/QuestionNav.vue'

// ============== Stores ==============
const router = useRouter()
const uiStore = useUIStore()
const chatStore = useChatStore()
const userStore = useUserStore()
const { promptMode } = storeToRefs(uiStore)

// ============== Refs ==============
const sidebarRef = ref(null)
const promptBarRef = ref(null)
const messagesContainer = ref(null)

const userInput = ref('')
const savedModelId = localStorage.getItem(CHAT_MODEL_STORAGE_KEY)
const currentModelId = ref(getChatModel(savedModelId || DEFAULT_CHAT_MODEL_ID).id)
const currentModel = computed(() => getChatModel(currentModelId.value))
const deepThinkingEnabled = ref(localStorage.getItem('chatThinkingEnabled') === 'true')
const wechatQrUrl = String(import.meta.env.VITE_WECHAT_QR_URL || '').trim()
const showWechatModal = ref(false)
const showQuestionNav = ref(false)

// ============== 流式管线 ==============
const chatStream = useChatStream()

const streamingHasContent = computed(() => {
  return (chatStream.contentStream.blocks.value?.length || 0) > 0
    || (chatStream.thinkingStream.blocks.value?.length || 0) > 0
})

// ============== 滚动控制 ==============
const userHasScrolledUp = ref(false)
const lastScrollTop = ref(0)
const lastUserScrollAt = ref(0)
let scrollScheduled = false

function isNearBottom() {
  const c = messagesContainer.value
  if (!c) return true
  return c.scrollHeight - c.scrollTop - c.clientHeight < 100
}

function handleScroll() {
  const c = messagesContainer.value
  if (!c) return
  const top = c.scrollTop
  lastUserScrollAt.value = Date.now()
  if (top < lastScrollTop.value && !isNearBottom()) {
    userHasScrolledUp.value = true
  }
  if (isNearBottom()) {
    userHasScrolledUp.value = false
  }
  lastScrollTop.value = top
  scheduleQuestionUpdate()
}

function scrollToBottom(force = false) {
  if (!force && Date.now() - lastUserScrollAt.value < 1200) return
  if (userHasScrolledUp.value && !force) return
  nextTick(() => {
    const c = messagesContainer.value
    if (!c) return
    const behavior = chatStream.isStreaming.value ? 'auto' : 'smooth'
    try { c.scrollTo({ top: c.scrollHeight, behavior }) }
    catch { c.scrollTop = c.scrollHeight }
    scheduleQuestionUpdate()
  })
}

function scheduleScrollToBottom(force = false) {
  if (scrollScheduled) return
  scrollScheduled = true
  requestAnimationFrame(() => {
    scrollScheduled = false
    scrollToBottom(force)
  })
}

function forceScrollToBottom() {
  userHasScrolledUp.value = false
  scheduleScrollToBottom(true)
}

// ============== 题目导航 ==============
const userQuestions = computed(() => chatStore.messages.filter((m) => m.role === 'user'))
const currentQuestionIndex = ref(-1)
const currentQuestionId = computed(() => {
  const i = currentQuestionIndex.value
  return i >= 0 && userQuestions.value[i] ? userQuestions.value[i].id : ''
})

let questionUpdateScheduled = false
function scheduleQuestionUpdate() {
  if (questionUpdateScheduled) return
  questionUpdateScheduled = true
  requestAnimationFrame(() => {
    questionUpdateScheduled = false
    updateCurrentQuestion()
  })
}

function updateCurrentQuestion() {
  const c = messagesContainer.value
  if (!c) return
  const qs = userQuestions.value
  if (!qs.length) { currentQuestionIndex.value = -1; return }
  const containerTop = c.getBoundingClientRect().top
  const threshold = 80
  let matched = -1
  for (let i = 0; i < qs.length; i++) {
    const el = c.querySelector(`[data-message-id="${qs[i].id}"]`)
    if (!el) continue
    const top = el.getBoundingClientRect().top
    if (top - containerTop <= threshold) matched = i
    else if (matched !== -1) break
  }
  if (matched === -1) matched = 0
  currentQuestionIndex.value = matched
}

function toggleQuestionNav() {
  showQuestionNav.value = !showQuestionNav.value
}

function scrollToMessage(messageId) {
  if (!messageId) return
  const c = messagesContainer.value
  if (!c) return
  const el = c.querySelector(`[data-message-id="${messageId}"]`)
  if (!el) return
  userHasScrolledUp.value = false
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  el.classList.add('is-highlighted')
  setTimeout(() => el.classList.remove('is-highlighted'), 1800)
}

// ============== 主流程 ==============
const hasMessages = computed(() => chatStore.messages.length > 0)
let runGeneration = 0

async function handleSend(message) {
  if (!message || !message.trim()) return
  if (chatStream.isStreaming.value) return

  if (!chatStore.currentMemoryId) {
    chatStore.createNewChat()
  }

  if (chatStore.messages.length === 0) {
    uiStore.movePromptBarToBottom()
  }

  chatStore.addUserMessage(message)
  uiStore.hideWelcome()
  await runStream(message)
}

// 执行一次流式对话（不负责插入用户消息，供 send 与 regenerate 复用）
async function runStream(message) {
  const generation = ++runGeneration
  userHasScrolledUp.value = false

  try {
    await nextTick()
    if (generation !== runGeneration) return
    scrollToBottom(true)

    const result = await chatStream.send({
      memoryId: chatStore.currentMemoryId,
      message,
      promptMode: promptMode.value,
      deepThinking: deepThinkingEnabled.value,
      provider: currentModel.value.provider,
      modelName: currentModel.value.modelName,
    })

    if (generation !== runGeneration || result.cancelled || result.discarded) return
    // stop() 正常 resolve 且保留部分内容，因此仍加入当前对话视图；服务端只持久化
    // 收到 [DONE] 的完整回复，避免把不完整文本伪装成正式历史记录。
    if (result.content || result.thinking) {
      chatStore.addAssistantMessage(
        result.content || '',
        result.thinking || '',
        result.pipelineStages || [],
      )
    }
    setTimeout(() => {
      if (generation === runGeneration) refreshSidebarHistory()
    }, 500)
  } catch (err) {
    if (generation !== runGeneration) return
    /* eslint-disable no-console */
    console.error('[chat] 流式请求失败:', err)
    const partialContent = chatStream.contentStream.getRaw()
    const partialThinking = chatStream.thinkingStream.getRaw()
    if (partialContent || partialThinking) {
      chatStore.addAssistantMessage(
        partialContent || '',
        partialThinking || '',
        chatStream.stages.value.map((stage) => ({ ...stage })),
      )
    }
    ElMessage.error('对话失败：' + (err?.message || '未知错误'))
  } finally {
    // 旧 run 不得 reset/释放后续 generation 的流状态。
    if (generation === runGeneration) {
      chatStream.reset()
      chatStore.loading = false
    }
  }
}

// 停止生成：中止当前流，保留已生成部分（useChatStream 内部按成功处理）
function handleStop() {
  chatStream.stop()
}

function handleHistorySelecting() {
  // Switching conversations is not a user "stop": the old generation must be
  // discarded so it cannot append its eventual result to the selected history.
  runGeneration++
  chatStream.cancel()
  // cancel() intentionally snapshots the discarded request for its own Promise, so it does not
  // clear shared render buffers.  A history switch owns the view transition and must clear those
  // buffers immediately; otherwise the old partial answer/timeline can appear under the newly
  // loaded conversation.
  chatStream.reset()
}

// 重新生成：定位该 AI 消息的前序用户提问，裁剪该消息及其后，重发
async function handleRegenerate(messageId) {
  if (chatStream.isStreaming.value) return
  const idx = chatStore.messages.findIndex((m) => m.id === messageId)
  if (idx < 0) return

  let userMsg = null
  for (let i = idx - 1; i >= 0; i--) {
    if (chatStore.messages[i].role === 'user') {
      userMsg = chatStore.messages[i]
      break
    }
  }
  if (!userMsg) {
    ElMessage.warning('未找到对应的提问，无法重新生成')
    return
  }

  // 裁剪掉这条 AI 回复及其后的所有消息，保留前序用户提问
  chatStore.messages.splice(idx)
  await runStream(userMsg.content)
}

function handleNewChat() {
  // 先失效视图 run，再丢弃底层请求；旧 Promise 后续完成时不能写入新会话。
  runGeneration += 1
  chatStream.cancel()
  chatStore.createNewChat()
  chatStream.reset()
  userInput.value = ''
  showQuestionNav.value = false
  currentQuestionIndex.value = -1
  uiStore.resetPromptBarToCenter()
  uiStore.displayWelcome()
  ElMessage.success('已创建新会话')
}

function handleModeChange(mode) {
  if (mode === 'interview') router.push('/aiInterview')
}

function handleInterviewSelect(data) {
  sessionStorage.setItem('interviewReportData', JSON.stringify(data))
  router.push('/interview-report')
}

function handleChangeModel(modelId) {
  if (chatStream.isStreaming.value) return
  const model = getChatModel(modelId)
  currentModelId.value = model.id
  localStorage.setItem(CHAT_MODEL_STORAGE_KEY, model.id)
  ElMessage.success('已切换模型：' + model.label)
}

watch(deepThinkingEnabled, (enabled) => {
  localStorage.setItem('chatThinkingEnabled', String(enabled))
})

function handleOpenMarkdownEditor() {
  // MessageItem 内部已 window.open，这里仅记录事件，预留扩展
}

function getQuestionTitle(idx) {
  if (idx <= 0) return ''
  const prev = chatStore.messages[idx - 1]
  if (prev && prev.role === 'user') {
    return prev.content.slice(0, 60)
  }
  return ''
}

async function refreshSidebarHistory() {
  if (sidebarRef.value && typeof sidebarRef.value.loadHistoryList === 'function') {
    await sidebarRef.value.loadHistoryList(true)
  }
}

// 工具栏菜单
const openMarkdownEditor = () => window.open('/markdown-editor.html', '_blank', 'noopener')
const openCodeEditor = () => window.open('/code-editor.html', '_blank', 'noopener')
const openResumeEditor = () => window.open('/resume_editor.html', '_blank', 'noopener')
const openNetworkReview = () => window.open('/review/index.html', '_blank', 'noopener')
const openHtmlShowcase = () => window.open('/拓展实验实验报告.html', '_blank', 'noopener')

// ============== Watchers ==============
watch(promptMode, (v) => {
  const labels = { none: '无', simple: '简洁', professional: '专业', reasoning: '推演' }
  ElMessage.success('提示词模式：' + (labels[v] || '专业'))
})

// 跟随消息更新自动滚动
watch(() => chatStore.messages.length, (n) => {
  if (n > 0 && uiStore.promptBarPosition === 'center') {
    uiStore.movePromptBarToBottom()
  }
  scheduleScrollToBottom()
})

watch(
  () => [
    chatStream.contentStream.blocks.value?.length,
    chatStream.thinkingStream.blocks.value?.length,
  ],
  () => scheduleScrollToBottom()
)

watch(userQuestions, () => {
  nextTick(() => scheduleQuestionUpdate())
})

// ============== 生命周期 ==============
onMounted(() => {
  uiStore.initialize()
  if (chatStore.messages.length > 0) {
    uiStore.movePromptBarToBottom()
    uiStore.hideWelcome()
  }
  nextTick(() => scheduleQuestionUpdate())
})

onUnmounted(() => {
  runGeneration += 1
  uiStore.cleanup()
  chatStream.cancel()
})
</script>

<style scoped>
.xzm-chat-page {
  position: relative;
  display: flex;
  width: 100vw;
  max-width: 100vw;
  height: 100vh;
  overflow: hidden;
  background-color: var(--xzm-surface-0);
  color: var(--xzm-text-primary);
}

/* 背景层（V2 极简：纯单色） */
.xzm-chat-page__bg {
  position: absolute;
  inset: 0;
  background-color: var(--xzm-surface-0);
  pointer-events: none;
  z-index: 0;
}

.xzm-chat-page__main {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
  transition: margin-left 260ms cubic-bezier(0.4, 0.0, 0.2, 1);
}

.xzm-chat-page__content {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  min-height: 0;
}

.xzm-chat-page__messages {
  position: absolute;
  inset: 0 0 var(--xzm-prompt-bar-height) 0;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 32px 24px 24px;
  scroll-behavior: smooth;
  box-sizing: border-box;
}

.xzm-chat-page__messages-inner {
  max-width: var(--xzm-content-max-width);
  margin: 0 auto;
  padding-bottom: 24px;
}

.xzm-chat-page__message-row {
  width: 100%;
  max-width: 100%;
  border-radius: var(--xzm-radius-lg);
  transition: background-color var(--xzm-duration-normal) var(--xzm-ease-out);
}

.xzm-chat-page__message-row.is-highlighted {
  background-color: var(--xzm-surface-2);
  animation: xzm-msg-highlight 1.2s var(--xzm-ease-out);
}

@keyframes xzm-msg-highlight {
  0%   { background-color: var(--xzm-surface-2); }
  100% { background-color: transparent; }
}

/* 微信弹窗 */
.xzm-wechat-modal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 16px 8px;
}
.xzm-wechat-modal__qr {
  max-width: 100%;
  height: auto;
  border-radius: var(--xzm-radius-lg);
}
.xzm-wechat-modal__caption {
  margin: 0;
  font-size: var(--xzm-fs-sm);
  color: var(--xzm-text-tertiary);
}

@media (max-width: 768px) {
  .xzm-chat-page__main { margin-left: 0 !important; }
  .xzm-chat-page__messages {
    padding: 16px 14px 12px;
    bottom: 120px;
  }
}
</style>
