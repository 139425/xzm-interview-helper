package com.xzm.xzm_interview_helper.serveragent;

import com.xzm.xzm_interview_helper.config.JwtAuthenticationFilter;
import com.xzm.xzm_interview_helper.config.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ServerAgentController.class)
@ContextConfiguration(classes = {SecurityConfig.class, ServerAgentController.class})
class ServerAgentControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private ServerStatusService statusService;
    @MockitoBean
    private ServerToolService toolService;
    @MockitoBean
    private ServerApprovalService approvalService;
    @MockitoBean
    private ServerAgentService agentService;
    @MockitoBean
    private ServerAgentRepository repository;

    @BeforeEach
    void configureAuthenticationFilter() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            FilterChain chain = invocation.getArgument(2);
            String role = request.getHeader("X-Test-Role");
            if (role != null) {
                request.setAttribute("userId", 42);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "tester",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        )
                );
            }
            try {
                chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void rejectsAnonymousAndOrdinaryUsersButAllowsAdministrator() throws Exception {
        when(statusService.status()).thenReturn(Map.of("executionUser", "www", "agentEnabled", true));

        mockMvc.perform(get("/admin/server-agent/status"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/server-agent/status").header("X-Test-Role", "USER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/server-agent/status").header("X-Test-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionUser").value("www"));
    }

    @Test
    void commandEndpointUsesDocumentedRequestAndResponseShape() throws Exception {
        when(toolService.execute(anyInt(), any())).thenReturn(new ToolExecutionResponse(
                "APPROVAL_REQUIRED",
                ServerToolName.COMMAND,
                ServerRisk.DANGEROUS,
                "",
                null,
                0,
                false,
                "approval-id",
                "command: touch /www/wwwroot/ready",
                "This exact action needs a second confirmation"
        ));

        mockMvc.perform(post("/admin/server-agent/command")
                        .header("X-Test-Role", "ADMIN")
                        .contentType("application/json")
                        .content("{\"command\":\"touch /www/wwwroot/ready\",\"timeoutSeconds\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.data.approvalRequestId").value("approval-id"))
                .andExpect(jsonPath("$.data.actionSummary").value("command: touch /www/wwwroot/ready"));
    }
}
