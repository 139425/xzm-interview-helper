package com.xzm.xzm_interview_helper.serveragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/**
 * A bounded ReAct orchestrator. The model proposes one typed action at a time; Java owns all
 * authorization, risk classification, approval, execution, limits, and audit decisions.
 */
@Service
public class ServerAgentService {
    private static final String SYSTEM_PROMPT = """
            You are an administrator's general-purpose server operations agent.
            Use a concise ReAct loop: inspect the objective and previous observations, then choose exactly one action.
            Return exactly one JSON object and no markdown:
            {"rationale":"brief reason","action":"SERVER_STATUS|COMMAND|READ_FILE|WRITE_FILE|CREATE_SITE|SERVICE|FINISH","arguments":{},"answer":"only for FINISH"}
            Argument schemas:
            COMMAND {"command":"...","timeoutSeconds":30}
            READ_FILE {"path":"absolute path"}
            WRITE_FILE {"path":"absolute path","content":"..."}
            CREATE_SITE {"siteName":"directory or domain","content":"index html"}
            SERVICE {"service":"unit name","action":"status|is-active|is-enabled|start|stop|restart|reload|enable|disable"}
            SERVER_STATUS {}
            FINISH {}
            Prefer typed file/service tools over shell commands. Never request, read, print, copy, transmit, or infer passwords,
            tokens, private keys, environment secrets, SSH material, or cloud credentials. Never claim an action succeeded
            unless its observation says it executed successfully. Mutating actions will pause for human approval.
            """;

    private final ServerAgentProperties properties;
    private final ServerAgentModelGateway modelGateway;
    private final ServerToolService toolService;
    private final ServerApprovalService approvalService;
    private final ServerAgentRepository repository;
    private final CredentialRedactor redactor;
    private final ObjectMapper objectMapper;
    private final Semaphore admission;

    public ServerAgentService(
            ServerAgentProperties properties,
            ServerAgentModelGateway modelGateway,
            ServerToolService toolService,
            ServerApprovalService approvalService,
            ServerAgentRepository repository,
            CredentialRedactor redactor,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.modelGateway = modelGateway;
        this.toolService = toolService;
        this.approvalService = approvalService;
        this.repository = repository;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
        this.admission = new Semaphore(Math.max(1, properties.getMaxConcurrentAgents()));
    }

