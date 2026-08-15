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

  it('将进行中的文字交给短语淡入组件而不改变 Markdown 完成态', () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [{ id: 'p1', kind: 'paragraph', raw: '正在逐字生成一段足够长的回答', done: false }],
          isFinalized: { value: false },
        },
      },
    })

    const reveal = wrapper.findComponent({ name: 'StreamingTextReveal' })
    expect(reveal.exists()).toBe(true)
    expect(reveal.props('text')).toBe('正在逐字生成一段足够长的回答')
    expect(reveal.props('mode')).toBe('text')
  })

  it('将未完成的表格源码按代码文本块淡入', () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [{ id: 'table-1', kind: 'table', raw: '| 公司 | 状态 |', done: false }],
          isFinalized: { value: false },
        },
      },
    })

    const reveal = wrapper.findComponent({ name: 'StreamingTextReveal' })
    expect(reveal.props()).toMatchObject({
      text: '| 公司 | 状态 |',
      mode: 'code',
    })
  })
})
