/**
 * useTheme — 主题切换 composable
 *
 * - 持久化到 localStorage('xzm-theme')
 * - 'system' 模式跟随 prefers-color-scheme
 * - 切换时给 <html> 加 data-theme，所有 token 自动响应
 */
import { ref, computed, watch } from 'vue'

const STORAGE_KEY = 'xzm-theme'
const VALID_THEMES = ['dark', 'light', 'system']

const theme = ref(loadInitialTheme())
const systemPrefersDark = ref(getSystemPrefersDark())

let mediaQuery = null
let mediaListener = null

function loadInitialTheme() {
  if (typeof localStorage === 'undefined') return 'light'
  // 兼容旧 ui store 写入的 'theme' key
  const saved = localStorage.getItem(STORAGE_KEY) || localStorage.getItem('theme')
  return VALID_THEMES.includes(saved) ? saved : 'light'
}

function getSystemPrefersDark() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyThemeToDocument(t) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', t)
  // 同步给 body 一份，便于 Element Plus 选择器命中
  document.body?.setAttribute('data-theme', t)
}

const resolvedTheme = computed(() => {
  if (theme.value === 'system') {
    return systemPrefersDark.value ? 'dark' : 'light'
  }
  return theme.value
})

function setTheme(next) {
  if (!VALID_THEMES.includes(next)) return
  theme.value = next
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, next)
    // 兼容旧 store key
    if (next !== 'system') localStorage.setItem('theme', next)
  }
  // 立即同步到 document，避免依赖 watch 异步触发（测试与首屏均需要同步）
  applyThemeToDocument(next === 'system'
    ? (systemPrefersDark.value ? 'dark' : 'light')
    : next)
}

function toggle() {
  // 仅在 dark / light 两态间切换；system 视作其当前 resolved 的反面
  const current = resolvedTheme.value
  setTheme(current === 'dark' ? 'light' : 'dark')
}

function attachSystemListener() {
  if (typeof window === 'undefined' || !window.matchMedia) return
  if (mediaQuery && mediaListener) return
  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaListener = (e) => {
    systemPrefersDark.value = e.matches
  }
  if (mediaQuery.addEventListener) {
    mediaQuery.addEventListener('change', mediaListener)
  } else if (mediaQuery.addListener) {
    mediaQuery.addListener(mediaListener)
  }
}

// 第一次创建即应用一次
applyThemeToDocument(resolvedTheme.value)

watch(resolvedTheme, (next) => {
  applyThemeToDocument(next)
})

export function useTheme() {
  // This module is an application-wide singleton. Keep one idempotent media
  // listener for the page lifetime so direct composable use remains valid too.
  attachSystemListener()

  return {
    theme,
    resolvedTheme,
    setTheme,
    toggle,
    THEMES: VALID_THEMES,
  }
}

/**
 * 初始化（在 Vue 组件外使用，例如 main.js）
 * 立即应用一次 data-theme 并附加 system listener
 */
export function initTheme() {
  applyThemeToDocument(resolvedTheme.value)
  attachSystemListener()
}
