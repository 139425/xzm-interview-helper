package com.xzm.xzm_interview_helper.serveragent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerAgentServiceTest {
    private ServerAgentModelGateway gateway;
    private ServerToolService tools;
    private ServerApprovalService approvals;
    private ServerAgentService service;

    @BeforeEach
    void setUp() {
        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setEnabled(true);
        properties.setMaxAgentSteps(4);
        gateway = mock(ServerAgentModelGateway.class);
        tools = mock(ServerToolService.class);
        approvals = mock(ServerApprovalService.class);
        service = new ServerAgentService(
                properties,
                gateway,
                tools,
                approvals,
                mock(ServerAgentRepository.class),
                new CredentialRedactor(Map.of()),
                new ObjectMapper()
        );
    }

    @Test
    void performsRealObservationLoopUntilModelFinishes() {
        when(gateway.decide(anyString(), anyString())).thenReturn(
                "{\"rationale\":\"inspect first\",\"action\":\"SERVER_STATUS\",\"arguments\":{}}",
                "{\"rationale\":\"status is healthy\",\"action\":\"FINISH\",\"arguments\":{},\"answer\":\"Server is healthy\"}"
        );
        when(tools.execute(anyInt(), any())).thenReturn(new ToolExecutionResponse(
                "EXECUTED", ServerToolName.SERVER_STATUS, ServerRisk.READ_ONLY,
                "{\"uptimeSeconds\":30}", 0, 3, false, null, "read server status", "Operation completed"
        ));
        AgentRunRequest request = request("Check whether the server is healthy");

        AgentRunResponse response = service.run(5, request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.answer()).isEqualTo("Server is healthy");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).observation()).contains("uptimeSeconds");
    }

    @Test
    void dangerousModelActionPausesForHumanApproval() {
        when(gateway.decide(anyString(), anyString())).thenReturn(
                "{\"rationale\":\"publish page\",\"action\":\"WRITE_FILE\","
                        + "\"arguments\":{\"path\":\"/www/wwwroot/a.html\",\"content\":\"ok\"}}"
        );
        ToolExecutionResponse pending = ToolExecutionResponse.approvalRequired(
                ServerToolName.WRITE_FILE,
                "approval-request",
                "write_file: /www/wwwroot/a.html"
        );
        when(tools.execute(anyInt(), any())).thenReturn(pending);

        AgentRunResponse response = service.run(6, request("Publish a page"));

        assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(response.pendingApproval()).isEqualTo(pending);
        assertThat(response.steps()).singleElement().satisfies(step ->
                assertThat(step.status()).isEqualTo("APPROVAL_REQUIRED")
        );
    }

    @Test
    void approvedResumeExecutesStoredExactActionBeforeCallingModelAgain() {
        ServerToolRequest restored = new ServerToolRequest();
        restored.setTool(ServerToolName.SERVICE);
        restored.setService("nginx");
        restored.setAction("restart");
        when(approvals.consumeForAgent(8, "approval-request", "single-use-token")).thenReturn(restored);
        when(tools.executeApproved(8, restored)).thenReturn(new ToolExecutionResponse(
                "EXECUTED", ServerToolName.SERVICE, ServerRisk.DANGEROUS,
                "restarted", 0, 30, false, null, "service: restart nginx", "Operation completed"
        ));
        when(gateway.decide(anyString(), anyString())).thenReturn(
                "{\"rationale\":\"restart succeeded\",\"action\":\"FINISH\",\"arguments\":{},\"answer\":\"nginx restarted\"}"
        );
        AgentRunRequest request = request("Restart nginx");
        request.setApprovalRequestId("approval-request");
        request.setApprovalToken("single-use-token");

        AgentRunResponse response = service.run(8, request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).rationale()).contains("exact action");
        verify(tools).executeApproved(8, restored);
    }

    @Test
    void rejectsMalformedModelOutputInsteadOfExecutingIt() {
        when(gateway.decide(anyString(), anyString())).thenReturn("run rm now");

        assertThatThrownBy(() -> service.run(2, request("do something")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502");
    }

    private AgentRunRequest request(String objective) {
        AgentRunRequest request = new AgentRunRequest();
        request.setObjective(objective);
        request.setMaxSteps(4);
        return request;
    }
}
