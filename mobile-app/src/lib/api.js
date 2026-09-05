import { Browser } from '@capacitor/browser'
import { Capacitor, CapacitorHttp } from '@capacitor/core'

const API_URL_KEY = 'xzm.mobile.apiUrl'
const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'

export class ApiError extends Error {
  constructor(message, status = 0, data = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.data = data
  }
}

export function normalizeApiUrl(value) {
  const raw = String(value || '').trim().replace(/\/+$/, '')
  if (!raw) return ''
  let parsed
  try {
    parsed = new URL(raw)
  } catch {
    throw new ApiError('请输入完整地址，例如 http://192.168.1.8:8104/xzm')
  }
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new ApiError('服务地址只支持 HTTP 或 HTTPS')
  }
  if (!parsed.pathname || parsed.pathname === '/') parsed.pathname = '/xzm'
  return parsed.href.replace(/\/$/, '')
}

export function getApiUrl() {
  return localStorage.getItem(API_URL_KEY) || ''
}

export function setApiUrl(value) {
  const normalized = normalizeApiUrl(value)
  localStorage.setItem(API_URL_KEY, normalized)
  return normalized
}

export function getAuth() {
  let user = null
  try {
    user = JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    localStorage.removeItem(USER_KEY)
  }
  return { token: localStorage.getItem(TOKEN_KEY) || '', user }
}

export function saveAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

function buildUrl(path, params) {
  const base = getApiUrl()
  if (!base) throw new ApiError('请先配置后端服务地址')
  const url = new URL(`${base}${path.startsWith('/') ? path : `/${path}`}`)
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined && value !== false) {
      url.searchParams.set(key, String(value))
    }
  })
  return url.href
}

function errorMessage(data, status) {
  if (typeof data === 'string') {
    const clean = data.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
    if (clean && clean.length < 180) return clean
  }
  return data?.message || data?.error || data?.detail || `请求失败（${status || '网络错误'}）`
}

async function nativeRequest(url, options) {
  const response = await CapacitorHttp.request({
    url,
    method: options.method,
    headers: options.headers,
    data: options.body,
    connectTimeout: 20_000,
    readTimeout: options.timeout || 120_000,
    responseType: options.responseType || 'json',
  })
  return { status: response.status, data: response.data }
}

async function webRequest(url, options) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), options.timeout || 120_000)
  try {
    const response = await fetch(url, {
      method: options.method,
      headers: options.headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    })
    const text = await response.text()
    let data = text
    try { data = text ? JSON.parse(text) : null } catch { /* keep text */ }
    return { status: response.status, data }
  } finally {
    clearTimeout(timer)
  }
}

export async function request(path, { method = 'GET', params, body, auth = true, timeout, responseType } = {}) {
  const url = buildUrl(path, params)
  const headers = { Accept: 'application/json', 'Content-Type': 'application/json' }
  const token = getAuth().token
  if (auth && token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    const options = { method, headers, body, timeout, responseType }
    response = Capacitor.isNativePlatform()
      ? await nativeRequest(url, options)
      : await webRequest(url, options)
  } catch (error) {
    if (error?.name === 'AbortError') throw new ApiError('请求超时，请检查服务地址与网络')
    throw new ApiError(error?.message || '无法连接后端服务，请检查地址与网络')
  }

  if (response.status === 401) {
    clearAuth()
    window.dispatchEvent(new CustomEvent('xzm:auth-expired'))
    throw new ApiError('登录已过期，请重新登录', 401, response.data)
  }
  if (response.status < 200 || response.status >= 300) {
    throw new ApiError(errorMessage(response.data, response.status), response.status, response.data)
  }
  return response.data
}

export async function testApiUrl(value) {
  const previous = getApiUrl()
  const normalized = setApiUrl(value)
  try {
    await request('/api/recruitments', {
      params: { page: 1, size: 1, targetGraduates: '2027届' },
      auth: false,
      timeout: 15_000,
    })
    return normalized
  } catch (error) {
    if (previous) localStorage.setItem(API_URL_KEY, previous)
    else localStorage.removeItem(API_URL_KEY)
    throw error
  }
}

