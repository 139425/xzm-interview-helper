<template>
  <main class="auth-shell">
    <section class="auth-story" aria-label="产品介绍">
      <div class="brand-mark" aria-hidden="true">X</div>
      <div class="story-copy">
        <p class="eyebrow">XZM INTERVIEW HELPER</p>
        <h1>把准备，变成<br />一次次确定的进步。</h1>
        <p class="story-lead">对话、模拟面试与投递进度，都在一个专注的工作台里。</p>
      </div>
      <div class="trust-note">
        <span class="trust-dot"></span>
        <span>登录凭证加密传输 · 验证结果一次有效</span>
      </div>
    </section>

    <section class="auth-panel">
      <div class="mobile-brand">
        <span class="brand-mark">X</span>
        <span>XZM 面试助手</span>
      </div>

      <div class="auth-card">
        <header class="auth-header">
          <p class="section-kicker">{{ isLogin ? '欢迎回来' : '创建账号' }}</p>
          <h2>{{ isLogin ? '继续你的求职准备' : '从今天开始稳步准备' }}</h2>
          <p>{{ isLogin ? '登录后继续上次的对话与练习。' : '注册只保留必要信息，并通过验证防止恶意请求。' }}</p>
        </header>

        <form class="auth-form" @submit.prevent="handleSubmit">
          <label class="field">
            <span>用户名</span>
            <div class="field-control" :class="{ invalid: errors.username }">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 21a8 8 0 0 0-16 0M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z" /></svg>
              <input
                v-model.trim="form.username"
                autocomplete="username"
                placeholder="4–20 个字符"
                @blur="validateUsername"
              />
            </div>
            <small v-if="errors.username" class="field-error">{{ errors.username }}</small>
          </label>

          <label v-if="isRegister && registrationMode === 'EMAIL'" class="field">
            <span>邮箱</span>
            <div class="field-control" :class="{ invalid: errors.email }">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 6 9 7 9-7M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z" /></svg>
              <input
                v-model.trim="form.email"
                type="email"
                autocomplete="email"
                placeholder="支持 QQ 邮箱及其他常用邮箱"
                @blur="validateEmail"
              />
            </div>
            <small v-if="errors.email" class="field-error">{{ errors.email }}</small>
          </label>

          <label class="field">
            <span>密码</span>
            <div class="field-control" :class="{ invalid: errors.password }">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 10V8a6 6 0 0 1 12 0v2M5 10h14a2 2 0 0 1 2 2v8H3v-8a2 2 0 0 1 2-2Z" /></svg>
              <input
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                :autocomplete="isLogin ? 'current-password' : 'new-password'"
                placeholder="至少 6 个字符"
                @blur="validatePassword"
              />
              <button type="button" class="text-button" @click="showPassword = !showPassword">
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </div>
            <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
          </label>

          <label v-if="isRegister" class="field">
            <span>确认密码</span>
            <div class="field-control" :class="{ invalid: errors.confirmPassword }">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6" /></svg>
              <input
                v-model="form.confirmPassword"
                type="password"
                autocomplete="new-password"
                placeholder="再次输入密码"
                @blur="validateConfirmPassword"
              />
            </div>
            <small v-if="errors.confirmPassword" class="field-error">{{ errors.confirmPassword }}</small>
          </label>

          <div v-if="isLogin || (isRegister && registrationMode === 'EMAIL')" class="verification-block">
            <div class="verification-heading">
              <span>{{ isLogin ? '安全验证' : '发送邮箱验证码前，请先验证' }}</span>
              <span v-if="sliderVerified" class="verified-label">已通过</span>
            </div>
            <div class="slider-box" :class="{ verified: sliderVerified, busy: sliderBusy }">
              <div class="slider-fill" :style="{ width: `${sliderProgress}%` }"></div>
              <span class="slider-copy">{{ sliderLabel }}</span>
              <input
                v-model.number="sliderProgress"
                type="range"
                min="0"
                max="100"
                :disabled="sliderBusy || sliderVerified"
                aria-label="拖动滑块完成人机验证"
                @change="completeSlider"
              />
            </div>
          </div>

          <div v-if="isRegister && registrationMode === 'EMAIL'" class="field">
            <span>邮箱验证码</span>
            <div class="code-row">
              <div class="field-control" :class="{ invalid: errors.verification }">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 12h10M12 7v10M4 4h16v16H4z" /></svg>
                <input v-model.trim="form.emailCode" inputmode="numeric" maxlength="6" placeholder="6 位验证码" />
              </div>
              <button
                type="button"
                class="secondary-button"
                :disabled="sendingCode || countdown > 0 || !sliderVerified || !emailValid"
                @click="sendEmailCode"
              >
                {{ countdown > 0 ? `${countdown}s 后重发` : (sendingCode ? '发送中' : '发送验证码') }}
              </button>
            </div>
            <small v-if="errors.verification" class="field-error">{{ errors.verification }}</small>
          </div>

          <div v-if="isRegister && registrationMode === 'CAPTCHA'" class="field">
            <span>图片验证</span>
            <div class="captcha-row">
              <button type="button" class="captcha-image" title="点击刷新" @click="loadCaptcha">
                <img v-if="captcha.imageDataUrl" :src="captcha.imageDataUrl" alt="计算验证码，点击可刷新" />
                <span v-else>加载中…</span>
              </button>
              <div class="field-control compact" :class="{ invalid: errors.verification }">
                <input v-model.trim="form.captchaAnswer" inputmode="numeric" placeholder="计算结果" />
              </div>
            </div>
            <small class="field-hint">看不清？点击图片换一张</small>
            <small v-if="errors.verification" class="field-error">{{ errors.verification }}</small>
          </div>

          <button class="primary-button" type="submit" :disabled="loading || !isFormReady">
            <span v-if="loading" class="spinner" aria-hidden="true"></span>
            {{ loading ? (isLogin ? '正在登录' : '正在注册') : (isLogin ? '登录' : '创建账号') }}
          </button>
        </form>

        <p class="switch-copy">
          {{ isLogin ? '还没有账号？' : '已有账号？' }}
          <button type="button" @click="toggleMode">{{ isLogin ? '立即注册' : '返回登录' }}</button>
        </p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isLogin = computed(() => route.path !== '/register')
