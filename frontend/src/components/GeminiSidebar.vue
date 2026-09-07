<template>
  <!-- 移动端遮罩层 -->
  <div
    v-if="uiStore.sidebarExpanded && uiStore.isMobile"
    class="gemini-sidebar-overlay"
    aria-hidden="true"
    @click="uiStore.collapseSidebar"
  ></div>

  <!-- 收起状态的展开按钮（所有设备） -->
  <!-- 侧边栏容器 -->
  <aside
    ref="sidebarElement"
    class="gemini-sidebar"
    :class="{
      expanded: uiStore.sidebarExpanded,
      collapsed: !uiStore.sidebarExpanded,
      mobile: uiStore.isMobile,
      'content-visible': showExpandedContent,
    }"
    :inert="uiStore.isMobile && !uiStore.sidebarExpanded"
    :aria-hidden="
      uiStore.isMobile && !uiStore.sidebarExpanded ? 'true' : undefined
    "
  >
    <!-- 顶部区域 -->
    <div class="sidebar-top">
      <!-- 展开/收起按钮 -->
      <button
        type="button"
        class="gemini-icon-button toggle-btn"
        @click="uiStore.toggleSidebar"
        :aria-expanded="uiStore.sidebarExpanded"
        :aria-label="uiStore.sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
        :title="uiStore.sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
      >
        <el-icon :size="24">
          <Expand v-if="!uiStore.sidebarExpanded" />
          <Fold v-else />
        </el-icon>
      </button>

      <!-- Logo 和标题（仅展开时显示） -->
      <div v-if="showExpandedContent" class="logo-section">
        <span class="app-icon" aria-hidden="true">IA</span>
        <span class="logo-text">面试助手</span>
      </div>
    </div>

    <!-- 新对话按钮 -->
    <div v-if="hasConversationHistory" class="sidebar-actions">
      <button
        type="button"
        class="gemini-icon-button new-chat-btn"
        @click="handleNewChat"
        :title="uiStore.sidebarExpanded ? '' : '新对话'"
      >
        <el-icon :size="24">
          <EditPen />
        </el-icon>
        <span v-if="showExpandedContent" class="btn-text">新对话</span>
      </button>
    </div>

    <nav class="workspace-switcher" aria-label="工作区切换">
      <WorkspacePicker :items="availableModeItems" :active-mode="activeMode"
        :collapsed="!showExpandedContent" @select="switchWorkspace" @open-change="workspacePickerOpen = $event" />
      <div class="practice-switcher" aria-label="面试准备">
        <a v-for="item in practiceItems" :key="item.id" :href="item.route"
          class="mode-btn practice-link" :class="{ active: activeMode === item.id }"
          :aria-current="activeMode === item.id ? 'page' : undefined" :aria-label="item.label"
          :title="item.label" @click="navigateWorkspace($event, item)">
          <span class="mode-icon" aria-hidden="true"><el-icon><component :is="item.icon" /></el-icon></span>
          <span v-if="showExpandedContent" class="mode-copy"><strong>{{ item.shortLabel }}</strong></span>
        </a>
      </div>
      <div class="career-switcher">
        <span v-if="showExpandedContent" class="nav-section-label">求职进程</span>
        <a v-for="item in [...careerItems, ...extraItems]" :key="item.id" :href="item.route"
          class="mode-btn career-link" :class="{ active: activeMode === item.id }"
          :aria-current="activeMode === item.id ? 'page' : undefined" :aria-label="item.label"
          :title="item.label" @click="navigateWorkspace($event, item)"
          @pointerenter="prefetchWorkspace(item.id)" @focus="prefetchWorkspace(item.id)">
          <span class="mode-icon" aria-hidden="true"><el-icon><component :is="item.icon" /></el-icon></span>
          <span v-if="showExpandedContent" class="mode-copy"><strong>{{ item.label }}</strong></span>
        </a>
      </div>
    </nav>

    <section
      v-if="activeMode === 'algorithm'"
      v-show="showExpandedContent"
      class="algorithm-context"
      aria-label="算法题库"
    >
      <slot name="context"></slot>
    </section>

    <!-- 历史记录区域（仅展开时显示） -->
    <div
      v-if="hasConversationHistory"
      v-show="showExpandedContent"
      class="history-section"
    >
      <!-- 历史记录头部 -->
      <div class="history-header">
        <span class="history-title">历史记录</span>
        <button
          v-if="historyList.length > 0 && !isBatchMode"
          type="button"
          class="batch-btn"
          @click="enterBatchMode"
          title="批量删除"
        >
          批量
        </button>
      </div>

      <!-- 批量操作控制栏 -->
      <div v-if="isBatchMode" class="batch-controls">
        <button type="button" @click="selectAll" class="control-btn">
          {{ selectedItems.length === historyList.length ? '取消' : '全选' }}
        </button>
        <button
          type="button"
          @click="batchDelete"
          :disabled="selectedItems.length === 0"
          class="control-btn delete-btn"
        >
          删除 ({{ selectedItems.length }})
        </button>
        <button type="button" @click="exitBatchMode" class="control-btn">
          取消
        </button>
      </div>

      <!-- 历史记录列表 -->
      <div
        ref="listContainer"
        class="history-list gemini-smooth-scroll"
        @scroll="handleScroll"
      >
        <!-- 加载状态 -->
        <div v-if="loading" class="list-state">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <!-- 空状态 -->
        <div v-else-if="historyList.length === 0" class="list-state">
          <el-icon :size="32"><ChatDotRound /></el-icon>
          <span>暂无历史记录</span>
        </div>

        <!-- 历史记录项 -->
        <div v-else class="history-items">
          <div
            v-for="item in historyList"
            :key="item.memoryId"
            class="history-item"
            :class="{
              active: isSameMemoryId(item.memoryId, currentMemoryId),
              'batch-mode': isBatchMode,
              selected: selectedItems.includes(item.memoryId),
              loading: isSameMemoryId(item.memoryId, historyLoadingId),
            }"
          >
            <!-- 批量选择复选框 -->
            <div v-if="isBatchMode" class="checkbox-wrapper">
              <input
                type="checkbox"
                :checked="selectedItems.includes(item.memoryId)"
                @click.stop="toggleSelection(item.memoryId)"
                :aria-label="`选择会话：${truncateText(item.lastQuestion, 30)}`"
                class="history-checkbox"
              />
            </div>

            <button
              type="button"
              class="history-primary"
              :aria-current="
                isSameMemoryId(item.memoryId, currentMemoryId)
                  ? 'page'
                  : undefined
              "
              :aria-busy="isSameMemoryId(item.memoryId, historyLoadingId)"
              @click="handleHistoryClick(item)"
            >
              <!-- 历史记录内容 -->
              <div class="item-content">
                <div class="item-title gemini-truncate">
                  {{ truncateText(item.lastQuestion, 30) }}
                </div>
                <div class="item-meta">
                  <span class="item-time">{{
                    formatTime(item.lastChatTime)
                  }}</span>
                  <span v-if="activeMode === 'chat'" class="item-count">
                    {{ item.messageCount }}条
                  </span>
                  <span
                    v-else-if="activeMode === 'interview' && item.isFinished"
                    class="status-badge finished"
                  >
                    已完成
                  </span>
                  <span
                    v-else-if="activeMode === 'interview' && !item.isFinished"
                    class="status-badge in-progress"
                  >
                    未完成
                  </span>
                </div>
              </div>

              <el-icon
                v-if="isSameMemoryId(item.memoryId, historyLoadingId)"
                class="history-loading-icon is-loading"
                :size="16"
                aria-label="正在加载会话"
              >
                <Loading />
              </el-icon>
            </button>

            <!-- 删除按钮 -->
            <button
              v-if="!isBatchMode"
              type="button"
              class="delete-btn-icon"
              @click.stop="confirmDelete(item.memoryId)"
              :aria-label="`删除会话：${truncateText(item.lastQuestion, 30)}`"
              title="删除"
            >
              <el-icon :size="16"><Delete /></el-icon>
            </button>
          </div>

          <!-- 加载更多状态 -->
          <div v-if="loadingMore" class="loading-more">
            <el-icon class="is-loading" :size="20"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <!-- 没有更多数据 -->
          <div v-else-if="!hasMore && historyList.length > 0" class="no-more">
            已加载全部记录
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { workspaces, prefetchWorkspace } from '../utils/workspaceNavigation'
import WorkspacePicker from './WorkspacePicker.vue'
import { useUIStore } from '../stores/ui'
import { useChatStore } from '../stores/chat'
import { useUserStore } from '../stores/user'
import { chatApi } from '../api/chat'
import { interviewApi } from '../api/interview'
import {
  Expand,
  Fold,
  EditPen,
  ChatDotRound,
  Loading,
  Delete,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// Props
const props = defineProps({
  mode: {
    type: String,
    default: 'chat',
    validator: (value) =>
      [
        'chat',
        'interview',
        'algorithm',
        'recruitment',
        'applications',
        'schedule',
        'knowledge',
        'serverAgent',
        'users',
      ].includes(value),
  },
})

// Emits
const emit = defineEmits([
  'new-chat',
  'mode-change',
  'interview-select',
  'interview-delete',
  'history-selecting',
])

// Stores
const router = useRouter()
const uiStore = useUIStore()
const chatStore = useChatStore()
const userStore = useUserStore()
const sidebarElement = ref(null)
const workspacePickerOpen = ref(false)
let sidebarReturnFocus
function handleSidebarKey(event) {
  if (!uiStore.isMobile || !uiStore.sidebarExpanded || workspacePickerOpen.value) return
  if (event.key === 'Escape') { event.preventDefault(); uiStore.collapseSidebar(); return }
  if (event.key !== 'Tab') return
  const nodes = [...sidebarElement.value.querySelectorAll('button:not(:disabled), input, a[href]')].filter(el => el.getClientRects().length)
  const first = nodes[0], last = nodes.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
watch(() => uiStore.isMobile && uiStore.sidebarExpanded, async (open) => {
  if (open) {
    sidebarReturnFocus = document.activeElement
    document.addEventListener('keydown', handleSidebarKey)
    await nextTick()
    sidebarElement.value?.querySelector('button')?.focus({ preventScroll: true })
  } else {
    document.removeEventListener('keydown', handleSidebarKey)
    await nextTick()
    if (sidebarReturnFocus?.isConnected) sidebarReturnFocus.focus({ preventScroll: true })
    sidebarReturnFocus = null
  }
}, { immediate: true })

// 状态
const loading = ref(false)
const loadingMore = ref(false)
const historyList = ref([])
const isBatchMode = ref(false)
const selectedItems = ref([])
const historyLoadingId = ref(null)
let historyRequestId = 0
let historySelectionRequestId = 0
let isUnmounted = false

// 收起时只保留导航轨道。隐藏的长文本不再参与布局，避免 240px 内容被
// clip-path 裁成 72px 后仍挤压图标，也是收起态错位的根因。
const showExpandedContent = computed(() => uiStore.sidebarExpanded)
const activeMode = computed(() => props.mode || uiStore.currentMode)
const hasConversationHistory = computed(() =>
  ['chat', 'interview'].includes(activeMode.value),
)
const modeItems = workspaces
const availableModeItems = computed(() =>
  modeItems.filter((item) => !item.adminOnly || userStore.isAdmin),
)
const practiceItems = computed(() => availableModeItems.value.filter(item => item.group === 'practice'))
const careerItems = computed(() => availableModeItems.value.filter(item => item.group === 'career'))
const extraItems = computed(() => availableModeItems.value.filter(item => !item.group && item.id === activeMode.value))

// 分页状态
const currentPage = ref(1)
const pageSize = ref(10)
const hasMore = ref(true)
const listContainer = ref(null)

// 计算属性
const currentMemoryId = computed(() => chatStore.currentMemoryId)
const isSameMemoryId = (left, right) => {
  return left != null && right != null && String(left) === String(right)
}

// ========== 侧边栏操作 ==========

// 新对话
const handleNewChat = () => {
  emit('new-chat')
  // Only chat mode owns the centered prompt-bar state. Interview mode uses its
  // own intake screen and should not inherit chat-only layout transitions.
  if (activeMode.value === 'chat') {
    uiStore.resetPromptBarToCenter()
    uiStore.displayWelcome()
  }
}

const switchWorkspace = (item) => {
  if (!item || activeMode.value === item.id) return

  // Workspace navigation must never mutate the current conversation. A new
  // conversation is created only through the explicit "新对话" action.
  uiStore.switchMode(item.id)
  emit('mode-change', item.id)

  if (uiStore.isMobile) uiStore.collapseSidebar()
  if (router.currentRoute.value.path !== item.route) {
    router.push(item.route)
  }
}

function navigateWorkspace(event, item) {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) return
  event.preventDefault()
  switchWorkspace(item)
}

// ========== 历史记录操作 ==========

// 加载历史记录（首次加载）
const loadHistoryList = async (reset = true) => {
  if (!hasConversationHistory.value) {
    historyRequestId += 1
    historyList.value = []
    loading.value = false
    loadingMore.value = false
    hasMore.value = false
    return
  }

  const requestId = ++historyRequestId
  const requestedMode = activeMode.value
  const requestedPage = reset ? 1 : currentPage.value
  const isCurrentRequest = () =>
    !isUnmounted &&
    requestId === historyRequestId &&
    requestedMode === activeMode.value

  if (reset) {
    loading.value = true
    currentPage.value = 1
    historyList.value = []
    hasMore.value = true
  } else {
    loadingMore.value = true
  }

  try {
    let data
    const page = requestedPage
    const size = pageSize.value

    if (activeMode.value === 'interview') {
      // Interview Agent sessions are already persisted by the new session API.
      // Keep frontend pagination for the shared sidebar without using a second
      // persistence path.
      if (userStore.isLoggedIn && userStore.userId) {
        const response = await interviewApi.listSessions()
        const allData = Array.isArray(response)
          ? response
          : response?.records || response?.items || response?.sessions || []

        const start = (page - 1) * size
        const end = start + size
        data = allData.slice(start, end)
        hasMore.value = end < allData.length

        data = data
          .map((item) => {
            const session = item?.session || item
            const sessionId =
              session?.id || session?.sessionId || session?.session_id
            const role =
              session?.targetRole || session?.target_role || session?.role
            const resumeName =
              session?.resumeName ||
              session?.resume_name ||
              session?.resumeFileName ||
              session?.resume_file_name ||
              session?.fileName ||
              session?.file_name
            const status = String(
              session?.status || session?.state || '',
            ).toLowerCase()
            const isFinished =
              Boolean(
                session?.completed ||
                session?.isCompleted ||
                session?.is_completed,
              ) || /complete|finish|report|closed|done/.test(status)

            return {
              memoryId: sessionId,
              sessionId,
              lastQuestion: role
                ? `模拟面试 · ${role}`
                : `模拟面试 · ${resumeName || '未命名会话'}`,
              lastChatTime:
                session?.updatedAt ||
                session?.updated_at ||
                session?.createdAt ||
                session?.created_at,
              messageCount: 0,
              isFinished,
              interviewId: sessionId,
            }
          })
          .filter((item) => item.memoryId)
      } else {
        data = []
        hasMore.value = false
      }
    } else {
      // 对话模式 - 使用后端分页接口
      if (userStore.isLoggedIn && userStore.userId) {
        console.log(
          '📡 调用分页API, userId:',
          userStore.userId,
          'page:',
          page,
          'size:',
          size,
        )
        const result = await chatApi.getChatHistorySummariesByUserPaged(
          userStore.userId,
          page,
          size,
        )
        console.log('📡 分页API返回结果:', result)
        data = result.records || []
        hasMore.value = result.hasMore ?? false
        console.log('📡 解析后的数据:', data, 'hasMore:', hasMore.value)
      } else {
        // 未登录用户保持原有逻辑
        const allData = await chatApi.getAllChatHistorySummaries()
        const start = (page - 1) * size
        const end = start + size
        data = (allData || []).slice(start, end)
        hasMore.value = end < (allData || []).length
      }
    }

    if (!isCurrentRequest()) return
    if (reset) {
      historyList.value = data || []
    } else {
      historyList.value = [...historyList.value, ...(data || [])]
    }
  } catch (error) {
    if (isCurrentRequest()) {
      if (!reset && currentPage.value === requestedPage) {
        currentPage.value = Math.max(1, requestedPage - 1)
      }
      console.error('加载历史记录失败:', error)
      ElMessage.error('加载历史记录失败')
    }
  } finally {
    if (isCurrentRequest()) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

// 加载更多历史记录
const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return

  currentPage.value++
  await loadHistoryList(false)
}

// 滚动事件处理
const handleScroll = (event) => {
  const container = event.target
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight

  // 距离底部50px时触发加载
  if (scrollHeight - scrollTop - clientHeight < 50) {
    loadMore()
  }
}

// 处理历史记录点击
const handleHistoryClick = async (item) => {
  if (!item?.memoryId || historyLoadingId.value != null || isUnmounted) return

  if (isBatchMode.value) {
    toggleSelection(item.memoryId)
    return
  }

  if (activeMode.value === 'interview') {
    // Hand the selected Agent session to the interview workspace. It will load
    // the canonical session snapshot and decide whether it is resumable.
    const interviewData = {
      sessionId: item.sessionId || item.memoryId || item.interviewId,
      interviewId: item.sessionId || item.memoryId || item.interviewId,
      isFinished: Boolean(item.isFinished),
      userDescription: item.lastQuestion || '',
      createTime: item.lastChatTime,
    }

    console.log('GeminiSidebar - 面试记录数据:', item)
    console.log('GeminiSidebar - 处理后的interviewData:', interviewData)

    emit('interview-select', interviewData)
  } else {
    // 对话模式
    if (
      isSameMemoryId(item.memoryId, currentMemoryId.value) &&
      chatStore.messages.length > 0
    ) {
      return
    }

    // Let the chat workspace invalidate and abort any in-flight generation
    // before this store is replaced with another conversation.
    emit('history-selecting', item)
    const requestId = ++historySelectionRequestId
    historyLoadingId.value = item.memoryId
    try {
      const success =
        userStore.isLoggedIn && userStore.userId
          ? await chatStore.loadChatByIdAndUser(
              item.memoryId,
              userStore.userId,
              item.conversationId,
            )
          : await chatStore.loadChatById(item.memoryId, item.conversationId)

      if (
        isUnmounted ||
        requestId !== historySelectionRequestId ||
        activeMode.value !== 'chat'
      )
        return

      if (!success) {
        ElMessage.warning('该会话暂无可显示的消息')
        return
      }

      uiStore.hideWelcome()
      uiStore.movePromptBarToBottom()
      if (item.conversationId) {
        await router.push({
          name: 'Chat',
          params: { conversationId: item.conversationId },
        })
      }
    } catch (error) {
      if (
        !isUnmounted &&
        requestId === historySelectionRequestId &&
        activeMode.value === 'chat'
      ) {
        console.error('切换历史会话失败:', error)
        ElMessage.error('打开历史会话失败，请稍后重试')
      }
    } finally {
      if (requestId === historySelectionRequestId) {
        historyLoadingId.value = null
      }
    }
  }
}

// 确认删除
const confirmDelete = async (memoryId) => {
  const confirmMessage =
    activeMode.value === 'interview'
      ? '删除后不可恢复，确认删除该面试记录吗？'
      : '删除后不可恢复，确认删除该ID的所有历史记录吗？'

  try {
    await ElMessageBox.confirm(confirmMessage, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    if (activeMode.value === 'interview') {
      await interviewApi.deleteSession(memoryId)
      emit('interview-delete', [memoryId])
    } else {
      if (userStore.isLoggedIn && userStore.userId) {
        await chatApi.clearChatHistoryByUser(memoryId, userStore.userId)
      } else {
        await chatApi.deleteHistoryById(memoryId)
      }
    }

    // 如果删除的是当前对话，重置状态并显示欢迎界面
    const isCurrentChat = chatStore.currentMemoryId === memoryId
    if (isCurrentChat) {
      chatStore.currentMemoryId = null
      chatStore.currentConversationId = null
      chatStore.messages = []
      if (router.currentRoute.value.name === 'Chat')
        router.replace({ name: 'Chat' })
      // 直接重置UI状态，显示欢迎界面
      uiStore.resetPromptBarToCenter()
      uiStore.displayWelcome()
    }

    await loadHistoryList()

    const successMessage =
      activeMode.value === 'interview' ? '面试记录删除成功' : '删除成功'
    ElMessage.success(successMessage)
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败，请稍后重试')
  }
}

// ========== 批量操作 ==========

// 进入批量模式
const enterBatchMode = () => {
  isBatchMode.value = true
  selectedItems.value = []
}

// 退出批量模式
const exitBatchMode = () => {
  isBatchMode.value = false
  selectedItems.value = []
}

// 切换选择
const toggleSelection = (memoryId) => {
  const index = selectedItems.value.indexOf(memoryId)
  if (index > -1) {
    selectedItems.value.splice(index, 1)
  } else {
    selectedItems.value.push(memoryId)
  }
}

// 全选/取消全选
const selectAll = () => {
  if (selectedItems.value.length === historyList.value.length) {
    selectedItems.value = []
  } else {
    selectedItems.value = historyList.value.map((item) => item.memoryId)
  }
}

// 批量删除
const batchDelete = async () => {
  if (selectedItems.value.length === 0) return

  const deleteCount = selectedItems.value.length
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${deleteCount} 条历史记录吗？删除后不可恢复。`,
      '批量删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    const deletedIds = [...selectedItems.value]
    const deletePromises = deletedIds.map((memoryId) => {
      if (activeMode.value === 'interview') {
        return interviewApi.deleteSession(memoryId)
      }
      if (userStore.isLoggedIn && userStore.userId) {
        return chatApi.clearChatHistoryByUser(memoryId, userStore.userId)
      } else {
        return chatApi.deleteHistoryById(memoryId)
      }
    })

    await Promise.all(deletePromises)

    if (activeMode.value === 'interview') {
      emit('interview-delete', deletedIds)
    }

    // 如果删除的包含当前对话，重置状态并显示欢迎界面
    const deletedCurrentChat = selectedItems.value.includes(
      chatStore.currentMemoryId,
    )
    if (deletedCurrentChat) {
      chatStore.currentMemoryId = null
      chatStore.currentConversationId = null
      chatStore.messages = []
      if (router.currentRoute.value.name === 'Chat')
        router.replace({ name: 'Chat' })
      // 直接重置UI状态，显示欢迎界面
      uiStore.resetPromptBarToCenter()
      uiStore.displayWelcome()
    }

    exitBatchMode()
    await loadHistoryList()

    ElMessage.success(`成功删除 ${deleteCount} 条历史记录`)
  } catch (error) {
    console.error('批量删除失败:', error)
    ElMessage.error('批量删除失败，请稍后重试')
  }
}

// ========== 工具函数 ==========

// 截断文本
const truncateText = (text, maxLength) => {
  if (!text) return '新对话'
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text
}

// 格式化时间
const formatTime = (dateString) => {
  if (!dateString) return ''

  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now - date
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffDays === 0) {
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    })
  } else if (diffDays === 1) {
    return '昨天'
  } else if (diffDays < 7) {
    return `${diffDays}天前`
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }
}

// 刷新历史记录
const refreshHistory = () => {
  console.log('🔄 GeminiSidebar.refreshHistory 被调用')
  loadHistoryList(true) // 强制重置并重新加载
}

// ========== 生命周期 ==========

onMounted(() => {
  if (hasConversationHistory.value) loadHistoryList()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleSidebarKey)
  isUnmounted = true
  historyRequestId += 1
  historySelectionRequestId += 1
})

// 监听 store 的刷新触发器
watch(
  () => uiStore.sidebarRefreshTrigger,
  (newVal) => {
    if (newVal > 0 && hasConversationHistory.value) {
      console.log('🔄 GeminiSidebar 收到刷新信号, trigger:', newVal)
      loadHistoryList(true)
    }
  },
)

// 监听模式变化，加载对应的历史记录
watch(activeMode, (newMode, oldMode) => {
  if (newMode !== oldMode) {
    if (newMode !== 'chat') exitBatchMode()
    console.log('🔄 模式切换:', oldMode, '->', newMode)
    if (!hasConversationHistory.value) {
      historyList.value = []
      loading.value = false
      loadingMore.value = false
      hasMore.value = false
    } else {
      loadHistoryList(true)
    }
  }
})

// 暴露方法
defineExpose({
  refreshHistory,
  loadHistoryList,
})
</script>

<style scoped src="../assets/sidebar.css"></style>
