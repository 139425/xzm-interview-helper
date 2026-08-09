<template>
  <div id="app">
    <a class="skip-link" href="#app-content">跳到主要内容</a>
    <!-- 加载动画 -->
    <LoadingAnimation v-if="isLoading" :show="isLoading" />
    
    <!-- 路由视图 -->
    <div id="app-content" tabindex="-1">
      <router-view v-show="!isLoading" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import LoadingAnimation from './components/LoadingAnimation.vue'

const isLoading = ref(true)

// 主题由 main.js 在 Vue 挂载前统一初始化，避免根组件再次覆盖默认值。
onMounted(() => {
  requestAnimationFrame(() => {
    isLoading.value = false
  })
})
</script>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  background-color: var(--gemini-bg-primary, #f7fafc);
  color: var(--gemini-text-primary, #1f2937);
  overflow-x: hidden;
  /* 防止任何内容撑开页面 */
  max-width: 100vw;
}

#app {
  min-height: 100vh;
  width: 100%;
  max-width: 100vw;
  background-color: var(--gemini-bg-primary, #131314);
  overflow-x: hidden;
}

/* 星空背景动画 */
@keyframes twinkle {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
}

::-webkit-scrollbar-track {
  background: var(--bg-secondary);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--border-hover);
}

/* 暗色模式滚动条样式 */
[data-theme="dark"] ::-webkit-scrollbar-track {
  background: #1e293b;
}

[data-theme="dark"] ::-webkit-scrollbar-thumb {
  background: #475569;
}

[data-theme="dark"] ::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}

/* Element Plus 组件样式覆盖 */
.el-input__wrapper {
  background-color: var(--bg-secondary) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 8px !important;
  box-shadow: none !important;
}

.el-input__wrapper:hover {
  border-color: var(--primary-color) !important;
}

.el-input__wrapper.is-focus {
  border-color: var(--primary-color) !important;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2) !important;
}

.el-input__inner {
  color: var(--text-primary) !important;
  background-color: transparent !important;
}

.el-input__inner::placeholder {
  color: var(--text-tertiary) !important;
}

/* 暗色模式下的Element Plus组件样式 */
[data-theme="dark"] .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3) !important;
}

.el-button {
  border-radius: 8px !important;
  font-weight: 500 !important;
  transition:
    color 160ms ease,
    background-color 160ms ease,
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease !important;
}

.el-button--primary {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%) !important;
  border: none !important;
}

.el-button--primary:hover {
  background: linear-gradient(135deg, #66b1ff 0%, #85ce61 100%) !important;
  transform: translateY(-2px) !important;
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.3) !important;
}

.el-message {
  border-radius: 8px !important;
  backdrop-filter: blur(10px) !important;
}

.el-message--success {
  background-color: rgba(103, 194, 58, 0.9) !important;
}

.el-message--error {
  background-color: rgba(245, 108, 108, 0.9) !important;
}

.el-message--warning {
  background-color: rgba(230, 162, 60, 0.9) !important;
}

/* 通用工具类 */
.text-center {
  text-align: center;
}

.flex {
  display: flex;
}

.flex-center {
  display: flex;
  justify-content: center;
  align-items: center;
}

.flex-column {
  flex-direction: column;
}

.w-full {
  width: 100%;
}

.h-full {
  height: 100%;
}

.min-h-screen {
  min-height: 100vh;
}

.glass-panel {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.gradient-text {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .glass-panel {
    margin: 10px;
    border-radius: 12px;
  }
  
  /* 全局代码块溢出控制 - 防止代码块撑开页面 */
  pre {
    max-width: 100% !important;
    overflow-x: auto !important;
    -webkit-overflow-scrolling: touch !important;
  }
  
  .code-block-wrapper {
    max-width: 100% !important;
    overflow: hidden !important;
  }
  
  .code-block-wrapper pre {
    max-width: 100% !important;
    overflow-x: auto !important;
  }
  
  /* 确保消息内容不会撑开 */
  .message-content,
  .assistant-content-wrapper,
  .markdown-content {
    max-width: 100% !important;
    overflow: hidden !important;
  }
}

/* 动画效果 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.slide-up-enter-from {
  transform: translateY(20px);
  opacity: 0;
}

.skip-link {
  position: fixed;
  top: 10px;
  left: 10px;
  z-index: 10000;
  padding: 10px 14px;
  border-radius: 9px;
  color: #08131f;
  background: #9be8f8;
  font-weight: 700;
  transform: translateY(-160%);
  transition: transform 140ms ease-out;
}

.skip-link:focus {
  transform: translateY(0);
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
