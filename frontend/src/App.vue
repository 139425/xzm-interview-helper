<template>
  <div id="app" :class="{ 'has-workspace-nav': showNavigation, 'keyboard-open': keyboardOpen }">
    <a class="skip-link" href="#app-content">跳到主要内容</a>
    <div id="app-content" tabindex="-1">
      <router-view />
    </div>
    <MobileWorkspaceNav v-if="showNavigation && !keyboardOpen" />
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import MobileWorkspaceNav from './components/MobileWorkspaceNav.vue'
import { prefetchWorkspace } from './utils/workspaceNavigation'
const route = useRoute()
const showNavigation = computed(() => ['Chat', 'AiInterview', 'AlgorithmPractice', 'RecruitmentDirectory', 'ApplicationTracker', 'AssessmentSchedule', 'KnowledgeBase', 'UserManagement', 'ServerAgent'].includes(route.name))
const keyboardOpen = ref(false)
let viewportFrame = 0, idleHandle, warmTimer
function updateViewport() {
  cancelAnimationFrame(viewportFrame)
  viewportFrame = requestAnimationFrame(() => {
    const viewport = window.visualViewport
    const mobile = window.innerWidth <= 768
    const editing = document.activeElement?.matches('input, textarea, [contenteditable="true"]')
    // Use this window's layout viewport, not the physical screen (split view and
    // landscape can be much smaller). Pinch zoom is not an on-screen keyboard.
    const layoutHeight = Math.max(window.innerHeight, document.documentElement.clientHeight)
    keyboardOpen.value = Boolean(mobile && editing && viewport && viewport.scale === 1 && layoutHeight - viewport.height > 160)
    document.documentElement.style.setProperty('--xzm-available-height', mobile && viewport ? `${viewport.height}px` : '100dvh')
  })
}
function warmNavigation() {
  window.cancelIdleCallback?.(idleHandle)
  clearTimeout(warmTimer)
  if (!showNavigation.value) return
  if ('requestIdleCallback' in window) idleHandle = window.requestIdleCallback(() => ['schedule', 'applications', 'recruitment'].forEach(prefetchWorkspace), { timeout: 3000 })
  else warmTimer = setTimeout(() => ['schedule', 'applications', 'recruitment'].forEach(prefetchWorkspace), 1500)
}
onMounted(() => { updateViewport(); window.visualViewport?.addEventListener('resize', updateViewport); window.addEventListener('resize', updateViewport); document.addEventListener('focusin', updateViewport); document.addEventListener('focusout', updateViewport); warmNavigation() })
watch(showNavigation, value => { if (value) warmNavigation() })
onBeforeUnmount(() => { cancelAnimationFrame(viewportFrame); window.cancelIdleCallback?.(idleHandle); clearTimeout(warmTimer); window.visualViewport?.removeEventListener('resize', updateViewport); window.removeEventListener('resize', updateViewport); document.removeEventListener('focusin', updateViewport); document.removeEventListener('focusout', updateViewport) })
</script>

<style>
*,
*::before,
*::after {
  box-sizing: border-box;
}

* {
  margin: 0;
}

html {
  height: 100%;
  background: var(--xzm-surface-0);
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  min-width: 320px;
  min-height: 100%;
  overflow-x: hidden;
  color: var(--xzm-text-primary);
  background: var(--xzm-surface-0);
  font-family: var(--xzm-font-sans);
  font-size: var(--xzm-fs-base);
  line-height: var(--xzm-lh-normal);
}

button,
input,
textarea,
select {
  font: inherit;
}

button,
[role="button"] {
  -webkit-tap-highlight-color: transparent;
}

button:focus-visible,
a:focus-visible,
input:focus-visible,
textarea:focus-visible,
select:focus-visible,
[tabindex]:focus-visible {
  outline: 2px solid var(--xzm-brand);
  outline-offset: 2px;
}

#app,
#app-content {
  width: 100%;
  min-height: 100vh;
}

#app {
  isolation: isolate;
  overflow-x: clip;
  background: var(--xzm-surface-0);
}

#app-content:focus {
  outline: none;
}

::selection {
  color: #102044;
  background: #cfe3ff;
}

::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  min-height: 40px;
  border: 2px solid transparent;
  border-radius: var(--xzm-radius-full);
  background: var(--xzm-scrollbar-thumb);
  background-clip: padding-box;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--xzm-scrollbar-thumb-hover);
  background-clip: padding-box;
}

.el-input__wrapper {
  border: 1px solid var(--xzm-border-color) !important;
  border-radius: var(--xzm-radius-md) !important;
  background: var(--xzm-surface-elevated) !important;
  box-shadow: none !important;
  transition:
    border-color var(--xzm-duration-fast) var(--xzm-ease-standard),
    box-shadow var(--xzm-duration-fast) var(--xzm-ease-standard) !important;
}

.el-input__wrapper:hover {
  border-color: var(--xzm-border-color-hover) !important;
}

.el-input__wrapper.is-focus {
  border-color: var(--xzm-brand) !important;
  box-shadow: 0 0 0 3px var(--xzm-focus-ring-soft) !important;
}