const isRegister = computed(() => !isLogin.value)
const loading = computed(() => isLogin.value ? userStore.loginLoading : userStore.registerLoading)
const registrationMode = ref('CAPTCHA')
const showPassword = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  emailCode: '',
  captchaAnswer: '',
})
const errors = reactive({ username: '', email: '', password: '', confirmPassword: '', verification: '' })
const captcha = reactive({ captchaId: '', imageDataUrl: '' })
const slider = reactive({ challengeId: '', verificationToken: '' })
const sliderProgress = ref(0)
const sliderBusy = ref(false)
const sliderVerified = computed(() => Boolean(slider.verificationToken))
const sliderLabel = computed(() => {
  if (sliderBusy.value) return '正在验证…'
  if (sliderVerified.value) return '验证通过'
  return '按住滑块，拖动到最右侧'
})

const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(form.email))
const isFormReady = computed(() => {
  const base = form.username.length >= 4 && form.password.length >= 6
  if (!base) return false
  if (isLogin.value) return sliderVerified.value
  if (form.password !== form.confirmPassword) return false
  if (registrationMode.value === 'EMAIL') return emailValid.value && /^\d{6}$/.test(form.emailCode)
  return Boolean(captcha.captchaId && form.captchaAnswer)
})

function clearErrors() {
  Object.keys(errors).forEach((key) => { errors[key] = '' })
}

function validateUsername() {
  errors.username = form.username.length < 4 || form.username.length > 20 ? '用户名需为 4–20 个字符' : ''
  return !errors.username
}

function validateEmail() {
  errors.email = isRegister.value && registrationMode.value === 'EMAIL' && !emailValid.value ? '请输入有效的邮箱地址' : ''
  return !errors.email
}

