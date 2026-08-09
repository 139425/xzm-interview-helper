package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.model.entity.HelperUser;
import com.xzm.xzm_interview_helper.service.HelperUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员控制器
 * 提供用户管理相关接口（仅管理员可访问）
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Autowired
    private HelperUserService helperUserService;

    /**
     * 获取所有用户列表
     * @return 用户列表
     */
    @GetMapping("/users")
    public Map<String, Object> getAllUsers() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<HelperUser> users = helperUserService.list();
            
            // 移除密码字段
            users.forEach(user -> user.setPassword(null));
            
            response.put("code", 200);
            response.put("message", "获取用户列表成功");
            response.put("data", users);
            
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            response.put("code", 500);
            response.put("message", "获取用户列表失败：" + e.getMessage());
            response.put("data", null);
        }
        
        return response;
    }

    /**
     * 更新用户类型
     * @param request 更新请求
     * @return 更新结果
     */
    @PostMapping("/updateUserType")
    public Map<String, Object> updateUserType(@RequestBody UpdateUserTypeRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            HelperUser user = helperUserService.getById(request.getUserId());
            
            if (user == null) {
                response.put("code", 404);
                response.put("message", "用户不存在");
                response.put("data", null);
                return response;
            }
            
            user.setUser_type(request.getUserType());
            boolean success = helperUserService.updateById(user);
            
            if (success) {
                user.setPassword(null);
                response.put("code", 200);
                response.put("message", "更新用户类型成功");
                response.put("data", user);
            } else {
                response.put("code", 500);
                response.put("message", "更新用户类型失败");
                response.put("data", null);
            }
            
        } catch (Exception e) {
            log.error("更新用户类型失败", e);
            response.put("code", 500);
            response.put("message", "更新用户类型失败：" + e.getMessage());
            response.put("data", null);
        }
        
        return response;
    }

    /**
     * 删除用户
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/deleteUser/{userId}")
    public Map<String, Object> deleteUser(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = helperUserService.removeById(userId);
            
            if (success) {
                response.put("code", 200);
                response.put("message", "删除用户成功");
                response.put("data", null);
            } else {
                response.put("code", 500);
                response.put("message", "删除用户失败");
                response.put("data", null);
            }
            
        } catch (Exception e) {
            log.error("删除用户失败", e);
            response.put("code", 500);
            response.put("message", "删除用户失败：" + e.getMessage());
            response.put("data", null);
        }
        
        return response;
    }

    /**
     * 重置用户密码
     * @param request 重置密码请求
     * @return 重置结果
     */
    @PostMapping("/resetPassword")
    public Map<String, Object> resetPassword(@RequestBody ResetPasswordRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            HelperUser user = helperUserService.getById(request.getUserId());
            
            if (user == null) {
                response.put("code", 404);
                response.put("message", "用户不存在");
                response.put("data", null);
                return response;
            }
            
            // 设置新密码（实际应用中应该加密）
            user.setPassword(request.getNewPassword());
            boolean success = helperUserService.updateById(user);
            
            if (success) {
                response.put("code", 200);
                response.put("message", "重置密码成功");
                response.put("data", null);
            } else {
                response.put("code", 500);
                response.put("message", "重置密码失败");
                response.put("data", null);
            }
            
        } catch (Exception e) {
            log.error("重置密码失败", e);
            response.put("code", 500);
            response.put("message", "重置密码失败：" + e.getMessage());
            response.put("data", null);
        }
        
        return response;
    }

    /**
     * 更新用户类型请求类
     */
    public static class UpdateUserTypeRequest {
        private Long userId;
        private String userType;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUserType() {
            return userType;
        }

        public void setUserType(String userType) {
            this.userType = userType;
        }
    }

    /**
     * 重置密码请求类
     */
    public static class ResetPasswordRequest {
        private Long userId;
        private String newPassword;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }
}