<template>
  <div
    class="xzm-chat-page"
    :class="{ 'has-outline': showQuestionNav && !outlineOverlay }"
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
      mode="chat"
      @new-chat="handleNewChat"
      @mode-change="handleModeChange"
      @interview-select="handleInterviewSelect"
      @history-selecting="handleHistorySelecting"
    />

    <main
      ref="mainPanel" class="xzm-chat-page__main"
      :inert="
        (uiStore.isMobile && uiStore.sidebarExpanded) ||
        (showQuestionNav && outlineOverlay)
      "
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
          <button
            v-if="userQuestions.length"
            type="button"
            class="chat-outline-toggle"
            :class="{ active: showQuestionNav }"
            :aria-expanded="showQuestionNav"
            aria-label="题目目录"
            @click="toggleQuestionNav"
          >
            <svg
              viewBox="0 0 24 24"
              width="18"
              height="18"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              aria-hidden="true"
            >
              <path d="M8 6h12M8 12h12M8 18h12M3 6h1M3 12h1M3 18h1" />
            </svg>
          </button>
          <ChatToolbar
            :is-mobile="uiStore.isMobile"
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

      <div
        class="chat-workspace"
        :class="{
          'is-welcome':
            uiStore.showWelcome &&
            !hasMessages &&
            !chatStream.isStreaming.value,
        }"
      >
        <section class="xzm-chat-page__content">
          <!-- 欢迎页（V2 极简：仅居中问候） -->
          <WelcomeSection
            v-if="
              uiStore.showWelcome &&
              chatStore.messages.length === 0 &&
              !chatStream.isStreaming.value
            "
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
            @wheel.passive="handleReadingIntent"
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

          <Transition name="latest-message">
            <button
              v-if="userHasScrolledUp"
              type="button"
              class="chat-latest"
              @click="forceScrollToBottom"
              aria-label="查看最新回复"
            >
              ↓ <span>查看最新回复</span>
            </button>
          </Transition>
        </section>

        <!-- 微信二维码弹窗 -->
        <el-dialog
          v-model="showWechatModal"
          title="扫码加微信"
          width="90%"
          style="max-width: 380px"
        >
          <div class="xzm-wechat-modal">
            <img
              v-if="wechatQrUrl"
              :src="wechatQrUrl"
              alt="微信二维码"
              class="xzm-wechat-modal__qr"
            />
            <p class="xzm-wechat-modal__caption">
              {{ wechatQrUrl ? "扫描添加微信" : "暂未配置联系二维码" }}
            </p>
          </div>
        </el-dialog>

        <!-- 输入区 -->
        <div ref="composerRef" class="chat-composer">
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
          <p class="chat-composer__hint">Enter 发送 · Shift + Enter 换行</p>
        </div>
      </div>
    </main>
    <QuestionNav
      v-model:visible="showQuestionNav"
      :questions="userQuestions"
      :current-id="currentQuestionId"
      :overlay="outlineOverlay"
      @select="scrollToMessage"
    />
  </div>
</template>

<script setup>
import { usePanelMotion } from "@/composables/usePanelMotion";
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from "vue";
import { storeToRefs } from "pinia";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUIStore } from "../stores/ui";
import { useChatStore } from "../stores/chat";
import { useUserStore } from "../stores/user";
import { useChatStream } from "../composables/useChatStream";
import {
  CHAT_MODEL_STORAGE_KEY,
  DEFAULT_CHAT_MODEL_ID,
  getChatModel,
} from "../config/models";

import GeminiSidebar from "../components/GeminiSidebar.vue";
import GeminiPromptBar from "../components/GeminiPromptBar.vue";
import WelcomeSection from "../components/WelcomeSection.vue";
import UserAvatar from "../components/UserAvatar.vue";
import MessageItem from "../components/MessageItem.vue";
import ChatTopBar from "../components/chat/ChatTopBar.vue";
import ChatToolbar from "../components/chat/ChatToolbar.vue";
import QuestionNav from "../components/chat/QuestionNav.vue";

