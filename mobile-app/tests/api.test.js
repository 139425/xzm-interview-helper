import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  DEFAULT_API_URL,
  buildUrl,
  chatApi,
  getApiUrl,
  normalizeApiUrl,
  parseChatStreamPayload,
  saveAuth,
  setApiUrl,
} from '../src/lib/api'

afterEach(() => vi.unstubAllGlobals())

describe('API address normalization', () => {
  beforeEach(() => localStorage.clear())

  it('uses the deployed API without first-run configuration', () => {
    expect(getApiUrl()).toBe(DEFAULT_API_URL)
    expect(buildUrl('/api/recruitments')).toBe(`${DEFAULT_API_URL}/api/recruitments`)
  })

  it('adds the default context path', () => {
    expect(normalizeApiUrl('http://192.168.1.8:8104')).toBe('http://192.168.1.8:8104/xzm')
  })

  it('keeps an explicit context path and removes trailing slashes', () => {
    expect(normalizeApiUrl('https://api.example.com/xzm///')).toBe('https://api.example.com/xzm')
  })

  it('rejects unsupported protocols', () => {
    expect(() => normalizeApiUrl('file:///tmp/api')).toThrow('HTTP')
  })

  it('persists normalized addresses', () => {
    setApiUrl('http://10.0.2.2:8104')
    expect(getApiUrl()).toBe('http://10.0.2.2:8104/xzm')
  })
})

describe('AI stream payload parsing', () => {
  it('joins content frames and ignores process frames', () => {
    const payload = [
      'data:[STAGE]{"phase":"retrieval","status":"done"}',
      '',
      'data: [THINKING]先分析',
      '',
      'data:[CONTENT]你好，',
      '',
      'data:[CONTENT]欢迎使用。',
      '',
      'data:[DONE]',
      '',
    ].join('\n')
    expect(parseChatStreamPayload(payload)).toBe('你好，欢迎使用。')
  })

  it('surfaces typed AI errors', () => {
    expect(() => parseChatStreamPayload('data:[ERROR]服务繁忙\n\n')).toThrow('服务繁忙')
  })

  it('rejects incomplete streams', () => {
    expect(() => parseChatStreamPayload('data:[CONTENT]半条回复\n\n')).toThrow('回复中断')
  })

  it('keeps compatibility with plain-text gateways', () => {
    expect(parseChatStreamPayload('完整回答')).toBe('完整回答')
  })
})

describe('AI chat transport', () => {
  beforeEach(() => {
    localStorage.clear()
    saveAuth('test-token', { userId: 7, username: 'tester' })
  })

  it('uses the authenticated standard stream and returns the assembled answer', async () => {
    const fetchMock = vi.fn(async () => ({
      status: 200,
      text: async () => 'data:[CONTENT]移动端回复正常\n\ndata:[DONE]\n\n',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(chatApi.reply(42, '你好')).resolves.toBe('移动端回复正常')
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe(`${DEFAULT_API_URL}/longcat/streamChat`)
    expect(options.headers.Authorization).toBe('Bearer test-token')
    expect(JSON.parse(options.body)).toMatchObject({
      userMemoryId: 42,
      message: '你好',
      promptMode: 'professional',
      provider: 'deepseek',
      modelName: 'deepseek-v4-flash',
    })
  })

  it('uses the thinking stream in deep-analysis mode', async () => {
    const fetchMock = vi.fn(async () => ({
      status: 200,
      text: async () => 'data:[THINKING]分析中\n\ndata:[CONTENT]分析结果\n\ndata:[DONE]\n\n',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(chatApi.reply(9, '分析一下', true)).resolves.toBe('分析结果')
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe(`${DEFAULT_API_URL}/longcat/streamThinkChat`)
    expect(JSON.parse(options.body).promptMode).toBe('reasoning')
  })
})
