package com.xzm.xzm_interview_helper.serveragent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerApprovalServiceTest {
    private ServerAgentRepository repository;
    private ServerApprovalService service;

    @BeforeEach
    void setUp() {
        repository = mock(ServerAgentRepository.class);
        ServerAgentProperties properties = new ServerAgentProperties();
        properties.setApprovalTtlSeconds(120);
        service = new ServerApprovalService(
                repository,
                properties,
                new CredentialRedactor(Map.of()),
                new ObjectMapper()
        );
    }

    @Test
    void tokenIsBoundToActionAndNotPartOfItsFingerprint() {
        ServerToolRequest request = command("touch /tmp/example");
        String first = service.actionHash(request);
        request.setApprovalRequestId("other-request");
        request.setApprovalToken("other-token");

        assertThat(service.actionHash(request)).isEqualTo(first);

        String approvalId = service.requestApproval(7, request);
        assertThat(approvalId).isNotBlank();
        verify(repository).createApproval(
                anyString(),
                anyInt(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void approveReturnsOpaqueTokenAndAgentRestoresExactStoredAction() {
        when(repository.approve(anyString(), anyInt(), anyString())).thenReturn(true);
        Map<String, Object> approved = service.approve(9, "approval-id", true);
        assertThat(approved.get("approvalToken")).isInstanceOf(String.class);
        assertThat((String) approved.get("approvalToken")).isNotBlank();

        when(repository.consumeAndLoad(anyString(), anyInt(), anyString()))
                .thenReturn(Optional.of("{\"tool\":\"SERVICE\",\"service\":\"nginx\",\"action\":\"restart\"}"));
        ServerToolRequest restored = service.consumeForAgent(9, "approval-id", "opaque-token");
        assertThat(restored.getTool()).isEqualTo(ServerToolName.SERVICE);
        assertThat(restored.getService()).isEqualTo("nginx");
        assertThat(restored.getAction()).isEqualTo("restart");
        assertThat(restored.getApprovalToken()).isNull();
    }

    @Test
    void commandApprovalKeepsTheDangerousSuffixVisible() {
        String suffix = " && rm -rf /www/wwwroot/example";
        String command = "printf x".repeat(100) + suffix;

        String summary = service.actionSummary(command(command));

        assertThat(summary).startsWith("command: ");
        assertThat(summary).endsWith(suffix);
        assertThat(summary).hasSizeLessThanOrEqualTo(2_000);
    }

    @Test
    void writeAndSiteApprovalsFingerprintAndPreviewBothEndsOfContent() {
        String content = "START-" + "x".repeat(5_000) + "-DANGEROUS-END";
        ServerToolRequest write = new ServerToolRequest();
        write.setTool(ServerToolName.WRITE_FILE);
        write.setPath("/www/wwwroot/example/index.html");
        write.setContent(content);

        String writeSummary = service.actionSummary(write);

        assertThat(writeSummary)
                .contains("write file: /www/wwwroot/example/index.html")
                .contains("content: " + content.length() + " chars")
                .contains("sha256=")
                .contains("START-")
                .contains("-DANGEROUS-END")
                .contains("preview chars omitted");
        assertThat(writeSummary).hasSizeLessThanOrEqualTo(2_000);

        write.setTool(ServerToolName.CREATE_SITE);
        write.setSiteName("review-site");
        String siteSummary = service.actionSummary(write);
        assertThat(siteSummary)
                .contains("create site: review-site")
                .contains("START-")
                .contains("-DANGEROUS-END");
    }

    private ServerToolRequest command(String command) {
        ServerToolRequest request = new ServerToolRequest();
        request.setTool(ServerToolName.COMMAND);
        request.setCommand(command);
        return request;
    }
}
