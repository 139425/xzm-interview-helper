<template>
  <div id="app">
    <a class="skip-link" href="#app-content">跳到主要内容</a>
    <div id="app-content" tabindex="-1">
      <router-view />
    </div>
  </div>
</template>

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
