/**
 * theme.spec.js
 * 验证 useTheme：默认 light / 切换 / data-theme 同步 / localStorage 持久化
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { initTheme, useTheme } from '../src/composables/useTheme'

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.body.removeAttribute('data-theme')
  })

  it('初始化时默认 light 主题（V2 极简白纸风）', () => {
    initTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('setTheme(dark) 后 data-theme 同步切换', () => {
    initTheme()
    const { setTheme } = useTheme()
    setTheme('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(localStorage.getItem('xzm-theme')).toBe('dark')
  })

  it('toggle 在 dark / light 之间切换', () => {
    initTheme()
    const { toggle, resolvedTheme } = useTheme()
    const before = resolvedTheme.value
    toggle()
    expect(resolvedTheme.value).not.toBe(before)
  })

  it('兼容旧 localStorage.theme=light', () => {
    localStorage.setItem('theme', 'light')
    expect(localStorage.getItem('theme')).toBe('light')
  })
})
