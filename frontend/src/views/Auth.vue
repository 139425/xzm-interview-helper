<template>
  <div class="auth-container">
    <!-- Animated background -->
    <div class="animated-bg">
      <div class="gradient-overlay"></div>
      <div class="floating-orb orb-1"></div>
      <div class="floating-orb orb-2"></div>
      <div class="floating-orb orb-3"></div>
    </div>

    <div class="auth-card">
      <div class="auth-content">
        <div class="logo-section">
          <div class="logo-icon">🎓</div>
          <h1 class="app-title">XZM Interview Helper</h1>
          <p class="app-subtitle">AI 驱动的面试助手</p>
        </div>

        <!-- Dynamic title -->
        <div class="title-stack" :class="{ 'is-login': isLogin, 'is-register': isRegister }" aria-live="polite">
          <div class="stack-item login">
            <h2 class="auth-title">欢迎回来</h2>
          </div>
          <div class="stack-item register">
            <h2 class="auth-title">创建账户</h2>
          </div>
        </div>

        <!-- Dynamic subtitle -->
        <div class="subtitle-stack" :class="{ 'is-login': isLogin, 'is-register': isRegister }">
          <div class="stack-item login">
            <p class="auth-subtitle">登录您的账户继续使用</p>
          </div>
          <div class="stack-item register">
            <p class="auth-subtitle">开始您的面试准备之旅</p>
          </div>
        </div>

        <form @submit.prevent="handleSubmit" class="auth-form">
          <!-- Username -->
          <div class="input-group">
            <div class="input-wrapper" :class="{ focused: usernameFocused }">
              <span class="input-icon" aria-hidden="true">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
              </span>
              <input
                v-model="form.username"
                type="text"
                placeholder="用户名"
                class="form-input"
                :class="{ error: errors.username }"
                autocomplete="username"
                autocorrect="off"
                autocapitalize="none"
                spellcheck="false"
                @focus="usernameFocused = true"
                @blur="onUsernameBlur"
              />
            </div>
            <transition name="slide-fade">
              <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
            </transition>
          </div>

          <!-- Password -->
          <div class="input-group">
            <div class="input-wrapper" :class="{ focused: passwordFocused }">
              <span class="input-icon" aria-hidden="true">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
              </span>
              <input
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="密码"
                class="form-input"
                :class="{ error: errors.password }"
                :autocomplete="isLogin ? 'current-password' : 'new-password'"
                @focus="passwordFocused = true"
                @blur="onPasswordBlur"
              />
              <button
                type="button"
                class="password-toggle"
                @click="showPassword = !showPassword"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              >
                <svg
                  v-if="!showPassword"
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
                <svg
                  v-else
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                  <line x1="1" y1="1" x2="23" y2="23"></line>
                </svg>
              </button>
            </div>
            <transition name="slide-fade">
              <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
            </transition>
          </div>

          <!-- Confirm password (register only) -->
          <div class="input-group confirm-group" :class="{ visible: isRegister }">
            <div class="input-wrapper" :class="{ focused: confirmFocused }">
              <span class="input-icon" aria-hidden="true">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                  <polyline points="22 4 12 14.01 9 11.01"></polyline>
                </svg>
              </span>
              <input
                v-model="form.confirmPassword"
                type="password"
                placeholder="确认密码"
                class="form-input"
                :class="{ error: errors.confirmPassword }"
                autocomplete="new-password"
                @focus="confirmFocused = true"
                @blur="onConfirmBlur"
              />
            </div>
            <transition name="slide-fade">
              <span v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</span>
            </transition>
          </div>

          <button
            type="submit"
            class="submit-button"
            :disabled="loading || !isFormValid"
            :class="{ loading }"
          >
            <span v-if="loading" class="loading-spinner"></span>
            <span class="button-text">{{ buttonText }}</span>
          </button>
        </form>

        <div class="switch-link">
          <span>{{ isLogin ? '还没有账户？' : '已经有账户？' }}</span>
          <button type="button" class="link" @click="toggleMode">
            {{ isLogin ? '立即注册' : '立即登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const mode = computed(() => (route.path === '/register' ? 'register' : 'login'))

const isLogin = computed(() => mode.value === 'login')
const isRegister = computed(() => mode.value === 'register')

const loading = computed(() => (isLogin.value ? userStore.loginLoading : userStore.registerLoading))

const buttonText = computed(() => {
  if (loading.value) return isLogin.value ? '登录中...' : '注册中...'
  return isLogin.value ? '登录' : '注册'
})

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const errors = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const usernameFocused = ref(false)
const passwordFocused = ref(false)
const confirmFocused = ref(false)
const showPassword = ref(false)

const clearErrors = () => {
  errors.username = ''
  errors.password = ''
  errors.confirmPassword = ''
}

watch(
  () => route.path,
  () => {
    clearErrors()
    if (route.path !== '/register') {
      form.confirmPassword = ''
      errors.confirmPassword = ''
    }
  },
  { immediate: true }
)

const toggleMode = async () => {
  const nextPath = isLogin.value ? '/register' : '/login'
  await router.replace(nextPath)
}

const validateUsername = () => {
  const username = form.username.trim()
  if (!username) {
    errors.username = '请输入用户名'
    return false
  }
  if (username.length < 3) {
    errors.username = '用户名至少3个字符'
    return false
  }
  errors.username = ''
  return true
}

const validatePassword = () => {
  const password = form.password.trim()
  if (!password) {
    errors.password = '请输入密码'
    return false
  }

  const minLength = isLogin.value ? 4 : 6
  if (password.length < minLength) {
    errors.password = `密码至少${minLength}个字符`
    return false
  }

  errors.password = ''
  return true
}

const validateConfirmPassword = () => {
  if (!isRegister.value) {
    errors.confirmPassword = ''
    return true
  }

  if (!form.confirmPassword) {
    errors.confirmPassword = '请确认密码'
    return false
  }

  if (form.password !== form.confirmPassword) {
    errors.confirmPassword = '两次输入的密码不一致'
    return false
  }

  errors.confirmPassword = ''
  return true
}

const isFormValid = computed(() => {
  const usernameOk = form.username.trim().length >= 3
  const passwordMin = isLogin.value ? 4 : 6
  const passwordOk = form.password.trim().length >= passwordMin
  const confirmOk = isLogin.value || (form.confirmPassword.length > 0 && form.password === form.confirmPassword)

  return usernameOk && passwordOk && confirmOk && !errors.username && !errors.password && !errors.confirmPassword
})

const onUsernameBlur = () => {
  usernameFocused.value = false
  validateUsername()
}

const onPasswordBlur = () => {
  passwordFocused.value = false
  validatePassword()
  validateConfirmPassword()
}

const onConfirmBlur = () => {
  confirmFocused.value = false
  validateConfirmPassword()
}

const handleSubmit = async () => {
  clearErrors()

  const okUsername = validateUsername()
  const okPassword = validatePassword()
  const okConfirm = validateConfirmPassword()

  if (!okUsername || !okPassword || !okConfirm) return

  if (isLogin.value) {
    const result = await userStore.login(form.username, form.password)
    if (result.success) {
      ElMessage.success('登录成功')
      await router.push('/chat')
      return
    }

    ElMessage.error(result.message || '登录失败')
    return
  }

  const result = await userStore.register({
    username: form.username,
    password: form.password,
    captcha: ''
  })

  if (result.success) {
    ElMessage.success('注册成功！')
    form.confirmPassword = ''

    setTimeout(() => {
      router.replace('/login')
    }, 800)

    return
  }

  ElMessage.error(result.message || '注册失败，请重试')
}

onMounted(() => {
  const authExpired = sessionStorage.getItem('authExpired')
  if (authExpired === 'true') {
    sessionStorage.removeItem('authExpired')
    ElMessage.warning({ message: '认证过期，请重新登录', duration: 3000 })
  }

  if (userStore.isLoggedIn) {
    router.replace('/chat')
  }
})
</script>

<style scoped>
.auth-container {
  --auth-primary: #0ea5a4;
  --auth-primary-hover: #0d9488;
  --auth-text: #0f172a;
  --auth-muted: #475569;
  --auth-border: rgba(15, 23, 42, 0.12);
  --auth-bg-card: rgba(255, 255, 255, 0.9);
  --auth-shadow: 0 20px 45px rgba(15, 23, 42, 0.12);

  min-height: 100vh;
  /* Mobile viewport fix */
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #eef6ff 0%, #f4fffb 45%, #fff8ef 100%);
}

.animated-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  /* Prevent layout thrashing */
  transform: translateZ(0);
}

.gradient-overlay {
  position: absolute;
  inset: 0;
  background: radial-gradient(1200px 800px at 20% 10%, rgba(14, 165, 164, 0.18), transparent 55%),
    radial-gradient(1000px 700px at 80% 20%, rgba(37, 99, 235, 0.14), transparent 60%),
    linear-gradient(135deg, #eef6ff 0%, #f4fffb 45%, #fff8ef 100%);
}

.floating-orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(90px);
  opacity: 0.35;
  animation: float 28s ease-in-out infinite;
  mix-blend-mode: multiply;
  /* Hardware acceleration */
  will-change: transform;
}

.orb-1 {
  top: -18%;
  right: -14%;
  width: 620px;
  height: 620px;
  background: radial-gradient(circle, rgba(14, 165, 164, 0.28) 0%, transparent 68%);
  animation-delay: 0s;
}

.orb-2 {
  bottom: -34%;
  left: -22%;
  width: 560px;
  height: 560px;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.22) 0%, transparent 70%);
  animation-delay: -9s;
}