// ============== Stores ==============
const router = useRouter();
const route = useRoute();
const uiStore = useUIStore();
const mainPanel = ref(null);
usePanelMotion(mainPanel, () => uiStore.sidebarWidth);
const chatStore = useChatStore();
const userStore = useUserStore();
const { promptMode } = storeToRefs(uiStore);

// ============== Refs ==============
const sidebarRef = ref(null);
const promptBarRef = ref(null);
const messagesContainer = ref(null);
const composerRef = ref(null);
let composerMotion;
watch(
  () => uiStore.promptBarPosition,
  async () => {
    const element = composerRef.value;
    if (
      !element ||
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    )
      return;
    const previous = element.getBoundingClientRect();
    composerMotion?.cancel();
    await nextTick();
    if (!element.isConnected || typeof element.animate !== "function") return;
    const current = element.getBoundingClientRect();
    const delta = previous.top - current.top;
    if (Math.abs(delta) < 1) return;
    composerMotion = element.animate(
      [{ transform: `translateY(${delta}px)` }, { transform: "translateY(0)" }],
      { duration: 280, easing: "cubic-bezier(0.22, 0.72, 0.2, 1)" },
    );
  },
);

const userInput = ref("");
const savedModelId = localStorage.getItem(CHAT_MODEL_STORAGE_KEY);
const currentModelId = ref(
  getChatModel(savedModelId || DEFAULT_CHAT_MODEL_ID).id,
);
const currentModel = computed(() => getChatModel(currentModelId.value));
const deepThinkingEnabled = ref(
  localStorage.getItem("chatThinkingEnabled") === "true",
);
const showWechatModal = ref(false);
const wechatQrUrl = String(import.meta.env.VITE_WECHAT_QR_URL || "").trim();
const showQuestionNav = ref(false);
const outlineOverlay = computed(() => uiStore.viewportWidth < 1400);
watch(
  () => uiStore.sidebarExpanded,
  (open) => {
    if (open && uiStore.isMobile) showQuestionNav.value = false;
  },
);

// ============== 流式管线 ==============
const chatStream = useChatStream();

const streamingHasContent = computed(() => {
  return (
    (chatStream.contentStream.blocks.value?.length || 0) > 0 ||
    (chatStream.thinkingStream.blocks.value?.length || 0) > 0
  );
});

// ============== 滚动控制 ==============
const userHasScrolledUp = ref(false);
const lastScrollTop = ref(0);
let contentObserver;
let observedContent;
let scrollFrame = 0;
let followFrame = 0;
let autoScrollTop = -1;
let disposed = false;
let scrollScheduled = false;

function isNearBottom() {
  const c = messagesContainer.value;
  if (!c) return true;
  return c.scrollHeight - c.scrollTop - c.clientHeight < 100;
}

function handleReadingIntent(event) {
  if (event.deltaY < 0) {
    userHasScrolledUp.value = true;
    cancelAnimationFrame(followFrame);
    followFrame = 0;
  }
}

function handleScroll() {
  const c = messagesContainer.value;
  if (!c) return;
  const top = c.scrollTop;
  const isAutomatic = Math.abs(top - autoScrollTop) < 1;
  if (!isAutomatic && top < lastScrollTop.value) userHasScrolledUp.value = true;
  if (!isAutomatic && top > lastScrollTop.value && isNearBottom()) userHasScrolledUp.value = false;
  lastScrollTop.value = top;
  scheduleQuestionUpdate();
}

