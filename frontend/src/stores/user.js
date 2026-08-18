import { defineStore } from 'pinia'
import { userApi } from '../api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    // 用户登录状态
    isLoggedIn: false,
    // 用户信息
    userInfo: null,
    // 登录token或session信息
    token: null,
    // 登录加载状态
    loginLoading: false,
    // 注册加载状态
    registerLoading: false,
    // 用户管理相关状态
    userList: [], // 用户列表
    userListLoading: false, // 用户列表加载状态
    lastUserListUpdate: null // 最后更新时间
  }),
  
  getters: {
    // 获取用户ID
    userId: (state) => state.userInfo?.userId || null,
    // 获取用户名
    username: (state) => state.userInfo?.username || '',
    // 获取用户类型
    userType: (state) => state.userInfo?.userType || '',
    // 检查是否为管理员
    isAdmin: (state) => state.isLoggedIn && state.userInfo?.userType === '管理员'
  },
  
  actions: {
    // 用户登录
    async login(username, password, verificationToken) {
      this.loginLoading = true
      try {
        const response = await userApi.login({ username, password, verificationToken })
        
        // 检查登录是否成功 - 兼容多种返回格式
        const isSuccess = response.code === 200 || 
                         (response.data && response.data.user_id) ||
                         (response.message && response.message.includes('成功'))
        
        if (isSuccess && response.data) {
          // 登录成功，保存用户信息
          this.isLoggedIn = true
          this.userInfo = {
            userId: response.data.user_id || response.data.id,
            username: response.data.username,
            userType: response.data.user_type
          }
          this.token = response.token || null
          
          // 保存到localStorage
          localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
          if (response.token) {
            localStorage.setItem('token', response.token)
          }
          
          return { success: true, message: response.message || '登录成功' }
        } else {
          return { success: false, message: response.message || '登录失败' }
        }
      } catch (error) {
        console.error('登录请求失败:', error)
        return { success: false, message: '网络错误，请稍后重试' }
      } finally {
        this.loginLoading = false
      }
    },
    
    // 用户注册
    async register(registerData) {
      this.registerLoading = true
      try {
        const response = await userApi.register(registerData)
        
        if (response.code === 200) {
          return { success: true, message: '注册成功，请登录' }
        } else {
          return { success: false, message: response.message || '注册失败' }
        }
      } catch (error) {
        console.error('注册请求失败:', error)
        return { success: false, message: '网络错误，请稍后重试' }
      } finally {
        this.registerLoading = false
      }
    },
    
    // 用户登出
    logout() {
      this.isLoggedIn = false
      this.userInfo = null
      this.token = null
      
      // 清除localStorage
      localStorage.removeItem('userInfo')
      localStorage.removeItem('token')
    },
    
    // 从localStorage恢复用户状态
    restoreUserState() {
      try {
        const userInfo = localStorage.getItem('userInfo')
        const token = localStorage.getItem('token')
        
        if (userInfo) {
          this.userInfo = JSON.parse(userInfo)
          this.isLoggedIn = true
        }
        
        if (token) {
          this.token = token
        }
      } catch (error) {
        console.error('恢复用户状态失败:', error)
        // 清除可能损坏的数据
        this.logout()
      }
    },
    
    // 检查登录状态
    checkLoginStatus() {
      return this.isLoggedIn && this.userInfo !== null
    },
    
    // 更新用户信息
    updateUserInfo(newUserInfo) {
      this.userInfo = { ...this.userInfo, ...newUserInfo }
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    
    // 设置用户列表
    setUserList(users) {
      this.userList = users
      this.lastUserListUpdate = new Date()
    },
    
    // 设置用户列表加载状态
    setUserListLoading(loading) {
      this.userListLoading = loading
    },
    
    // 清除用户列表缓存
    clearUserListCache() {
      this.userList = []
      this.lastUserListUpdate = null
    },
    
    // 检查用户列表缓存是否有效（5分钟内）
    isUserListCacheValid() {
      if (!this.lastUserListUpdate) return false
      const now = new Date()
      const diff = now - this.lastUserListUpdate
      return diff < 5 * 60 * 1000 // 5分钟
    }
  }
})
