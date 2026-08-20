import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { useUIStore } from '@/stores/ui'

describe('UI prompt mode state', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('accepts reasoning mode without falling back to professional', async () => {
    const store = useUIStore()

    store.setPromptMode('reasoning')
    await nextTick()

    expect(store.promptMode).toBe('reasoning')
    expect(localStorage.getItem('promptMode')).toBe('reasoning')
  })

  it('restores a persisted reasoning mode', () => {
    localStorage.setItem('promptMode', 'reasoning')
    setActivePinia(createPinia())

    expect(useUIStore().promptMode).toBe('reasoning')
  })

  it('still rejects unknown modes', () => {
    const store = useUIStore()

    store.setPromptMode('unexpected')

    expect(store.promptMode).toBe('professional')
  })

  it('uses the light theme on a first visit', () => {
    expect(useUIStore().currentTheme).toBe('light')
  })

  it('persists the desktop sidebar and workspace density preferences', () => {
    const store = useUIStore()
    store.viewportWidth = 1440

    expect(store.sidebarWidth).toBe(224)
    expect(store.workspaceListExpanded).toBe(false)

    store.collapseSidebar()
    store.toggleWorkspaceList()

    expect(store.sidebarWidth).toBe(64)
    expect(localStorage.getItem('sidebarExpanded')).toBe('false')
    expect(localStorage.getItem('workspaceListExpanded')).toBe('true')
  })

  it('keeps mobile content fixed while the sidebar opens as an overlay', () => {
    const store = useUIStore()
    store.viewportWidth = 390
    store.sidebarExpanded = true

    expect(store.sidebarWidth).toBe(0)
  })
})