function scrollToBottom(force = false) {
  if (userHasScrolledUp.value && !force) return;
  nextTick(() => {
    const c = messagesContainer.value;
    if (!c || disposed || (userHasScrolledUp.value && !force)) return;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!chatStream.isStreaming.value || reduce || force) {
      cancelAnimationFrame(followFrame); followFrame = 0;
      const target = Math.max(0, c.scrollHeight - c.clientHeight);
      autoScrollTop = target;
      c.scrollTo({ top: target, behavior: reduce || chatStream.isStreaming.value ? 'auto' : 'smooth' });
      return;
    }
    if (followFrame) return;
    let previous = performance.now();
    const follow = (now) => {
      followFrame = 0;
      if (disposed || userHasScrolledUp.value || c !== messagesContainer.value) return;
      const target = Math.max(0, c.scrollHeight - c.clientHeight);
      const distance = target - c.scrollTop;
      const dt = Math.min(50, Math.max(1, now - previous)); previous = now;
      const next = Math.abs(distance) <= 2 || Math.abs(distance) > c.clientHeight
        ? target : c.scrollTop + distance * (1 - Math.exp(-dt / 38));
      c.scrollTop = next;
      autoScrollTop = c.scrollTop;
      if (Math.abs(target - c.scrollTop) >= 1) followFrame = requestAnimationFrame(follow);
    };
    followFrame = requestAnimationFrame(follow);
  });
}

function scheduleScrollToBottom(force = false) {
  if (scrollScheduled) return;
  scrollScheduled = true;
  scrollFrame = requestAnimationFrame(() => {
    if (disposed) return;
    scrollScheduled = false;
    scrollToBottom(force);
  });
}

function forceScrollToBottom() {
  userHasScrolledUp.value = false;
  scheduleScrollToBottom(true);
}

// ============== 题目导航 ==============
const userQuestions = computed(() =>
  chatStore.messages.filter((m) => m.role === "user"),
);
const currentQuestionIndex = ref(-1);
const currentQuestionId = computed(() => {
  const i = currentQuestionIndex.value;
  return i >= 0 && userQuestions.value[i] ? userQuestions.value[i].id : "";
});

let questionUpdateScheduled = false;
function scheduleQuestionUpdate() {
  if (questionUpdateScheduled) return;
  questionUpdateScheduled = true;
  requestAnimationFrame(() => {
    questionUpdateScheduled = false;
    updateCurrentQuestion();
  });
}

function updateCurrentQuestion() {
  const c = messagesContainer.value;
  if (!c) return;
  const qs = userQuestions.value;
  if (!qs.length) {
    currentQuestionIndex.value = -1;
    return;
  }
  const containerTop = c.getBoundingClientRect().top;
  const threshold = 80;
  let matched = -1;
  for (let i = 0; i < qs.length; i++) {
    const el = c.querySelector(`[data-message-id="${qs[i].id}"]`);
    if (!el) continue;
    const top = el.getBoundingClientRect().top;
    if (top - containerTop <= threshold) matched = i;
    else if (matched !== -1) break;
  }
  if (matched === -1) matched = 0;
  currentQuestionIndex.value = matched;
}

function toggleQuestionNav() {
  showQuestionNav.value = !showQuestionNav.value;
}

function scrollToMessage(messageId) {
  if (!messageId) return;
  const c = messagesContainer.value;
  if (!c) return;
  const el = c.querySelector(`[data-message-id="${messageId}"]`);
  if (!el) return;
  userHasScrolledUp.value = true;
  el.scrollIntoView({
    behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "auto"
      : "smooth",
    block: "start",
  });
  el.classList.add("is-highlighted");
  setTimeout(() => el.classList.remove("is-highlighted"), 1800);
}

// ============== 主流程 ==============
const hasMessages = computed(() => chatStore.messages.length > 0);
let runGeneration = 0;

async function handleSend(message) {
  if (!message || !message.trim()) return;
  if (chatStream.isStreaming.value) return;

  if (!chatStore.currentMemoryId) {
    try {
      const identity = await chatStore.createNewChat();
      await router.replace({
        name: "Chat",
        params: { conversationId: identity.conversationId },
      });
    } catch (error) {
      if (!userInput.value) userInput.value = message;
      ElMessage.error(
        error?.response?.data?.message || "创建会话失败，请稍后重试",
      );
      return;
    }
  }

  if (chatStore.messages.length === 0) {
    uiStore.movePromptBarToBottom();
  }

  chatStore.addUserMessage(message);
  uiStore.hideWelcome();
  await runStream(message);
}