function validatePassword() {
  errors.password = form.password.length < 6 || form.password.length > 20 ? '密码需为 6–20 个字符' : ''
  return !errors.password
}

function validateConfirmPassword() {
  errors.confirmPassword = isRegister.value && form.password !== form.confirmPassword ? '两次输入的密码不一致' : ''
  return !errors.confirmPassword
}

async function loadVerificationConfig() {
  try {
    const response = await userApi.getVerificationConfig()
    registrationMode.value = response?.data?.registrationMode === 'EMAIL' ? 'EMAIL' : 'CAPTCHA'
  } catch {
    registrationMode.value = 'CAPTCHA'
  }
}

async function createSlider() {
  sliderBusy.value = true
  sliderProgress.value = 0
  slider.challengeId = ''
  slider.verificationToken = ''
  try {
    const response = await userApi.createSliderChallenge()
    if (response?.code !== 200 || !response?.data?.challengeId) throw new Error(response?.message || '验证加载失败')
    slider.challengeId = response.data.challengeId
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '安全验证加载失败')
  } finally {
    sliderBusy.value = false
  }
}

async function completeSlider() {
  if (sliderProgress.value < 100) {
    sliderProgress.value = 0
    return
  }
  if (!slider.challengeId) await createSlider()
  if (!slider.challengeId) return
  sliderBusy.value = true
  try {
    const response = await userApi.verifySlider(slider.challengeId, 100)
    if (response?.code !== 200 || !response?.data?.verificationToken) {
      throw new Error(response?.message || '验证未通过')
    }
    slider.verificationToken = response.data.verificationToken
    sliderProgress.value = 100
  } catch (error) {
    ElMessage.warning(error?.response?.data?.message || error?.message || '请重新完成验证')
    await createSlider()
  } finally {
    sliderBusy.value = false
  }
}

async function loadCaptcha() {
  captcha.captchaId = ''
  captcha.imageDataUrl = ''
  form.captchaAnswer = ''
  try {
    const response = await userApi.createCaptcha()
    if (response?.code !== 200 || !response?.data?.captchaId) throw new Error(response?.message || '验证码加载失败')
    captcha.captchaId = response.data.captchaId
    captcha.imageDataUrl = response.data.imageDataUrl
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '图片验证码加载失败')
  }
}

function startCountdown(seconds) {
  clearInterval(countdownTimer)
  countdown.value = Number(seconds) || 60
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      createSlider()
    }
  }, 1000)
}

async function sendEmailCode() {
  if (!validateEmail() || !sliderVerified.value || sendingCode.value) return
  sendingCode.value = true
  try {
    const response = await userApi.sendRegistrationCode(form.email, slider.verificationToken)
    if (response?.code !== 200) throw new Error(response?.message || '发送失败')
    slider.verificationToken = ''
    sliderProgress.value = 0
    startCountdown(response?.data?.retryAfterSeconds)
    ElMessage.success('验证码已发送，请检查邮箱')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '验证码发送失败')
    await createSlider()
  } finally {
    sendingCode.value = false
  }
}

async function resetVerification() {
  clearInterval(countdownTimer)
  countdown.value = 0
  clearErrors()
  form.emailCode = ''
  form.captchaAnswer = ''
  if (isLogin.value || registrationMode.value === 'EMAIL') await createSlider()
  if (isRegister.value && registrationMode.value === 'CAPTCHA') await loadCaptcha()
}

async function toggleMode() {
  await router.replace(isLogin.value ? '/register' : '/login')
}

