package com.xzm.xzm_interview_helper.serveragent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Service
public class ServerToolService {
    private static final Set<String> READ_ONLY_SERVICE_ACTIONS = Set.of("status", "is-active", "is-enabled");
    private static final Set<String> MUTATING_SERVICE_ACTIONS = Set.of(
            "start", "stop", "restart", "reload", "enable", "disable"
    );

    private final ServerAgentProperties properties;
    private final ServerCommandPolicy commandPolicy;
    private final ServerPathPolicy pathPolicy;
    private final ServerCommandRunner commandRunner;
    private final ServerStatusService statusService;
    private final ServerApprovalService approvalService;
    private final ServerAgentRepository repository;
    private final CredentialRedactor redactor;
    private final ObjectMapper objectMapper;
    private final Semaphore admission;

    public ServerToolService(
            ServerAgentProperties properties,
            ServerCommandPolicy commandPolicy,
            ServerPathPolicy pathPolicy,
            ServerCommandRunner commandRunner,
            ServerStatusService statusService,
            ServerApprovalService approvalService,
            ServerAgentRepository repository,
            CredentialRedactor redactor,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.commandPolicy = commandPolicy;
        this.pathPolicy = pathPolicy;
        this.commandRunner = commandRunner;
        this.statusService = statusService;
        this.approvalService = approvalService;
        this.repository = repository;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
        this.admission = new Semaphore(Math.max(1, properties.getMaxConcurrentCommands()));
    }

    public ToolExecutionResponse execute(int userId, ServerToolRequest request) {
        return executeInternal(userId, request, false);
    }

    ToolExecutionResponse executeApproved(int userId, ServerToolRequest request) {
        return executeInternal(userId, request, true);
    }

    private ToolExecutionResponse executeInternal(int userId, ServerToolRequest request, boolean alreadyApproved) {
        requireEnabled();
        validate(request);
        ServerRisk risk = riskOf(request);
        String target = approvalService.actionSummary(request);
        if (risk == ServerRisk.BLOCKED) {
            audit(userId, request, target, risk, "REJECTED", null, null, 0, "Blocked by server policy");
            return new ToolExecutionResponse(
                    "REJECTED", request.getTool(), risk, "", null, 0, false, null, target,
                    "Credential access or an unsupported service action is blocked"
            );
        }
        boolean suppliedApproval = notBlank(request.getApprovalRequestId()) || notBlank(request.getApprovalToken());
        if (risk == ServerRisk.DANGEROUS && !alreadyApproved) {
            if (!suppliedApproval) {
                String approvalId = approvalService.requestApproval(userId, request);
                audit(userId, request, target, risk, "APPROVAL_REQUIRED", approvalId, null, 0, "Awaiting confirmation");
                return ToolExecutionResponse.approvalRequired(request.getTool(), approvalId, target);
            }
        }

        if (!acquireAdmission(alreadyApproved)) {
            audit(userId, request, target, risk, "BUSY", request.getApprovalRequestId(), null, 0, "Concurrency limit reached");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The server operation limit is busy");
        }
        long started = System.nanoTime();
        try {
            // Consume only after capacity is reserved, so a busy server cannot burn a one-use token.
            if (risk == ServerRisk.DANGEROUS
                    && !alreadyApproved
                    && suppliedApproval
                    && !approvalService.consume(userId, request)) {
                audit(userId, request, target, risk, "REJECTED", request.getApprovalRequestId(), null, 0,
                        "Invalid or expired approval");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Approval is invalid, expired, used, or belongs to another action");
            }
            ToolExecutionResponse response = perform(request, risk);
            long measuredDuration = (System.nanoTime() - started) / 1_000_000;
            if (response.durationMs() == 0) {
                response = new ToolExecutionResponse(
                        response.status(), response.tool(), response.risk(), response.output(), response.exitCode(),
                        measuredDuration, response.truncated(), response.approvalRequestId(), response.actionSummary(),
                        response.message()
                );
            }
            audit(
                    userId,
                    request,
                    target,
                    risk,
                    response.status(),
                    request.getApprovalRequestId(),
                    response.exitCode(),
                    response.durationMs(),
                    response.output()
            );
            return response;
        } catch (ResponseStatusException exception) {
            long duration = (System.nanoTime() - started) / 1_000_000;
            audit(userId, request, target, risk, "FAILED", request.getApprovalRequestId(), null, duration,
                    exception.getReason());
            throw exception;
        } catch (Exception exception) {
            long duration = (System.nanoTime() - started) / 1_000_000;
            String message = redactor.redact(exception.getMessage());
            audit(userId, request, target, risk, "FAILED", request.getApprovalRequestId(), null, duration, message);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Server operation failed: " + message);
        } finally {
            admission.release();
        }
    }

