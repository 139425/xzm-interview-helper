export const AUTH_EXPIRED_EVENT = 'auth-expired'

export const readStoredAuth = () => {
  if (typeof localStorage === 'undefined') {
    return { token: null, userInfo: null }
  }

  const token = localStorage.getItem('token')
  const rawUserInfo = localStorage.getItem('userInfo')
  if (!token || !rawUserInfo) return { token: null, userInfo: null }

  try {
    return { token, userInfo: JSON.parse(rawUserInfo) }
  } catch {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    return { token: null, userInfo: null }
  }
}

export const notifyAuthExpired = () => {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
}
