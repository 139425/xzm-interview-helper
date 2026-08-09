<template>
  <div class="login-container">
    <!-- 动画背景 -->
    <div class="animated-bg">
      <div class="gradient-overlay"></div>
      <div class="floating-orb orb-1"></div>
      <div class="floating-orb orb-2"></div>
      <div class="floating-orb orb-3"></div>
    </div>
    
    <div class="login-card">
      <div class="form-section">
        <div class="logo-section">
          <div class="logo-icon">🎓</div>
          <h1 class="app-title">XZM Interview Helper</h1>
          <p class="app-subtitle">AI 驱动的面试助手</p>
        </div>
        
        <div class="form-container">
          <h2 class="login-title">欢迎回来</h2>
          <p class="login-subtitle">登录您的账户继续使用</p>
          
          <form @submit.prevent="handleLogin" class="login-form">
            <!-- 用户名输入框 -->
            <div class="input-group">
              <div class="input-wrapper" :class="{ 'focused': usernameFocused }">
                <span class="input-icon">
                  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path>
                    <circle cx="12" cy="7" r="4"></circle>
                  </svg>
                </span>
                <input
                  v-model="loginForm.username"
                  type="text"
                  placeholder="用户名"
                  class="form-input"
                  :class="{ 'error': errors.username }"
                  @blur="validateUsername(); usernameFocused = false"
                  @focus="usernameFocused = true"
                />
              </div>
              <transition name="slide-fade">
                <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
              </transition>
            </div>
            
            <!-- 密码输入框 -->
            <div class="input-group">
              <div class="input-wrapper" :class="{ 'focused': passwordFocused }">
                <span class="input-icon">
                  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                  </svg>
                </span>
                <input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="密码"
                  class="form-input"
                  :class="{ 'error': errors.password }"
                  @blur="validatePassword(); passwordFocused = false"
                  @focus="passwordFocused = true"
                />
              </div>
              <transition name="slide-fade">
                <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
              </transition>
            </div>
            
            <!-- 登录按钮 -->
            <button 
              type="submit" 
              class="login-button"
              :disabled="loginLoading || !isFormValid"
              :class="{ 'loading': loginLoading }"
            >
              <span v-if="loginLoading" class="loading-spinner"></span>
              <span class="button-text">{{ loginLoading ? '登录中...' : '登录' }}</span>
            </button>
          </form>
          
          <!-- 注册链接 -->
          <div class="register-link">
            <span>还没有账户？</span>
            <router-link to="/register" class="link">立即注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

// 表单数据
const loginForm = ref({
  username: '',
  password: ''
})

// 表单验证错误
const errors = ref({
  username: '',
  password: ''
})

// 记住我选项
const rememberMe = ref(false)

// 输入框焦点状态
const usernameFocused = ref(false)
const passwordFocused = ref(false)

// 登录加载状态
const loginLoading = computed(() => userStore.loginLoading)

// 表单验证状态
const isFormValid = computed(() => {
  return loginForm.value.username.trim() !== '' && 
         loginForm.value.password.trim() !== '' && 
         !errors.value.username && 
         !errors.value.password
})

// 验证用户名
const validateUsername = () => {
  if (!loginForm.value.username.trim()) {
    errors.value.username = '请输入用户名'
  } else if (loginForm.value.username.length < 3) {
    errors.value.username = '用户名至少3个字符'
  } else {
    errors.value.username = ''
  }
}

// 验证密码
const validatePassword = () => {
  if (!loginForm.value.password.trim()) {
    errors.value.password = '请输入密码'
  } else if (loginForm.value.password.length < 4) {
    errors.value.password = '密码至少4个字符'
  } else {
    errors.value.password = ''
  }
}

// 处理登录
const handleLogin = async () => {
  // 验证表单
  validateUsername()
  validatePassword()
  
  if (!isFormValid.value) {
    return
  }
  
  try {
    const result = await userStore.login(loginForm.value.username, loginForm.value.password)
    
    if (result.success) {
      // 登录成功，跳转到聊天页面
      router.push('/chat')
    } else {
      // 显示错误信息
      ElMessage.error(result.message || '登录失败，请检查账号和密码')
    }
  } catch (error) {
    console.error('登录失败:', error)
    ElMessage.error('登录失败，请稍后重试')
  }
}

// 组件挂载时恢复用户状态
onMounted(() => {
  userStore.restoreUserState()
  
  // 检查是否是认证过期跳转过来的
  const authExpired = sessionStorage.getItem('authExpired')
  if (authExpired === 'true') {
    // 清除标志
    sessionStorage.removeItem('authExpired')
    ElMessage.warning({
      message: '认证过期，请重新登录',
      duration: 3000
    })
  }
  
  // 如果已经登录，直接跳转到聊天页面
  if (userStore.isLoggedIn) {
    router.push('/chat')
  }
})
</script>

<style scoped>
/* ============================================
   登录页面样式 - 参考 App.tsx 设计
   ============================================ */

.login-container {
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
  right: -10%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(255,255,255,0.3) 0%, transparent 70%);
  animation-delay: 0s;
}

.orb-2 {
  bottom: -30%;
  left: -20%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(255,255,255,0.2) 0%, transparent 70%);
  animation-delay: -7s;
}

.orb-3 {
  top: 50%;
  left: 50%;
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
    transform: translate(-30px, 30px) scale(1.05);
  }
  50% { 
    transform: translate(-20px, 20px) rotate(180deg) scale(0.95); 
  }
  75% {
    transform: translate(20px, -20px) scale(1.02);
  }
}

/* 登录卡片 */
.login-card {
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

.form-section {
  display: flex;
  flex-direction: column;
  padding: 48px 40px;
}

/* Logo 区域 */
.logo-section {
  text-align: center;
  margin-bottom: 40px;
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

/* 表单容器 */
.form-container {
  width: 100%;
}

.login-title {
  font-size: 32px;
  font-weight: 700;
  color: #2d3748;
  margin: 0 0 8px 0;
  text-align: center;
}

.login-subtitle {
  font-size: 15px;
  color: #718096;
  margin: 0 0 32px 0;
  text-align: center;
}

/* 表单样式 */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.input-group {
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

.error-message {
  color: #e53e3e;
  font-size: 13px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 登录按钮 */
.login-button {
  width: 100%;
  padding: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  position: relative;
  overflow: hidden;
}

.login-button::before {
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

.login-button:hover:not(:disabled)::before {
  left: 100%;
}

.login-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.login-button:active:not(:disabled) {
  transform: translateY(0);
}

.login-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.login-button.loading {
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

/* 注册链接 */
.register-link {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: #4a5568;
}

.link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
  margin-left: 4px;
  transition: all 0.2s ease;
}

.link:hover {
  text-decoration: underline;
  color: #764ba2;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-card {
    max-width: 400px;
  }
  
  .form-section {
    padding: 36px 24px;
  }
  
  .logo-icon {
    font-size: 48px;
  }
  
  .app-title {
    font-size: 24px;
  }
  
  .login-title {
    font-size: 28px;
  }
}

@media (max-width: 480px) {
  .login-container {
    padding: 16px;
  }
  
  .form-section {
    padding: 32px 20px;
  }
  
  .login-title {
    font-size: 24px;
  }
  
  .login-subtitle {
    font-size: 14px;
  }
}
</style>
