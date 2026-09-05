<template>
  <main class="auth-view">
    <div class="auth-orb auth-orb--one"></div>
    <div class="auth-orb auth-orb--two"></div>
    <header class="auth-brand-row">
      <div class="brand-lockup"><span class="brand-mark">X</span><div><b>XZM</b><small>面试助手</small></div></div>
      <button class="auth-settings" type="button" aria-label="配置服务地址" @click="$emit('open-settings')"><AppIcon name="settings" /></button>
    </header>

    <section class="auth-intro">
      <span class="auth-badge"><i></i> 2027 秋招作战台</span>
      <h1>把准备变成<br><em>下一步行动。</em></h1>
      <p>对话、日程、投递与机会，在手机上保持同一节奏。</p>
    </section>

    <section class="auth-card">
      <div class="segmented auth-tabs">
        <button :class="{ active: mode === 'login' }" type="button" @click="switchMode('login')">登录</button>
        <button :class="{ active: mode === 'register' }" type="button" @click="switchMode('register')">注册</button>
      </div>

      <form class="stack-form" @submit.prevent="submit">
        <label class="input-field">
          <span>用户名</span>
          <input v-model.trim="form.username" autocomplete="username" maxlength="20" placeholder="4–20 个字符" />
        </label>
        <label v-if="mode === 'register' && registrationMode === 'EMAIL'" class="input-field">
          <span>邮箱</span>
          <input v-model.trim="form.email" type="email" autocomplete="email" placeholder="name@example.com" />
        </label>
        <label class="input-field">
          <span>密码</span>
          <div class="password-field">
            <input v-model="form.password" :type="showPassword ? 'text' : 'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 6 个字符" />
            <button type="button" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button>
          </div>
        </label>
        <label v-if="mode === 'register'" class="input-field">
          <span>确认密码</span>
          <input v-model="form.confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码" />
        </label>

        <div v-if="needsSlider" class="verify-field">
          <div class="field-label"><span>安全验证</span><b v-if="sliderVerified">已通过</b></div>
          <div class="slider-verify" :class="{ verified: sliderVerified, busy: sliderBusy }">
            <span class="slider-progress" :style="{ width: `${sliderProgress}%` }"></span>
            <p>{{ sliderLabel }}</p>
            <input v-model.number="sliderProgress" type="range" min="0" max="100" :disabled="sliderBusy || sliderVerified" aria-label="拖动滑块完成验证" @change="finishSlider" />
          </div>
        </div>

        <div v-if="mode === 'register' && registrationMode === 'CAPTCHA'" class="verify-field">
          <div class="field-label"><span>图片验证</span><button type="button" @click="loadCaptcha">换一张</button></div>
          <div class="captcha-row">
            <button type="button" class="captcha-image" @click="loadCaptcha">
              <img v-if="captcha.imageDataUrl" :src="captcha.imageDataUrl" alt="计算验证码" />
              <span v-else>加载中…</span>
            </button>
            <input v-model.trim="form.captchaAnswer" inputmode="numeric" placeholder="计算结果" />
          </div>
        </div>

        <div v-if="mode === 'register' && registrationMode === 'EMAIL'" class="verify-field">
          <div class="field-label"><span>邮箱验证码</span></div>
          <div class="email-code-row">
            <input v-model.trim="form.emailCode" inputmode="numeric" maxlength="6" placeholder="6 位验证码" />
            <button type="button" :disabled="codeBusy || countdown > 0 || !sliderVerified" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : codeBusy ? '发送中' : '发送验证码' }}
            </button>
          </div>
        </div>

        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="primary-action auth-submit" type="submit" :disabled="busy">
          <span v-if="busy" class="mini-spinner"></span>
          {{ busy ? '请稍候…' : mode === 'login' ? '进入工作台' : '创建账号' }}
          <AppIcon v-if="!busy" name="arrow" />
        </button>
      </form>

      <p class="auth-footnote"><span></span>账号数据由你的后端服务独立保存</p>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { authApi, saveAuth } from '@/lib/api'
import AppIcon from '@/components/AppIcon.vue'

const emit = defineEmits(['authenticated', 'open-settings', 'toast'])
const mode = ref('login')
const registrationMode = ref('CAPTCHA')
const busy = ref(false)
const error = ref('')
const showPassword = ref(false)
const sliderProgress = ref(0)
const sliderBusy = ref(false)
const slider = reactive({ challengeId: '', verificationToken: '' })
const captcha = reactive({ captchaId: '', imageDataUrl: '' })
const codeBusy = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  username: '', password: '', confirmPassword: '', email: '', emailCode: '', captchaAnswer: '',
})

