<template>
  <section
    v-if="uiStore.showWelcome"
    class="xzm-welcome"
    :class="{ 'is-animating-out': uiStore.welcomeAnimating }"
  >
    <h1 class="xzm-welcome__title">
      你好，<span class="xzm-welcome__name">{{ userName }}</span>
    </h1>
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
  transform: translate(-50%, calc(-50% + 15vh - 130px));
  z-index: 90;
  width: calc(100% - var(--sidebar-width, 0px));
  max-width: var(--xzm-content-max-width);
  padding: 0 24px;
  text-align: center;
  pointer-events: none;
  animation: xzm-welcome-in 260ms var(--xzm-ease-out) both;
  transition:
    left 260ms cubic-bezier(0.4, 0.0, 0.2, 1),
    width 260ms cubic-bezier(0.4, 0.0, 0.2, 1);
}

.xzm-welcome.is-animating-out {
  animation: xzm-welcome-out 200ms var(--xzm-ease-out) forwards;
}

@keyframes xzm-welcome-in {
  from { opacity: 0; transform: translate(-50%, calc(-50% + 15vh - 122px)); }
  to   { opacity: 1; transform: translate(-50%, calc(-50% + 15vh - 130px)); }
}

@keyframes xzm-welcome-out {
  to { opacity: 0; }
}

.xzm-welcome__title {
  margin: 0;
  font-size: clamp(1.75rem, 3.6vw, 2.5rem); /* 28px → 40px */
  font-weight: var(--xzm-fw-semibold);
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--xzm-text-primary);
  user-select: none;
}

.xzm-welcome__name {
  color: var(--xzm-text-primary);
}

@media (max-width: 768px) {
  .xzm-welcome {
    padding: 0 16px;
    transform: translate(-50%, calc(-50% + 15vh - 110px));
  }
  .xzm-welcome__title {
    font-size: 1.75rem;
  }
}
</style>
