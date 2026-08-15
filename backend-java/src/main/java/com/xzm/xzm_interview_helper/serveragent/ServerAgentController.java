package com.xzm.xzm_interview_helper.serveragent;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Administrator-only HTTP boundary. SecurityConfig protects every /admin/** request. */
@RestController
@RequestMapping("/admin/server-agent")
public class ServerAgentController {
    private final ServerStatusService statusService;
    private final ServerToolService toolService;
    private final ServerApprovalService approvalService;
    private final ServerAgentService agentService;
    private final ServerAgentRepository repository;

    public ServerAgentController(
            ServerStatusService statusService,
            ServerToolService toolService,
            ServerApprovalService approvalService,
            ServerAgentService agentService,
            ServerAgentRepository repository
    ) {
        this.statusService = statusService;
        this.toolService = toolService;
        this.approvalService = approvalService;
        this.agentService = agentService;
        this.repository = repository;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return ok("Server status loaded", statusService.status());
    }

    @PostMapping("/command")
    public Map<String, Object> command(
            @Valid @RequestBody CommandRequest command,
            HttpServletRequest httpRequest
    ) {
        ServerToolRequest request = new ServerToolRequest();
        request.setTool(ServerToolName.COMMAND);
        request.setCommand(command.getCommand());
        request.setTimeoutSeconds(command.getTimeoutSeconds());
        request.setApprovalRequestId(command.getApprovalRequestId());
        request.setApprovalToken(command.getApprovalToken());
        return ok("Server command handled", toolService.execute(userId(httpRequest), request));
    }

    @PostMapping("/tools")
    public Map<String, Object> tool(
            @Valid @RequestBody ServerToolRequest request,
            HttpServletRequest httpRequest
    ) {
        return ok("Server tool handled", toolService.execute(userId(httpRequest), request));
    }

    @PostMapping("/approvals/{approvalRequestId}/approve")
    public Map<String, Object> approve(
            @PathVariable String approvalRequestId,
            @Valid @RequestBody ApprovalConfirmRequest request,
            HttpServletRequest httpRequest
    ) {
        if (!approvalRequestId.matches("[0-9a-fA-F-]{36}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid approval request id");
        }
        return ok(
                "Action approved once",
                approvalService.approve(userId(httpRequest), approvalRequestId, request.isConfirm())
        );
    }

    @PostMapping("/run")
    public Map<String, Object> run(
            @Valid @RequestBody AgentRunRequest request,
            HttpServletRequest httpRequest
    ) {
        return ok("Server agent run handled", agentService.run(userId(httpRequest), request));
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest httpRequest
    ) {
        return ok("Server audit loaded", repository.findRecentAudits(userId(httpRequest), limit));
    }

    private int userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Number number)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is missing");
        }
        return number.intValue();
    }

    private Map<String, Object> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    @Getter
    @Setter
    public static class CommandRequest {
        @NotBlank
        @Size(max = 1_000)
        private String command;
        private Integer timeoutSeconds;
        @Size(max = 36)
        private String approvalRequestId;
        @Size(max = 256)
        private String approvalToken;
    }

    @Getter
    @Setter
    public static class ApprovalConfirmRequest {
        private boolean confirm;
    }
}
