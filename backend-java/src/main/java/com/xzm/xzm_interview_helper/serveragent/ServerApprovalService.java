package com.xzm.xzm_interview_helper.serveragent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ServerApprovalService {
    private final ServerAgentRepository repository;
    private final ServerAgentProperties properties;
    private final CredentialRedactor redactor;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public ServerApprovalService(
            ServerAgentRepository repository,
            ServerAgentProperties properties,
            CredentialRedactor redactor,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.properties = properties;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
    }

    public String requestApproval(int userId, ServerToolRequest request) {
        String id = UUID.randomUUID().toString();
        repository.createApproval(
                id,
                userId,
                request.getTool(),
                actionHash(request),
                actionSummary(request),
                actionPayload(request),
                LocalDateTime.now().plusSeconds(Math.max(30, properties.getApprovalTtlSeconds()))
        );
        return id;
    }

    public Map<String, Object> approve(int userId, String approvalId, boolean confirmed) {
        if (!confirmed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit confirmation is required");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (!repository.approve(approvalId, userId, sha256(token))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval is missing, expired, or already handled");
        }
        return Map.of(
                "approvalRequestId", approvalId,
                "approvalToken", token,
                "expiresInSeconds", Math.max(30, properties.getApprovalTtlSeconds())
        );
    }

    public boolean consume(int userId, ServerToolRequest request) {
        if (request.getApprovalRequestId() == null || request.getApprovalToken() == null) {
            return false;
        }
        return repository.consume(
                request.getApprovalRequestId(),
                userId,
                actionHash(request),
                sha256(request.getApprovalToken())
        );
    }

    public ServerToolRequest consumeForAgent(int userId, String approvalId, String approvalToken) {
        if (approvalId == null || approvalToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both approval fields are required");
        }
        String payload = repository.consumeAndLoad(approvalId, userId, sha256(approvalToken))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Approval is invalid, expired, used, or belongs to another administrator"
                ));
        try {
            ServerToolRequest request = objectMapper.readValue(payload, ServerToolRequest.class);
            request.setApprovalRequestId(null);
            request.setApprovalToken(null);
            return request;
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approved action payload is unavailable");
        }
    }

    String actionHash(ServerToolRequest request) {
        try {
            return sha256(objectMapper.writeValueAsString(canonicalAction(request)));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to fingerprint the requested action");
        }
    }

    private String actionPayload(ServerToolRequest request) {
        try {
            return objectMapper.writeValueAsString(canonicalAction(request));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to persist the requested action");
        }
    }

    String actionSummary(ServerToolRequest request) {
        String summary = switch (request.getTool()) {
            case COMMAND -> "command: " + redactor.redact(safe(request.getCommand()));
            case READ_FILE -> "read file: " + summarizeText(request.getPath(), 1_700);
            case WRITE_FILE -> contentSummary("write file", request.getPath(), request.getContent());
            case CREATE_SITE -> contentSummary("create site", request.getSiteName(), request.getContent());
            case SERVICE -> "service: " + safe(request.getAction()) + " " + safe(request.getService());
            case SERVER_STATUS -> "read server status";
        };
        return summarizeText(summary, 2_000);
    }

    private String contentSummary(String operation, String target, String content) {
        String safeContent = safe(content);
        return operation + ": " + summarizeText(target, 360)
                + System.lineSeparator()
                + "content: " + safeContent.length() + " chars, sha256=" + sha256(safeContent)
                + System.lineSeparator()
                + "preview:"
                + System.lineSeparator()
                + summarizeText(safeContent, 1_360);
    }

    /** Keeps both ends visible so a dangerous suffix cannot be hidden by truncation. */
    private String summarizeText(String value, int limit) {
        String redacted = redactor.redact(safe(value));
        if (redacted.length() <= limit) {
            return redacted;
        }
        String markerTemplate = System.lineSeparator()
                + "... [%d preview chars omitted] ..."
                + System.lineSeparator();
        int markerReserve = 64;
        int visible = Math.max(2, limit - markerReserve);
        int head = visible / 2;
        int tail = visible - head;
        int omitted = Math.max(0, redacted.length() - head - tail);
        String marker = markerTemplate.formatted(omitted);
        while (head + tail + marker.length() > limit && tail > 1) {
            tail--;
        }
        return redacted.substring(0, head) + marker + redacted.substring(redacted.length() - tail);
    }

    private Map<String, Object> canonicalAction(ServerToolRequest request) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("tool", request.getTool());
        action.put("command", safe(request.getCommand()));
        action.put("path", safe(request.getPath()));
        action.put("content", safe(request.getContent()));
        action.put("service", safe(request.getService()));
        action.put("action", safe(request.getAction()));
        action.put("siteName", safe(request.getSiteName()));
        action.put("timeoutSeconds", request.getTimeoutSeconds());
        return action;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

}
