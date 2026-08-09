package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.model.dto.AlgorithmCustomExecutionRequest;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionRequest;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemDetail;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemSummary;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmSubmissionReviewResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmChallengeResponse;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentSessionResponse;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentStreamEvent;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmSubmission;
import com.xzm.xzm_interview_helper.service.AiOperationGate;
import com.xzm.xzm_interview_helper.service.AlgorithmJudgeService;
import com.xzm.xzm_interview_helper.service.AlgorithmOperationGate;
import com.xzm.xzm_interview_helper.service.AlgorithmProblemCatalogService;
import com.xzm.xzm_interview_helper.service.AlgorithmSubmissionService;
import com.xzm.xzm_interview_helper.service.AlgorithmSubmissionAiReviewService;
import com.xzm.xzm_interview_helper.service.InMemoryAdmissionGate;
import com.xzm.xzm_interview_helper.service.InterviewAgentOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/algorithm")
@RequiredArgsConstructor
public class AlgorithmController {

    private final AlgorithmProblemCatalogService catalogService;
    private final AlgorithmJudgeService judgeService;
    private final AlgorithmSubmissionService submissionService;
    private final AlgorithmSubmissionAiReviewService submissionAiReviewService;
    private final InterviewAgentOrchestrator interviewAgentOrchestrator;
    private final AlgorithmOperationGate operationGate;
    private final AiOperationGate aiOperationGate;