async function handleSubmit() {
  clearErrors()
  const valid = validateUsername() && validatePassword() && validateConfirmPassword() && validateEmail()
  if (!valid) return

  if (isLogin.value) {
    if (!sliderVerified.value) return
    const result = await userStore.login(form.username, form.password, slider.verificationToken)
    if (result.success) {
      ElMessage.success('登录成功')
      await router.push('/chat')
      return
    }
    ElMessage.error(result.message || '登录失败')
    await createSlider()
    return
  }

  if (registrationMode.value === 'EMAIL' && !/^\d{6}$/.test(form.emailCode)) {
    errors.verification = '请输入 6 位邮箱验证码'
    return
  }
  if (registrationMode.value === 'CAPTCHA' && !form.captchaAnswer) {
    errors.verification = '请输入图片中的计算结果'
    return
  }

  const result = await userStore.register({
    username: form.username,
    password: form.password,
    email: form.email,
    emailCode: form.emailCode,
    captchaId: captcha.captchaId,
    captchaAnswer: form.captchaAnswer,
    captcha: '',
  })
  if (result.success) {
    ElMessage.success('注册成功，请登录')
    await router.replace('/login')
    return
  }
  ElMessage.error(result.message || '注册失败')
  if (registrationMode.value === 'CAPTCHA') await loadCaptcha()
}

watch(() => route.path, resetVerification)

onMounted(async () => {
  if (userStore.isLoggedIn) {
    await router.replace('/chat')
    return
  }
  await loadVerificationConfig()
  await resetVerification()
  if (sessionStorage.getItem('authExpired') === 'true') {
    sessionStorage.removeItem('authExpired')
    ElMessage.warning('登录已过期，请重新登录')
  }
})

onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<style scoped>
.auth-shell {
  --ink: #12223d;
  --muted: #637087;
  --line: #dfe5ef;
  --blue: #275efe;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(520px, 1.15fr);
  background: #f4f6fa;
  color: var(--ink);
}

.auth-story {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: clamp(36px, 5vw, 72px);
  color: #fff;
  background: #112547;
}

.auth-story::before,
.auth-story::after {
  content: '';
  position: absolute;
  border: 1px solid rgba(255,255,255,.12);
  border-radius: 50%;
}

.auth-story::before { width: 520px; height: 520px; right: -290px; top: -120px; }
.auth-story::after { width: 340px; height: 340px; left: -210px; bottom: 80px; }

.brand-mark {
  width: 46px;
  height: 46px;
  display: inline-grid;
  place-items: center;
  border-radius: 14px;
  background: #fff;
  color: #173261;
  font: 800 22px/1 ui-sans-serif, system-ui;
  box-shadow: 0 10px 30px rgba(0,0,0,.12);
}

