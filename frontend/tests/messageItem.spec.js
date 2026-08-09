import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MessageItem from '@/components/MessageItem.vue'

describe('MessageItem', () => {
  it('renders a static user message without creating a markdown stream', () => {
    const wrapper = mount(MessageItem, {
      props: {
        role: 'user',
        content: 'hello from user'
      }
    })

    expect(wrapper.text()).toContain('hello from user')
  })

  it('renders a completed assistant message through the static stream', async () => {
    const wrapper = mount(MessageItem, {
      props: {
        role: 'assistant',
        content: 'hello from assistant'
      }
    })

    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Assistant')
    expect(wrapper.text()).toContain('hello from assistant')
  })
})