.orb-3 {
  top: 46%;
  left: 48%;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(34, 197, 94, 0.16) 0%, transparent 72%);
  animation-delay: -18s;
}

@keyframes float {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  33% {
    transform: translate3d(-18px, 14px, 0) scale(1.03);
  }
  66% {
    transform: translate3d(16px, -10px, 0) scale(0.98);
  }
}

.auth-card {
  background: var(--auth-bg-card);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 24px;
  box-shadow: var(--auth-shadow);
  overflow: hidden;
  max-width: 440px;
  width: 100%;
  position: relative;
  z-index: 1;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  animation: cardSlideUp 0.55s cubic-bezier(0.2, 0.8, 0.2, 1);
  /* Fix for mobile overflow */
  margin: auto;
}

@keyframes cardSlideUp {
  from {
    opacity: 0;
    transform: translateY(22px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.auth-content {
  display: flex;
  flex-direction: column;
  padding: 40px;
}

.logo-section {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  font-size: 56px;
  margin-bottom: 12px;
  animation: logoFloat 6s ease-in-out infinite;
  display: inline-block;
}

@keyframes logoFloat {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}

.app-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--auth-text);
  margin: 0 0 4px 0;
  letter-spacing: -0.02em;
}

.app-subtitle {
  font-size: 14px;
  color: var(--auth-muted);
  margin: 0;
}

/* Switching Animation Container */
.title-stack {
  position: relative;
  height: 40px;
  overflow: hidden;
  margin-bottom: 4px;
}

.subtitle-stack {
  position: relative;
  height: 24px;
  overflow: hidden;
  margin-bottom: 32px;
}

.stack-item {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translateY(100%);
  opacity: 0;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.3s ease;
  will-change: transform, opacity;
}

/* Login State */
.title-stack.is-login .login,
.subtitle-stack.is-login .login {
  transform: translateY(0);
  opacity: 1;
}

.title-stack.is-login .register,
.subtitle-stack.is-login .register {
  transform: translateY(100%);
  opacity: 0;
}

/* Register State */
.title-stack.is-register .login,
.subtitle-stack.is-register .login {
  transform: translateY(-100%);
  opacity: 0;
}

.title-stack.is-register .register,
.subtitle-stack.is-register .register {
  transform: translateY(0);
  opacity: 1;
}

.auth-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--auth-text);
  margin: 0;
  text-align: center;
}

