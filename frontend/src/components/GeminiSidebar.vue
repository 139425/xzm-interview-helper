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
        <span class="logo-text">AI 助手</span>
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
      <div v-if="showExpandedContent" class="workspace-heading">
        <span class="workspace-label">WORKSPACES</span>
        <button
          type="button"
          class="workspace-density-toggle"
          :aria-expanded="uiStore.workspaceListExpanded"
          :title="
            uiStore.workspaceListExpanded
              ? '只展开当前工作区'
              : '展开全部工作区说明'
          "
          @click="uiStore.toggleWorkspaceList"
        >
          <span>{{ uiStore.workspaceListExpanded ? '收拢' : '展开' }}</span>
          <el-icon :size="13">
            <ArrowUp v-if="uiStore.workspaceListExpanded" />
            <ArrowDown v-else />
          </el-icon>
        </button>
      </div>
      <div class="mode-switcher">
        <button
          v-for="item in visibleModeItems"
          :key="item.id"
          type="button"
          class="mode-btn"
          :class="{
            active: activeMode === item.id,
            'is-compact':
              !uiStore.workspaceListExpanded && activeMode !== item.id,
          }"
          :aria-current="activeMode === item.id ? 'page' : undefined"
          :aria-label="item.label"
          :title="
            uiStore.sidebarExpanded ? '' : `${item.label}：${item.description}`
          "
          @click="switchWorkspace(item)"
        >
          <span class="mode-icon" aria-hidden="true">
            <el-icon :size="19"><component :is="item.icon" /></el-icon>
          </span>
          <span v-if="showExpandedContent" class="mode-copy">
            <strong>{{ item.label }}</strong>
            <small
              v-if="uiStore.workspaceListExpanded || activeMode === item.id"
            >
              {{ item.description }}
            </small>
          </span>
        </button>
      </div>
    </nav>

    <section
      v-if="activeMode === 'algorithm' && showExpandedContent"
      class="algorithm-context"
      aria-label="算法题库"
    >
      <slot name="context"></slot>
    </section>

    <!-- 历史记录区域（仅展开时显示） -->
    <div
      v-if="showExpandedContent && hasConversationHistory"
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
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
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
  Document,
  Loading,
  Delete,
  Cpu,
  Briefcase,
  TrendCharts,
  Collection,
  Monitor,
  ArrowDown,
  ArrowUp,
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
        'knowledge',
        'serverAgent',
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
const modeItems = [
  {
    id: 'chat',
    label: 'AI 对话',
    description: '检索 · 推演 · 作答',
    icon: ChatDotRound,
    route: '/chat',
  },
  {
    id: 'interview',
    label: '模拟面试',
    description: '简历驱动的实战演练',
    icon: Document,
    route: '/aiInterview',
  },
  {
    id: 'algorithm',
    label: '算法训练',
    description: '题库 · 编码 · 评测',
    icon: Cpu,
    route: '/algorithms',
  },
  {
    id: 'recruitment',
    label: '秋招信息',
    description: '每日更新 · 官网优先',
    icon: Briefcase,
    route: '/recruitment',
  },
  {
    id: 'applications',
    label: '投递追踪',
    description: '流程 · 截止 · 提醒',
    icon: TrendCharts,
    route: '/applications',
  },
  {
    id: 'knowledge',
    label: '个人资料',
    description: '隔离知识库 · 来源',
    icon: Collection,
    route: '/knowledge',
  },
  {
    id: 'serverAgent',
    label: '服务器 Agent',
    description: '运维 · 文件 · 建站 · 审计',
    icon: Monitor,
    route: '/admin/server',
    adminOnly: true,
  },
]
const availableModeItems = computed(() =>
  modeItems.filter((item) => !item.adminOnly || userStore.isAdmin),
)
const visibleModeItems = computed(() => {
  // “工作区收拢”只保留当前入口；侧边栏整体收窄为图标轨道时，
  // 仍展示全部图标，保证不必先展开侧栏才能切换功能。
  if (!showExpandedContent.value || uiStore.workspaceListExpanded)
    return availableModeItems.value
  return availableModeItems.value.filter((item) => item.id === activeMode.value)
})

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

<style scoped>
/* 侧边栏容器 */
.gemini-sidebar {
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--xzm-signal) 4%, transparent),
      transparent 190px
    ),
    var(--gemini-bg-secondary);
  border-right: 1px solid var(--gemini-border-color);
  display: flex;
  flex-direction: column;
  z-index: var(--gemini-z-sidebar);
  width: var(--gemini-sidebar-width-expanded);
  transition: width 220ms cubic-bezier(0.2, 0, 0, 1);
  overflow: hidden;
  contain: layout paint style;
  will-change: width;
}

