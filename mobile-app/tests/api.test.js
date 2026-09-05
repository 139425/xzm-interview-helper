import { beforeEach, describe, expect, it } from 'vitest'
import { normalizeApiUrl, setApiUrl, getApiUrl } from '../src/lib/api'

describe('API address normalization', () => {
  beforeEach(() => localStorage.clear())

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
