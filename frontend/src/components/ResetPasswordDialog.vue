<template>
  <el-dialog
    v-model="dialogVisible"
    title="重置用户密码"
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
      </div>
      
      <el-form 
        :model="form" 
        :rules="rules" 
        ref="formRef" 
        label-width="100px"
        class="password-form"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input 
            v-model="form.newPassword" 
            type="password" 
            placeholder="请输入新密码"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input 
            v-model="form.confirmPassword" 
            type="password" 
            placeholder="请再次输入新密码"
            show-password
            clearable
          />
        </el-form-item>
      </el-form>

      <div class="password-tips">
        <h4>密码要求：</h4>
        <ul>
          <li>长度至少4个字符</li>
          <li>建议包含字母和数字</li>
          <li>避免使用过于简单的密码</li>
        </ul>
      </div>
    </div>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button 
          type="primary" 
          @click="handleConfirm"
          :loading="loading"
        >
          确认重置
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

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
const formRef = ref(null)
const form = ref({
  newPassword: '',
  confirmPassword: ''
})

// 表单验证规则
const rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 4, message: '密码长度至少4个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.value.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 计算属性
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

// 监听对话框显示状态，重置表单
watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    nextTick(() => {
      resetForm()
    })
  }
})

// 方法
const handleClose = () => {
  dialogVisible.value = false
  resetForm()
}

const handleConfirm = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    
    loading.value = true
    await emit('confirm', props.user.id, form.value.newPassword)
  } catch (error) {
    // 表单验证失败
    console.log('表单验证失败:', error)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.value.newPassword = ''
  form.value.confirmPassword = ''
  
  if (formRef.value) {
    formRef.value.clearValidate()
  }
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
  margin: 0;
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

.password-form {
  margin-bottom: 1.5rem;
}

.password-tips {
  background-color: var(--bg-tertiary);
  border-radius: 8px;
  padding: 1rem;
  border-left: 4px solid var(--primary-color);
}

.password-tips h4 {
  margin: 0 0 0.75rem 0;
  font-size: 0.875rem;
  color: var(--text-primary);
}

.password-tips ul {
  margin: 0;
  padding-left: 1.25rem;
}

.password-tips li {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-bottom: 0.25rem;
}

.password-tips li:last-child {
  margin-bottom: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

/* 表单项样式调整 */
:deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--text-primary);
}

:deep(.el-input__wrapper) {
  border-radius: 6px;
}
</style>