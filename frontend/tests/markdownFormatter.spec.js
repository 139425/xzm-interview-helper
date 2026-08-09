/**
 * markdownFormatter.spec.js
 * 验证简化后的 renderBlock / renderInline / decodeHtmlEntities / escapeFallback
 */
import { describe, it, expect } from 'vitest'
import {
  renderBlock,
  renderMarkdown,
  renderInline,
  decodeHtmlEntities,
  escapeHtml,
  escapeFallback,
} from '../src/utils/markdownFormatter'

describe('escapeHtml', () => {
  it('转义 5 个基础字符', () => {
    expect(escapeHtml('<div class="a&b">\'x\'</div>')).toBe(
      '&lt;div class=&quot;a&amp;b&quot;&gt;&#039;x&#039;&lt;/div&gt;'
    )
  })
})

describe('decodeHtmlEntities', () => {
  it('解码常见 HTML 实体', () => {
    // 注意：&nbsp; 在浏览器 textarea 解码下会变成 U+00A0（NBSP），不属于本测试用例
    expect(decodeHtmlEntities('&lt;div&gt;&amp;&quot;&#039;')).toBe(`<div>&"'`)
  })
})

describe('renderInline', () => {
  it('支持 markdown 语法', () => {
    const html = renderInline('**bold** and *italic*')
    expect(html).toContain('<strong>')
    expect(html).toContain('<em>')
  })

  it('空输入返回空字符串', () => {
    expect(renderInline('')).toBe('')
  })
})

describe('renderBlock', () => {
  it('渲染段落', () => {
    const html = renderBlock('Hello\n\nWorld', 'paragraph')
    expect(html).toContain('<p>')
  })

  it('渲染列表', () => {
    const html = renderBlock('- a\n- b\n', 'list')
    expect(html).toContain('<ul>')
    expect(html).toContain('<li>')
  })

  it('渲染标题', () => {
    const html = renderBlock('# Hello', 'heading')
    expect(html).toContain('<h1>')
  })

  it('代码 kind 走兜底转义（避免与 marked 重复）', () => {
    const html = renderBlock('console.log("<x>")', 'code')
    expect(html).toContain('&lt;x&gt;')
    expect(html).toContain('xzm-fallback-pre')
  })
})

describe('renderMarkdown', () => {
  it('is the explicit sanitised rendering export used by streaming v-html blocks', () => {
    const html = renderMarkdown('[unsafe](javascript:alert(1))')

    expect(html).not.toContain('javascript:')
  })

  it('将模型输出的 ###a) 编号标题按 Markdown 标题渲染', () => {
    const html = renderMarkdown('###a) 常规回答')

    expect(html).toContain('<h3>')
    expect(html).not.toContain('###a)')
  })
})

describe('escapeFallback', () => {
  it('包裹在 <pre> 中并转义', () => {
    expect(escapeFallback('<x>')).toBe('<pre class="xzm-fallback-pre">&lt;x&gt;</pre>')
  })
})
