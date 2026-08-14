package com.xzm.xzm_interview_helper.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigDispatcherTest.DispatchController.class)
@ContextConfiguration(classes = {
        SecurityConfig.class,
        SecurityConfigDispatcherTest.DispatchController.class
})
class SecurityConfigDispatcherTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passRequestsThroughJwtFilterMock() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void permitsInternalAsyncRedispatchAfterTheInitialRequestWasAuthenticated()
            throws Exception {
        mockMvc.perform(get("/security-dispatch-test")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ASYNC);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void stillProtectsAnOrdinaryExternalRequest() throws Exception {
        mockMvc.perform(get("/security-dispatch-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permitsPatchCorsPreflightForInlineApplicationStatusUpdates() throws Exception {
        mockMvc.perform(options("/api/applications/77/status")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
    }

    @RestController
    static class DispatchController {

        @GetMapping("/security-dispatch-test")
        String ok() {
            return "ok";
        }
    }
}
