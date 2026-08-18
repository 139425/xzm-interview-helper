package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.model.entity.HelperUser;
import com.xzm.xzm_interview_helper.service.AuthenticationAttemptGate;
import com.xzm.xzm_interview_helper.service.AuthenticationVerificationService;
import com.xzm.xzm_interview_helper.service.ClientAddressResolver;
import com.xzm.xzm_interview_helper.service.HelperUserService;
import com.xzm.xzm_interview_helper.service.InMemoryAdmissionGate;
import com.xzm.xzm_interview_helper.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
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
    private final AuthenticationVerificationService verificationService;

    @GetMapping("/verification/config")
    public Map<String, Object> verificationConfig() {
        return success(Map.of(
                "loginMode", "SLIDER",
                "registrationMode", verificationService.registrationMode().name()
        ), "验证配置加载成功");
    }

    @PostMapping("/verification/slider")
    public Map<String, Object> createSlider(HttpServletRequest request) {
        String address = clientAddressResolver.resolve(request);
        try (InMemoryAdmissionGate.Permit ignored = authenticationAttemptGate.acquireChallenge(address)) {
            return success(verificationService.issueSlider(address), "滑块验证已创建");
        } catch (InMemoryAdmissionGate.RejectedException exception) {
            throw tooManyAuthenticationAttempts(exception, "验证");
        }
    }

    @PostMapping("/verification/slider/verify")
    public Map<String, Object> verifySlider(
            @RequestBody SliderVerificationRequest body,
            HttpServletRequest request
    ) {
        try {
            return success(
                    verificationService.verifySlider(
                            body.getChallengeId(),
                            body.getSliderValue(),
                            clientAddressResolver.resolve(request)
                    ),
                    "人机验证通过"
            );
        } catch (RuntimeException exception) {
            return failure(exception.getMessage());
        }
    }

    @PostMapping("/verification/captcha")
    public Map<String, Object> createCaptcha(HttpServletRequest request) {
        String address = clientAddressResolver.resolve(request);
        try (InMemoryAdmissionGate.Permit ignored = authenticationAttemptGate.acquireChallenge(address)) {
            return success(verificationService.issueCaptcha(address), "图片验证码已创建");
        } catch (InMemoryAdmissionGate.RejectedException exception) {
            throw tooManyAuthenticationAttempts(exception, "验证");
        }
    }

    @PostMapping("/verification/email-code")
    public Map<String, Object> sendEmailCode(
            @RequestBody EmailCodeRequest body,
            HttpServletRequest request
    ) {
        String address = clientAddressResolver.resolve(request);
        try (InMemoryAdmissionGate.Permit ignored = authenticationAttemptGate.acquireEmail(address)) {
            try {
                return success(
                        verificationService.sendRegistrationCode(
                                body.getEmail(),
                                body.getVerificationToken(),
                                address
                        ),
                        "验证码已发送，请检查邮箱"
                );
            } catch (RuntimeException exception) {
                return failure(exception.getMessage());
            }
        } catch (InMemoryAdmissionGate.RejectedException exception) {
            throw tooManyAuthenticationAttempts(exception, "验证码发送");
        }
    }

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
                verificationService.consumeSliderToken(
                        loginRequest.getVerificationToken(),
                        clientAddressResolver.resolve(servletRequest)
                );
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
                if (verificationService.registrationMode()
                        == AuthenticationVerificationService.RegistrationMode.EMAIL) {
                    verificationService.verifyRegistrationCode(
                            registerRequest.getEmail(),
                            registerRequest.getEmailCode()
                    );
                } else {
                    verificationService.verifyCaptcha(
                            registerRequest.getCaptchaId(),
                            registerRequest.getCaptchaAnswer(),
                            clientAddressResolver.resolve(servletRequest)
                    );
                }
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

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 400);
        response.put("message", StringUtils.hasText(message) ? message : "验证失败");
        response.put("data", null);
        return response;
    }

    /**
     * 登录请求参数类
     */
    public static class LoginRequest {
        private String username;
        private String password;
        private String verificationToken;

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

        public String getVerificationToken() {
            return verificationToken;
        }

        public void setVerificationToken(String verificationToken) {
            this.verificationToken = verificationToken;
        }
    }

    /**
     * 注册请求参数类
     */
    public static class RegisterRequest {
        private String username;
        private String password;
        private String captcha;
        private String email;
        private String emailCode;
        private String captchaId;
        private String captchaAnswer;

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

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getEmailCode() { return emailCode; }
        public void setEmailCode(String emailCode) { this.emailCode = emailCode; }
        public String getCaptchaId() { return captchaId; }
        public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
        public String getCaptchaAnswer() { return captchaAnswer; }
        public void setCaptchaAnswer(String captchaAnswer) { this.captchaAnswer = captchaAnswer; }
    }

    public static class SliderVerificationRequest {
        private String challengeId;
        private int sliderValue;
        public String getChallengeId() { return challengeId; }
        public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
        public int getSliderValue() { return sliderValue; }
        public void setSliderValue(int sliderValue) { this.sliderValue = sliderValue; }
    }

    public static class EmailCodeRequest {
        private String email;
        private String verificationToken;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getVerificationToken() { return verificationToken; }
        public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    }
}