// 执行一次流式对话（不负责插入用户消息，供 send 与 regenerate 复用）
async function runStream(message) {
  const generation = ++runGeneration;
  userHasScrolledUp.value = false;

  try {
    await nextTick();
    if (generation !== runGeneration) return;
    scrollToBottom(true);

    const result = await chatStream.send({
      memoryId: chatStore.currentMemoryId,
      message,
      promptMode: promptMode.value,
      deepThinking: deepThinkingEnabled.value,
      provider: currentModel.value.provider,
      modelName: currentModel.value.modelName,
    });

    if (generation !== runGeneration || result.cancelled || result.discarded)
      return;
    // stop() 正常 resolve 且保留部分内容，因此仍加入当前对话视图；服务端只持久化
    // 收到 [DONE] 的完整回复，避免把不完整文本伪装成正式历史记录。
    if (result.content || result.thinking) {
      chatStore.addAssistantMessage(
        result.content || "",
        result.thinking || "",
        result.pipelineStages || [],
      );
    }
    setTimeout(() => {
      if (generation === runGeneration) refreshSidebarHistory();
    }, 500);
  } catch (err) {
    if (generation !== runGeneration) return;
    /* eslint-disable no-console */
    console.error("[chat] 流式请求失败:", err);
    const partialContent = chatStream.contentStream.getRaw();
    const partialThinking = chatStream.thinkingStream.getRaw();
    if (partialContent || partialThinking) {
      chatStore.addAssistantMessage(
        partialContent || "",
        partialThinking || "",
        chatStream.stages.value.map((stage) => ({ ...stage })),
      );
    }
    ElMessage.error("对话失败：" + (err?.message || "未知错误"));
  } finally {
    // 旧 run 不得 reset/释放后续 generation 的流状态。
    if (generation === runGeneration) {
      chatStream.reset();
      chatStore.loading = false;
    }
  }
}

// 停止生成：中止当前流，保留已生成部分（useChatStream 内部按成功处理）
function handleStop() {
  chatStream.stop();
}

function handleHistorySelecting() {
  userHasScrolledUp.value = false;
  lastScrollTop.value = 0;
  // Switching conversations is not a user "stop": the old generation must be
  // discarded so it cannot append its eventual result to the selected history.
  runGeneration++;
  chatStream.cancel();
  // cancel() intentionally snapshots the discarded request for its own Promise, so it does not
  // clear shared render buffers.  A history switch owns the view transition and must clear those
  // buffers immediately; otherwise the old partial answer/timeline can appear under the newly
  // loaded conversation.
  chatStream.reset();
}

// 重新生成：定位该 AI 消息的前序用户提问，裁剪该消息及其后，重发
async function handleRegenerate(messageId) {
  if (chatStream.isStreaming.value) return;
  const idx = chatStore.messages.findIndex((m) => m.id === messageId);
  if (idx < 0) return;

  let userMsg = null;
  for (let i = idx - 1; i >= 0; i--) {
    if (chatStore.messages[i].role === "user") {
      userMsg = chatStore.messages[i];
      break;
    }
  }
  if (!userMsg) {
    ElMessage.warning("未找到对应的提问，无法重新生成");
    return;
  }

  // 裁剪掉这条 AI 回复及其后的所有消息，保留前序用户提问
  chatStore.messages.splice(idx);
  await runStream(userMsg.content);
}

async function handleNewChat() {
  userHasScrolledUp.value = false;
  lastScrollTop.value = 0;
  // 先失效视图 run，再丢弃底层请求；旧 Promise 后续完成时不能写入新会话。
  runGeneration += 1;
  chatStream.cancel();
  chatStore.resetChat();
  await router.push({ name: "Chat" });
  chatStream.reset();
  userInput.value = "";
  showQuestionNav.value = false;
  currentQuestionIndex.value = -1;
  uiStore.resetPromptBarToCenter();
  uiStore.displayWelcome();
  ElMessage.success("已创建新会话");
}

function handleModeChange(mode) {
  if (mode === "interview") router.push("/aiInterview");
}

function handleInterviewSelect(data) {
  sessionStorage.setItem("interviewReportData", JSON.stringify(data));
  router.push("/interview-report");
}