.gemini-sidebar.expanded {
  width: var(--gemini-sidebar-width-expanded);
}

@keyframes sidebar-content-fade-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.gemini-sidebar.expanded.content-visible .logo-section,
.gemini-sidebar.expanded.content-visible .workspace-switcher,
.gemini-sidebar.expanded.content-visible .mode-switcher,
.gemini-sidebar.expanded.content-visible .algorithm-context,
.gemini-sidebar.expanded.content-visible .history-section,
.gemini-sidebar.expanded.content-visible .new-chat-btn .btn-text {
  will-change: opacity, transform;
  animation: sidebar-content-fade-in 200ms cubic-bezier(0.32, 0.72, 0, 1) both;
}

/* Desktop collapse keeps the list alive and fades it behind the clipped rail. */
.gemini-sidebar.collapsed .logo-section,
.gemini-sidebar.collapsed .workspace-label,
.gemini-sidebar.collapsed .mode-copy,
.gemini-sidebar.collapsed .algorithm-context,
.gemini-sidebar.collapsed .history-section,
.gemini-sidebar.collapsed .new-chat-btn .btn-text {
  opacity: 0;
  visibility: hidden;
  transform: translateX(-8px);
  pointer-events: none;
  transition:
    opacity 120ms ease-out,
    transform 180ms ease-out,
    visibility 0s linear 180ms;
  white-space: nowrap;
}

.gemini-sidebar .logo-section,
.gemini-sidebar .new-chat-btn .btn-text {
  white-space: nowrap;
  min-width: 0;
}

.gemini-sidebar.collapsed {
  width: var(--gemini-sidebar-width-collapsed);
}

/* 收起状态的展开按钮 */
/* 移动端遮罩 */
.gemini-sidebar-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: calc(var(--gemini-z-sidebar) - 1);
  animation: gemini-fade-in var(--gemini-transition-fast);
}

/* 顶部区域 */
.sidebar-top {
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-md);
  min-height: 64px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--gemini-border-color);
  box-sizing: border-box;
}

.toggle-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background-color: transparent;
  border-radius: var(--gemini-radius-md);
  color: var(--gemini-text-primary);
  cursor: pointer;
  transition:
    background-color 140ms ease-out,
    color 140ms ease-out;
}

.toggle-btn:hover {
  background-color: var(--gemini-bg-hover);
}

.logo-section {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-sm);
}

