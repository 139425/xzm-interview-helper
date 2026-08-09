<template>
  <div class="user-avatar-container">
    <!-- 用户头像按钮 -->
    <button
      class="user-avatar-btn"
      @click="toggleMenu"
      :title="userStore.isLoggedIn ? userStore.username : '未登录'"
    >
      <el-icon v-if="!userStore.isLoggedIn" :size="22">
        <User />
      </el-icon>
      <span v-else class="avatar-text">
        {{ avatarInitial }}
      </span>
    </button>

    <!-- 下拉菜单 -->
    <transition name="menu-fade">
      <div v-if="showMenu" class="user-menu" @click.stop>
        <!-- 用户信息 -->
        <div v-if="userStore.isLoggedIn" class="menu-header">
          <div class="user-info">
            <div class="user-name">{{ userStore.username }}</div>
            <div class="user-type">{{ userStore.userType || '普通用户' }}</div>
          </div>
        </div>

        <div v-if="userStore.isLoggedIn" class="menu-divider"></div>

        <!-- 菜单项 -->
        <div class="menu-items">
          <!-- 主题切换（始终显示） -->
          <button class="menu-item" @click="handleThemeToggle">
            <el-icon :size="18">
              <Sunny v-if="currentThemeValue === 'dark'" />
              <Moon v-else />
            </el-icon>
            <span>{{ currentThemeValue === 'dark' ? '切换到亮色模式' : '切换到暗色模式' }}</span>
          </button>

          <div class="menu-divider"></div>

          <!-- 登录状态菜单 -->
          <template v-if="userStore.isLoggedIn">
            <button class="menu-item" @click="handleProfile">
              <el-icon :size="18"><User /></el-icon>
              <span>个人信息</span>
            </button>

            <button class="menu-item" @click="handleSettings">
              <el-icon :size="18"><Setting /></el-icon>
              <span>设置</span>
            </button>

            <!-- 管理员菜单 -->
            <button
              v-if="userStore.isAdmin"
              class="menu-item"
              @click="handleUserManagement"
            >
              <el-icon :size="18"><UserFilled /></el-icon>
              <span>用户管理</span>
            </button>

            <div class="menu-divider"></div>

            <button class="menu-item logout" @click="handleLogout">
              <el-icon :size="18"><SwitchButton /></el-icon>
              <span>退出登录</span>
            </button>
          </template>

          <!-- 未登录状态菜单 -->
          <template v-else>
            <button class="menu-item" @click="handleLogin">
              <el-icon :size="18"><User /></el-icon>
              <span>登录</span>
            </button>

            <button class="menu-item" @click="handleRegister">
              <el-icon :size="18"><EditPen /></el-icon>
              <span>注册</span>
            </button>
          </template>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useUIStore } from '../stores/ui'
import { useTheme } from '../composables/useTheme'
import { User, Setting, UserFilled, SwitchButton, EditPen, Sunny, Moon } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const uiStore = useUIStore()
const { resolvedTheme, toggle: toggleAppTheme } = useTheme()

// 状态
const showMenu = ref(false)
const currentThemeValue = computed(() => resolvedTheme.value)

// 计算头像首字母
const avatarInitial = computed(() => {
  if (userStore.username) {
    return userStore.username.charAt(0).toUpperCase()
  }
  return 'U'
})

// 更新当前主题值（保留方法供旧代码调用，但现在由 useTheme 自动同步）
const updateCurrentTheme = () => {
  // no-op: currentThemeValue 已经是 resolvedTheme 的 computed
}

// 切换菜单
const toggleMenu = () => {
  showMenu.value = !showMenu.value
}

// 关闭菜单
const closeMenu = () => {
  showMenu.value = false
}

// ========== 主题切换 ==========

const handleThemeToggle = () => {
  toggleAppTheme()
  // 同步给旧 uiStore（供其它仍读取 uiStore.currentTheme 的代码使用）
  if (typeof uiStore.toggleTheme === 'function') {
    // 仅在两侧不一致时同步一次（避免再次切换）
    const ui = uiStore.currentTheme
    if (ui !== resolvedTheme.value) {
      uiStore.toggleTheme()
    }
  }
}

// ========== 菜单操作 ==========