.story-copy { position: relative; z-index: 1; max-width: 560px; }
.eyebrow, .section-kicker { margin: 0 0 18px; font-size: 12px; font-weight: 800; letter-spacing: .16em; }
.eyebrow { color: #8fb2ff; }
.story-copy h1 { margin: 0; font-size: clamp(42px, 5vw, 68px); line-height: 1.12; letter-spacing: -.045em; }
.story-lead { max-width: 460px; margin: 26px 0 0; color: #b9c8df; font-size: 17px; line-height: 1.8; }
.trust-note { position: relative; z-index: 1; display: flex; align-items: center; gap: 10px; color: #aebdd4; font-size: 13px; }
.trust-dot { width: 8px; height: 8px; border-radius: 50%; background: #58d6a9; box-shadow: 0 0 0 5px rgba(88,214,169,.12); }

.auth-panel { display: grid; place-items: center; padding: 36px; overflow-y: auto; }
.mobile-brand { display: none; }
.auth-card { width: min(100%, 460px); padding: 18px 0; }
.auth-header { margin-bottom: 30px; }
.section-kicker { color: var(--blue); margin-bottom: 10px; }
.auth-header h2 { margin: 0; font-size: 32px; line-height: 1.25; letter-spacing: -.035em; }
.auth-header p:last-child { margin: 10px 0 0; color: var(--muted); line-height: 1.6; }
.auth-form { display: grid; gap: 18px; }
.field { display: grid; gap: 8px; font-size: 13px; font-weight: 700; }
.field-control { position: relative; display: flex; align-items: center; height: 52px; border: 1px solid var(--line); border-radius: 12px; background: #fff; transition: .2s ease; }
.field-control:focus-within { border-color: var(--blue); box-shadow: 0 0 0 4px rgba(39,94,254,.1); }
.field-control.invalid { border-color: #dc4c64; }
.field-control svg { width: 19px; height: 19px; margin-left: 16px; fill: none; stroke: #8793a7; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; flex: none; }
.field-control input { min-width: 0; flex: 1; height: 100%; border: 0; outline: 0; padding: 0 14px; color: var(--ink); background: transparent; font: 500 15px/1 system-ui, sans-serif; }
.field-control input::placeholder { color: #a1aaba; }
.text-button { margin-right: 8px; border: 0; background: transparent; color: var(--blue); padding: 8px; cursor: pointer; }
.field-error { color: #c8324c; font-weight: 500; }
.field-hint { color: var(--muted); font-weight: 500; }

.verification-block { display: grid; gap: 9px; }
.verification-heading { display: flex; justify-content: space-between; font-size: 13px; font-weight: 700; }
.verified-label { color: #13805d; }
.slider-box { position: relative; height: 48px; overflow: hidden; border: 1px solid var(--line); border-radius: 12px; background: #e9edf4; }
.slider-fill { position: absolute; inset: 0 auto 0 0; background: #dce6ff; transition: width .08s linear; }
.slider-box.verified .slider-fill { background: #dff5eb; }
.slider-copy { position: absolute; inset: 0; display: grid; place-items: center; pointer-events: none; color: #5e6a7d; font-size: 13px; font-weight: 650; }
.slider-box.verified .slider-copy { color: #137557; }
.slider-box input { position: absolute; inset: 0; width: 100%; height: 100%; margin: 0; opacity: .01; cursor: grab; }
.slider-box input:active { cursor: grabbing; }
.slider-box.busy { opacity: .7; }

.code-row, .captcha-row { display: grid; grid-template-columns: minmax(0, 1fr) 138px; gap: 10px; }
.secondary-button { border: 1px solid #cbd5e5; border-radius: 12px; color: #214379; background: #fff; font-weight: 700; cursor: pointer; }
.secondary-button:disabled { color: #9aa5b7; background: #f2f4f8; cursor: not-allowed; }
.captcha-row { grid-template-columns: 180px minmax(0, 1fr); }
.captcha-image { height: 56px; padding: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 12px; background: #fff; cursor: pointer; }
.captcha-image img { width: 100%; height: 100%; object-fit: cover; display: block; }
.field-control.compact { height: 56px; }

.primary-button { height: 52px; margin-top: 4px; border: 0; border-radius: 12px; background: var(--blue); color: #fff; font-size: 15px; font-weight: 800; cursor: pointer; box-shadow: 0 12px 26px rgba(39,94,254,.2); transition: transform .18s, box-shadow .18s; }
.primary-button:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 15px 30px rgba(39,94,254,.27); }
.primary-button:disabled { opacity: .5; cursor: not-allowed; box-shadow: none; }
.spinner { display: inline-block; width: 14px; height: 14px; margin-right: 8px; border: 2px solid rgba(255,255,255,.4); border-top-color: #fff; border-radius: 50%; animation: spin .75s linear infinite; vertical-align: -2px; }
@keyframes spin { to { transform: rotate(360deg); } }
.switch-copy { margin: 24px 0 0; color: var(--muted); text-align: center; font-size: 14px; }
.switch-copy button { border: 0; background: transparent; color: var(--blue); font-weight: 800; cursor: pointer; }

@media (max-width: 820px) {
  .auth-shell { display: block; background: #fff; }
  .auth-story { display: none; }
  .auth-panel { min-height: 100vh; min-height: 100dvh; display: block; padding: 24px; }
  .mobile-brand { display: flex; align-items: center; gap: 12px; margin-bottom: 42px; font-weight: 800; }
  .mobile-brand .brand-mark { width: 38px; height: 38px; color: #fff; background: #173261; font-size: 18px; }
  .auth-card { margin: 0 auto; }
}

@media (max-width: 480px) {
  .auth-panel { padding: 20px; }
  .auth-header h2 { font-size: 28px; }
  .code-row { grid-template-columns: minmax(0, 1fr) 118px; }
  .captcha-row { grid-template-columns: 150px minmax(0, 1fr); }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
</style>