const needsSlider = computed(() => mode.value === 'login' || registrationMode.value === 'EMAIL')
const sliderVerified = computed(() => Boolean(slider.verificationToken))
const sliderLabel = computed(() => {
  if (sliderBusy.value) return '正在验证…'
  if (sliderVerified.value) return '验证完成'
  return sliderProgress.value >= 100 ? '松开完成验证' : '向右滑动完成验证'
})

async function initialize() {
  error.value = ''
  try {
    const config = await authApi.verificationConfig()
    registrationMode.value = config.registrationMode || 'CAPTCHA'
    await resetVerification()
  } catch (requestError) {
    error.value = requestError.message
  }
}

async function createSlider() {
  sliderProgress.value = 0
  slider.challengeId = ''
  slider.verificationToken = ''
  try {
    Object.assign(slider, await authApi.createSlider())
  } catch (requestError) {
    error.value = requestError.message
  }
}

async function finishSlider() {
  if (sliderProgress.value < 100 || sliderBusy.value) return
  sliderBusy.value = true
  error.value = ''
  try {
    const result = await authApi.verifySlider(slider.challengeId, 100)
    slider.verificationToken = result.verificationToken || ''
    sliderProgress.value = 100
  } catch (requestError) {
    error.value = requestError.message
    await createSlider()
  } finally {
    sliderBusy.value = false
  }
}

async function loadCaptcha() {
  form.captchaAnswer = ''
  try {
    Object.assign(captcha, await authApi.createCaptcha())
  } catch (requestError) {
    error.value = requestError.message
  }
}

async function resetVerification() {
  error.value = ''
  sliderProgress.value = 0
  slider.challengeId = ''
  slider.verificationToken = ''
  captcha.captchaId = ''
  captcha.imageDataUrl = ''
  if (needsSlider.value) await createSlider()
  if (mode.value === 'register' && registrationMode.value === 'CAPTCHA') await loadCaptcha()
}

async function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  form.password = ''
  form.confirmPassword = ''
  await resetVerification()
}

function validate() {
  if (form.username.length < 4 || form.username.length > 20) return '用户名需为 4–20 个字符'
  if (form.password.length < 6) return '密码至少需要 6 个字符'
  if (mode.value === 'login' && !sliderVerified.value) return '请先完成滑块验证'
  if (mode.value === 'register') {
    if (form.password !== form.confirmPassword) return '两次输入的密码不一致'
    if (registrationMode.value === 'CAPTCHA' && !form.captchaAnswer) return '请输入图片中的计算结果'
    if (registrationMode.value === 'EMAIL' && !/^\S+@\S+\.\S+$/.test(form.email)) return '请输入有效的邮箱地址'
    if (registrationMode.value === 'EMAIL' && !/^\d{6}$/.test(form.emailCode)) return '请输入 6 位邮箱验证码'
  }
  return ''
}

async function sendCode() {
  if (!/^\S+@\S+\.\S+$/.test(form.email)) {
    error.value = '请先填写有效的邮箱地址'
    return
  }
  codeBusy.value = true
  error.value = ''
  try {
    const response = await authApi.sendEmailCode(form.email, slider.verificationToken)
    const seconds = Number(response?.data?.retryAfterSeconds || 60)
    countdown.value = seconds
    clearInterval(countdownTimer)
    countdownTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) clearInterval(countdownTimer)
    }, 1000)
    emit('toast', '验证码已发送，请检查邮箱')
    await createSlider()
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    codeBusy.value = false
  }
}

async function submit() {
  error.value = validate()
  if (error.value) return
  busy.value = true
  try {
    if (mode.value === 'login') {
      const response = await authApi.login(form.username, form.password, slider.verificationToken)
      if (response?.code !== 200 || !response?.token || !response?.data) {
        throw new Error(response?.message || '登录失败')
      }
      const user = {
        userId: response.data.user_id || response.data.id,
        username: response.data.username,
        userType: response.data.user_type,
      }
      saveAuth(response.token, user)
      emit('authenticated', user)
      return
    }
    const response = await authApi.register({
      username: form.username,
      password: form.password,
      email: form.email,
      emailCode: form.emailCode,
      captchaId: captcha.captchaId,
      captchaAnswer: form.captchaAnswer,
      captcha: '',
    })
    if (response?.code !== 200) throw new Error(response?.message || '注册失败')
    emit('toast', '注册成功，请登录')
    await switchMode('login')
  } catch (requestError) {
    error.value = requestError.message || '请求失败，请稍后重试'
    if (mode.value === 'login') await createSlider()
    if (mode.value === 'register' && registrationMode.value === 'CAPTCHA') await loadCaptcha()
  } finally {
    busy.value = false
  }
}

onMounted(initialize)
onBeforeUnmount(() => clearInterval(countdownTimer))
</script>
