package com.xzm.xzm_interview_helper.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Authenticated, bounded proxy for the code editor's Piston execution request.
 *
 * <p>Only the small request surface needed by the bundled editor is forwarded.
 * This prevents callers from using the application as an arbitrary Piston proxy
 * or from overriding upstream resource-related fields.</p>
 */
@RestController
@RequestMapping("code")
@Slf4j
public class CodeExecutionController {

    private static final String PISTON_API_URL = "https://emkc.org/api/v2/piston/execute";
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("python", "c++", "java");
    private static final Pattern SAFE_VERSION = Pattern.compile("^[A-Za-z0-9*._+-]{1,64}$");
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z0-9._$-]{1,128}$");

    private static final int MAX_FILES = 3;
    private static final int MAX_FILE_CHARS = 24_000;
    private static final int MAX_TOTAL_SOURCE_CHARS = 48_000;
    private static final int MAX_STDIN_CHARS = 8_000;
    private static final int MAX_ARGS = 20;
    private static final int MAX_ARG_CHARS = 256;
    private static final int MAX_EXECUTIONS_PER_MINUTE = 6;
    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED_USERS = 2_048;

    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<Long, UserExecutionBudget> executionBudgets = new ConcurrentHashMap<>();

    public CodeExecutionController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Piston constrains the process itself; the gateway also bounds how long a request can occupy a server thread.
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostMapping("/execute")
    public ResponseEntity<Object> executeCode(
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest servletRequest
    ) {
        Long userId = resolveAuthenticatedUserId(servletRequest);
        if (userId == null) {
            // The JWT filter should reject this first; retain a defensive controller-level guard.
            return error(HttpStatus.UNAUTHORIZED, "请先登录后再执行云端代码");
        }

        final Map<String, Object> sanitizedRequest;
        try {
            sanitizedRequest = sanitizeRequest(requestBody);
        } catch (RequestValidationException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        PermitOutcome permitOutcome = acquireExecutionPermit(userId);
        if (permitOutcome == PermitOutcome.IN_FLIGHT) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "当前已有代码执行任务，请等待其结束后再试");
        }
        if (permitOutcome == PermitOutcome.RATE_LIMITED) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "代码执行请求过于频繁，请稍后再试");
        }

        try {
            List<?> files = (List<?>) sanitizedRequest.get("files");
            int sourceChars = files.stream()
                    .map(file -> (Map<?, ?>) file)
                    .mapToInt(file -> ((String) file.get("content")).length())
                    .sum();
            log.info("Code execution request accepted: userId={}, language={}, files={}, sourceChars={}",
                    userId, sanitizedRequest.get("language"), files.size(), sourceChars);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(sanitizedRequest, headers);
            ResponseEntity<Object> response = restTemplate.postForEntity(PISTON_API_URL, entity, Object.class);

            log.info("Piston response received: userId={}, status={}", userId, response.getStatusCode());
            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException exception) {
            log.warn("Piston rejected code execution request: userId={}, status={}", userId, exception.getStatusCode());
            return error(HttpStatus.BAD_GATEWAY, "代码执行服务暂时不可用，请稍后重试");
        } catch (HttpServerErrorException exception) {
            log.warn("Piston service failure: userId={}, status={}", userId, exception.getStatusCode());
            return error(HttpStatus.BAD_GATEWAY, "代码执行服务暂时不可用，请稍后重试");
        } catch (ResourceAccessException exception) {
            log.warn("Piston request timed out or was unreachable: userId={}", userId);
            return error(HttpStatus.GATEWAY_TIMEOUT, "代码执行服务响应超时，请稍后重试");
        } catch (RestClientException exception) {
            log.warn("Piston request failed: userId={}, type={}", userId, exception.getClass().getSimpleName());
            return error(HttpStatus.BAD_GATEWAY, "代码执行服务暂时不可用，请稍后重试");
        } catch (RuntimeException exception) {
            log.error("Unexpected code execution proxy failure: userId={}", userId, exception);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "代码执行请求失败，请稍后重试");
        } finally {
            releaseExecutionPermit(userId);
        }
    }

    private Map<String, Object> sanitizeRequest(Map<String, Object> requestBody) {
        if (requestBody == null) {
            throw new RequestValidationException("请求体不能为空");
        }

        String language = requiredShortText(requestBody, "language", 32).toLowerCase(Locale.ROOT);
        if (!ALLOWED_LANGUAGES.contains(language)) {
            throw new RequestValidationException("仅支持 Python、C++ 和 Java 执行");
        }

        String version = requiredShortText(requestBody, "version", 64);
        if (!SAFE_VERSION.matcher(version).matches()) {
            throw new RequestValidationException("运行时版本格式无效");
        }

        Object filesValue = requestBody.get("files");
        if (!(filesValue instanceof List<?> rawFiles) || rawFiles.isEmpty() || rawFiles.size() > MAX_FILES) {
            throw new RequestValidationException("files 必须包含 1 到 " + MAX_FILES + " 个源码文件");
        }

        int totalSourceChars = 0;
        List<Map<String, String>> files = new ArrayList<>();
        for (Object rawFile : rawFiles) {
            if (!(rawFile instanceof Map<?, ?> file)) {
                throw new RequestValidationException("每个源码文件必须是对象");
            }
            Object contentValue = file.get("content");
            if (!(contentValue instanceof String content)) {
                throw new RequestValidationException("源码文件必须包含文本 content");
            }
            if (content.length() > MAX_FILE_CHARS) {
                throw new RequestValidationException("单个源码文件不能超过 " + MAX_FILE_CHARS + " 个字符");
            }
            totalSourceChars += content.length();
            if (totalSourceChars > MAX_TOTAL_SOURCE_CHARS) {
                throw new RequestValidationException("全部源码不能超过 " + MAX_TOTAL_SOURCE_CHARS + " 个字符");
            }

            Map<String, String> sanitizedFile = new LinkedHashMap<>();
            Object nameValue = file.get("name");
            if (nameValue != null) {
                if (!(nameValue instanceof String fileName)
                        || !SAFE_FILE_NAME.matcher(fileName).matches()
                        || ".".equals(fileName)
                        || "..".equals(fileName)) {
                    throw new RequestValidationException("源码文件名格式无效");
                }
                sanitizedFile.put("name", fileName);
            }
            sanitizedFile.put("content", content);
            files.add(sanitizedFile);
        }

        String stdin = optionalText(requestBody.get("stdin"), "stdin", MAX_STDIN_CHARS);
        List<String> args = sanitizeArgs(requestBody.get("args"));

        Map<String, Object> sanitizedRequest = new LinkedHashMap<>();
        sanitizedRequest.put("language", language);
        sanitizedRequest.put("version", version);
        sanitizedRequest.put("files", files);
        sanitizedRequest.put("stdin", stdin);
        if (!args.isEmpty()) {
            sanitizedRequest.put("args", args);
        }
        return sanitizedRequest;
    }

    private List<String> sanitizeArgs(Object argsValue) {
        if (argsValue == null) {
            return List.of();
        }
        if (!(argsValue instanceof List<?> rawArgs) || rawArgs.size() > MAX_ARGS) {
            throw new RequestValidationException("args 最多包含 " + MAX_ARGS + " 个文本参数");
        }
        List<String> args = new ArrayList<>();
        for (Object rawArg : rawArgs) {
            if (!(rawArg instanceof String argument) || argument.length() > MAX_ARG_CHARS) {
                throw new RequestValidationException("每个命令行参数必须是不超过 " + MAX_ARG_CHARS + " 个字符的文本");
            }
            args.add(argument);
        }
        return args;
    }

    private String requiredShortText(Map<String, Object> requestBody, String fieldName, int maxChars) {
        Object value = requestBody.get(fieldName);
        if (!(value instanceof String text)) {
            throw new RequestValidationException(fieldName + " 必须是文本");
        }
        String normalized = text.trim();
        if (normalized.isEmpty() || normalized.length() > maxChars) {
            throw new RequestValidationException(fieldName + " 长度无效");
        }
        return normalized;
    }

    private String optionalText(Object value, String fieldName, int maxChars) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text) || text.length() > maxChars) {
            throw new RequestValidationException(fieldName + " 必须是不超过 " + maxChars + " 个字符的文本");
        }
        return text;
    }

    private Long resolveAuthenticatedUserId(HttpServletRequest request) {
        Object rawUserId = request.getAttribute("userId");
        if (rawUserId instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        if (rawUserId instanceof String text) {
            try {
                long userId = Long.parseLong(text);
                return userId > 0 ? userId : null;
            } catch (NumberFormatException ignored) {
                // Fall through to the unauthenticated response.
            }
        }
        return null;
    }

    private PermitOutcome acquireExecutionPermit(Long userId) {
        long now = System.currentTimeMillis();
        if (executionBudgets.size() > MAX_TRACKED_USERS) {
            executionBudgets.entrySet().removeIf(entry -> entry.getValue().isIdleAndExpired(now));
        }
        return executionBudgets
                .computeIfAbsent(userId, ignored -> new UserExecutionBudget())
                .tryAcquire(now);
    }

    private void releaseExecutionPermit(Long userId) {
        UserExecutionBudget budget = executionBudgets.get(userId);
        if (budget != null) {
            budget.release();
        }
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", true);
        body.put("message", message);
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }

    private enum PermitOutcome {
        GRANTED,
        IN_FLIGHT,
        RATE_LIMITED
    }

    private static final class UserExecutionBudget {
        private final Deque<Long> executionStarts = new ArrayDeque<>();
        private boolean inFlight;

        private synchronized PermitOutcome tryAcquire(long now) {
            discardExpiredStarts(now);
            if (inFlight) {
                return PermitOutcome.IN_FLIGHT;
            }
            if (executionStarts.size() >= MAX_EXECUTIONS_PER_MINUTE) {
                return PermitOutcome.RATE_LIMITED;
            }
            executionStarts.addLast(now);
            inFlight = true;
            return PermitOutcome.GRANTED;
        }

        private synchronized void release() {
            inFlight = false;
        }

        private synchronized boolean isIdleAndExpired(long now) {
            discardExpiredStarts(now);
            return !inFlight && executionStarts.isEmpty();
        }

        private void discardExpiredStarts(long now) {
            while (!executionStarts.isEmpty() && now - executionStarts.peekFirst() >= RATE_WINDOW_MILLIS) {
                executionStarts.removeFirst();
            }
        }
    }

    private static final class RequestValidationException extends RuntimeException {
        private RequestValidationException(String message) {
            super(message);
        }
    }
}