.auth-subtitle {
  font-size: 14px;
  color: var(--auth-muted);
  margin: 0;
  text-align: center;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  transition: all 0.2s ease;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: #94a3b8;
  z-index: 2;
  transition: color 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.input-wrapper.focused .input-icon {
  color: var(--auth-primary);
}

.form-input {
  width: 100%;
  height: 48px; /* Mobile touch target optimization */
  padding: 0 48px 0 48px; /* Balanced padding for icons/toggles */
  border: 1.5px solid var(--auth-border);
  border-radius: 12px;
  font-size: 16px; /* Prevent iOS zoom */
  line-height: normal;
  transition: all 0.2s ease;
  background: rgba(255, 255, 255, 0.6);
  color: var(--auth-text);
  -webkit-appearance: none; /* Remove default iOS styling */
}

.form-input::placeholder {
  color: #94a3b8;
}

.form-input:focus {
  outline: none;
  border-color: var(--auth-primary);
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(14, 165, 164, 0.1);
}

.form-input.error {
  border-color: #ef4444;
  background: #fef2f2;
}

.form-input.error:focus {
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
}

.password-toggle {
  position: absolute;
  right: 8px;
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s;
  z-index: 3;
  -webkit-tap-highlight-color: transparent;
}

.password-toggle:hover {
  color: var(--auth-text);
}

/* Confirm Password Animation */
.confirm-group {
  display: grid;
  grid-template-rows: 0fr;
  opacity: 0;
  margin-top: -10px;
  transition: grid-template-rows 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              opacity 0.3s ease,
              margin-top 0.35s ease;
}

.confirm-group > div {
  overflow: hidden;
}

.confirm-group.visible {
  grid-template-rows: 1fr;
  opacity: 1;
  margin-top: 0;
}

.error-message {
  color: #ef4444;
  font-size: 13px;
  margin-top: 4px;
  margin-left: 4px;
  min-height: 18px; /* Prevent layout shift */
}

.submit-button {
  width: 100%;
  height: 48px; /* Mobile touch target optimization */
  margin-top: 8px;
  background: linear-gradient(135deg, var(--auth-primary) 0%, #059669 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(14, 165, 164, 0.25);
  -webkit-tap-highlight-color: transparent;
}

.submit-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(14, 165, 164, 0.35);
  filter: brightness(1.05);
}

.submit-button:active:not(:disabled) {
  transform: translateY(0);
}

.submit-button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  filter: grayscale(0.2);
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.switch-link {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--auth-muted);
}