function handleChangeModel(modelId) {
  if (chatStream.isStreaming.value) return;
  const model = getChatModel(modelId);
  currentModelId.value = model.id;
  localStorage.setItem(CHAT_MODEL_STORAGE_KEY, model.id);
  ElMessage.success("已切换模型：" + model.label);
}

watch(deepThinkingEnabled, (enabled) => {
  localStorage.setItem("chatThinkingEnabled", String(enabled));
});

function handleOpenMarkdownEditor() {
  // MessageItem 内部已 window.open，这里仅记录事件，预留扩展
}

function getQuestionTitle(idx) {
  if (idx <= 0) return "";
  const prev = chatStore.messages[idx - 1];
  if (prev && prev.role === "user") {
    return prev.content.slice(0, 60);
  }
  return "";
}

async function refreshSidebarHistory() {
  if (
    sidebarRef.value &&
    typeof sidebarRef.value.loadHistoryList === "function"
  ) {
    await sidebarRef.value.loadHistoryList(true);
  }
}

// 工具栏菜单
const openMarkdownEditor = () =>
  window.open("/markdown-editor.html", "_blank", "noopener");
const openCodeEditor = () =>
  window.open("/code-editor.html", "_blank", "noopener");
const openResumeEditor = () =>
  window.open("/resume_editor.html", "_blank", "noopener");
const openNetworkReview = () =>
  window.open("/review/index.html", "_blank", "noopener");
const openHtmlShowcase = () =>
  window.open("/html-preview.html", "_blank", "noopener");

// ============== Watchers ==============
watch(promptMode, (v) => {
  const labels = {
    none: "无",
    simple: "简洁",
    professional: "专业",
    reasoning: "推演",
  };
  ElMessage.success("提示词模式：" + (labels[v] || "专业"));
});

// 跟随消息更新自动滚动
watch(
  () => chatStore.messages.length,
  (n) => {
    if (n > 0 && uiStore.promptBarPosition === "center") {
      uiStore.movePromptBarToBottom();
    }
    scheduleScrollToBottom();
  },
);

watch(
  () => [
    chatStream.contentStream.blocks.value?.length,
    chatStream.thinkingStream.blocks.value?.length,
  ],
  () => scheduleScrollToBottom(),
);

watch(userQuestions, () => {
  nextTick(() => scheduleQuestionUpdate());
});

async function loadConversationFromRoute(conversationId) {
  const targetId =
    typeof conversationId === "string" ? conversationId.trim() : "";
  if (!targetId) {
    if (chatStore.currentConversationId) chatStore.resetChat();
    uiStore.resetPromptBarToCenter();
    uiStore.displayWelcome();
    return;
  }
  if (targetId === chatStore.currentConversationId && chatStore.currentMemoryId)
    return;

  handleHistorySelecting();
  try {
    const hasHistory = await chatStore.loadChatByConversationId(targetId);
    if (!hasHistory) {
      ElMessage.info("这是一个新会话，可以直接开始提问");
      uiStore.resetPromptBarToCenter();
      uiStore.displayWelcome();
      return;
    }
    uiStore.hideWelcome();
    uiStore.movePromptBarToBottom();
    await nextTick();
    scheduleQuestionUpdate();
  } catch (error) {
    if (error?.response?.status !== 401) {
      ElMessage.error(error?.response?.data?.message || "会话不存在或无权访问");
      chatStore.resetChat();
      await router.replace({ name: "Chat" });
    }
  }
}

watch(() => route.params.conversationId, loadConversationFromRoute);

// Observe actual rendered height: a long paragraph grows without adding a block.
watch(
  messagesContainer,
  async (container) => {
    contentObserver?.disconnect();
    await nextTick();
    observedContent = container?.querySelector(
      ".xzm-chat-page__messages-inner",
    );
    if (!observedContent || typeof ResizeObserver === "undefined") return;
    contentObserver = new ResizeObserver(() => scheduleScrollToBottom());
    contentObserver.observe(observedContent);
  },
  { flush: "post" },
);

