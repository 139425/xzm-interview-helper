<template>
  <section
    v-if="uiStore.showWelcome"
    class="xzm-welcome"
    :class="{ 'is-animating-out': uiStore.welcomeAnimating }"
  >
    <h1 class="xzm-welcome__title">
      你好，<span class="xzm-welcome__name">{{ userName }}</span>
    </h1>
    <p class="xzm-welcome__subtitle">把问题想清楚，把下一步准备好。</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useUIStore } from '../stores/ui'
import { useUserStore } from '../stores/user'

const uiStore = useUIStore()
const userStore = useUserStore()

const userName = computed(() => {
  if (userStore.isLoggedIn && userStore.username) return userStore.username
  return '访客'
})
</script>

<style scoped>
.xzm-welcome { width: 100%; padding: 36px 24px 24px; text-align: center; animation: welcome-in 280ms var(--xzm-ease-out) both; }
.xzm-welcome__title { margin: 0; font-size: clamp(25px, 3vw, 36px); font-weight: 600; letter-spacing: -.035em; line-height: 1.35; color: var(--xzm-text-primary); }
.xzm-welcome__name { color: inherit; }
.xzm-welcome__subtitle { margin: 12px 0 0; color: var(--xzm-text-tertiary); font-size: 14px; }
@keyframes welcome-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
@media (max-height: 620px) { .xzm-welcome { padding-block: 16px; } }
</style>
