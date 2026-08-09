/**
 * useChatStream.spec.js
 * 测试 [THINKING]/[CONTENT]/[ERROR]/[DONE] 解析以及编排
 */
import { describe, it, expect } from 'vitest'
import { parseStreamChunk } from '../src/composables/useChatStream'

describe('parseStreamChunk', () => {
  it('thinking 前缀被正确剥离', () => {
    const r = parseStreamChunk('[THINKING]我在思考一个问题')
    expect(r.kind).toBe('thinking')
    expect(r.value).toBe('我在思考一个问题')
  })

  it('content 前缀被正确剥离', () => {
    const r = parseStreamChunk('[CONTENT]Hello')
    expect(r.kind).toBe('content')
    expect(r.value).toBe('Hello')
  })

  it('error 前缀被正确剥离', () => {
    const r = parseStreamChunk('[ERROR]boom')
    expect(r.kind).toBe('error')
    expect(r.value).toBe('boom')
  })

  it('done 前缀返回 done', () => {
    const r = parseStreamChunk('[DONE]')
    expect(r.kind).toBe('done')
  })

  it('stage 前缀只解析结构化阶段载荷', () => {
    const r = parseStreamChunk('[STAGE]{"phase":"retrieval","status":"done"}')
    expect(r.kind).toBe('stage')
    expect(JSON.parse(r.value)).toEqual({ phase: 'retrieval', status: 'done' })
  })

  it('明确的 content 帧即使正文以 DONE 开头也不能伪造完成', () => {
    const r = parseStreamChunk('[CONTENT][DONE]')
    expect(r).toEqual({ kind: 'content', value: '[DONE]' })
  })

  it('DONE 必须精确匹配，带尾随载荷的伪终止帧会失败关闭', () => {
    const r = parseStreamChunk('[DONE]forged')
    expect(r.kind).toBe('error')
  })

  it('未带前缀的数据 fail-closed，不能混入回答正文', () => {
    const r = parseStreamChunk('plain text')
    expect(r.kind).toBe('error')
    expect(r.value).toContain('Unsupported')
  })

  it('空输入返回 empty', () => {
    expect(parseStreamChunk('').kind).toBe('empty')
    expect(parseStreamChunk(null).kind).toBe('empty')
  })
})