    public AgentRunResponse run(int userId, AgentRunRequest request) {
        requireEnabled();
        if (!admission.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The server agent is already running");
        }
        long started = System.nanoTime();
        List<AgentRunResponse.AgentStep> steps = new ArrayList<>();
        try {
            int maxSteps = boundedSteps(request.getMaxSteps());
            int nextStep = 1;
            boolean hasApprovalId = request.getApprovalRequestId() != null && !request.getApprovalRequestId().isBlank();
            boolean hasApprovalToken = request.getApprovalToken() != null && !request.getApprovalToken().isBlank();
            if (hasApprovalId != hasApprovalToken) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both approval fields are required to resume an agent run");
            }
            if (hasApprovalId) {
                ServerToolRequest approvedAction = approvalService.consumeForAgent(
                        userId,
                        request.getApprovalRequestId(),
                        request.getApprovalToken()
                );
                ToolExecutionResponse approvedResult = toolService.executeApproved(userId, approvedAction);
                steps.add(new AgentRunResponse.AgentStep(
                        nextStep++,
                        "Execute the exact action that the administrator approved.",
                        approvedAction.getTool().name(),
                        observation(approvedResult),
                        approvedResult.status()
                ));
            }
            for (int index = nextStep; index <= maxSteps; index++) {
                String prompt = userPrompt(request.getObjective(), steps);
                Decision decision = parseDecision(modelGateway.decide(SYSTEM_PROMPT, prompt));
                if ("FINISH".equals(decision.action())) {
                    String answer = decision.answer().isBlank()
                            ? "The requested server task is complete."
                            : redactor.redact(decision.answer());
                    steps.add(new AgentRunResponse.AgentStep(
                            index,
                            redactor.redact(decision.rationale()),
                            "FINISH",
                            answer,
                            "COMPLETED"
                    ));
                    auditAgent(userId, request.getObjective(), "COMPLETED", started, answer);
                    return new AgentRunResponse("COMPLETED", answer, List.copyOf(steps), null);
                }

                ServerToolRequest toolRequest = toToolRequest(decision);
                ToolExecutionResponse result;
                try {
                    result = toolService.execute(userId, toolRequest);
                } catch (ResponseStatusException exception) {
                    String observation = "REJECTED: " + redactor.redact(exception.getReason());
                    steps.add(new AgentRunResponse.AgentStep(
                            index,
                            redactor.redact(decision.rationale()),
                            decision.action(),
                            observation,
                            "REJECTED"
                    ));
                    continue;
                }
                String observation = observation(result);
                steps.add(new AgentRunResponse.AgentStep(
                        index,
                        redactor.redact(decision.rationale()),
                        decision.action(),
                        observation,
                        result.status()
                ));
                if ("APPROVAL_REQUIRED".equals(result.status())) {
                    auditAgent(userId, request.getObjective(), "AWAITING_APPROVAL", started,
                            "Approval request " + result.approvalRequestId());
                    return new AgentRunResponse(
                            "AWAITING_APPROVAL",
                            "A mutating action is waiting for your confirmation.",
                            List.copyOf(steps),
                            result
                    );
                }
            }
            String answer = "The agent reached its configured step limit before it could verify completion.";
            auditAgent(userId, request.getObjective(), "STEP_LIMIT", started, answer);
            return new AgentRunResponse("STEP_LIMIT", answer, List.copyOf(steps), null);
        } finally {
            admission.release();
        }
    }

    Decision parseDecision(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            String action = text(node, "action").toUpperCase(Locale.ROOT);
            if (!action.equals("FINISH")) {
                ServerToolName.valueOf(action);
            }
            JsonNode arguments = node.path("arguments");
            if (!arguments.isObject() && !action.equals("FINISH")) {
                throw new IllegalArgumentException("arguments must be an object");
            }
            return new Decision(
                    clip(text(node, "rationale"), 1_000),
                    action,
                    arguments,
                    clip(text(node, "answer"), 8_000)
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server agent returned an invalid action");
        }
    }

    private ServerToolRequest toToolRequest(Decision decision) {
        ServerToolRequest request = new ServerToolRequest();
        request.setTool(ServerToolName.valueOf(decision.action()));
        JsonNode arguments = decision.arguments();
        request.setCommand(optionalText(arguments, "command"));
        request.setPath(optionalText(arguments, "path"));
        request.setContent(optionalText(arguments, "content"));
        request.setService(optionalText(arguments, "service"));
        request.setAction(optionalText(arguments, "action"));
        request.setSiteName(optionalText(arguments, "siteName"));
        if (arguments.has("timeoutSeconds") && arguments.get("timeoutSeconds").canConvertToInt()) {
            request.setTimeoutSeconds(arguments.get("timeoutSeconds").intValue());
        }
        return request;
    }

    private String userPrompt(String objective, List<AgentRunResponse.AgentStep> steps) {
        StringBuilder prompt = new StringBuilder("Objective:\n")
                .append(redactor.redact(objective))
                .append("\n\nPrevious actions and observations:\n");
        if (steps.isEmpty()) {
            prompt.append("(none)");
        } else {
            for (AgentRunResponse.AgentStep step : steps) {
                prompt.append(step.step()).append(". ")
                        .append(step.action()).append(" -> ")
                        .append(step.status()).append(": ")
                        .append(step.observation()).append('\n');
            }
        }
        String value = prompt.toString();
        return value.length() <= 24_000 ? value : value.substring(value.length() - 24_000);
    }

    private String observation(ToolExecutionResponse result) {
        String output = result.output() == null ? "" : result.output();
        if (output.length() > 8_000) output = output.substring(0, 8_000) + "\n[observation truncated]";
        return result.status() + (output.isBlank() ? "" : ": " + redactor.redact(output));
    }

    private void auditAgent(int userId, String objective, String status, long started, String output) {
        repository.appendAudit(
                userId,
                "AGENT_RUN",
                clip(redactor.redact(objective), 2_000),
                ServerRisk.READ_ONLY,
                status,
                null,
                null,
                (System.nanoTime() - started) / 1_000_000,
                clip(redactor.redact(output), 20_000)
        );
    }

    private int boundedSteps(Integer requested) {
        int configured = Math.max(1, Math.min(20, properties.getMaxAgentSteps()));
        return requested == null ? configured : Math.max(1, Math.min(configured, requested));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Server agent is disabled; set SERVER_AGENT_ENABLED=true on the host");
        }
    }

    private String extractJson(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty response");
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("missing JSON object");
        return raw.substring(start, end + 1);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").strip();
    }

    private String optionalText(JsonNode node, String field) {
        String value = text(node, field);
        return value.isEmpty() ? null : value;
    }

    private String clip(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    record Decision(String rationale, String action, JsonNode arguments, String answer) {
    }
}