// ============== 生命周期 ==============
onMounted(async () => {
  await loadConversationFromRoute(route.params.conversationId);
  if (chatStore.messages.length > 0) {
    uiStore.movePromptBarToBottom();
    uiStore.hideWelcome();
  }
  nextTick(() => scheduleQuestionUpdate());
});

onUnmounted(() => {
  composerMotion?.cancel();
  disposed = true;
  contentObserver?.disconnect();
  cancelAnimationFrame(scrollFrame);
  cancelAnimationFrame(followFrame);
  runGeneration += 1;
  chatStream.cancel();
});
</script>

<style scoped>
.xzm-chat-page {
  position: relative;
  display: flex;
  width: 100vw;
  max-width: 100vw;
  height: 100dvh;
  overflow: hidden;
  background-color: var(--xzm-surface-0);
  color: var(--xzm-text-primary);
}

.xzm-chat-page__bg {
  display: none;
}

.xzm-chat-page__main {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100dvh;
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
  transition: none;
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
  inset: 0;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 32px 24px 24px;
  /* Explicit JS calls opt into smooth scrolling only after a completed turn.
     Keeping CSS smooth here restarts an animation for every streaming update. */
  scroll-behavior: auto;
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
  content-visibility: auto;
  contain-intrinsic-block-size: auto 240px;
  transition: background-color var(--xzm-duration-normal) var(--xzm-ease-out);
}

.xzm-chat-page__message-row.is-highlighted {
  background-color: var(--xzm-surface-2);
  animation: xzm-msg-highlight 1.2s var(--xzm-ease-out);
}

@keyframes xzm-msg-highlight {
  0% {
    background-color: var(--xzm-surface-2);
  }
  100% {
    background-color: transparent;
  }
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
  .xzm-chat-page__main {
    margin-left: 0 !important;
  }
  .xzm-chat-page__messages {
    padding: 16px 14px 12px;
    bottom: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .xzm-chat-page__messages {
    scroll-behavior: auto;
  }
}

.chat-workspace {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.chat-workspace.is-welcome {
  justify-content: center;
  padding-bottom: min(15dvh, 130px);
  overflow-y: auto;
}
.chat-workspace.is-welcome .xzm-chat-page__content {
  flex: 0 0 auto;
  overflow: visible;
}
.chat-composer {
  flex: 0 0 auto;
  width: min(100% - 48px, var(--xzm-content-max-width));
  margin: 0 auto;
  padding: 8px 0 max(10px, env(safe-area-inset-bottom));
}
.chat-composer__hint {
  margin: 7px 0 0;
  text-align: center;
  color: var(--xzm-text-tertiary);
  font-size: 10px;
  letter-spacing: 0.02em;
}
.xzm-chat-page.has-outline .xzm-chat-page__main {
  margin-right: 296px;
}
.chat-outline-toggle {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 9px;
  color: var(--xzm-text-secondary);
  background: transparent;
  cursor: pointer;
}
.chat-outline-toggle:hover,
.chat-outline-toggle.active {
  background: var(--xzm-brand-soft);
  color: var(--xzm-brand);
}
.chat-latest {
  position: absolute;
  z-index: 3;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 14px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 99px;
  color: var(--xzm-text-primary);
  background: var(--xzm-surface-elevated);
  box-shadow: var(--xzm-shadow-medium);
  cursor: pointer;
  font-size: 12px;
}
.latest-message-enter-active,
.latest-message-leave-active {
  transition:
    opacity 160ms,
    bottom 160ms;
}
.latest-message-enter-from,
.latest-message-leave-to {
  opacity: 0;
  bottom: 6px;
}
@media (max-width: 768px) {
  .chat-composer {
    width: calc(100% - 24px);
  }
  .chat-composer__hint {
    display: none;
  }
  .chat-workspace.is-welcome {
    padding-bottom: 8dvh;
  }
}
@media (max-height: 620px) {
  .chat-workspace.is-welcome {
    justify-content: flex-start;
    padding-bottom: 0;
  }
}
</style>