export async function openExternal(value) {
  let url
  try { url = new URL(value) } catch { throw new ApiError('链接格式无效') }
  if (!['http:', 'https:'].includes(url.protocol)) throw new ApiError('不支持此链接')
  if (Capacitor.isNativePlatform()) await Browser.open({ url: url.href })
  else window.open(url.href, '_blank', 'noopener,noreferrer')
}

function unwrap(response, fallback = null) {
  return response?.data ?? fallback
}

export const authApi = {
  async verificationConfig() {
    const result = await request('/user/verification/config', { auth: false })
    return unwrap(result, { loginMode: 'SLIDER', registrationMode: 'CAPTCHA' })
  },
  async createSlider() {
    return unwrap(await request('/user/verification/slider', { method: 'POST', body: {}, auth: false }), {})
  },
  async verifySlider(challengeId, sliderValue = 100) {
    return unwrap(await request('/user/verification/slider/verify', {
      method: 'POST', body: { challengeId, sliderValue }, auth: false,
    }), {})
  },
  async createCaptcha() {
    return unwrap(await request('/user/verification/captcha', { method: 'POST', body: {}, auth: false }), {})
  },
  async sendEmailCode(email, verificationToken) {
    return request('/user/verification/email-code', {
      method: 'POST', body: { email, verificationToken }, auth: false,
    })
  },
  login(username, password, verificationToken) {
    return request('/user/login', {
      method: 'POST', body: { username, password, verificationToken }, auth: false,
    })
  },
  register(payload) {
    return request('/user/register', { method: 'POST', body: payload, auth: false })
  },
}

export const chatApi = {
  createConversation: () => request('/record/conversations', { method: 'POST', body: {} }),
  history: (memoryId) => request(`/record/history/${memoryId}`),
  histories: () => request('/record/histories'),
  reply(memoryId, message, deepThinking = false) {
    return request('/longcat/directChat', {
      method: 'POST',
      body: {
        userMemoryId: memoryId,
        message,
        promptMode: deepThinking ? 'reasoning' : 'professional',
        provider: 'deepseek',
        modelName: 'deepseek-v4-flash',
      },
      timeout: 180_000,
      responseType: 'text',
    })
  },
}

export const scheduleApi = {
  list: async () => unwrap(await request('/api/schedules'), { items: [], summary: {}, trash: [] }),
  create: async (body) => unwrap(await request('/api/schedules', { method: 'POST', body })),
  update: async (id, body) => unwrap(await request(`/api/schedules/${id}`, { method: 'PUT', body })),
  complete: async (id, completed) => unwrap(await request(`/api/schedules/${id}/completed`, {
    method: 'PATCH', body: { completed },
  })),
  remove: (id) => request(`/api/schedules/${id}`, { method: 'DELETE' }),
}

export const applicationApi = {
  list: async (params = {}) => unwrap(await request('/api/applications', { params }), { items: [], summary: {} }),
  create: async (body) => unwrap(await request('/api/applications', { method: 'POST', body })),
  update: async (id, body) => unwrap(await request(`/api/applications/${id}`, { method: 'PUT', body })),
  status: async (id, status) => unwrap(await request(`/api/applications/${id}/status`, {
    method: 'PATCH', body: { status },
  })),
  fromRecruitment: async (recruitmentId) => unwrap(await request('/api/applications/from-recruitment', {
    method: 'POST', body: { recruitmentId },
  })),
  remove: (id) => request(`/api/applications/${id}`, { method: 'DELETE' }),
}

export const recruitmentApi = {
  list: async (params = {}) => unwrap(await request('/api/recruitments', { params, auth: false }), {
    items: [], total: 0, page: 1, size: 20, hasMore: false, summary: {},
  }),
}
