import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

export const useUIStore = defineStore('ui', () => {
  const SIDEBAR_STORAGE_KEY = 'sidebarExpanded'
  const WORKSPACE_LIST_STORAGE_KEY = 'workspaceListExpanded'
  const savedSidebarExpanded = localStorage.getItem(SIDEBAR_STORAGE_KEY)

  // ========== 侧边栏状态 ==========
  const sidebarExpanded = ref(savedSidebarExpanded === null ? true : savedSidebarExpanded === 'true')
  // 默认只展开当前工作区的说明，避免六个入口长期占据侧栏高度。
  const workspaceListExpanded = ref(localStorage.getItem(WORKSPACE_LIST_STORAGE_KEY) === 'true')
  const sidebarMode = computed(() => {
    // 根据展开状态和屏幕宽度决定模式
    if (sidebarExpanded.value) {
      return 'full' // 完全展开
    }
    return isMobile.value ? 'hidden' : 'icons' // 移动端隐藏，桌面端显示图标
  })
  
  // ========== 输入框状态 ==========
  const promptBarPosition = ref('center') // 'center' | 'bottom'
  const promptBarFocused = ref(false)
  
  // ========== 欢迎界面状态 ==========
  const showWelcome = ref(true) // 是否显示欢迎界面
  const welcomeAnimating = ref(false) // 欢迎界面是否正在动画中
  
  // ========== 右侧面板状态 ==========
  const rightPanelVisible = ref(false) // 右侧面板是否可见
  const rightPanelTop = ref(100) // 右侧面板顶部位置（px）
  const rightPanelDragging = ref(false) // 是否正在拖动
  
  // ========== 当前模式 ==========
  const currentMode = ref('chat')
  
  // ========== 侧边栏刷新触发器 ==========
  const sidebarRefreshTrigger = ref(0) // 每次+1触发侧边栏刷新
  
  // ========== 主题状态 ==========
  // 从 localStorage 读取保存的主题；首次访问默认使用白色主题。
  const savedTheme = localStorage.getItem('theme')
  const currentTheme = ref((savedTheme === 'light' || savedTheme === 'dark') ? savedTheme : 'light')

  const PROMPT_MODE_STORAGE_KEY = 'promptMode'
  const PROMPT_MODES = new Set(['none', 'simple', 'professional', 'reasoning'])
  const savedPromptMode = localStorage.getItem(PROMPT_MODE_STORAGE_KEY)
  const promptMode = ref(
    PROMPT_MODES.has(savedPromptMode) ? savedPromptMode : 'professional'
  )

  const setPromptMode = (mode) => {
    if (PROMPT_MODES.has(mode)) {
      promptMode.value = mode
      return
    }
    promptMode.value = 'professional'
  }

  watch(promptMode, (mode) => {
    localStorage.setItem(PROMPT_MODE_STORAGE_KEY, mode)
  })
  
  // ========== 视口信息 ==========
  const viewportWidth = ref(window.innerWidth)
  const viewportHeight = ref(window.innerHeight)
  const isMobile = computed(() => viewportWidth.value <= 768)
  const isTablet = computed(() => viewportWidth.value > 768 && viewportWidth.value <= 1024)
  const isDesktop = computed(() => viewportWidth.value > 1024)
  
  // ========== 计算属性 ==========
  
  // 侧边栏宽度
  const sidebarWidth = computed(() => {
    // 移动端侧栏以抽屉覆盖主界面，不应推动主内容横向位移。
    if (isMobile.value) {
      return 0
    }
    if (sidebarMode.value === 'full') {
      return 248
    } else if (sidebarMode.value === 'icons') {
      return 64
    }
    return 0
  })
  
  // 右侧面板宽度
  const rightPanelWidth = computed(() => {
    return rightPanelVisible.value && !isMobile.value ? 280 : 0
  })
  
  // 主内容区域宽度
  const mainContentWidth = computed(() => {
    return viewportWidth.value - sidebarWidth.value - rightPanelWidth.value
  })
  
  // 是否有足够空间显示完整布局
  const hasEnoughSpace = computed(() => {
    return viewportWidth.value >= 1024
  })
  
  // ========== Actions ==========
  
  // 切换侧边栏
  const toggleSidebar = () => {
    sidebarExpanded.value = !sidebarExpanded.value
    if (!isMobile.value) {
      localStorage.setItem(SIDEBAR_STORAGE_KEY, String(sidebarExpanded.value))
    }
  }
  
  // 展开侧边栏
  const expandSidebar = () => {
    sidebarExpanded.value = true
    if (!isMobile.value) {
      localStorage.setItem(SIDEBAR_STORAGE_KEY, 'true')
    }
  }
  
  // 收起侧边栏
  const collapseSidebar = () => {
    sidebarExpanded.value = false
    if (!isMobile.value) {
      localStorage.setItem(SIDEBAR_STORAGE_KEY, 'false')
    }
  }

  const toggleWorkspaceList = () => {
    workspaceListExpanded.value = !workspaceListExpanded.value
    localStorage.setItem(WORKSPACE_LIST_STORAGE_KEY, String(workspaceListExpanded.value))
  }
  
  // 移动输入框到底部
  const movePromptBarToBottom = () => {
    if (promptBarPosition.value === 'center') {
      promptBarPosition.value = 'bottom'
      hideWelcome()
    }
  }
  
  // 重置输入框到中央
  const resetPromptBarToCenter = () => {
    promptBarPosition.value = 'center'
    showWelcome.value = true
  }
  
  // 隐藏欢迎界面
  const hideWelcome = () => {
    if (showWelcome.value) {
      welcomeAnimating.value = true
      showWelcome.value = false
      // 动画完成后重置状态
      setTimeout(() => {
        welcomeAnimating.value = false
      }, 300)
    }
  }
  
  // 显示欢迎界面
  const displayWelcome = () => {
    showWelcome.value = true
  }
  
  // 切换右侧面板
  const toggleRightPanel = () => {
    rightPanelVisible.value = !rightPanelVisible.value
  }
  
  // 设置右侧面板位置
  const setRightPanelTop = (top) => {
    // 限制在合理范围内
    const minTop = 64
    const maxTop = viewportHeight.value - 200 // 至少保留 200px 高度
    rightPanelTop.value = Math.max(minTop, Math.min(maxTop, top))
  }
  
  // 开始拖动右侧面板
  const startDraggingRightPanel = () => {
    rightPanelDragging.value = true
  }
  
  // 停止拖动右侧面板
  const stopDraggingRightPanel = () => {
    rightPanelDragging.value = false
  }
  
  // 切换模式
  const switchMode = (mode) => {
    if (['chat', 'interview', 'algorithm', 'recruitment', 'applications', 'knowledge'].includes(mode)) {
      currentMode.value = mode
    }
  }
  
  // 触发侧边栏刷新
  const triggerSidebarRefresh = () => {
    sidebarRefreshTrigger.value++
    console.log('🔄 触发侧边栏刷新, trigger:', sidebarRefreshTrigger.value)
  }
  
  // 切换主题
  const toggleTheme = () => {
    console.log('[UIStore] toggleTheme 被调用')
    console.log('[UIStore] 切换前主题:', currentTheme.value)
    currentTheme.value = currentTheme.value === 'dark' ? 'light' : 'dark'
    console.log('[UIStore] 切换后主题:', currentTheme.value)
    // 应用主题到 document (html 和 body)
    // 同时设置 body 是为了确保 Element Plus 的 teleport 组件（如下拉菜单）也能正确应用主题
    document.documentElement.setAttribute('data-theme', currentTheme.value)
    document.body.setAttribute('data-theme', currentTheme.value)
    // 保存到localStorage
    localStorage.setItem('theme', currentTheme.value)
    console.log('[UIStore] document.documentElement data-theme 已设置为:', currentTheme.value)
  }
  
  // 更新视口尺寸
  const updateViewportSize = () => {
    viewportWidth.value = window.innerWidth
    viewportHeight.value = window.innerHeight
  }
  
  const hasInitialized = ref(false)

  // 初始化
  const initialize = () => {
    console.log('[UIStore] initialize 被调用')
    console.log('[UIStore] 初始主题:', currentTheme.value)

    window.removeEventListener('resize', handleResize)
    window.addEventListener('resize', handleResize)

    // 初始化视口尺寸
    updateViewportSize()

    if (!hasInitialized.value) {
      if (isMobile.value) {
        sidebarExpanded.value = false
        rightPanelVisible.value = false
      } else if (hasEnoughSpace.value) {
        sidebarExpanded.value = savedSidebarExpanded === null
          ? true
          : savedSidebarExpanded === 'true'
      }
      hasInitialized.value = true
    }

    // 应用主题到document（确保主题正确应用）
    // 同时设置 body 是为了确保 Element Plus 的 teleport 组件（如下拉菜单）也能正确应用主题
    document.documentElement.setAttribute('data-theme', currentTheme.value)
    document.body.setAttribute('data-theme', currentTheme.value)
    console.log('[UIStore] 主题已应用到document:', currentTheme.value)
  }
  
  // 清理
  const cleanup = () => {
    window.removeEventListener('resize', handleResize)
  }
  
  // 节流处理窗口大小变化
  let resizeTimer = null
  const handleResize = () => {
    if (resizeTimer) {
      clearTimeout(resizeTimer)
    }
    resizeTimer = setTimeout(() => {
      updateViewportSize()
      
      // 移动端自动收起侧边栏和右侧面板
      if (isMobile.value) {
        sidebarExpanded.value = false
        rightPanelVisible.value = false
      }
    }, 150) // 150ms 节流
  }
  
  // 监听移动端状态变化
  watch(isMobile, (newValue) => {
    if (newValue) {
      // 切换到移动端时，收起侧边栏和右侧面板
      sidebarExpanded.value = false
      rightPanelVisible.value = false
    }
  })
  
  return {
    // 状态
    sidebarExpanded,
    workspaceListExpanded,
    sidebarMode,
    promptBarPosition,
    promptBarFocused,
    showWelcome,
    welcomeAnimating,
    rightPanelVisible,
    rightPanelTop,
    rightPanelDragging,
    currentMode,
    currentTheme,
    promptMode,
    viewportWidth,
    viewportHeight,
    isMobile,
    isTablet,
    isDesktop,
    
    // 计算属性
    sidebarWidth,
    rightPanelWidth,
    mainContentWidth,
    hasEnoughSpace,
    
    // 侧边栏刷新
    sidebarRefreshTrigger,
    triggerSidebarRefresh,
    
    // 方法
    toggleSidebar,
    expandSidebar,
    collapseSidebar,
    toggleWorkspaceList,
    movePromptBarToBottom,
    resetPromptBarToCenter,
    hideWelcome,
    displayWelcome,
    toggleRightPanel,
    setRightPanelTop,
    startDraggingRightPanel,
    stopDraggingRightPanel,
    switchMode,
    toggleTheme,
    setPromptMode,
    updateViewportSize,
    initialize,
    cleanup
  }
})
