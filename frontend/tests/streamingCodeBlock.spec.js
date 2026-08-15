import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StreamingCodeBlock from '../src/components/streaming/StreamingCodeBlock.vue'

describe('StreamingCodeBlock', () => {
  it('reveals unfinished source in code-sized blocks', () => {
    const wrapper = shallowMount(StreamingCodeBlock, {
      props: {
        code: 'const answer = 42\n',
        lang: 'js',
        isStreaming: true,
        blockId: 'code-1',
      },
    })

    const reveal = wrapper.findComponent({ name: 'StreamingTextReveal' })
    expect(reveal.exists()).toBe(true)
    expect(reveal.props()).toMatchObject({
      text: 'const answer = 42\n',
      mode: 'code',
    })
  })

  it('keeps completed source on the syntax-highlighted rendering path', () => {
    const wrapper = shallowMount(StreamingCodeBlock, {
      props: {
        code: 'const answer = 42',
        lang: 'js',
        isStreaming: false,
        blockId: 'code-2',
      },
    })

    expect(wrapper.findComponent({ name: 'StreamingTextReveal' }).exists()).toBe(false)
    expect(wrapper.find('code.hljs').exists()).toBe(true)
  })
})