.el-input__inner {
  color: var(--xzm-text-primary) !important;
}

.el-button {
  border-radius: var(--xzm-radius-md) !important;
  font-weight: var(--xzm-fw-medium) !important;
  transition:
    color var(--xzm-duration-fast) var(--xzm-ease-standard),
    background-color var(--xzm-duration-fast) var(--xzm-ease-standard),
    border-color var(--xzm-duration-fast) var(--xzm-ease-standard),
    box-shadow var(--xzm-duration-fast) var(--xzm-ease-standard),
    transform var(--xzm-duration-fast) var(--xzm-ease-emphasized) !important;
}

.el-button--primary {
  border-color: transparent !important;
  color: var(--xzm-text-on-brand) !important;
  background: var(--xzm-brand-gradient) !important;
  box-shadow: var(--xzm-shadow-brand) !important;
}

.el-button--primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--xzm-shadow-brand-hover) !important;
}

.el-button--primary:active:not(:disabled) {
  transform: translateY(0) scale(0.98);
}

.el-message {
  border: 1px solid var(--xzm-border-color) !important;
  border-radius: var(--xzm-radius-lg) !important;
  background: color-mix(in srgb, var(--xzm-surface-elevated) 94%, transparent) !important;
  box-shadow: var(--xzm-shadow-floating) !important;
  backdrop-filter: blur(18px) saturate(130%);
}

/* All programmatic confirmations share one centered, top-level overlay. */
.el-overlay.is-message-box {
  z-index: 12010 !important;
}

.el-overlay-message-box {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 16px !important;
}

.el-message-box {
  max-width: min(420px, calc(100vw - 32px)) !important;
}

.text-center { text-align: center; }
.flex { display: flex; }
.flex-center { display: flex; align-items: center; justify-content: center; }
.flex-column { flex-direction: column; }
.w-full { width: 100%; }
.h-full { height: 100%; }
.min-h-screen { min-height: 100vh; }

.glass-panel {
  border: 1px solid var(--xzm-border-color);
  border-radius: var(--xzm-radius-xl);
  background: var(--xzm-surface-elevated);
  box-shadow: var(--xzm-shadow-medium);
}

.gradient-text {
  color: var(--xzm-brand);
  background: var(--xzm-brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.skip-link {
  position: fixed;
  top: 10px;
  left: 10px;
  z-index: var(--xzm-z-toast);
  padding: 9px 13px;
  border-radius: var(--xzm-radius-md);
  color: var(--xzm-text-on-brand);
  background: var(--xzm-brand);
  box-shadow: var(--xzm-shadow-floating);
  font-weight: var(--xzm-fw-semibold);
  transform: translateY(-160%);
  transition: transform var(--xzm-duration-normal) var(--xzm-ease-emphasized);
}

.skip-link:focus {
  transform: translateY(0);
}

.algorithm-main,
.agent-main,
.report-main {
  animation: xzm-route-enter var(--xzm-duration-normal) var(--xzm-ease-emphasized) both;
}

/*
 * The chat composer is viewport-fixed. A retained transform on this ancestor
 * turns it into the composer's containing block and applies the sidebar offset
 * twice, so the bottom bar drifts right. Keep the chat entrance transform-free.
 */
.xzm-chat-page__main {
  animation: xzm-chat-route-fade var(--xzm-duration-normal) var(--xzm-ease-emphasized) both;
}

@keyframes xzm-route-enter {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes xzm-chat-route-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@media (max-width: 768px) {
  /* iOS zooms focused controls below 16px, which shifts the fixed navigation. */
  #app input:not([type='checkbox']):not([type='radio']):not([type='range']), #app textarea, #app select { font-size: 16px; }
  pre {
    max-width: 100% !important;
    overflow-x: auto !important;
    -webkit-overflow-scrolling: touch !important;
  }

  .code-block-wrapper,
  .message-content,
  .assistant-content-wrapper,
  .markdown-content {
    max-width: 100% !important;
    overflow: hidden !important;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    animation-duration: 1ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 1ms !important;
  }
}
</style>

<style>
#app { --xzm-bottom-nav: 0px; }
@media (max-width: 768px) {
  #app.has-workspace-nav { --xzm-bottom-nav: calc(78px + env(safe-area-inset-bottom)); padding-bottom: var(--xzm-bottom-nav); }
  #app.has-workspace-nav.keyboard-open { --xzm-bottom-nav: 0px; }
  #app.has-workspace-nav #app-content { min-height: calc(var(--xzm-available-height, 100dvh) - var(--xzm-bottom-nav)); }
  #app.has-workspace-nav .xzm-chat-page, #app.has-workspace-nav .xzm-chat-page__main { height: calc(var(--xzm-available-height, 100dvh) - var(--xzm-bottom-nav)); min-height: 0; }
  #app.has-workspace-nav .agent-main { padding-bottom: var(--xzm-bottom-nav); }
  #app.has-workspace-nav .gemini-sidebar { height: var(--xzm-available-height, 100dvh); }
}
</style>
