import request from '@/utils/request'

export const adminApi = {
  /**
   * 获取所有用户列表
   * @returns {Promise} - 返回用户列表
   */
  async getAllUsers() {
    try {
      const response = await request.get('/admin/users')
      return response.data
    } catch (error) {
      console.error('获取用户列表失败:', error)
      throw error
    }
  },

  /**
   * 更新用户类型
   * @param {number} userId - 用户ID
   * @param {string} userType - 用户类型
   * @returns {Promise} - 返回更新结果
   */
  async updateUserType(userId, userType) {
    try {
      const response = await request.post('/admin/updateUserType', {
        userId,
        userType
      })
      return response.data
    } catch (error) {
      console.error('更新用户类型失败:', error)
      throw error
    }
  },

  /**
   * 删除用户
   * @param {number} userId - 用户ID
   * @returns {Promise} - 返回删除结果
   */
  async deleteUser(userId) {
    try {
      const response = await request.delete(`/admin/deleteUser/${userId}`)
      return response.data
    } catch (error) {
      console.error('删除用户失败:', error)
      throw error
    }
  },

  /**
   * 重置用户密码
   * @param {number} userId - 用户ID
   * @param {string} newPassword - 新密码
   * @returns {Promise} - 返回重置结果
   */
  async resetPassword(userId, newPassword) {
    try {
      const response = await request.post('/admin/resetPassword', {
        userId,
        newPassword
      })
      return response.data
    } catch (error) {
      console.error('重置密码失败:', error)
      throw error
    }
  }
}
