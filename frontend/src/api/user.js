import request from '@/utils/request'

export const userApi = {
  async getVerificationConfig() {
    const response = await request.get('/user/verification/config')
    return response.data
  },

  async createSliderChallenge() {
    const response = await request.post('/user/verification/slider')
    return response.data
  },

  async verifySlider(challengeId, sliderValue) {
    const response = await request.post('/user/verification/slider/verify', {
      challengeId,
      sliderValue,
    })
    return response.data
  },

  async createCaptcha() {
    const response = await request.post('/user/verification/captcha')
    return response.data
  },

  async sendRegistrationCode(email, verificationToken) {
    const response = await request.post('/user/verification/email-code', {
      email,
      verificationToken,
    })
    return response.data
  },

  /**
   * 用户登录
   * @param {object} loginData - 登录数据 {username, password, verificationToken}
   * @returns {Promise} - 返回登录结果
   */
  async login(loginData) {
    try {
      const response = await request.post('/user/login', loginData)
      // 登录成功后保存token到localStorage (Requirements 5.1)
      if (response.data && response.data.token) {
        localStorage.setItem('token', response.data.token)
      }
      return response.data
    } catch (error) {
      console.error('登录请求失败:', error)

      // 处理不同类型的错误
      if (error.response) {
        // 服务器返回了错误状态码
        return {
          success: false,
          message: error.response.data?.message || '登录失败'
        }
      } else if (error.request) {
        // 请求发送了但没有收到响应
        return {
          success: false,
          message: '网络连接失败，请检查网络或后端服务'
        }
      } else {
        // 其他错误
        return {
          success: false,
          message: '请求发送失败'
        }
      }
    }
  },

  /**
   * 用户注册
   * @param {object} registerData - 注册数据 {username, password, confirmPassword, phoneNumber}
   * @returns {Promise} - 返回注册结果
   */
  async register(registerData) {
    try {
      const response = await request.post('/user/register', registerData)
      return response.data
    } catch (error) {
      console.error('注册请求失败:', error)

      // 处理不同类型的错误
      if (error.response) {
        // 服务器返回了错误状态码
        return {
          success: false,
          message: error.response.data?.message || '注册失败'
        }
      } else if (error.request) {
        // 请求发送了但没有收到响应
        return {
          success: false,
          message: '网络连接失败，请检查网络或后端服务'
        }
      } else {
        // 其他错误
        return {
          success: false,
          message: '请求发送失败'
        }
      }
    }
  },

  /**
   * 获取用户信息
   * @param {number} userId - 用户ID
   * @returns {Promise} - 返回用户信息
   */
  async getUserInfo(userId) {
    try {
      const response = await request.get(`/user/info/${userId}`)
      return response.data
    } catch (error) {
      console.error('获取用户信息失败:', error)
      throw error
    }
  },

  /**
   * 验证用户登录状态
   * @param {string} token - 用户token
   * @returns {Promise} - 返回验证结果
   */
  async validateToken(token) {
    try {
      const response = await request.post('/user/validate', { token })
      return response.data
    } catch (error) {
      console.error('Token验证失败:', error)
      return { success: false, message: 'Token验证失败' }
    }
  },

  /**
   * 用户登出
   * @param {string} token - 用户token
   * @returns {Promise} - 返回登出结果
   */
  async logout(token) {
    try {
      const response = await request.post('/user/logout', { token })
      return response.data
    } catch (error) {
      console.error('登出请求失败:', error)
      // 即使登出请求失败，也返回成功，因为前端可以清除本地状态
      return { success: true, message: '已登出' }
    }
  }
}
