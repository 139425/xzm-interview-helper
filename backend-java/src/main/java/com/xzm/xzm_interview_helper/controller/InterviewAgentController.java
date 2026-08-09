package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.model.dto.CreateInterviewAgentSessionRequest;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentSessionResponse;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentStreamEvent;
import com.xzm.xzm_interview_helper.model.dto.SubmitInterviewAgentAnswerRequest;
import com.xzm.xzm_interview_helper.service.AiOperationGate;
import com.xzm.xzm_interview_helper.service.InMemoryAdmissionGate;
import com.xzm.xzm_interview_helper.service.InterviewAgentOrchestrator;
import com.xzm.xzm_interview_helper.service.ResumeTextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Supplier;

/**
 * Authenticated REST/SSE boundary for the adaptive interview agent. PII is sent only in POST bodies
 * or multipart requests and identity always comes from the verified JWT, never from client input.
 */
@RestController
@RequestMapping("/interview-agent")
@RequiredArgsConstructor
public class InterviewAgentController {

    private final InterviewAgentOrchestrator interviewAgentOrchestrator;
    private final ResumeTextExtractor resumeTextExtractor;
    private final AiOperationGate aiOperationGate;

    @PostMapping("/sessions")
    public ResponseEntity<InterviewAgentSessionResponse> createSession(
            @Valid @RequestBody CreateInterviewAgentSessionRequest request,
            HttpServletRequest servletRequest
    ) {
        InterviewAgentSessionResponse session = interviewAgentOrchestrator.createSession(currentUserId(servletRequest), request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping(value = "/sessions/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InterviewAgentSessionResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String modelProvider,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) Boolean enableThinking,
            HttpServletRequest servletRequest
    ) {
        ResumeTextExtractor.ExtractedResume extracted = resumeTextExtractor.extract(file);
        CreateInterviewAgentSessionRequest request = new CreateInterviewAgentSessionRequest();
        request.setResumeText(extracted.text());
        request.setTargetRole(targetRole);
        request.setModelProvider(modelProvider);
        request.setModelName(modelName);
        request.setEnableThinking(enableThinking);
        InterviewAgentSessionResponse session = interviewAgentOrchestrator.createSession(
                currentUserId(servletRequest), request, extracted.filename());
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping(value = "/sessions/{sessionId}/start/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InterviewAgentStreamEvent>> start(
            @PathVariable String sessionId,
            HttpServletRequest servletRequest
    ) {
        int userId = currentUserId(servletRequest);
        return guardStream(
                userId,
                () -> asSse(interviewAgentOrchestrator.startSession(sessionId, userId))
        );
    }

    @PostMapping(value = "/sessions/{sessionId}/turns/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InterviewAgentStreamEvent>> submitAnswer(
            @PathVariable String sessionId,
            @Valid @RequestBody SubmitInterviewAgentAnswerRequest request,
            HttpServletRequest servletRequest
    ) {
        int userId = currentUserId(servletRequest);
        return guardStream(
                userId,
                () -> asSse(
                        interviewAgentOrchestrator.submitAnswer(
                                sessionId,
                                userId,
                                request.getAnswer()
                        )
                )
        );
    }

    @PostMapping(value = "/sessions/{sessionId}/retry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InterviewAgentStreamEvent>> retry(
            @PathVariable String sessionId,
            HttpServletRequest servletRequest
    ) {
        int userId = currentUserId(servletRequest);
        return guardStream(
                userId,
                () -> asSse(interviewAgentOrchestrator.retry(sessionId, userId))
        );
    }

    @GetMapping("/sessions/{sessionId}")
    public InterviewAgentSessionResponse getSession(
            @PathVariable String sessionId,
            HttpServletRequest servletRequest
    ) {
        return interviewAgentOrchestrator.getSession(sessionId, currentUserId(servletRequest));
    }

    @GetMapping("/sessions")
    public List<InterviewAgentSessionResponse> listSessions(
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest servletRequest
    ) {
        return interviewAgentOrchestrator.listSessions(currentUserId(servletRequest), limit);
    }

    private Flux<ServerSentEvent<InterviewAgentStreamEvent>> asSse(Flux<InterviewAgentStreamEvent> events) {
        return events.map(event -> ServerSentEvent.<InterviewAgentStreamEvent>builder()
                .event(event.getType())
                .data(event)
                .build());
    }

    private <T> Flux<T> guardStream(long userId, Supplier<Flux<T>> sourceFactory) {
        try {
            return aiOperationGate.guardFlux(userId, sourceFactory);
        } catch (InMemoryAdmissionGate.RejectedException exception) {
            String message = switch (exception.getReason()) {
                case KEY_BUSY -> "当前已有 AI 任务，请停止或等待完成后再试";
                case RATE_LIMITED -> "AI 请求过于频繁，请稍后再试";
                case GLOBAL_BUSY, TRACKING_CAPACITY -> "AI 服务繁忙，请稍后再试";
            };
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    message,
                    exception
            );
        }
    }

    private int currentUserId(HttpServletRequest request) {
        Object rawUserId = request.getAttribute("userId");
        if (rawUserId instanceof Number number) {
            return Math.toIntExact(number.longValue());
        }
        if (rawUserId instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // Fall through to an authorization error below.
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未能识别当前登录用户");
    }
}