    @GetMapping("/problems")
    public List<AlgorithmProblemSummary> problems(
            @RequestParam(defaultValue = "") String source,
            @RequestParam(defaultValue = "") String difficulty,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "false") boolean judgeableOnly,
            HttpServletRequest request
    ) {
        currentUserId(request);
        return catalogService.list(source, difficulty, keyword, judgeableOnly);
    }

    @GetMapping("/problems/{slug}")
    public AlgorithmProblemDetail problem(
            @PathVariable String slug,
            HttpServletRequest request
    ) {
        currentUserId(request);
        try {
            return catalogService.detail(slug);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping("/run")
    public AlgorithmExecutionResponse run(
            @Valid @RequestBody AlgorithmExecutionRequest request,
            HttpServletRequest servletRequest
    ) {
        int userId = currentUserId(servletRequest);
        try (AlgorithmOperationGate.Permit ignored = acquireJudgePermit(userId)) {
            return execute(request, false);
        }
    }

    @PostMapping("/run/custom")
    public AlgorithmExecutionResponse runCustom(
            @Valid @RequestBody AlgorithmCustomExecutionRequest request,
            HttpServletRequest servletRequest
    ) {
        int userId = currentUserId(servletRequest);
        try (AlgorithmOperationGate.Permit ignored = acquireJudgePermit(userId)) {
            try {
                AlgorithmProblemSummary problem = catalogService.requireSummary(request.getProblemSlug());
                return judgeService.executeCustom(
                        problem,
                        request.getLanguage(),
                        request.getCode(),
                        request.getDriverCode(),
                        request.getExpectedOutput()
                );
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
            }
        }
    }

    @PostMapping("/submit")
    public AlgorithmExecutionResponse submit(
            @Valid @RequestBody AlgorithmExecutionRequest request,
            HttpServletRequest servletRequest
    ) {
        int userId = currentUserId(servletRequest);
        try (AlgorithmOperationGate.Permit ignored = acquireJudgePermit(userId)) {
            AlgorithmExecutionResponse response = execute(request, true);
            AlgorithmProblemSummary problem = catalogService.requireSummary(request.getProblemSlug());

            AlgorithmSubmission submission = new AlgorithmSubmission();
            submission.setUser_id(userId);
            submission.setProblem_slug(problem.getSlug());
            submission.setProblem_source(String.join(",", problem.getSources()));
            submission.setDifficulty(problem.getDifficulty());
            submission.setLanguage(request.getLanguage().toLowerCase(Locale.ROOT));
            submission.setSource_code(request.getCode());
            submission.setStatus(response.getStatus());
            submission.setPassed_cases(response.getPassedCases());
            submission.setTotal_cases(response.getTotalCases());
            submission.setRuntime_ms(response.getRuntimeMs());
            submission.setOutput(response.getOutput());
            submission.setError_message(response.getError());
            submission.setCreate_time(new Date());
            if (request.getInterviewSessionId() != null && !request.getInterviewSessionId().isBlank()) {
                Long submissionId = interviewAgentOrchestrator.saveAndRecordAlgorithmSubmission(
                        request.getInterviewSessionId().trim(),
                        userId,
                        submission,
                        response
                );
                response.setSubmissionId(submissionId);
            } else {
                if (!submissionService.save(submission)) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "算法提交保存失败"
                    );
                }
                response.setSubmissionId(submission.getId());
            }
            return response;
        }
    }

    @GetMapping("/interview/{sessionId}")
    public AlgorithmChallengeResponse interviewChallenge(
            @PathVariable String sessionId,
            HttpServletRequest request
    ) {
        InterviewAgentSessionResponse session =
                interviewAgentOrchestrator.getSession(sessionId, currentUserId(request));
        if (session.getAlgorithmChallenge() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该面试尚未分配算法终局题");
        }
        return session.getAlgorithmChallenge();
    }

    @PostMapping(value = "/interview/{sessionId}/finish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<InterviewAgentStreamEvent> finishInterviewChallenge(
            @PathVariable String sessionId,
            HttpServletRequest request
    ) {
        int userId = currentUserId(request);
        return guardAiStream(
                userId,
                () -> interviewAgentOrchestrator.finishAfterAlgorithm(sessionId, userId)
        );
    }

    @PostMapping(value = "/interview/{sessionId}/abandon", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<InterviewAgentStreamEvent> abandonInterviewChallenge(
            @PathVariable String sessionId,
            HttpServletRequest request
    ) {
        int userId = currentUserId(request);
        return guardAiStream(
                userId,
                () -> interviewAgentOrchestrator.abandonAlgorithm(sessionId, userId)
        );
    }

    @GetMapping("/submissions")
    public List<AlgorithmSubmission> submissions(
            @RequestParam(defaultValue = "") String problemSlug,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request
    ) {
        int userId = currentUserId(request);
        int safeLimit = Math.max(1, Math.min(100, limit));
        return submissionService.lambdaQuery()
                .eq(AlgorithmSubmission::getUser_id, userId)
                .eq(!problemSlug.isBlank(), AlgorithmSubmission::getProblem_slug, problemSlug.trim())
                .orderByDesc(AlgorithmSubmission::getCreate_time)
                .last("LIMIT " + safeLimit)
                .list();
    }

    @PostMapping("/submissions/{submissionId}/review")
    public AlgorithmSubmissionReviewResponse reviewSubmission(
            @PathVariable long submissionId,
            HttpServletRequest request
    ) {
        int userId = currentUserId(request);
        try {
            try (AlgorithmOperationGate.Permit ignored = acquireAiReviewPermit(userId)) {
                return submissionAiReviewService.review(submissionId, userId);
            }
        } catch (java.util.NoSuchElementException exception) {
            // Return the same response for a missing and a foreign submission,
            // so this endpoint cannot be used to enumerate another user's IDs.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "算法提交不存在");
        }
    }

    @GetMapping("/submissions/{submissionId}/review")
    public AlgorithmSubmissionReviewResponse reviewSubmissionStatus(
            @PathVariable long submissionId,
            HttpServletRequest request
    ) {
        try {
            return submissionAiReviewService.getStatus(
                    submissionId,
                    currentUserId(request)
            );
        } catch (java.util.NoSuchElementException exception) {
            // Keep missing and foreign submissions indistinguishable.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "算法提交不存在");
        }
    }

    private AlgorithmOperationGate.Permit acquireJudgePermit(int userId) {
        try {
            return operationGate.acquireJudge(userId);
        } catch (AlgorithmOperationGate.RejectedException exception) {
            throw tooManyRequests(exception, "判题");
        }
    }

    private <T> Flux<T> guardAiStream(long userId, Supplier<Flux<T>> sourceFactory) {
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

    private AlgorithmOperationGate.Permit acquireAiReviewPermit(int userId) {
        try {
            return operationGate.acquireAiReview(userId);
        } catch (AlgorithmOperationGate.RejectedException exception) {
            throw tooManyRequests(exception, "AI 评价");
        }
    }

    private ResponseStatusException tooManyRequests(
            AlgorithmOperationGate.RejectedException exception,
            String operation
    ) {
        String message = switch (exception.getReason()) {
            case USER_IN_FLIGHT -> "当前已有" + operation + "任务，请等待完成后再试";
            case RATE_LIMITED -> operation + "请求过于频繁，请稍后再试";
            case GLOBAL_BUSY -> operation + "服务繁忙，请稍后再试";
        };
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message, exception);
    }

    private AlgorithmExecutionResponse execute(
            AlgorithmExecutionRequest request,
            boolean submit
    ) {
        try {
            AlgorithmProblemSummary problem = catalogService.requireSummary(request.getProblemSlug());
            return judgeService.execute(problem, request.getLanguage(), request.getCode(), submit);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    private Long resolveInterviewSessionId(String publicId, int userId) {
        /*
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        AiInterviewAgentSession session = interviewSessionService.lambdaQuery()
                .eq(AiInterviewAgentSession::getPublic_id, publicId.trim())
                .eq(AiInterviewAgentSession::getUser_id, userId)
                .one();
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "面试会话不存在");
        }
        return session.getId();
        */
        return null;
    }

    private int currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                int id = Integer.parseInt(text);
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
}
