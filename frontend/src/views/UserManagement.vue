<template>
  <div class="user-management-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">管理系统中的所有用户账户</p>
      </div>
      <div class="header-actions">
        <button 
          @click="refreshUserList" 
          :disabled="loading"
          class="refresh-btn"
        >
          <el-icon :size="16" :class="{ 'is-loading': loading }">
            <Refresh />
          </el-icon>
          <span>刷新</span>
        </button>
      </div>
    </div>

    <!-- 搜索和筛选区域 -->
    <div class="search-section">
      <div class="search-controls">
        <div class="search-input">
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索用户名..."
            @input="handleSearch"
            clearable
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        
        <div class="filter-select">
          <el-select 
            v-model="userTypeFilter" 
            placeholder="筛选用户类型" 
            clearable
            @change="handleFilter"
          >
            <el-option label="全部" value=""></el-option>
            <el-option label="管理员" value="管理员"></el-option>
            <el-option label="正常用户" value="普通用户"></el-option>
          </el-select>
        </div>
      </div>
    </div>

    <!-- 用户列表表格 -->
    <div class="table-section">
      <el-table 
        :data="paginatedUsers" 
        v-loading="loading"
        stripe
        style="width: 100%"
        :empty-text="getEmptyText()"
      >
        <el-table-column prop="id" label="用户ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="user_type" label="用户类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.user_type === '管理员' ? 'danger' : 'primary'">
              {{ row.user_type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="注册时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.create_time) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button 
                size="small" 
                @click="openEditUserType(row)"
                :disabled="row.id === userStore.userId"
              >
                修改权限
              </el-button>
              <el-button 
                size="small" 
                type="warning"
                @click="openResetPassword(row)"
              >
                重置密码
              </el-button>
              <el-button 
                size="small" 
                type="danger"
                @click="confirmDeleteUser(row)"
                :disabled="row.id === userStore.userId"
              >
                删除用户
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页组件 -->
    <div class="pagination-section">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="filteredUsers.length"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 修改用户类型对话框 -->
    <UserTypeDialog 
      v-model:visible="showUserTypeDialog"
      :user="selectedUser"
      @confirm="handleUpdateUserType"
    />

    <!-- 重置密码对话框 -->
    <ResetPasswordDialog
      v-model:visible="showResetPasswordDialog"
      :user="selectedUser"
      @confirm="handleResetPassword"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '../stores/user'
import { adminApi } from '../api/admin'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UserTypeDialog from '../components/UserTypeDialog.vue'
import ResetPasswordDialog from '../components/ResetPasswordDialog.vue'

// 状态管理
const userStore = useUserStore()

// 响应式数据
const users = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const userTypeFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const selectedUser = ref(null)
const showUserTypeDialog = ref(false)
const showResetPasswordDialog = ref(false)

// 计算属性
const filteredUsers = computed(() => {
  let result = users.value

  // 搜索过滤
  if (searchKeyword.value) {
    result = result.filter(user => 
      user.username.toLowerCase().includes(searchKeyword.value.toLowerCase())
    )
  }

  // 用户类型过滤
  if (userTypeFilter.value) {
    result = result.filter(user => user.user_type === userTypeFilter.value)
  }

  return result
})

const paginatedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredUsers.value.slice(start, end)
})

// 方法
const loadUserList = async () => {
  loading.value = true
  try {
    const response = await adminApi.getAllUsers()
    if (response.code === 200) {
      users.value = response.data || []
      userStore.setUserList(users.value)
    } else {
      ElMessage.error(response.message || '获取用户列表失败')
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

const refreshUserList = () => {
  userStore.clearUserListCache()
  loadUserList()
}

// 搜索防抖处理
let searchTimeout = null
const handleSearch = () => {
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }
  
  searchTimeout = setTimeout(() => {
    currentPage.value = 1
  }, 300)
}

const handleFilter = () => {
  currentPage.value = 1
}

const handleSizeChange = (newSize) => {
  pageSize.value = newSize
  currentPage.value = 1
}

const handleCurrentChange = (newPage) => {
  currentPage.value = newPage
}

const formatDateTime = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getEmptyText = () => {
  if (loading.value) return '加载中...'
  if (searchKeyword.value || userTypeFilter.value) {
    return '没有找到符合条件的用户'
  }
  return '暂无用户数据'
}

const openEditUserType = (user) => {
  selectedUser.value = user
  showUserTypeDialog.value = true
}

const openResetPassword = (user) => {
  selectedUser.value = user
  showResetPasswordDialog.value = true
}

const confirmDeleteUser = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${user.username}" 吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
    
    await handleDeleteUser(user)
  } catch (error) {
    // 用户取消删除
  }
}

