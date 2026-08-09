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
})