.link {
  color: var(--auth-primary);
  background: none;
  border: none;
  padding: 8px 12px; /* Increase touch area */
  margin: -8px 0 -8px 0; /* Compensate padding */
  font-weight: 600;
  cursor: pointer;
  font-size: 14px;
  transition: color 0.2s;
}

.link:hover {
  color: var(--auth-primary-hover);
  text-decoration: underline;
}

/* Transitions */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

/* Responsive Design */
@media (max-width: 768px) {
  .auth-container {
    padding: 16px;
    align-items: center; /* Center vertically on mobile too */
  }

  .auth-card {
    max-width: 100%;
    border-radius: 20px;
  }

  .auth-content {
    padding: 32px 24px;
  }

  .logo-icon {
    font-size: 48px;
  }
}

@media (max-width: 480px) {
  .auth-container {
    padding: 12px;
  }

  .auth-content {
    padding: 24px 20px;
  }

  .app-title {
    font-size: 22px;
  }

  .auth-title {
    font-size: 24px;
  }

  .logo-section {
    margin-bottom: 24px;
  }

  .orb-1, .orb-2 {
    width: 300px;
    height: 300px;
    filter: blur(60px);
  }
  
  /* Ensure form elements don't cause overflow */
  .auth-form {
    width: 100%;
  }
}

/* Dark mode support preference (optional but good for future) */
@media (prefers-color-scheme: dark) {
  /* Add dark mode variables here if needed, keeping light for now as per design */
}
</style>
