<template>
  <section
    v-if="uiStore.showWelcome"
    class="xzm-welcome"
    :class="{ 'is-animating-out': uiStore.welcomeAnimating }"
  >
    <h1 class="xzm-welcome__title">
      你好，<span class="xzm-welcome__name">{{ userName }}</span>
    </h1>
    <p class="xzm-welcome__subtitle">今天想一起解决什么？</p>
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
/*
  V2 极简欢迎页：
  当 PromptBar 处于 center 位置时，PromptBar 通过 fixed 定位居中（视窗中线 + 15vh 下移）。
  WelcomeSection 也用 fixed 居中，并放在 PromptBar 上方约 130px 处，形成 ChatGPT 式的中央视觉组。
  侧栏宽度通过 --sidebar-width 变量同步，保证与 PromptBar 共用同一中线。
*/
.xzm-welcome {
  position: fixed;
  top: 50%;
  left: calc(var(--sidebar-width, 0px) / 2 + 50%);
  transform: translate(-50%, calc(-50% + 15vh - 156px));
  z-index: 90;
  width: calc(100% - var(--sidebar-width, 0px));
  max-width: var(--xzm-content-max-width);
  padding: 0 24px;
  text-align: center;
  pointer-events: none;
  animation: xzm-welcome-in var(--xzm-duration-slow) var(--xzm-ease-emphasized) both;
  transition:
    left var(--xzm-duration-normal) var(--xzm-ease-standard),
    width var(--xzm-duration-normal) var(--xzm-ease-standard);
}

.xzm-welcome.is-animating-out {
  animation: xzm-welcome-out var(--xzm-duration-normal) var(--xzm-ease-standard) forwards;
}

@keyframes xzm-welcome-in {
  from { opacity: 0; transform: translate(-50%, calc(-50% + 15vh - 146px)); }
  to   { opacity: 1; transform: translate(-50%, calc(-50% + 15vh - 156px)); }
}

@keyframes xzm-welcome-out {
  to { opacity: 0; }
}

.xzm-welcome__title {
  margin: 0;
  font-size: clamp(1.85rem, 3.7vw, 2.65rem);
  font-weight: var(--xzm-fw-semibold);
  letter-spacing: -0.035em;
  line-height: 1.16;
  color: var(--xzm-text-primary);
  user-select: none;
}

.xzm-welcome__name {
  color: var(--xzm-brand);
  background: var(--xzm-brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.xzm-welcome__subtitle {
  margin-top: 10px;
  color: var(--xzm-text-tertiary);
  font-size: clamp(0.92rem, 1.4vw, 1.02rem);
  font-weight: var(--xzm-fw-regular);
  letter-spacing: 0.01em;
}

@media (max-width: 768px) {
  .xzm-welcome {
    padding: 0 16px;
    transform: translate(-50%, calc(-50% + 8vh - 180px));
    animation: none;
  }
  .xzm-welcome__title {
    font-size: clamp(1.75rem, 8vw, 2rem);
  }
}
</style>