.app-icon {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  flex-shrink: 0;
  display: inline-grid;
  place-items: center;
  color: var(--xzm-signal-ink);
  background: var(--xzm-signal);
  box-shadow: 0 5px 14px rgba(93, 117, 0, 0.16);
  font-family: var(--xzm-font-data);
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.logo-text {
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--gemini-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 操作按钮区域 —— 与 sidebar-top 同 padding，让两个按钮起点对齐 */
.sidebar-actions {
  padding: 8px 12px 12px;
  border-bottom: 1px solid var(--gemini-border-color);
  box-sizing: border-box;
}

.new-chat-btn {
  width: 100%;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  /* gap=12 配合 padding-left=8 + icon(24)/2=12 → icon 中心距按钮左边 20px,
     再加 sidebar-actions padding 8px → icon 中心距侧栏左边 28px,
     正好与 toggle-btn icon 中心位置一致 */
  gap: 12px;
  padding: 0 8px;
  border: none;
  background-color: transparent;
  color: var(--gemini-text-primary);
  border-radius: var(--gemini-radius-md);
  cursor: pointer;
  box-sizing: border-box;
  transition: background-color 140ms ease-out;
}

.new-chat-btn:hover {
  background-color: var(--gemini-bg-hover);
}

.btn-text {
  font-size: 0.92rem;
  font-weight: 500;
}

/* Workspace dock */
.workspace-switcher {
  padding: 10px 8px 11px;
  border-bottom: 1px solid var(--gemini-border-color);
  box-sizing: border-box;
}

.workspace-heading {
  display: flex;
  min-height: 24px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 8px 6px;
}

.workspace-label {
  color: var(--gemini-text-tertiary);
  font-size: 0.64rem;
  font-weight: 750;
  letter-spacing: 0.14em;
  line-height: 1;
}

.workspace-density-toggle {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-height: 24px;
  padding: 0 3px 0 6px;
  border: 0;
  border-radius: 6px;
  color: var(--gemini-text-tertiary);
  background: transparent;
  font-size: 0.67rem;
  cursor: pointer;
  transition:
    color 140ms ease-out,
    background-color 140ms ease-out;
}

.workspace-density-toggle:hover {
  color: var(--gemini-text-primary);
  background: var(--gemini-bg-hover);
}

.workspace-density-toggle:focus-visible {
  outline: 2px solid var(--gemini-accent-blue);
  outline-offset: 1px;
}

.mode-switcher {
  display: flex;
  flex-direction: column;
  gap: 5px;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}

.mode-btn {
  position: relative;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 5px 8px;
  border: 1px solid transparent;
  border-radius: 11px;
  background-color: transparent;
  color: var(--gemini-text-secondary);
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
  transition:
    background-color 140ms ease-out,
    border-color 140ms ease-out,
    color 140ms ease-out,
    transform 140ms ease-out;
}

.mode-btn.is-compact {
  min-height: 38px;
  padding-block: 3px;
}

.mode-btn.is-compact .mode-icon {
  width: 29px;
  height: 29px;
  flex-basis: 29px;
}

.mode-btn::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 4px 4px 0;
  background: transparent;
  transition: background-color 140ms ease-out;
}

.mode-icon {
  width: 31px;
  height: 31px;
  flex: 0 0 31px;
  display: inline-grid;
  place-items: center;
  border: 1px solid var(--gemini-border-color);
  border-radius: 9px;
  color: var(--gemini-text-secondary);
  background: color-mix(in srgb, var(--gemini-bg-tertiary) 78%, transparent);
  transition:
    color 140ms ease-out,
    border-color 140ms ease-out,
    background 140ms ease-out;
}

.mode-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.mode-copy strong,
.mode-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mode-copy strong {
  color: var(--gemini-text-primary);
  font-size: 0.84rem;
  font-weight: 680;
  line-height: 1.2;
}

.mode-copy small {
  color: var(--gemini-text-tertiary);
  font-size: 0.67rem;
  line-height: 1.25;
}

.mode-btn:hover {
  background-color: var(--gemini-bg-tertiary);
  border-color: var(--gemini-border-hover);
  transform: translateX(1px);
}

.mode-btn.active {
  background: linear-gradient(
    105deg,
    var(--xzm-brand-soft),
    color-mix(in srgb, var(--xzm-signal) 14%, transparent)
  );
  border-color: color-mix(
    in srgb,
    var(--gemini-accent-blue) 38%,
    var(--gemini-border-color)
  );
  box-shadow: inset 0 0 0 1px
    color-mix(in srgb, var(--xzm-signal) 20%, transparent);
}

.mode-btn.active::before {
  background-color: var(--xzm-signal-ink);
}

.mode-btn.active .mode-icon {
  color: var(--gemini-accent-blue);
  border-color: color-mix(
    in srgb,
    var(--gemini-accent-blue) 45%,
    var(--gemini-border-color)
  );
  background: color-mix(
    in srgb,
    var(--gemini-accent-blue) 13%,
    var(--gemini-bg-secondary)
  );
}

.mode-btn:focus-visible {
  outline: 2px solid var(--gemini-accent-blue);
  outline-offset: 1px;
}

.algorithm-context {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.gemini-sidebar.collapsed .workspace-switcher {
  padding: 12px 10px;
}

.gemini-sidebar.collapsed .mode-btn {
  width: 44px;
  min-height: 44px;
  padding: 0;
  border-color: transparent;
  border-radius: 12px;
  justify-content: center;
  transform: none;
}

.gemini-sidebar.collapsed .mode-btn::before {
  left: -14px;
  top: 11px;
  bottom: 11px;
}

.gemini-sidebar.collapsed .mode-icon {
  width: 24px;
  height: 24px;
  flex-basis: 24px;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.gemini-sidebar.collapsed .mode-btn.active .mode-icon {
  border: 0;
  background: transparent;
}

/* 历史记录区域 */
.history-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--gemini-spacing-lg);
  padding-bottom: var(--gemini-spacing-md);
}

.history-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--gemini-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.batch-btn {
  padding: 4px 8px;
  border: 1px solid var(--gemini-border-color);
  border-radius: var(--gemini-radius-sm);
  background-color: transparent;
  color: var(--gemini-text-secondary);
  font-size: 0.75rem;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
}

.batch-btn:hover {
  background-color: var(--gemini-bg-tertiary);
  border-color: var(--gemini-border-hover);
}

/* 批量控制栏 */
.batch-controls {
  display: flex;
  gap: var(--gemini-spacing-sm);
  padding: 0 var(--gemini-spacing-lg) var(--gemini-spacing-md);
}

.control-btn {
  padding: 4px 8px;
  border: 1px solid var(--gemini-border-color);
  border-radius: var(--gemini-radius-sm);
  background-color: var(--gemini-bg-tertiary);
  color: var(--gemini-text-secondary);
  font-size: 0.75rem;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
}

.control-btn:hover {
  background-color: var(--gemini-bg-hover);
}

.control-btn.delete-btn {
  background-color: var(--gemini-accent-red);
  border-color: var(--gemini-accent-red);
  color: white;
}

.control-btn.delete-btn:hover {
  opacity: 0.9;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 历史记录列表 */
.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--gemini-spacing-md);
}

