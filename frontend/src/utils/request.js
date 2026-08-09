import axios from 'axios'
import router from '@/router'
import { notifyAuthExpired } from '@/utils/authSession'
import { API_BASE_URL } from '@/config/runtime'

// Production and development both use the same-origin /xzm gateway by default.
const BASE_URL = API_BASE_URL

// 创建axios实例
const request = axios.create({
  baseURL: BASE_URL,
  timeout: 90000, // 90秒超时
  headers: {
    'Content-Type': 'application/json'
  }
})

// 防止重复处理401的标志位
let isRedirectingToLogin = false

// 处理认证过期的统一函数
const handleAuthExpired = () => {
  // 如果已经在跳转中，直接返回，防止重复处理
  if (isRedirectingToLogin) {
    return
  }
  
  isRedirectingToLogin = true
  
  // 1. 清除本地存储的认证信息
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  
  // 2. 设置过期提示标志，登录页会读取并显示
  sessionStorage.setItem('authExpired', 'true')
  
  const finalize = () => {
    setTimeout(() => {
      isRedirectingToLogin = false
    }, 300)
  }

  const forceLocationToLogin = () => {
    if (window.location.pathname !== '/login') {
      window.location.replace('/login')
    }
  }

  // Keep the HTTP layer independent from Pinia. The user store imports userApi,
  // which imports this module, so importing the store here creates a cycle.
  notifyAuthExpired()

  Promise.resolve()
    .then(async () => {
      try {
        if (router.currentRoute?.value?.path === '/login') {
          finalize()
          return
        }

        await router.replace('/login')
        setTimeout(forceLocationToLogin, 0)
        finalize()
      } catch (error) {
        forceLocationToLogin()
        finalize()
      }
    })
}

const createPermissionError = (response) => {
  const error = new Error(response?.data?.message || '没有权限执行此操作')
  error.name = 'PermissionError'
  error.code = 'FORBIDDEN'
  error.status = 403
  error.response = response
  return error
}

// 请求拦截器：从localStorage读取token，添加Authorization头
request.interceptors.request.use(
  config => {
    // 从localStorage读取token
    const token = localStorage.getItem('token')
    if (token) {
      // 添加Authorization头，格式为"Bearer {token}"
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：处理401错误，清除存储，显示提示，重定向登录页
request.interceptors.response.use(
  response => {
    // 检查响应体中的code是否为401（某些后端返回HTTP 200但body中code为401）
    if (response.data?.code === 401) {
      handleAuthExpired()
      return Promise.reject(new Error(response.data.message || '认证过期'))
    }
    if (response.data?.code === 403) {
      return Promise.reject(createPermissionError(response))
    }
    
    return response
  },
  error => {
    // 只有 401 表示登录态失效；403 表示当前登录用户没有权限。
    if (error.response?.status === 401) {
      handleAuthExpired()
    }
    if (error.response?.status === 403) {
      return Promise.reject(createPermissionError(error.response))
    }
    
    return Promise.reject(error)
  }
)

// 导出BASE_URL供SSE等需要直接使用URL的场景
export const baseURL = BASE_URL

// 导出axios实例
export default request