const handleUpdateUserType = async (userId, newUserType) => {
  try {
    const response = await adminApi.updateUserType(userId, newUserType)
    if (response.code === 200) {
      ElMessage.success('用户权限修改成功')
      showUserTypeDialog.value = false
      
      // 更新本地数据，避免重新加载整个列表
      const userIndex = users.value.findIndex(user => user.id === userId)
      if (userIndex !== -1) {
        users.value[userIndex].user_type = newUserType
      }
    } else {
      ElMessage.error(response.message || '修改用户权限失败')
    }
  } catch (error) {
    console.error('修改用户权限失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const handleResetPassword = async (userId, newPassword) => {
  try {
    const response = await adminApi.resetPassword(userId, newPassword)
    if (response.code === 200) {
      ElMessage.success('密码重置成功')
      showResetPasswordDialog.value = false
    } else {
      ElMessage.error(response.message || '密码重置失败')
    }
  } catch (error) {
    console.error('密码重置失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const handleDeleteUser = async (user) => {
  try {
    const response = await adminApi.deleteUser(user.id)
    if (response.code === 200) {
      ElMessage.success('用户删除成功')
      
      // 从本地数据中移除用户，避免重新加载
      const userIndex = users.value.findIndex(u => u.id === user.id)
      if (userIndex !== -1) {
        users.value.splice(userIndex, 1)
      }
      
      // 如果当前页没有数据了，回到上一页
      if (paginatedUsers.value.length === 0 && currentPage.value > 1) {
        currentPage.value--
      }
    } else {
      ElMessage.error(response.message || '删除用户失败')
    }
  } catch (error) {
    console.error('删除用户失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

// 键盘快捷键处理
const handleKeydown = (event) => {
  // Ctrl/Cmd + R 刷新列表
  if ((event.ctrlKey || event.metaKey) && event.key === 'r') {
    event.preventDefault()
    refreshUserList()
  }
  // ESC 关闭对话框
  if (event.key === 'Escape') {
    showUserTypeDialog.value = false
    showResetPasswordDialog.value = false
  }
}

// 生命周期
onMounted(() => {
  // 检查缓存
  if (userStore.isUserListCacheValid() && userStore.userList.length > 0) {
    users.value = userStore.userList
  } else {
    loadUserList()
  }
  
  // 添加键盘事件监听
  document.addEventListener('keydown', handleKeydown)
})

// 清理事件监听
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }
})
</script>

<style scoped>
.user-management-container {
  min-height: 100vh;
  background-color: var(--bg-primary);
  padding: 2rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 2rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.header-left {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
}

.page-subtitle {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 1rem;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.refresh-btn:hover:not(:disabled) {
  background-color: var(--bg-secondary);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.search-section {
  margin-bottom: 1.5rem;
}

.search-controls {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.search-input {
  flex: 1;
  max-width: 300px;
}

.filter-select {
  width: 200px;
}

.table-section {
  background-color: var(--bg-secondary);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.action-buttons {
  display: flex;
  gap: 0.5rem;
  justify-content: center;
  flex-wrap: wrap;
}

.pagination-section {
  display: flex;
  justify-content: center;
  padding: 1rem 0;
}

/* 表格行悬停效果 */
:deep(.el-table__row:hover > td) {
  background-color: var(--bg-tertiary) !important;
}

/* 标签样式优化 */
:deep(.el-tag) {
  font-weight: 500;
  border-radius: 6px;
}

/* 按钮组样式优化 */
.action-buttons .el-button {
  border-radius: 6px;
  font-weight: 500;
}

.action-buttons .el-button--small {
  padding: 6px 12px;
}

/* 分页样式优化 */
:deep(.el-pagination) {
  --el-pagination-button-color: var(--text-secondary);
  --el-pagination-hover-color: var(--primary-color);
}

/* 加载状态优化 */
:deep(.el-loading-mask) {
  background-color: rgba(255, 255, 255, 0.8);
}

[data-theme="dark"] :deep(.el-loading-mask) {
  background-color: rgba(0, 0, 0, 0.8);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .user-management-container {
    padding: 1rem;
  }
  
  .page-header {
    flex-direction: column;
    gap: 1rem;
  }
  
  .search-controls {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-input,
  .filter-select {
    max-width: none;
    width: 100%;
  }
  
  .table-section {
    padding: 1rem;
    overflow-x: auto;
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 0.25rem;
  }
  
  .action-buttons .el-button {
    width: 100%;
    margin: 0;
  }
}
</style>