.list-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--gemini-spacing-md);
  padding: var(--gemini-spacing-2xl);
  color: var(--gemini-text-tertiary);
  font-size: 0.875rem;
}

.history-items {
  display: flex;
  flex-direction: column;
  gap: var(--gemini-spacing-xs);
}

.history-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border-radius: var(--gemini-radius-lg);
  transition: all var(--gemini-transition-fast);
  border: 1px solid transparent;
}

.history-primary {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-md);
  padding: var(--gemini-spacing-md);
  border: 0;
  border-radius: inherit;
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.history-primary:focus-visible {
  outline: 2px solid var(--gemini-accent-blue);
  outline-offset: -2px;
}

.history-item.loading .history-primary {
  cursor: progress;
  opacity: 0.74;
}

.history-loading-icon {
  flex: 0 0 auto;
  color: var(--gemini-accent-blue);
}

.history-item:hover {
  background-color: var(--gemini-bg-tertiary);
  border-color: var(--gemini-border-color);
}

.history-item.active {
  background-color: var(--gemini-bg-hover);
  border-color: var(--gemini-accent-blue);
}

.history-item.selected {
  background-color: rgba(138, 180, 248, 0.1);
  border-color: var(--gemini-accent-blue);
}

.checkbox-wrapper {
  flex-shrink: 0;
  margin-left: var(--gemini-spacing-md);
}

.history-checkbox {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--gemini-accent-blue);
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--gemini-text-primary);
  margin-bottom: 4px;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-sm);
  font-size: 0.75rem;
  color: var(--gemini-text-tertiary);
}

.item-time {
  flex: 1;
}

.item-count {
  padding: 2px 6px;
  border-radius: var(--gemini-radius-full);
  background-color: var(--gemini-bg-tertiary);
  font-size: 0.625rem;
}

.status-badge {
  padding: 2px 8px;
  border-radius: var(--gemini-radius-full);
  font-size: 0.625rem;
  font-weight: 600;
}

.status-badge.finished {
  background-color: var(--gemini-accent-green);
  color: var(--gemini-bg-primary);
}

.status-badge.in-progress {
  background-color: var(--gemini-accent-yellow);
  color: var(--gemini-bg-primary);
}

.delete-btn-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-right: 5px;
  border: none;
  border-radius: var(--gemini-radius-sm);
  background-color: transparent;
  color: var(--gemini-text-tertiary);
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
}

.delete-btn-icon:hover {
  background-color: var(--gemini-accent-red);
  color: white;
}

/* 加载更多状态 */
.loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gemini-spacing-sm);
  padding: var(--gemini-spacing-lg);
  color: var(--gemini-text-tertiary);
  font-size: 0.875rem;
}

.no-more {
  text-align: center;
  padding: var(--gemini-spacing-lg);
  color: var(--gemini-text-tertiary);
  font-size: 0.75rem;
}

/* 收起状态样式 */
.gemini-sidebar.collapsed .sidebar-top,
.gemini-sidebar.collapsed .sidebar-actions {
  justify-content: center;
  padding-left: 10px;
  padding-right: 10px;
}

.gemini-sidebar.collapsed .new-chat-btn {
  width: 44px;
  min-height: 44px;
  justify-content: center;
  padding: 0;
}

/* 移动端样式 */
@media (max-width: 768px) {
  .gemini-sidebar {
    width: 280px;
    contain: layout paint style;
    transition: transform 260ms cubic-bezier(0.2, 0, 0, 1);
    will-change: transform;
  }

  .gemini-sidebar.collapsed {
    width: 280px;
    transform: translateX(-100%);
  }

  .gemini-sidebar.expanded {
    width: 280px;
    box-shadow: var(--gemini-shadow-lg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .gemini-sidebar,
  .gemini-sidebar-overlay,
  .gemini-sidebar.expanded.content-visible .logo-section,
  .gemini-sidebar.expanded.content-visible .workspace-switcher,
  .gemini-sidebar.expanded.content-visible .mode-switcher,
  .gemini-sidebar.expanded.content-visible .algorithm-context,
  .gemini-sidebar.expanded.content-visible .history-section,
  .gemini-sidebar.expanded.content-visible .new-chat-btn .btn-text {
    animation: none;
    transition-duration: 1ms;
  }
}
</style>
