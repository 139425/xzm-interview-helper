<template>
  <el-dialog
    v-model="dialogVisible"
    title="修改用户权限"
    width="400px"
    :before-close="handleClose"
    destroy-on-close
  >
    <div class="dialog-content">
      <div class="user-info">
        <p class="info-item">
          <span class="label">用户:</span>
          <strong class="value">{{ user?.username }}</strong>
        </p>
        <p class="info-item">
          <span class="label">当前权限:</span>
          <el-tag :type="user?.user_type === '管理员' ? 'danger' : 'primary'">
            {{ user?.user_type }}
          </el-tag>
        </p>
      </div>
      
      <el-form :model="form" label-width="80px" class="permission-form">
        <el-form-item label="新权限" required>
          <el-select 
            v-model="form.userType" 
            placeholder="选择用户类型"
            style="width: 100%"
          >
            <el-option label="普通用户" value="普通用户">
              <div class="option-content">
                <span>普通用户</span>
                <span class="option-desc">基础功能权限</span>
              </div>
            </el-option>
            <el-option label="管理员" value="管理员">
              <div class="option-content">
                <span>管理员</span>
                <span class="option-desc">完整管理权限</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>

      <div class="warning-notice" v-if="form.userType === '管理员'">
        <el-icon class="warning-icon"><Warning /></el-icon>
        <span>管理员拥有系统完整权限，请谨慎授予。</span>
      </div>
    </div>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button 
          type="primary" 
          @click="handleConfirm"
          :loading="loading"
          :disabled="!form.userType || form.userType === user?.user_type"
        >
          确认修改
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Warning } from '@element-plus/icons-vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  user: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

// 响应式数据
const loading = ref(false)
const form = ref({
  userType: ''
})

// 计算属性
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

// 监听用户变化，重置表单
watch(() => props.user, (newUser) => {
  if (newUser) {
    form.value.userType = newUser.user_type
  }
}, { immediate: true })

// 方法
const handleClose = () => {
  dialogVisible.value = false
  resetForm()
}

const handleConfirm = async () => {
  if (!form.value.userType || form.value.userType === props.user?.user_type) {
    return
  }

  loading.value = true
  try {
    await emit('confirm', props.user.id, form.value.userType)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.value.userType = ''
}
</script>

<style scoped>
.dialog-content {
  padding: 0.5rem 0;
}

.user-info {
  background-color: var(--bg-secondary);
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1.5rem;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 0 0 0.75rem 0;
}

.info-item:last-child {
  margin-bottom: 0;
}

.label {
  color: var(--text-secondary);
  font-size: 0.875rem;
  min-width: 60px;
}

.value {
  color: var(--text-primary);
  font-size: 0.875rem;
}

.permission-form {
  margin-bottom: 1rem;
}

.option-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.option-desc {
  font-size: 0.75rem;
  color: var(--text-tertiary);
}

.warning-notice {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  background-color: #fef3cd;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #92400e;
  font-size: 0.875rem;
}

.warning-icon {
  color: #f59e0b;
  flex-shrink: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

/* 深色主题适配 */
[data-theme="dark"] .warning-notice {
  background-color: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.3);
  color: #fbbf24;
}
</style>