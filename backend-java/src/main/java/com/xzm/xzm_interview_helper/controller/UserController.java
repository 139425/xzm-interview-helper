package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.model.entity.HelperUser;
import com.xzm.xzm_interview_helper.service.AuthenticationAttemptGate;
import com.xzm.xzm_interview_helper.service.ClientAddressResolver;
import com.xzm.xzm_interview_helper.service.HelperUserService;
import com.xzm.xzm_interview_helper.service.InMemoryAdmissionGate;
import com.xzm.xzm_interview_helper.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 * 提供用户登录注册相关接口
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final HelperUserService helperUserService;
    private final JwtUtil jwtUtil;
    private final AuthenticationAttemptGate authenticationAttemptGate;
    private final ClientAddressResolver clientAddressResolver;

    /**
     * 用户登录
     * @param loginRequest 登录请求参数
     * @return 登录结果，包含JWT Token
     */
    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest servletRequest
    ) {
        try (InMemoryAdmissionGate.Permit ignored = acquireLoginPermit(servletRequest)) {
            Map<String, Object> response = new HashMap<>();
            try {
                HelperUser user = helperUserService.login(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                );

                // 生成JWT Token (将Integer转换为Long)
                String token = jwtUtil.generateToken(
                        user.getUser_id().longValue(),
                        user.getUsername(),
                        user.getUser_type()
                );

                // 构造返回数据
                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.getId());
                userData.put("user_id", user.getUser_id());
                userData.put("username", user.getUsername());
                userData.put("user_type", user.getUser_type());

                response.put("code", 200);
                response.put("message", "登录成功");
                response.put("data", userData);
                response.put("token", token);
            } catch (Exception exception) {
                response.put("code", 400);
                response.put("message", exception.getMessage());
                response.put("data", null);
            }
            return response;
        }
    }

    /**
     * 用户注册
     * @param registerRequest 注册请求参数
     * @return 注册结果
     */
    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestBody RegisterRequest registerRequest,
            HttpServletRequest servletRequest
    ) {
        try (InMemoryAdmissionGate.Permit ignored = acquireRegisterPermit(servletRequest)) {
            Map<String, Object> response = new HashMap<>();
            try {
                HelperUser user = helperUserService.register(
                        registerRequest.getUsername(),
                        registerRequest.getPassword(),
                        registerRequest.getCaptcha()
                );

                // 构造返回数据
                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.getId());
                userData.put("user_id", user.getUser_id());
                userData.put("username", user.getUsername());
                userData.put("user_type", user.getUser_type());

                response.put("code", 200);
                response.put("message", "注册成功");
                response.put("data", userData);
            } catch (Exception exception) {
                response.put("code", 400);
                response.put("message", exception.getMessage());
                response.put("data", null);
            }
            return response;
        }
    }

    private InMemoryAdmissionGate.Permit acquireLoginPermit(HttpServletRequest request) {
        try {
            return authenticationAttemptGate.acquireLogin(clientAddressResolver.resolve(request));
        } catch (InMemoryAdmissionGate.RejectedException exception) {
            throw tooManyAuthenticationAttempts(exception, "登录");
        }
    }

    private InMemoryAdmissionGate.Permit acquireRegisterPermit(HttpServletRequest request) {
        try {
            return authenticationAttemptGate.acquireRegister(clientAddressResolver.resolve(request));
        } catch (InMemoryAdmissionGate.RejectedException exception) {
            throw tooManyAuthenticationAttempts(exception, "注册");
        }
    }

    private ResponseStatusException tooManyAuthenticationAttempts(
            InMemoryAdmissionGate.RejectedException exception,
            String operation
    ) {
        String message = switch (exception.getReason()) {
            case KEY_BUSY -> operation + "请求正在处理中，请稍后再试";
            case RATE_LIMITED -> operation + "尝试过于频繁，请稍后再试";
            case GLOBAL_BUSY, TRACKING_CAPACITY -> "认证服务繁忙，请稍后再试";
        };
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message, exception);
    }

    /**
     * 登录请求参数类
     */
    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * 注册请求参数类
     */
    public static class RegisterRequest {
        private String username;
        private String password;
        private String captcha;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }
    }
}
