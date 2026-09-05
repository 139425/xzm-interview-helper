<template>
  <main class="page chat-page">
    <AppHeader title="AI 对话" eyebrow="CAREER COPILOT">
      <div class="header-actions">
        <button class="icon-button" type="button" aria-label="历史对话" @click="historyOpen = true"><AppIcon name="history" /></button>
        <button class="new-chat-button" type="button" @click="newChat"><AppIcon name="plus" /> 新对话</button>
      </div>
    </AppHeader>

    <div class="chat-mode-row">
      <button :class="{ active: !deepThinking }" type="button" @click="deepThinking = false">快速回答</button>
      <button :class="{ active: deepThinking }" type="button" @click="deepThinking = true"><AppIcon name="sparkle" /> 深度分析</button>
      <span>DeepSeek</span>
    </div>

    <section ref="messageList" class="message-list" :class="{ empty: !messages.length }" @click="handleContentClick">
      <div v-if="!messages.length" class="chat-welcome">
        <div class="ai-orbit"><span><AppIcon name="sparkle" /></span><i></i></div>
        <p>你的求职搭档</p>
        <h2>今天想推进哪一步？</h2>
        <span>我可以帮你梳理项目、准备面试，或拆解岗位要求。</span>
        <div class="prompt-grid">
          <button v-for="prompt in prompts" :key="prompt.title" type="button" @click="usePrompt(prompt.text)">
            <b>{{ prompt.mark }}</b><span><strong>{{ prompt.title }}</strong><small>{{ prompt.hint }}</small></span><AppIcon name="arrow" />
          </button>
        </div>
      </div>

      <article v-for="message in messages" :key="message.id" class="message" :class="`message--${message.role}`">
        <div v-if="message.role === 'assistant'" class="message-avatar"><span>X</span></div>
        <div class="message-bubble">
          <small>{{ message.role === 'assistant' ? 'XZM AI' : '我' }}</small>
          <div v-if="message.role === 'assistant'" class="markdown-body" v-html="markdown(message.content)"></div>
          <p v-else>{{ message.content }}</p>
        </div>
      </article>

      <article v-if="loading" class="message message--assistant">
        <div class="message-avatar"><span>X</span></div>
        <div class="message-bubble thinking-bubble">
          <small>XZM AI</small><div class="thinking-dots"><i></i><i></i><i></i></div><p>{{ deepThinking ? '正在深入分析…' : '正在组织回答…' }}</p>
        </div>
      </article>
    </section>

    <form class="chat-composer" @submit.prevent="send">
      <textarea ref="composer" v-model="draft" rows="1" maxlength="20000" placeholder="输入你想讨论的问题…" @keydown.enter.exact.prevent="send"></textarea>
      <button type="submit" :disabled="loading || !draft.trim()" aria-label="发送"><AppIcon name="send" /></button>
    </form>

    <BottomSheet :open="historyOpen" title="历史对话" eyebrow="RECENT CHATS" @close="historyOpen = false">
      <div class="history-list">
        <div v-if="historyLoading" class="inline-state"><span class="mini-spinner"></span>正在加载</div>
        <button v-for="item in histories" :key="item.memoryId" type="button" @click="selectHistory(item)">
          <span><strong>{{ item.lastQuestion || '新对话' }}</strong><small>{{ item.messageCount || 0 }} 条消息 · {{ dateLabel(item.lastChatTime) }}</small></span>
          <AppIcon name="arrow" />
        </button>
        <div v-if="!historyLoading && !histories.length" class="empty-mini"><AppIcon name="history" /><p>还没有历史对话</p></div>
      </div>
    </BottomSheet>
  </main>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { chatApi, openExternal } from '@/lib/api'
import { dateLabel, markdown } from '@/lib/format'
import AppHeader from '@/components/AppHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'

const emit = defineEmits(['toast'])
const messages = ref([])
const draft = ref('')
const loading = ref(false)
const memoryId = ref(null)
const deepThinking = ref(false)
const messageList = ref(null)
const composer = ref(null)
const historyOpen = ref(false)
const historyLoading = ref(false)
const histories = ref([])
let nextId = 1

const prompts = [
  { mark: '项', title: '项目深挖', hint: '把项目经历讲清楚', text: '请作为技术面试官，帮我深挖简历中的项目经历，并给出回答框架。' },
  { mark: '岗', title: '岗位拆解', hint: '快速抓住 JD 重点', text: '我会粘贴一份岗位 JD，请帮我拆解核心能力要求和准备优先级。' },
  { mark: '面', title: '模拟问答', hint: '练一轮高频问题', text: '请模拟一轮后端开发岗位面试，每次只问我一个问题。' },
  { mark: '复', title: '面试复盘', hint: '从经历中找到改进点', text: '请引导我复盘刚结束的面试，找出回答薄弱点和下一步行动。' },
]

function scrollBottom() {
  nextTick(() => {
    if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
  })
}

function usePrompt(value) {
  draft.value = value
  composer.value?.focus()
}

async function ensureConversation() {
  if (memoryId.value) return
  const identity = await chatApi.createConversation()
  memoryId.value = identity.memoryId
}

async function send() {
  const text = draft.value.trim()
  if (!text || loading.value) return
  draft.value = ''
  messages.value.push({ id: nextId++, role: 'user', content: text })
  loading.value = true
  scrollBottom()
  try {
    await ensureConversation()
    const response = await chatApi.reply(memoryId.value, text, deepThinking.value)
    const content = typeof response === 'string' ? response : response?.message || response?.content || String(response || '')
    messages.value.push({ id: nextId++, role: 'assistant', content })
  } catch (error) {
    emit('toast', error.message, 'error')
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function newChat() {
  messages.value = []
  memoryId.value = null
  draft.value = ''
  emit('toast', '已开启新对话')
}

async function loadHistories() {
  historyLoading.value = true
  try { histories.value = await chatApi.histories() || [] }
  catch (error) { emit('toast', error.message, 'error') }
  finally { historyLoading.value = false }
}

async function selectHistory(item) {
  historyOpen.value = false
  loading.value = true
  try {
    const records = await chatApi.history(item.memoryId)
    const loaded = []
    for (const record of records || []) {
      if (record.question) loaded.push({ id: nextId++, role: 'user', content: record.question })
      if (record.record) loaded.push({ id: nextId++, role: 'assistant', content: record.record })
    }
    messages.value = loaded
    memoryId.value = item.memoryId
    scrollBottom()
  } catch (error) { emit('toast', error.message, 'error') }
  finally { loading.value = false }
}

function handleContentClick(event) {
  const anchor = event.target.closest('a')
  if (!anchor) return
  event.preventDefault()
  openExternal(anchor.href).catch((error) => emit('toast', error.message, 'error'))
}

onMounted(loadHistories)
</script>
