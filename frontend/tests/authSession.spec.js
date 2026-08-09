import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AUTH_EXPIRED_EVENT,
  notifyAuthExpired,
  readStoredAuth,
} from '@/utils/authSession'

describe('auth session boundary', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns a valid persisted token and user snapshot', () => {
    localStorage.setItem('token', 'token-1')
    localStorage.setItem(
      'userInfo',
      JSON.stringify({ userId: 7, userType: '管理员' }),
    )

    expect(readStoredAuth()).toEqual({
      token: 'token-1',
      userInfo: { userId: 7, userType: '管理员' },
    })
  })

  it('fails closed and clears a corrupted user snapshot', () => {
    localStorage.setItem('token', 'token-1')
    localStorage.setItem('userInfo', '{broken-json')

    expect(readStoredAuth()).toEqual({ token: null, userInfo: null })
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
  })

  it('notifies the live Pinia boundary without importing the store', () => {
    const listener = vi.fn()
    window.addEventListener(AUTH_EXPIRED_EVENT, listener, { once: true })

    notifyAuthExpired()

    expect(listener).toHaveBeenCalledOnce()
  })
})
