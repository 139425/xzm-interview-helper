<template>
  <div class="app-shell" :class="{ 'is-auth': !authenticated }">
    <AuthView v-if="!authenticated" :key="authRevision" @authenticated="onAuthenticated" @open-settings="settingsOpen = true" @toast="toast" />
    <template v-else>
      <KeepAlive>
        <component :is="activeComponent" :user="user" @toast="toast" @go-tracker="activeTab = 'tracker'" @open-settings="settingsOpen = true" @logout="logout" @navigate="activeTab = $event" />
      </KeepAlive>
      <nav class="bottom-nav" aria-label="主要功能">
        <button v-for="item in tabs" :key="item.id" type="button" :class="{ active: activeTab === item.id }" @click="activeTab = item.id">
          <span><AppIcon :name="item.icon" /><i v-if="activeTab === item.id"></i></span><small>{{ item.label }}</small>
        </button>
      </nav>
    </template>

    <ServerSettings :open="settingsOpen" @close="closeSettings" @saved="settingsSaved" />
    <ToastStack :items="toasts" />
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, onUnmounted, ref } from 'vue'
import { clearAuth, getApiUrl, getAuth } from '@/lib/api'
import AppIcon from '@/components/AppIcon.vue'
import ServerSettings from '@/components/ServerSettings.vue'
import ToastStack from '@/components/ToastStack.vue'
import AuthView from '@/views/AuthView.vue'
import ChatView from '@/views/ChatView.vue'
import ScheduleView from '@/views/ScheduleView.vue'
import TrackerView from '@/views/TrackerView.vue'
import RecruitmentView from '@/views/RecruitmentView.vue'
import ProfileView from '@/views/ProfileView.vue'

const pages = {
  chat: markRaw(ChatView), schedule: markRaw(ScheduleView), tracker: markRaw(TrackerView), jobs: markRaw(RecruitmentView), profile: markRaw(ProfileView),
}
const tabs = [
  { id: 'chat', label: 'AI', icon: 'chat' }, { id: 'schedule', label: '待办', icon: 'calendar' },
  { id: 'tracker', label: '投递', icon: 'pipeline' }, { id: 'jobs', label: '秋招', icon: 'jobs' }, { id: 'profile', label: '我的', icon: 'user' },
]
const auth = getAuth()
const authenticated = ref(Boolean(auth.token && auth.user))
const user = ref(auth.user)
const activeTab = ref('chat')
const settingsOpen = ref(!getApiUrl())
const authRevision = ref(0)
const toasts = ref([])
let toastId = 0
const activeComponent = computed(() => pages[activeTab.value] || pages.chat)

function toast(message, tone = 'success') {
  const id = ++toastId
  toasts.value.push({ id, message: String(message || ''), tone })
  setTimeout(() => { toasts.value = toasts.value.filter((item) => item.id !== id) }, 3200)
}
function onAuthenticated(nextUser) { user.value = nextUser; authenticated.value = true; activeTab.value = 'chat'; toast('登录成功，欢迎回来') }
function closeSettings() { if (getApiUrl()) settingsOpen.value = false }
function settingsSaved() { settingsOpen.value = false; authRevision.value += 1; toast('服务连接成功') }
function logout() {
  if (!window.confirm('确定退出当前账号？')) return
  clearAuth(); authenticated.value = false; user.value = null; activeTab.value = 'chat'; toast('已安全退出')
}
function authExpired() { authenticated.value = false; user.value = null; toast('登录已过期，请重新登录', 'error') }
onMounted(() => window.addEventListener('xzm:auth-expired', authExpired))
onUnmounted(() => window.removeEventListener('xzm:auth-expired', authExpired))
</script>
