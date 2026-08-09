package com.xzm.xzm_interview_helper.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentRequest;
import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentResponse;
import com.xzm.xzm_interview_helper.grpc.client.PythonAiGrpcClient;
import com.xzm.xzm_interview_helper.mapper.AlgorithmSubmissionMapper;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemDetail;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmSubmissionReviewResponse;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentSession;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmSubmission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Runs a candidate-safe AI review after the deterministic judge has persisted a submission.
 *
 * <p>The review has its own leased state machine and updates only {@code ai_*}
 * columns. It cannot change the official judge status or expose server-owned
 * test cases to the model.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmSubmissionAiReviewService {

    static final String AI_PROCESSING = "PROCESSING";
    static final String AI_COMPLETED = "COMPLETED";
    static final String AI_FAILED = "FAILED";
    private static final long PROCESSING_LEASE_MILLIS = 3L * 60L * 1000L;
    private static final String OPERATION = "ALGORITHM_EVALUATE";
    private static final String FAILURE_MESSAGE = "AI 评价暂时不可用，官方判题结果未改变，可稍后重试。";

    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmProblemCatalogService catalogService;
    private final AiInterviewAgentSessionService interviewSessionService;
    private final PythonAiGrpcClient pythonAiGrpcClient;
    private final ObjectMapper objectMapper;

    /**
     * Ownership-checked, read-only status lookup used by polling clients.
     * It never claims a lease or invokes the model.
     */
    public AlgorithmSubmissionReviewResponse getStatus(long submissionId, int userId) {
        AlgorithmSubmission submission = findOwned(submissionId, userId);
        if (submission == null) {
            throw new NoSuchElementException("算法提交不存在");
        }
        return toResponse(submission);
    }

    public AlgorithmSubmissionReviewResponse review(long submissionId, int userId) {
        AlgorithmSubmission submission = findOwned(submissionId, userId);
        if (submission == null) {
            throw new NoSuchElementException("算法提交不存在");
        }
        if (AI_COMPLETED.equals(submission.getAi_status())) {
            return toResponse(submission);
        }
        if (isActiveLease(submission)) {
            return toResponse(submission);
        }

        Date claimTime = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        if (!claim(submission, userId, claimTime)) {
            AlgorithmSubmission current = findOwned(submissionId, userId);
            return current == null ? toResponse(submission) : toResponse(current);
        }

        try {
            AlgorithmProblemDetail problem = catalogService.detail(submission.getProblem_slug());
            InterviewAgentRequest request = buildRequest(submission, problem);
            InterviewAgentResponse aiResponse = pythonAiGrpcClient.runInterviewAgent(request);
            if (!aiResponse.getSuccess()
                    || aiResponse.getScore() < 0
                    || aiResponse.getScore() > 100
                    || !StringUtils.hasText(aiResponse.getEvaluation())) {
                return finishFailure(submission, userId, claimTime);
            }
            String evaluation = clip(aiResponse.getEvaluation().trim(), 12_000);
            Date completedAt = new Date();
            int updated = submissionMapper.update(
                    null,
                    claimGuard(submission.getId(), userId, claimTime)
                            .set("ai_status", AI_COMPLETED)
                            .set("ai_score", aiResponse.getScore())
                            .set("ai_evaluation", evaluation)
                            .set("ai_evaluated_at", completedAt)
            );
            if (updated == 0) {
                AlgorithmSubmission current = findOwned(submission.getId(), userId);
                return current == null ? toResponse(submission) : toResponse(current);
            }
            submission.setAi_status(AI_COMPLETED);
            submission.setAi_score(aiResponse.getScore());
            submission.setAi_evaluation(evaluation);
            submission.setAi_evaluated_at(completedAt);
            return toResponse(submission);
        } catch (Exception exception) {
            // Never log candidate source or provider response content.
            log.warn("Algorithm AI review failed for submission {} ({})",
                    submissionId, exception.getClass().getSimpleName());
            return finishFailure(submission, userId, claimTime);
        }
    }

    private InterviewAgentRequest buildRequest(
            AlgorithmSubmission submission,
            AlgorithmProblemDetail problem
    ) throws JsonProcessingException {
        Map<String, Object> publicProblem = new LinkedHashMap<>();
        publicProblem.put("slug", problem.getSlug());
        publicProblem.put("title", problem.getTitle());
        publicProblem.put("difficulty", problem.getDifficulty());
        publicProblem.put("tags", problem.getTags());
        publicProblem.put("description_html", clip(problem.getContentHtml(), 8_000));

        // Only aggregate outcomes are sent. output/error_message and every
        // server-owned judge definition are intentionally excluded.
        Map<String, Object> judgeResult = new LinkedHashMap<>();
        judgeResult.put("status", submission.getStatus());
        judgeResult.put("passed_cases", submission.getPassed_cases());
        judgeResult.put("total_cases", submission.getTotal_cases());
        judgeResult.put("runtime_ms", submission.getRuntime_ms());

        InterviewAgentRequest.Builder request = InterviewAgentRequest.newBuilder()
                .setOperation(OPERATION)
                .setCurrentQuestion(objectMapper.writeValueAsString(publicProblem))
                .setCandidateAnswer(submission.getSource_code())
                .setDialogueJson(objectMapper.writeValueAsString(judgeResult))
                .setTargetRole("Algorithm code reviewer");

        if (submission.getInterview_session_id() != null) {
            AiInterviewAgentSession session =
                    interviewSessionService.getById(submission.getInterview_session_id());
            if (session != null) {
                if (StringUtils.hasText(session.getModel_provider())) {
                    request.setProvider(session.getModel_provider());
                }
                if (StringUtils.hasText(session.getModel_name())) {
                    request.setModelName(session.getModel_name());
                }
                if (session.getThinking_enabled() != null) {
                    request.setEnableThinking(session.getThinking_enabled());
                }
            }
        }
        return request.build();
    }

    private boolean claim(
            AlgorithmSubmission submission,
            int userId,
            Date claimTime
    ) {
        UpdateWrapper<AlgorithmSubmission> update = Wrappers.update();
        update.eq("id", submission.getId())
                .eq("user_id", userId);
        if (submission.getAi_status() == null) {
            update.isNull("ai_status");
        } else {
            update.eq("ai_status", submission.getAi_status());
            if (AI_PROCESSING.equals(submission.getAi_status())) {
                if (submission.getAi_evaluated_at() == null) {
                    update.isNull("ai_evaluated_at");
                } else {
                    update.eq("ai_evaluated_at", submission.getAi_evaluated_at());
                }
            }
        }
        update.set("ai_status", AI_PROCESSING)
                .set("ai_score", null)
                .set("ai_evaluation", null)
                .set("ai_evaluated_at", claimTime);
        return submissionMapper.update(null, update) == 1;
    }

    private AlgorithmSubmissionReviewResponse finishFailure(
            AlgorithmSubmission submission,
            int userId,
            Date claimTime
    ) {
        Date failedAt = new Date();
        int updated = submissionMapper.update(
                null,
                claimGuard(submission.getId(), userId, claimTime)
                        .set("ai_status", AI_FAILED)
                        .set("ai_score", null)
                        .set("ai_evaluation", FAILURE_MESSAGE)
                        .set("ai_evaluated_at", failedAt)
        );
        if (updated == 0) {
            AlgorithmSubmission current = findOwned(submission.getId(), userId);
            return current == null ? toResponse(submission) : toResponse(current);
        }
        submission.setAi_status(AI_FAILED);
        submission.setAi_score(null);
        submission.setAi_evaluation(FAILURE_MESSAGE);
        submission.setAi_evaluated_at(failedAt);
        return toResponse(submission);
    }

    private UpdateWrapper<AlgorithmSubmission> claimGuard(
            Long submissionId,
            int userId,
            Date claimTime
    ) {
        return Wrappers.<AlgorithmSubmission>update()
                .eq("id", submissionId)
                .eq("user_id", userId)
                .eq("ai_status", AI_PROCESSING)
                .eq("ai_evaluated_at", claimTime);
    }

    private AlgorithmSubmission findOwned(long submissionId, int userId) {
        QueryWrapper<AlgorithmSubmission> query = Wrappers.query();
        query.eq("id", submissionId)
                .eq("user_id", userId)
                .last("LIMIT 1");
        return submissionMapper.selectOne(query);
    }

    private boolean isActiveLease(AlgorithmSubmission submission) {
        return AI_PROCESSING.equals(submission.getAi_status())
                && submission.getAi_evaluated_at() != null
                && submission.getAi_evaluated_at().getTime()
                > System.currentTimeMillis() - PROCESSING_LEASE_MILLIS;
    }

    private AlgorithmSubmissionReviewResponse toResponse(AlgorithmSubmission submission) {
        AlgorithmSubmissionReviewResponse response = new AlgorithmSubmissionReviewResponse();
        response.setSubmissionId(submission.getId());
        response.setJudgeStatus(submission.getStatus());
        response.setAiStatus(submission.getAi_status());
        response.setAiScore(submission.getAi_score());
        response.setAiEvaluation(submission.getAi_evaluation());
        response.setAiEvaluatedAt(submission.getAi_evaluated_at());
        return response;
    }

    private static String clip(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}