// 个人信息
const handleProfile = () => {
  closeMenu()
  ElMessage.info('个人信息功能开发中')
}

// 设置
const handleSettings = () => {
  closeMenu()
  ElMessage.info('设置功能开发中')
}

// 用户管理
const handleUserManagement = () => {
  closeMenu()
  router.push('/admin/users')
}

// 退出登录
const handleLogout = async () => {
  closeMenu()
  
  try {
    await ElMessageBox.confirm(
      '确认退出登录吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    userStore.logout()
    ElMessage.success('已退出登录')
    
    // 跳转到登录页
    router.push('/login')
  } catch {
    // 用户取消
  }
}

// 登录
const handleLogin = () => {
  closeMenu()
  router.push('/login')
}

// 注册
const handleRegister = () => {
  closeMenu()
  router.push('/register')
}

// ========== 点击外部关闭菜单 ==========

const handleClickOutside = (event) => {
  const container = event.target.closest('.user-avatar-container')
  if (!container && showMenu.value) {
    closeMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  // 初始化主题
  updateCurrentTheme()
  console.log('[UserAvatar] 组件挂载，当前主题:', currentThemeValue.value)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
/* 容器 */
.user-avatar-container {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-md);
}

/* 主题切换按钮 */
.theme-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--gemini-radius-full);
  background-color: var(--gemini-bg-tertiary);
  color: var(--gemini-text-primary);
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
}

.theme-toggle-btn:hover {
  background-color: var(--gemini-bg-hover);
  color: var(--gemini-accent-blue);
  transform: scale(1.05);
}

.theme-toggle-btn:active {
  transform: scale(0.95);
}

/* 头像按钮 */
.user-avatar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 2px solid var(--gemini-border-color);
  border-radius: var(--gemini-radius-full);
  background-color: var(--gemini-bg-tertiary);
  color: var(--gemini-text-primary);
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
  overflow: hidden;
}

.user-avatar-btn:hover {
  border-color: var(--gemini-accent-blue);
  background-color: var(--gemini-bg-hover);
  transform: scale(1.05);
}

.user-avatar-btn:active {
  transform: scale(0.95);
}

.avatar-text {
  font-size: 1.1rem;
  line-height: 1;
}

/* 下拉菜单 */
.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 220px;
  background-color: var(--gemini-bg-secondary);
  border: 1px solid var(--gemini-border-color);
  border-radius: var(--gemini-radius-lg);
  box-shadow: var(--gemini-shadow-lg);
  overflow: hidden;
  z-index: var(--gemini-z-dropdown);
}

/* 菜单淡入淡出动画 */
.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: all var(--gemini-transition-fast);
}

.menu-fade-enter-from,
.menu-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 菜单头部 */
.menu-header {
  padding: var(--gemini-spacing-lg);
  background-color: var(--gemini-bg-tertiary);
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-name {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gemini-text-primary);
}

.user-type {
  font-size: 0.75rem;
  color: var(--gemini-text-tertiary);
  padding: 2px 8px;
  border-radius: var(--gemini-radius-full);
  background-color: var(--gemini-accent-blue);
  color: white;
  display: inline-block;
  width: fit-content;
}

/* 分隔线 */
.menu-divider {
  height: 1px;
  background-color: var(--gemini-border-color);
  margin: var(--gemini-spacing-sm) 0;
}

/* 菜单项容器 */
.menu-items {
  padding: var(--gemini-spacing-sm);
}

/* 菜单项 */
.menu-item {
  display: flex;
  align-items: center;
  gap: var(--gemini-spacing-md);
  width: 100%;
  padding: var(--gemini-spacing-md) var(--gemini-spacing-lg);
  border: none;
  border-radius: var(--gemini-radius-md);
  background-color: transparent;
  color: var(--gemini-text-primary);
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;
  transition: all var(--gemini-transition-fast);
}

.menu-item:hover {
  background-color: var(--gemini-bg-hover);
  color: var(--gemini-accent-blue);
}

.menu-item:active {
  transform: scale(0.98);
}

.menu-item.logout {
  color: var(--gemini-accent-red);
}

.menu-item.logout:hover {
  background-color: rgba(242, 139, 130, 0.1);
  color: var(--gemini-accent-red);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .user-menu {
    right: -8px;
  }
}
</style>
