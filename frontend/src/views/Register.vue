<template>
  <div class="register-container">
    <!-- 动画背景 -->
    <div class="animated-bg">
      <div class="gradient-overlay"></div>
      <div class="floating-orb orb-1"></div>
      <div class="floating-orb orb-2"></div>
      <div class="floating-orb orb-3"></div>
    </div>
    
    <div class="register-card">
      <div class="register-content">
        <div class="logo-section">
          <div class="logo-icon">🎓</div>
          <h1 class="app-title">XZM Interview Helper</h1>
          <p class="app-subtitle">AI 驱动的面试助手</p>
        </div>
        
        <h1 class="register-title">创建账户</h1>
        <p class="register-subtitle">开始您的面试准备之旅</p>
        
        <form @submit.prevent="handleRegister" class="register-form">
          <div class="form-group">
            <div class="input-wrapper" :class="{ 'focused': usernameFocused }">
              <span class="input-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
              </span>
              <input
                v-model="form.username"
                type="text"
                placeholder="用户名"
                class="form-input"
                :class="{ 'error': errors.username }"
                @focus="usernameFocused = true"
                @blur="usernameFocused = false"
                required
              />
            </div>
            <transition name="slide-fade">
              <span v-if="errors.username" class="error-text">{{ errors.username }}</span>
            </transition>
          </div>

          <div class="form-group">
            <div class="input-wrapper" :class="{ 'focused': passwordFocused }">
              <span class="input-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
              </span>
              <input
                v-model="form.password"
                type="password"
                placeholder="密码"
                class="form-input"
                :class="{ 'error': errors.password }"
                @focus="passwordFocused = true"
                @blur="passwordFocused = false"
                required
              />
            </div>
            <transition name="slide-fade">
              <span v-if="errors.password" class="error-text">{{ errors.password }}</span>
            </transition>
          </div>

          <div class="form-group">
            <div class="input-wrapper" :class="{ 'focused': confirmFocused }">
              <span class="input-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                  <polyline points="22 4 12 14.01 9 11.01"></polyline>
                </svg>
              </span>
              <input
                v-model="form.confirmPassword"
                type="password"
                placeholder="确认密码"
                class="form-input"
                :class="{ 'error': errors.confirmPassword }"
                @focus="confirmFocused = true"
                @blur="confirmFocused = false"
                required
              />
            </div>
            <transition name="slide-fade">
              <span v-if="errors.confirmPassword" class="error-text">{{ errors.confirmPassword }}</span>
            </transition>
          </div>

          <button
            type="submit"
            class="register-btn"
            :disabled="loading"
            :class="{ 'loading': loading }"
          >
            <span v-if="loading" class="loading-spinner"></span>
            <span class="button-text">{{ loading ? '注册中...' : '注册' }}</span>
          </button>
        </form>

        <div class="login-link">
          <span>已经有账户？</span>
          <router-link to="/login" class="link">立即登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)

// 输入框焦点状态
const usernameFocused = ref(false)
const passwordFocused = ref(false)
const confirmFocused = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  agreeTerms: false
})

const errors = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  agreeTerms: ''
})

// 清除错误信息
const clearErrors = () => {
  Object.keys(errors).forEach(key => {
    errors[key] = ''
  })
}

// 表单验证
const validateForm = () => {
  clearErrors()
  let isValid = true

  // 用户名验证
  if (!form.username.trim()) {
    errors.username = '请输入用户名'
    isValid = false
  } else if (form.username.length < 3) {
    errors.username = '用户名至少3个字符'
    isValid = false
  }



  // 密码验证
  if (!form.password) {
    errors.password = '请输入密码'
    isValid = false
  } else if (form.password.length < 6) {
    errors.password = '密码至少6个字符'
    isValid = false
  }

  // 确认密码验证
  if (!form.confirmPassword) {
    errors.confirmPassword = '请确认密码'
    isValid = false
  } else if (form.password !== form.confirmPassword) {
    errors.confirmPassword = '两次输入的密码不一致'
    isValid = false
  }



  return isValid
}

