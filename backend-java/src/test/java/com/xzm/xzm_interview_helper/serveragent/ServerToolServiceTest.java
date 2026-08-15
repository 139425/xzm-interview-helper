package com.xzm.xzm_interview_helper.serveragent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerToolServiceTest {
    @TempDir
    Path root;

    private ServerApprovalService approvals;
    private ServerCommandRunner runner;
    private ServerAgentRepository repository;
    private ServerToolService service;

    @BeforeEach
    void setUp() {
        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setEnabled(true);
        properties.setAllowedRoots(List.of(root.toString()));
        properties.setWorkingDirectory(root.toString());
        properties.setSiteRoot(root.toString());
        CredentialRedactor redactor = new CredentialRedactor(Map.of());
        approvals = mock(ServerApprovalService.class);
        when(approvals.actionSummary(any())).thenAnswer(invocation -> {
            ServerToolRequest request = invocation.getArgument(0);
            return request.getTool().name() + ": " + (request.getPath() == null ? "" : request.getPath());
        });
        runner = mock(ServerCommandRunner.class);
        repository = mock(ServerAgentRepository.class);
        service = new ServerToolService(
                properties,
                new ServerCommandPolicy(),
                new ServerPathPolicy(properties),
                runner,
                new ServerStatusService(properties),
                approvals,
                repository,
                redactor,
                new ObjectMapper()
        );
    }

    @Test
    void blocksCredentialCommandsBeforeStartingAProcess() {
        ServerToolRequest request = command("cat /root/.ssh/id_rsa");

        ToolExecutionResponse response = service.execute(1, request);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.risk()).isEqualTo(ServerRisk.BLOCKED);
        verify(runner, never()).runShell(any(), any());
    }

    @Test
    void mutatingFileRequiresThenConsumesExactApproval() throws Exception {
        Path target = root.resolve("site/index.html");
        ServerToolRequest request = new ServerToolRequest();
        request.setTool(ServerToolName.WRITE_FILE);
        request.setPath(target.toString());
        request.setContent("hello");
        when(approvals.requestApproval(3, request)).thenReturn("approval-1");

        ToolExecutionResponse pending = service.execute(3, request);
        assertThat(pending.status()).isEqualTo("APPROVAL_REQUIRED");
        assertThat(pending.approvalRequestId()).isEqualTo("approval-1");
        assertThat(pending.actionSummary()).contains("WRITE_FILE");
        assertThat(Files.exists(target)).isFalse();

        request.setApprovalRequestId("approval-1");
        request.setApprovalToken("single-use-token");
        when(approvals.consume(3, request)).thenReturn(true);
        ToolExecutionResponse completed = service.execute(3, request);

        assertThat(completed.status()).isEqualTo("EXECUTED");
        assertThat(Files.readString(target)).isEqualTo("hello");
    }

    @Test
    void invalidApprovalCannotMutateAnything() {
        ServerToolRequest request = new ServerToolRequest();
        request.setTool(ServerToolName.WRITE_FILE);
        request.setPath(root.resolve("blocked.txt").toString());
        request.setContent("blocked");
        request.setApprovalRequestId("approval-1");
        request.setApprovalToken("invalid-token");
        when(approvals.consume(3, request)).thenReturn(false);

        assertThatThrownBy(() -> service.execute(3, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(Files.exists(root.resolve("blocked.txt"))).isFalse();
    }

    @Test
    void safeCommandRunsWithConfiguredBoundsAndIsAudited() {
        when(runner.runShell("uptime", 5)).thenReturn(
                new ServerCommandRunner.CommandResult(0, "up 4 days", 12, false, false)
        );
        ServerToolRequest request = command("uptime");
        request.setTimeoutSeconds(5);

        ToolExecutionResponse response = service.execute(4, request);

        assertThat(response.status()).isEqualTo("EXECUTED");
        assertThat(response.output()).isEqualTo("up 4 days");
        verify(repository).appendAudit(
                anyInt(), any(), any(), any(), any(), any(), any(), anyLong(), any()
        );
    }

    @Test
    void approvedSiteCreationReturnsItsPublicUrl() {
        ServerToolRequest request = new ServerToolRequest();
        request.setTool(ServerToolName.CREATE_SITE);
        request.setSiteName("demo.example");
        request.setContent("<h1>Demo</h1>");

        ToolExecutionResponse response = service.executeApproved(4, request);

        assertThat(response.status()).isEqualTo("EXECUTED");
        assertThat(response.output()).contains("Public URL: /agent-sites/demo.example/");
        assertThat(root.resolve("demo.example/index.html")).hasContent("<h1>Demo</h1>");
    }

    private ServerToolRequest command(String command) {
        ServerToolRequest request = new ServerToolRequest();
        request.setTool(ServerToolName.COMMAND);
        request.setCommand(command);
        return request;
    }
}
