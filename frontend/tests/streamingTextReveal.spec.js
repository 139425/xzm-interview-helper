import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import StreamingTextReveal from '../src/components/streaming/StreamingTextReveal.vue'

describe('StreamingTextReveal', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('reveals several incoming characters as one fading phrase', async () => {
    const wrapper = mount(StreamingTextReveal, { props: { text: '' } })

    for (const value of ['自', '自然', '自然淡', '自然淡入', '自然淡入更', '自然淡入更加', '自然淡入更加柔', '自然淡入更加柔和']) {
      await wrapper.setProps({ text: value })
    }

    const phrases = wrapper.findAll('.xzm-stream-reveal__phrase')
    expect(phrases).toHaveLength(1)
    expect(phrases[0].text()).toBe('自然淡入更加柔和')
    expect(wrapper.attributes('data-reveal-motion')).toBe('fade')
  })

  it('flushes a short final phrase after the stream pauses', async () => {
    const wrapper = mount(StreamingTextReveal, { props: { text: '结论' } })

    expect(wrapper.text()).toBe('')
    await vi.advanceTimersByTimeAsync(279)
    expect(wrapper.text()).toBe('')
    await vi.advanceTimersByTimeAsync(1)
    expect(wrapper.text()).toBe('结论')
  })

  it('groups content without animating it for reduced motion', async () => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))

    const wrapper = mount(StreamingTextReveal, { props: { text: '减少动态效果仍按短语展示' } })
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toBe('减少动态效果仍按短语展示')
    expect(wrapper.findAll('.xzm-stream-reveal__phrase').length).toBeGreaterThan(1)
    expect(wrapper.attributes('data-reveal-motion')).toBe('off')
  })

  it('keeps streamed code byte-for-byte readable across reveal spans', async () => {
    const source = 'const value = 1\n  return value\n'
    const wrapper = mount(StreamingTextReveal, {
      props: { text: source, mode: 'code' },
    })

    await vi.runAllTimersAsync()
    expect(wrapper.element.textContent).toBe(source)
    expect(wrapper.findAll('.xzm-stream-reveal__phrase').length).toBeGreaterThan(0)
  })
})