// 处理注册
const handleRegister = async () => {
  console.log('开始注册流程...')
  console.log('表单数据:', form)
  
  if (!validateForm()) {
    console.log('表单验证失败')
    return
  }

  console.log('表单验证通过')
  loading.value = true

  try {
    const registerData = {
      username: form.username,
      password: form.password,
      captcha: ''
    }

    console.log('发送注册请求:', registerData)
    const result = await userStore.register(registerData)
    console.log('注册响应:', result)
    
    if (result.success) {
      ElMessage.success('注册成功！')
      setTimeout(() => {
        router.push('/login')
      }, 1000)
    } else {
      ElMessage.error(result.message || '注册失败，请重试')
    }
  } catch (error) {
    console.error('注册失败:', error)
    ElMessage.error(error.message || '注册失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ============================================
   注册页面样式 - 参考 App.tsx 设计
   ============================================ */

.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

/* 动画背景 */
.animated-bg {
  position: fixed;
  inset: 0;
  z-index: -1;
}

.gradient-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.floating-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
  animation: float 20s ease-in-out infinite;
}

.orb-1 {
  top: -20%;
  left: -10%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(255,255,255,0.3) 0%, transparent 70%);
  animation-delay: 0s;
}

.orb-2 {
  bottom: -30%;
  right: -20%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(255,255,255,0.2) 0%, transparent 70%);
  animation-delay: -7s;
}

.orb-3 {
  top: 40%;
  right: 30%;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(255,255,255,0.15) 0%, transparent 70%);
  animation-delay: -14s;
}

@keyframes float {
  0%, 100% { 
    transform: translate(0, 0) scale(1); 
  }
  25% {
    transform: translate(30px, -30px) scale(1.05);
  }
  50% { 
    transform: translate(20px, -20px) rotate(180deg) scale(0.95); 
  }
  75% {
    transform: translate(-20px, 20px) scale(1.02);
  }
}

/* 注册卡片 */
.register-card {
  background: white;
  border-radius: 24px;
  box-shadow: 
    0 25px 50px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.1);
  overflow: hidden;
  max-width: 480px;
  width: 100%;
  position: relative;
  z-index: 1;
  backdrop-filter: blur(10px);
  animation: cardSlideUp 0.6s ease-out;
}

@keyframes cardSlideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.register-content {
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
}

/* Logo 区域 */
.logo-section {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  font-size: 64px;
  margin-bottom: 16px;
  animation: bounce 2s ease-in-out infinite;
  display: inline-block;
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.app-title {
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0 0 8px 0;
}

.app-subtitle {
  font-size: 14px;
  color: #718096;
  margin: 0;
}

/* 标题 */
.register-title {
  font-size: 32px;
  font-weight: 700;
  color: #2d3748;
  margin: 0 0 8px 0;
  text-align: center;
}

.register-subtitle {
  font-size: 15px;
  color: #718096;
  margin: 0 0 32px 0;
  text-align: center;
}

/* 表单样式 */
.register-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  transition: all 0.3s ease;
}

.input-wrapper.focused .input-icon {
  color: #667eea;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: #a0aec0;
  z-index: 1;
  transition: color 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-input {
  width: 100%;
  padding: 16px 16px 16px 50px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  font-size: 16px;
  transition: all 0.3s ease;
  background: #f7fafc;
  color: #2d3748;
}

.form-input::placeholder {
  color: #a0aec0;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  background: white;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-input.error {
  border-color: #e53e3e;
  background: #fff5f5;
}

.form-input.error:focus {
  box-shadow: 0 0 0 3px rgba(229, 62, 62, 0.1);
}

/* 错误信息动画 */
.slide-fade-enter-active {
  transition: all 0.3s ease;
}
.slide-fade-leave-active {
  transition: all 0.2s ease;
}
.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(-5px);
  opacity: 0;
}

.error-text {
  color: #e53e3e;
  font-size: 13px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 注册按钮 */
.register-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 16px 30px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  position: relative;
  overflow: hidden;
}

.register-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.2),
    transparent
  );
  transition: left 0.5s ease;
}

.register-btn:hover:not(:disabled)::before {
  left: 100%;
}

.register-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(102, 126, 234, 0.4);
}

.register-btn:active:not(:disabled) {
  transform: translateY(0);
}

.register-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.register-btn.loading {
  pointer-events: none;
}

.button-text {
  position: relative;
  z-index: 1;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid transparent;
  border-top: 2px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 登录链接 */
.login-link {
  text-align: center;
  margin-top: 30px;
  color: #718096;
  font-size: 14px;
}

.link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
  margin-left: 5px;
  transition: all 0.2s ease;
}

.link:hover {
  text-decoration: underline;
  color: #764ba2;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .register-card {
    max-width: 400px;
  }
  
  .register-content {
    padding: 36px 24px;
  }
  
  .logo-icon {
    font-size: 48px;
  }
  
  .app-title {
    font-size: 24px;
  }
  
  .register-title {
    font-size: 28px;
  }
}

@media (max-width: 480px) {
  .register-container {
    padding: 16px;
  }
  
  .register-content {
    padding: 32px 20px;
  }
  
  .register-title {
    font-size: 24px;
  }
  
  .register-subtitle {
    font-size: 14px;
  }
}
</style>