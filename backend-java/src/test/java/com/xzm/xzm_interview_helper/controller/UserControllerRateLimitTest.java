package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.service.AuthenticationAttemptGate;
import com.xzm.xzm_interview_helper.service.AuthenticationVerificationService;
import com.xzm.xzm_interview_helper.service.ClientAddressResolver;
import com.xzm.xzm_interview_helper.service.HelperUserService;
import com.xzm.xzm_interview_helper.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerRateLimitTest {

    @Mock
    private HelperUserService helperUserService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationVerificationService verificationService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(
                helperUserService,
                jwtUtil,
                new AuthenticationAttemptGate(),
                new ClientAddressResolver(""),
                verificationService
        );
    }

    @Test
    void eleventhLoginAttemptFromSameDirectAddressReturns429() {
        when(helperUserService.login(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("invalid credentials"));
        UserController.LoginRequest login = new UserController.LoginRequest();
        login.setUsername("candidate");
        login.setPassword("wrong");

        for (int attempt = 0; attempt < 10; attempt++) {
            Map<String, Object> response = controller.login(
                    login,
                    request("198.51.100.10", "203.0.113." + attempt)
            );
            assertEquals(400, response.get("code"));
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.login(
                        login,
                        request("198.51.100.10", "203.0.113.250")
                )
        );
        assertEquals(429, exception.getStatusCode().value());
        verify(helperUserService, times(10)).login("candidate", "wrong");
    }

    @Test
    void fourthRegistrationAttemptFromSameDirectAddressReturns429() {
        when(helperUserService.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("registration rejected"));
        UserController.RegisterRequest registration = new UserController.RegisterRequest();
        registration.setUsername("candidate");
        registration.setPassword("password");
        registration.setCaptcha("captcha");

        for (int attempt = 0; attempt < 3; attempt++) {
            Map<String, Object> response = controller.register(
                    registration,
                    request("198.51.100.20", "203.0.113." + attempt)
            );
            assertEquals(400, response.get("code"));
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.register(
                        registration,
                        request("198.51.100.20", "203.0.113.250")
                )
        );
        assertEquals(429, exception.getStatusCode().value());
        verify(helperUserService, times(3))
                .register("candidate", "password", "captcha");
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