    private boolean acquireAdmission(boolean waitForPreviouslyApprovedAction) {
        if (!waitForPreviouslyApprovedAction) return admission.tryAcquire();
        try {
            return admission.tryAcquire(Math.max(1, properties.getCommandTimeoutSeconds()), java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ToolExecutionResponse perform(ServerToolRequest request, ServerRisk risk) throws Exception {
        return switch (request.getTool()) {
            case SERVER_STATUS -> success(request, risk, json(statusService.status()), 0, 0, false);
            case COMMAND -> fromCommand(request, risk, commandRunner.runShell(request.getCommand(), request.getTimeoutSeconds()));
            case READ_FILE -> readFile(request, risk);
            case WRITE_FILE -> writeFile(request, risk);
            case CREATE_SITE -> createSite(request, risk);
            case SERVICE -> service(request, risk);
        };
    }

    private ToolExecutionResponse readFile(ServerToolRequest request, ServerRisk risk) throws Exception {
        Path path = pathPolicy.resolve(request.getPath());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File does not exist");
        }
        int limit = Math.max(1_024, properties.getMaxOutputChars());
        StringBuilder output = new StringBuilder(Math.min(limit, 8_192));
        long seen = 0;
        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS),
                StandardCharsets.UTF_8
        )) {
            char[] buffer = new char[4_096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                seen += count;
                int remaining = limit - output.length();
                if (remaining > 0) output.append(buffer, 0, Math.min(remaining, count));
            }
        }
        return success(request, risk, redactor.redact(output.toString()), 0, 0, seen > limit);
    }

    private ToolExecutionResponse writeFile(ServerToolRequest request, ServerRisk risk) throws Exception {
        Path path = pathPolicy.resolve(request.getPath());
        Path parent = path.getParent();
        if (parent == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File needs a parent directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".server-agent-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    request.getContent() == null ? "" : request.getContent(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveAtomically(temporary, path);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return success(request, risk, "Wrote " + Files.size(path) + " bytes to " + path, 0, 0, false);
    }

    private ToolExecutionResponse createSite(ServerToolRequest request, ServerRisk risk) throws Exception {
        String siteName = request.getSiteName().strip();
        if (!siteName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Site name contains unsupported characters");
        }
        Path site = pathPolicy.resolve(Path.of(properties.getSiteRoot()).resolve(siteName).toString());
        Files.createDirectories(site);
        Path index = pathPolicy.resolve(site.resolve("index.html").toString());
        String content = request.getContent();
        if (content == null || content.isBlank()) {
            content = "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>"
                    + siteName + "</title><h1>" + siteName + "</h1></html>";
        }
        ServerToolRequest write = new ServerToolRequest();
        write.setTool(ServerToolName.WRITE_FILE);
        write.setPath(index.toString());
        write.setContent(content);
        ToolExecutionResponse ignored = writeFile(write, risk);
        String base = properties.getSitePublicBaseUrl() == null
                ? "/agent-sites"
                : properties.getSitePublicBaseUrl().strip();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String publicUrl = base + "/" + siteName + "/";
        return success(request, risk,
                "Created site files at " + site + System.lineSeparator() + "Public URL: " + publicUrl,
                0, 0, false);
    }

    private ToolExecutionResponse service(ServerToolRequest request, ServerRisk risk) {
        String service = request.getService().strip();
        String action = request.getAction().strip().toLowerCase(Locale.ROOT);
        List<String> invocation = List.of("systemctl", action, service);
        return fromCommand(request, risk, commandRunner.run(invocation, request.getTimeoutSeconds()));
    }

    private ToolExecutionResponse fromCommand(
            ServerToolRequest request,
            ServerRisk risk,
            ServerCommandRunner.CommandResult result
    ) {
        return success(
                request,
                risk,
                result.output(),
                result.exitCode(),
                result.durationMs(),
                result.truncated()
        );
    }

    private ToolExecutionResponse success(
            ServerToolRequest request,
            ServerRisk risk,
            String output,
            Integer exitCode,
            long duration,
            boolean truncated
    ) {
        String status = exitCode == null || exitCode == 0 ? "EXECUTED" : "FAILED";
        return new ToolExecutionResponse(
                status,
                request.getTool(),
                risk,
                redactor.redact(output),
                exitCode,
                duration,
                truncated,
                null,
                approvalService.actionSummary(request),
                status.equals("EXECUTED") ? "Operation completed" : "Operation returned a non-zero exit code"
        );
    }

    private ServerRisk riskOf(ServerToolRequest request) {
        return switch (request.getTool()) {
            case SERVER_STATUS, READ_FILE -> ServerRisk.READ_ONLY;
            case WRITE_FILE, CREATE_SITE -> ServerRisk.DANGEROUS;
            case COMMAND -> commandPolicy.classify(request.getCommand());
            case SERVICE -> READ_ONLY_SERVICE_ACTIONS.contains(request.getAction().strip().toLowerCase(Locale.ROOT))
                    ? ServerRisk.READ_ONLY
                    : MUTATING_SERVICE_ACTIONS.contains(request.getAction().strip().toLowerCase(Locale.ROOT))
                    ? ServerRisk.DANGEROUS
                    : ServerRisk.BLOCKED;
        };
    }

    private void validate(ServerToolRequest request) {
        if (request == null || request.getTool() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A server tool is required");
        }
        switch (request.getTool()) {
            case COMMAND -> require(request.getCommand(), "A command is required", 1_000);
            case READ_FILE -> require(request.getPath(), "A file path is required", 2_000);
            case WRITE_FILE -> {
                require(request.getPath(), "A file path is required", 2_000);
                if (request.getContent() != null && request.getContent().length() > 1_000_000) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File content is too large");
                }
            }
            case CREATE_SITE -> {
                require(request.getSiteName(), "A site name is required", 128);
                if (request.getContent() != null && request.getContent().length() > 1_000_000) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Site content is too large");
                }
            }
            case SERVICE -> {
                require(request.getService(), "A service name is required", 128);
                require(request.getAction(), "A service action is required", 32);
                if (!request.getService().matches("[A-Za-z0-9@_.-]+")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name contains unsupported characters");
                }
            }
            case SERVER_STATUS -> {
                // No arguments.
            }
        }
    }

    private void require(String value, String message, int max) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        if (value.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message + " (too long)");
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Server agent is disabled; set SERVER_AGENT_ENABLED=true on the host");
        }
    }

    private void moveAtomically(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize server status");
        }
    }

    private void audit(
            int userId,
            ServerToolRequest request,
            String target,
            ServerRisk risk,
            String status,
            String approvalId,
            Integer exitCode,
            long duration,
            String output
    ) {
        repository.appendAudit(
                userId,
                request.getTool().name(),
                clip(redactor.redact(target), 2_000),
                risk,
                status,
                approvalId,
                exitCode,
                duration,
                clip(redactor.redact(output == null ? "" : output), 20_000)
        );
    }

    private String clip(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
