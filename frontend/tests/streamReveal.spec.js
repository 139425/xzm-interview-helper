import { describe, expect, it } from 'vitest'
import { takeRevealPhrase } from '../src/utils/streamReveal'

describe('takeRevealPhrase', () => {
  it('buffers single-character frames until a readable phrase is available', () => {
    expect(takeRevealPhrase('逐')).toBeNull()
    expect(takeRevealPhrase('逐字出现')).toBeNull()
    expect(takeRevealPhrase('逐字出现并不自然')).toEqual({
      phrase: '逐字出现并不自然',
      rest: '',
    })
  })

  it('uses punctuation as a natural phrase boundary', () => {
    expect(takeRevealPhrase('先给出结论，')).toEqual({
      phrase: '先给出结论，',
      rest: '',
    })
  })

  it('keeps a long sentence split into short visual blocks', () => {
    const source = '这是一段比较长的说明文字，需要按自然短语逐渐淡入。'
    const first = takeRevealPhrase(source)

    expect(Array.from(first.phrase).length).toBeLessThanOrEqual(14)
    expect(first.rest.length).toBeGreaterThan(0)
  })

  it('preserves every code point, including emoji, when splitting', () => {
    const source = '自然淡入🙂不会拆开字符边界并继续展示'
    const first = takeRevealPhrase(source)
    const second = takeRevealPhrase(first.rest, { force: true })

    expect(first.phrase + second.phrase).toBe(source)
    expect(first.phrase).toContain('🙂')
  })

  it('groups code by a larger unit without changing whitespace', () => {
    const source = 'const answer = calculate(value)\nreturn answer'
    const first = takeRevealPhrase(source, { mode: 'code' })
    const rest = takeRevealPhrase(first.rest, { mode: 'code', force: true })

    expect(first.phrase + rest.phrase).toBe(source)
  })
})
