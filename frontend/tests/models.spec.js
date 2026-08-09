import { describe, expect, it } from 'vitest'
import {
  CHAT_MODELS,
  DEFAULT_CHAT_MODEL_ID,
  getChatModel
} from '@/config/models'

describe('chat model catalog', () => {
  it('contains the supported providers and models', () => {
    expect(CHAT_MODELS.map(model => model.modelName)).toEqual([
      'GLM-4.7-Flash',
      'deepseek-v4-flash',
      'deepseek-v4-pro'
    ])
  })

  it('keeps thinking as a capability toggle instead of duplicating models', () => {
    expect(CHAT_MODELS).toHaveLength(3)
    expect(CHAT_MODELS.every(model => !Object.hasOwn(model, 'thinking'))).toBe(true)
  })

  it('falls back to the default model for an unknown id', () => {
    expect(DEFAULT_CHAT_MODEL_ID).toBe('deepseek-v4-flash')
    expect(getChatModel('unknown').id).toBe(DEFAULT_CHAT_MODEL_ID)
  })
})
