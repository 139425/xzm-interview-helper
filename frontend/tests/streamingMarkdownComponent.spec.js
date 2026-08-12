import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StreamingMarkdown from '../src/components/streaming/StreamingMarkdown.vue'

describe('StreamingMarkdown component routing', () => {
  it('将已完成的 mermaid 代码块交给图表渲染器', () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [
            {
              id: 'diagram-1',
              kind: 'code',
              lang: 'mermaid',
              raw: 'flowchart LR\nA --> B',
              done: true,
            },
          ],
          isFinalized: { value: true },
        },
      },
    })

    const diagram = wrapper.findComponent({ name: 'MermaidDiagram' })
    expect(diagram.exists()).toBe(true)
    expect(diagram.props('code')).toContain('flowchart LR')
  })

  it('只给进行中文字的短尾段添加淡入节点', () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [{ id: 'p1', kind: 'paragraph', raw: '正在逐字生成一段足够长的回答', done: false }],
          isFinalized: { value: false },
        },
      },
    })

    expect(wrapper.find('.xzm-stream-md__pending-tail').text()).toBe('正在逐字生成一段足够长的回答'.slice(-14))
  })
})
