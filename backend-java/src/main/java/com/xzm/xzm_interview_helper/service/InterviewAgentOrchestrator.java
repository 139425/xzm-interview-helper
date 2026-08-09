package com.xzm.xzm_interview_helper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentRequest;
import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentResponse;
import com.xzm.xzm_interview_helper.grpc.client.PythonAiGrpcClient;
import com.xzm.xzm_interview_helper.model.dto.CreateInterviewAgentSessionRequest;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmChallengeResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemSummary;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentEventResponse;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentSessionResponse;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentStreamEvent;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentTurnResponse;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentEvent;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentSession;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentTurn;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmInterviewChallenge;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmSubmission;
import com.xzm.xzm_interview_helper.mapper.AiInterviewAgentEventMapper;
import com.xzm.xzm_interview_helper.mapper.AiInterviewAgentSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The server-side state machine for an adaptive interview. The model may recommend an action, but
 * this class is the authority for ownership, question-count limits, persistence, and recovery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewAgentOrchestrator {

    public static final int MIN_PRIMARY_QUESTIONS = 3;
    public static final int MAX_PRIMARY_QUESTIONS = 8;
    public static final int MAX_TOTAL_QUESTIONS = 15;
    public static final int MAX_CONVERSATIONAL_QUESTIONS = MAX_TOTAL_QUESTIONS - 1;

    /** Keep every cross-service prompt bounded and syntactically complete. */
    private static final int MAX_RESUME_CONTEXT_CHARS = 24_000;
    private static final int MAX_DIALOGUE_CONTEXT_CHARS = 24_000;
    private static final int MAX_QUESTION_CONTEXT_CHARS = 2_000;
    private static final int MAX_ANSWER_CONTEXT_CHARS = 8_000;
    private static final int MAX_DIALOGUE_QUESTION_CHARS = 1_600;
    private static final int MAX_DIALOGUE_ANSWER_CHARS = 4_800;
    private static final int MAX_DIALOGUE_EVALUATION_CHARS = 3_200;
    private static final int MAX_DIALOGUE_KNOWLEDGE_TAGS_CHARS = 800;
    private static final long PROCESSING_LEASE_MILLIS = 3L * 60L * 1000L;

    private static final String STATUS_READY = "READY";
    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_AWAITING_ANSWER = "AWAITING_ANSWER";
    private static final String STATUS_EVALUATING = "EVALUATING";
    private static final String STATUS_EVALUATION_FAILED = "EVALUATION_FAILED";
    private static final String STATUS_SUMMARIZING = "SUMMARIZING";
    private static final String STATUS_AWAITING_ALGORITHM = "AWAITING_ALGORITHM";
    private static final String STATUS_SUMMARY_FAILED = "SUMMARY_FAILED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final String ACTION_PRIMARY = InterviewFlowPolicy.ASK_PRIMARY;
    private static final String ACTION_FOLLOW_UP = InterviewFlowPolicy.ASK_FOLLOW_UP;
    private static final String ACTION_END = InterviewFlowPolicy.END_INTERVIEW;
    private static final String INTERVIEW_MODEL_PROVIDER = "deepseek";
    private static final String DEFAULT_INTERVIEW_MODEL = "deepseek-v4-pro";
    private static final Set<String> INTERVIEW_MODELS = Set.of(
            "deepseek-v4-flash",
            "deepseek-v4-pro"
    );
    private static final InterviewFlowPolicy FLOW_POLICY = new InterviewFlowPolicy(
            MIN_PRIMARY_QUESTIONS,
            MAX_PRIMARY_QUESTIONS,
            MAX_CONVERSATIONAL_QUESTIONS
    );

    private final AiInterviewAgentSessionService sessionService;
    private final AiInterviewAgentTurnService turnService;
    private final AiInterviewAgentEventService eventService;
    private final AiInterviewAgentSessionMapper sessionMapper;
    private final AiInterviewAgentEventMapper eventMapper;
    private final AlgorithmInterviewChallengeService challengeService;
    private final AlgorithmSubmissionService submissionService;
    private final AlgorithmProblemCatalogService problemCatalogService;
    private final PythonAiGrpcClient pythonAiGrpcClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public InterviewAgentSessionResponse createSession(
            int userId,
            CreateInterviewAgentSessionRequest request,
            String resumeFileName
    ) {
        return inTransaction(() -> createSessionRecord(userId, request, resumeFileName));
    }

    private InterviewAgentSessionResponse createSessionRecord(
            int userId,
            CreateInterviewAgentSessionRequest request,
            String resumeFileName
    ) {
        String resume = normalizeRequired(request.getResumeText(), "简历内容不能为空", 60_000);
        String modelProvider = trimToNull(request.getModelProvider(), 64);
        String modelName = trimToNull(request.getModelName(), 128);
        modelProvider = modelProvider == null ? INTERVIEW_MODEL_PROVIDER : modelProvider.toLowerCase(Locale.ROOT);
        modelName = modelName == null ? DEFAULT_INTERVIEW_MODEL : modelName.toLowerCase(Locale.ROOT);
        if (!INTERVIEW_MODEL_PROVIDER.equals(modelProvider) || !INTERVIEW_MODELS.contains(modelName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的面试模型");
        }

        AiInterviewAgentSession session = new AiInterviewAgentSession();
        session.setPublic_id(UUID.randomUUID().toString());
        session.setUser_id(userId);
        session.setStatus(STATUS_READY);
        session.setResume_text(resume);
        session.setResume_file_name(trimToNull(resumeFileName, 255));
        session.setTarget_role(trimToNull(request.getTargetRole(), 255));
        // Persist the allowlisted choice before the first model call so retries and resumed
        // sessions always use the same capability profile.
        session.setModel_provider(modelProvider);
        session.setModel_name(modelName);
        session.setThinking_enabled(Boolean.TRUE.equals(request.getEnableThinking()));
        session.setTotal_question_count(0);
        session.setPrimary_question_count(0);
        session.setFollow_up_count(0);
        session.setCreate_time(new Date());
        session.setUpdate_time(new Date());
        sessionService.save(session);

        recordEvent(session, null, "session", "resume_intake", "简历已安全保存", "已建立可恢复的面试会话。", "candidate");
        return toSessionResponse(session, true);
    }

    public Flux<InterviewAgentStreamEvent> startSession(String publicId, int userId) {
        return stream((sink, cancelled) -> {
            AiInterviewAgentSession session = recoverExpiredProcessingState(requireOwnedSession(publicId, userId));
            if (!transition(session, STATUS_READY, STATUS_GENERATING)) {
                emitSnapshot(sink, requireOwnedSession(publicId, userId));
                return;
            }
            session = requireOwnedSession(publicId, userId);
            try {
                emitStage(sink, session, "分析简历重点", "正在提取可验证的经历、技能与项目线索。", "resume_analysis");
                emitStage(sink, session, "检索岗位知识", "正在为首题补充相关知识范围。", "rag_search");
                emitStage(sink, session, "生成首个问题", "面试官正在组织首个主问题。", "question_generation");

                InterviewAgentResponse response = callAgent(session, "START", null, null, cancelled);
                throwIfCancelled(cancelled);
                AiInterviewAgentTurn turn = persistInitialQuestion(session, response, cancelled);
                AiInterviewAgentSession updated = requireOwnedSession(publicId, userId);
                emitQuestion(sink, updated, turn, response.getDecisionNote());
            } catch (Exception exception) {
                if (isCancelled(cancelled)) {
                    recoverStartCancellation(session);
                } else {
                    recoverStartFailure(session, exception);
                    emitError(sink, "暂时无法生成首个问题，已保留简历，可以重试。");
                }
            }
        });
    }

    public Flux<InterviewAgentStreamEvent> submitAnswer(String publicId, int userId, String answer) {
        return stream((sink, cancelled) -> {
            PendingAnswer pending;
            try {
                pending = claimAnswer(publicId, userId, answer);
            } catch (ResponseStatusException exception) {
                emitError(sink, exception.getReason());
                return;
            }
            advanceAfterAnswer(sink, pending, cancelled);
        });
    }

    public Flux<InterviewAgentStreamEvent> retry(String publicId, int userId) {
        AiInterviewAgentSession initial = recoverExpiredProcessingState(requireOwnedSession(publicId, userId));
        if (STATUS_READY.equals(initial.getStatus())) {
            return startSession(publicId, userId);
        }
        if (STATUS_SUMMARY_FAILED.equals(initial.getStatus())) {
            return resumeSummary(publicId, userId);
        }
        return stream((sink, cancelled) -> {
            AiInterviewAgentSession session = recoverExpiredProcessingState(requireOwnedSession(publicId, userId));
            if (!STATUS_EVALUATION_FAILED.equals(session.getStatus())) {
                emitSnapshot(sink, session);
                return;
            }

            AiInterviewAgentTurn turn = latestUnevaluatedAnsweredTurn(session.getId());
            if (turn == null) {
                emitError(sink, "没有可恢复的回答，请刷新会话后重试。");
                return;
            }
            if (!transition(session, STATUS_EVALUATION_FAILED, STATUS_EVALUATING)) {
                emitSnapshot(sink, requireOwnedSession(publicId, userId));
                return;
            }
            advanceAfterAnswer(sink, new PendingAnswer(requireOwnedSession(publicId, userId), turn), cancelled);
        });
    }

    /** Resume only the final report after an explicit stop; do not re-evaluate an answered turn. */
    private Flux<InterviewAgentStreamEvent> resumeSummary(String publicId, int userId) {
        return stream((sink, cancelled) -> {
            AiInterviewAgentSession session = recoverExpiredProcessingState(requireOwnedSession(publicId, userId));
            if (!STATUS_SUMMARY_FAILED.equals(session.getStatus())) {
                emitSnapshot(sink, session);
                return;
            }

            AiInterviewAgentTurn turn = latestEvaluatedAnsweredTurn(session.getId());
            if (turn == null) {
                emitError(sink, "没有可用于生成总结的已评估回答，请刷新会话后重试。");
                return;
            }
            if (!transition(session, STATUS_SUMMARY_FAILED, STATUS_SUMMARIZING)) {
                emitSnapshot(sink, requireOwnedSession(publicId, userId));
                return;
            }
            finishInterview(
                    sink,
                    requireOwnedSession(publicId, userId),
                    turn,
                    "正在恢复已完成面试的总结。",
                    cancelled
            );
        });
    }

    public InterviewAgentSessionResponse getSession(String publicId, int userId) {
        return toSessionResponse(recoverExpiredProcessingState(requireOwnedSession(publicId, userId)), true);
    }

    public Long saveAndRecordAlgorithmSubmission(
            String publicId,
            int userId,
            AlgorithmSubmission newSubmission,
            AlgorithmExecutionResponse result
    ) {
        return inTransaction(() -> {
            if (newSubmission == null || !StringUtils.hasText(newSubmission.getProblem_slug())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "算法提交内容不完整");
            }
            String problemSlug = newSubmission.getProblem_slug();
            String sourceCode = newSubmission.getSource_code();
            AiInterviewAgentSession session = sessionMapper.lockByPublicId(publicId);
            if (session == null || session.getUser_id() != userId) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "面试会话不存在");
            }
            if (!STATUS_AWAITING_ALGORITHM.equals(session.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前面试不在算法作答阶段");
            }
            AlgorithmInterviewChallenge challenge = challengeService.lambdaQuery()
                    .eq(AlgorithmInterviewChallenge::getInterview_session_id, session.getId())
                    .one();
            if (challenge == null || !challenge.getProblem_slug().equals(problemSlug)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "提交题目与面试终局题不一致");
            }
            if (!"ASSIGNED".equals(challenge.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "算法终局题已经结束，不能继续提交"
                );
            }
            newSubmission.setId(null);
            newSubmission.setUser_id(userId);
            newSubmission.setInterview_session_id(session.getId());
            if (newSubmission.getCreate_time() == null) {
                newSubmission.setCreate_time(new Date());
            }
            if (!submissionService.save(newSubmission) || newSubmission.getId() == null) {
                throw new IllegalStateException("Algorithm submission could not be persisted");
            }
            Long submissionId = newSubmission.getId();
            AlgorithmSubmission submission = submissionService.getById(submissionId);
            if (submission == null
                    || submission.getUser_id() == null
                    || submission.getUser_id() != userId
                    || !session.getId().equals(submission.getInterview_session_id())
                    || !challenge.getProblem_slug().equals(submission.getProblem_slug())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "算法提交与当前面试不匹配");
            }

            Date now = new Date();
            boolean expired = challenge.getDeadline_at() != null
                    && !now.before(challenge.getDeadline_at());
            boolean accepted = "ACCEPTED".equals(result.getStatus()) && !expired;
            challenge.setLatest_submission_id(submissionId);
            challenge.setUpdate_time(now);
            if (accepted || expired) {
                challenge.setStatus(accepted ? "ACCEPTED" : "TIME_EXPIRED");
                challenge.setCompleted_at(now);
            }
            if (!challengeService.updateById(challenge)) {
                throw new IllegalStateException("Algorithm challenge could not be updated");
            }

            AiInterviewAgentTurn turn = turnService.getById(challenge.getTurn_id());
            if (turn == null) {
                throw new IllegalStateException("Algorithm interview turn no longer exists");
            }
            turn.setAnswer(trimToNull(sourceCode, 30_000));
            int total = Math.max(0, result.getTotalCases());
            int passed = Math.max(0, Math.min(total, result.getPassedCases()));
            turn.setScore(total == 0 ? 0 : (int) Math.round(passed * 10.0 / total));
            turn.setEvaluation(
                    (accepted ? "算法题通过" : expired ? "算法题限时结束" : "算法题尚未通过")
                            + "，隐藏用例 " + passed + "/" + total + "。"
            );
            turn.setKnowledge_tags("算法实战," + challenge.getDifficulty());
            turn.setAgent_action(accepted || expired ? ACTION_END : "ALGORITHM_RETRY");
            turn.setDecision_note(accepted
                    ? "候选人在限时内通过算法终局题。"
                    : expired ? "算法终局题已到达限时。" : "仍可在剩余时间内继续提交。");
            if (accepted || expired) {
                turn.setAnswered_at(now);
                turn.setEvaluated_at(now);
            }
            if (!turnService.updateById(turn)) {
                throw new IllegalStateException("Algorithm interview turn could not be updated");
            }
            recordEvent(
                    session,
                    turn.getId(),
                    "algorithm_submission",
                    "algorithm_judge",
                    accepted ? "算法题提交通过" : expired ? "算法题限时结束" : "算法题提交未通过",
                    "隐藏用例 " + passed + "/" + total
                            + (result.getRuntimeMs() == null ? "" : " · " + result.getRuntimeMs() + " ms"),
                    "candidate"
            );
            result.setInterviewChallenge(true);
            result.setInterviewReadyToComplete(accepted || expired);
            return submissionId;
        });
    }

    public Flux<InterviewAgentStreamEvent> finishAfterAlgorithm(String publicId, int userId) {
        return stream((sink, cancelled) -> {
            AlgorithmCompletion completion;
            try {
                completion = prepareAlgorithmCompletion(publicId, userId);
            } catch (ResponseStatusException exception) {
                emitError(sink, exception.getReason());
                return;
            }
            finishInterview(
                    sink,
                    completion.session(),
                    completion.turn(),
                    "算法终局题已完成，正在生成包含算法表现的总结。",
                    cancelled
            );
        });
    }

    public Flux<InterviewAgentStreamEvent> abandonAlgorithm(String publicId, int userId) {
        return stream((sink, cancelled) -> {
            AlgorithmCompletion completion;
            try {
                completion = prepareAlgorithmAbandonment(publicId, userId);
            } catch (ResponseStatusException exception) {
                emitError(sink, exception.getReason());
                return;
            }
            finishInterview(
                    sink,
                    completion.session(),
                    completion.turn(),
                    "候选人已主动放弃算法终局题，正在生成包含该结果的面试总结。",
                    cancelled
            );
        });
    }

    private AlgorithmCompletion prepareAlgorithmAbandonment(String publicId, int userId) {
        return inTransaction(() -> {
            AiInterviewAgentSession session = sessionMapper.lockByPublicId(publicId);
            if (session == null || session.getUser_id() != userId) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "面试会话不存在");
            }
            if (!STATUS_AWAITING_ALGORITHM.equals(session.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前面试不在算法作答阶段");
            }
            AlgorithmInterviewChallenge challenge = challengeService.lambdaQuery()
                    .eq(AlgorithmInterviewChallenge::getInterview_session_id, session.getId())
                    .one();
            if (challenge == null) {
                throw new IllegalStateException("Algorithm challenge no longer exists");
            }
            if (!"ASSIGNED".equals(challenge.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "算法题已经结束，不能重复放弃");
            }

            Date now = new Date();
            challenge.setStatus("ABANDONED");
            challenge.setCompleted_at(now);
            challenge.setUpdate_time(now);
            challengeService.updateById(challenge);

            AiInterviewAgentTurn turn = turnService.getById(challenge.getTurn_id());
            if (turn == null) {
                throw new IllegalStateException("Algorithm interview turn no longer exists");
            }
            turn.setAnswer("[候选人主动放弃算法终局题]");
            turn.setScore(0);
            turn.setEvaluation("候选人主动结束算法环节，未产生可判定的通过记录。");
            turn.setKnowledge_tags("算法实战," + challenge.getDifficulty());
            turn.setAgent_action(ACTION_END);
            turn.setDecision_note("候选人主动放弃算法终局题。");
            turn.setAnswered_at(now);
            turn.setEvaluated_at(now);
            turnService.updateById(turn);

            recordEvent(
                    session,
                    turn.getId(),
                    "algorithm_abandoned",
                    "algorithm_judge",
                    "候选人放弃算法题",
                    "算法终局题已按主动放弃记录，本题计 0 分。",
                    "candidate"
            );
            session.setStatus(STATUS_SUMMARIZING);
            session.setUpdate_time(now);
            sessionService.updateById(session);
            recordEvent(
                    session,
                    turn.getId(),
                    "stage",
                    "summary_generation",
                    "算法环节已结束",
                    "正在把问答表现与主动放弃结果合并为最终报告。",
                    "candidate"
            );
            return new AlgorithmCompletion(session, turn);
        });
    }

    private AlgorithmCompletion prepareAlgorithmCompletion(String publicId, int userId) {
        return inTransaction(() -> {
            AiInterviewAgentSession session = sessionMapper.lockByPublicId(publicId);
            if (session == null || session.getUser_id() != userId) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "面试会话不存在");
            }
            if (!STATUS_AWAITING_ALGORITHM.equals(session.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前面试不在算法作答阶段");
            }
            AlgorithmInterviewChallenge challenge = challengeService.lambdaQuery()
                    .eq(AlgorithmInterviewChallenge::getInterview_session_id, session.getId())
                    .one();
            if (challenge == null) {
                throw new IllegalStateException("Algorithm challenge no longer exists");
            }
            Date now = new Date();
            if ("ASSIGNED".equals(challenge.getStatus())) {
                if (challenge.getDeadline_at() == null || now.before(challenge.getDeadline_at())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "算法题尚未通过且仍在限时内");
                }
                challenge.setStatus("TIME_EXPIRED");
                challenge.setCompleted_at(now);
                challenge.setUpdate_time(now);
                challengeService.updateById(challenge);
            }
            if (!Set.of("ACCEPTED", "TIME_EXPIRED").contains(challenge.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "算法题尚未达到结束条件");
            }
            AiInterviewAgentTurn turn = turnService.getById(challenge.getTurn_id());
            if (turn == null) {
                throw new IllegalStateException("Algorithm interview turn no longer exists");
            }
            if (turn.getEvaluated_at() == null) {
                turn.setAnswer("[限时结束前未提交可判定代码]");
                turn.setScore(0);
                turn.setEvaluation("算法终局题限时结束，未产生通过记录。");
                turn.setKnowledge_tags("算法实战," + challenge.getDifficulty());
                turn.setAgent_action(ACTION_END);
                turn.setDecision_note("算法终局题已到达限时。");
                turn.setAnswered_at(now);
                turn.setEvaluated_at(now);
                turnService.updateById(turn);
            }
            session.setStatus(STATUS_SUMMARIZING);
            session.setUpdate_time(now);
            sessionService.updateById(session);
            recordEvent(
                    session,
                    turn.getId(),
                    "stage",
                    "summary_generation",
                    "算法环节已结束",
                    "正在把问答与算法表现合并为最终报告。",
                    "candidate"
            );
            return new AlgorithmCompletion(session, turn);
        });
    }

    public List<InterviewAgentSessionResponse> listSessions(int userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return sessionService.lambdaQuery()
                .eq(AiInterviewAgentSession::getUser_id, userId)
                .orderByDesc(AiInterviewAgentSession::getUpdate_time)
                .last("LIMIT " + safeLimit)
                .list()
                .stream()
                .map(session -> toSessionResponse(session, false))
                .toList();
    }

    private void advanceAfterAnswer(
            FluxSink<InterviewAgentStreamEvent> sink,
            PendingAnswer pending,
            AtomicBoolean cancelled
    ) {
        AiInterviewAgentSession session = pending.session();
        AiInterviewAgentTurn currentTurn = pending.turn();
        try {
            emitStage(sink, session, "评估回答", "正在核对回答的完整性、准确性与项目细节。", "answer_evaluation");
            emitStage(sink, session, "检索参考知识", "正在检索与本题相关的知识依据。", "rag_search");
            emitStage(sink, session, "决定下一步", "正在决定追问、切换主题或结束面试。", "interview_decision");

            InterviewAgentResponse response = callAgent(
                    session,
                    "ANSWER",
                    currentTurn.getQuestion(),
                    currentTurn.getAnswer(),
                    cancelled
            );
            throwIfCancelled(cancelled);
            AiInterviewAgentSession refreshed = requireOwnedSession(session.getPublic_id(), session.getUser_id());
            String action = constrainAction(response.getAction(), refreshed);

            if (ACTION_END.equals(action)) {
                AlgorithmAssignment assignment = persistEvaluationAndAssignAlgorithm(
                        refreshed,
                        currentTurn,
                        response,
                        action,
                        cancelled
                );
                emitAlgorithmChallenge(sink, assignment.session(), assignment.turn());
                return;
            }

            AiInterviewAgentTurn nextTurn = persistEvaluationAndNextQuestion(
                    refreshed,
                    currentTurn,
                    response,
                    action,
                    cancelled
            );
            AiInterviewAgentSession updated = requireOwnedSession(refreshed.getPublic_id(), refreshed.getUser_id());
            emitQuestion(sink, updated, nextTurn, response.getDecisionNote());
        } catch (Exception exception) {
            if (isCancelled(cancelled)) {
                recoverEvaluationCancellation(session, currentTurn);
            } else {
                recoverEvaluationFailure(session, currentTurn, exception);
                emitError(sink, "本题回答已保存，但暂时无法完成评估。请稍后重试。");
            }
        }
    }

    private void finishInterview(
            FluxSink<InterviewAgentStreamEvent> sink,
            AiInterviewAgentSession session,
            AiInterviewAgentTurn currentTurn,
            String decisionNote,
            AtomicBoolean cancelled
    ) {
        AiInterviewAgentSession summarizing = requireOwnedSession(session.getPublic_id(), session.getUser_id());
        if (!STATUS_SUMMARIZING.equals(summarizing.getStatus())) {
            throw new IllegalStateException("Interview state changed before summary generation");
        }
        emitStage(sink, summarizing, "生成面试总结", "正在汇总表现、优势与下一步练习建议。", "summary_generation");

        try {
            InterviewAgentResponse summaryResponse = callAgent(
                    summarizing,
                    "SUMMARIZE",
                    currentTurn.getQuestion(),
                    currentTurn.getAnswer(),
                    cancelled
            );
            String summary = requireAgentSummary(summaryResponse.getSummary());

            throwIfCancelled(cancelled);
            AiInterviewAgentSession completed = persistCompletion(
                    session.getId(),
                    currentTurn.getId(),
                    summary,
                    cancelled
            );

            InterviewAgentStreamEvent event = new InterviewAgentStreamEvent();
            event.setType("completed");
            event.setTitle("本次模拟面试已完成");
            event.setDetail(displaySafeNote(decisionNote, "已达到本轮面试的结束条件。"));
            event.setSummary(summary);
            attachCounts(event, completed);
            event.setSession(toSessionResponse(completed, true));
            emit(sink, event);
        } catch (CancellationException exception) {
            recoverSummaryCancellation(summarizing, currentTurn);
            throw exception;
        } catch (Exception exception) {
            recoverSummaryFailure(summarizing, currentTurn, exception);
            emitError(sink, "面试问答与评估已保存，但总结暂时无法生成。请稍后重试。");
        }
    }

    private AiInterviewAgentSession persistCompletion(
            Long sessionId,
            Long turnId,
            String summary,
            AtomicBoolean cancelled
    ) {
        return persistUnlessCancelled(cancelled, () -> inTransaction(() -> {
            AiInterviewAgentSession completed = sessionMapper.lockById(sessionId);
            if (completed == null || !STATUS_SUMMARIZING.equals(completed.getStatus())) {
                throw new IllegalStateException("Interview state changed before completion persistence");
            }
            completed.setSummary(summary);
            completed.setStatus(STATUS_COMPLETED);
            completed.setCompleted_at(new Date());
            completed.setUpdate_time(new Date());
            sessionService.updateById(completed);
            recordEvent(completed, turnId, "completed", "summary_generation", "面试已完成", "已生成可回看的面试总结。", "candidate");
            return completed;
        }));
    }

    private PendingAnswer claimAnswer(String publicId, int userId, String rawAnswer) {
        return inTransaction(() -> claimAnswerRecord(publicId, userId, rawAnswer));
    }

    private PendingAnswer claimAnswerRecord(String publicId, int userId, String rawAnswer) {
        String answer = normalizeRequired(rawAnswer, "回答不能为空", 30_000);
        AiInterviewAgentSession session = lockOwnedSession(publicId, userId);
        if (!STATUS_AWAITING_ANSWER.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前会话暂时不能提交回答，请刷新后重试");
        }

        AiInterviewAgentTurn turn = latestPendingTurn(session.getId());
        if (turn == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "没有等待回答的问题");
        }
        if (!transition(session, STATUS_AWAITING_ANSWER, STATUS_EVALUATING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "回答正在处理，请勿重复提交");
        }

        turn.setAnswer(answer);
        turn.setAnswered_at(new Date());
        turnService.updateById(turn);
        AiInterviewAgentSession updated = requireOwnedSession(publicId, userId);
        recordEvent(updated, turn.getId(), "answer", "answer_capture", "回答已保存", "正在由面试官评估本题回答。", "candidate");
        return new PendingAnswer(updated, turn);
    }

    private AiInterviewAgentTurn persistInitialQuestion(
            AiInterviewAgentSession original,
            InterviewAgentResponse response,
            AtomicBoolean cancelled
    ) {
        return persistUnlessCancelled(cancelled, () ->
                inTransaction(() -> persistInitialQuestionRecord(original, response))
        );
    }

    private AiInterviewAgentTurn persistInitialQuestionRecord(AiInterviewAgentSession original, InterviewAgentResponse response) {
        AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
        if (session == null) {
            throw new IllegalStateException("Interview session no longer exists");
        }
        if (!STATUS_GENERATING.equals(session.getStatus())) {
            throw new IllegalStateException("会话状态已变化，不能写入首题");
        }
        AiInterviewAgentTurn turn = newQuestion(
                session,
                null,
                ACTION_PRIMARY,
                response
        );
        session.setTotal_question_count(count(session.getTotal_question_count()) + 1);
        session.setPrimary_question_count(count(session.getPrimary_question_count()) + 1);
        session.setStatus(STATUS_AWAITING_ANSWER);
        session.setStarted_at(new Date());
        session.setUpdate_time(new Date());
        sessionService.updateById(session);
        recordEvent(session, turn.getId(), "question", "question_generation", "首个主问题已准备好", "面试官已根据简历选择首个话题。", "candidate");
        return turn;
    }

    private AiInterviewAgentTurn persistNextQuestionRecord(
            AiInterviewAgentSession original,
            AiInterviewAgentTurn parentTurn,
            InterviewAgentResponse response,
            String action
    ) {
        AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
        if (session == null) {
            throw new IllegalStateException("Interview session no longer exists");
        }
        if (!STATUS_EVALUATING.equals(session.getStatus())) {
            throw new IllegalStateException("会话状态已变化，不能写入下一题");
        }
        AiInterviewAgentTurn turn = newQuestion(
                session,
                ACTION_FOLLOW_UP.equals(action) ? parentTurn.getId() : null,
                action,
                response
        );
        session.setTotal_question_count(count(session.getTotal_question_count()) + 1);
        if (ACTION_PRIMARY.equals(action)) {
            session.setPrimary_question_count(count(session.getPrimary_question_count()) + 1);
        } else {
            session.setFollow_up_count(count(session.getFollow_up_count()) + 1);
        }
        session.setStatus(STATUS_AWAITING_ANSWER);
        session.setUpdate_time(new Date());
        sessionService.updateById(session);
        String title = ACTION_FOLLOW_UP.equals(action) ? "准备了一道追问" : "进入下一个主问题";
        recordEvent(session, turn.getId(), "question", "question_generation", title, displaySafeNote(response.getDecisionNote(), "已根据上一轮表现调整后续问题。"), "candidate");
        return turn;
    }

    private AiInterviewAgentTurn newQuestion(
            AiInterviewAgentSession session,
            Long parentTurnId,
            String action,
            InterviewAgentResponse response
    ) {
        String kind = ACTION_FOLLOW_UP.equals(action) ? "FOLLOW_UP" : "PRIMARY";
        AiInterviewAgentTurn turn = new AiInterviewAgentTurn();
        turn.setSession_id(session.getId());
        turn.setSequence_no(count(session.getTotal_question_count()) + 1);
        turn.setParent_turn_id(parentTurnId);
        turn.setQuestion_kind(kind);
        String generatedQuestion = trimToNull(response.getQuestion(), 2_000);
        if (!StringUtils.hasText(generatedQuestion)) {
            generatedQuestion = ACTION_FOLLOW_UP.equals(action)
                    ? "请补充一个能验证你刚才结论的具体实现细节或案例。"
                    : "请结合你的经历，说明一个最能体现岗位核心能力的技术决策。";
        }
        turn.setQuestion(generatedQuestion);
        turn.setAgent_action(action);
        turn.setDecision_note(displaySafeNote(response.getDecisionNote(), "根据当前面试进度选择了这个问题。"));
        turn.setModel_provider(trimToNull(session.getModel_provider(), 64));
        turn.setModel_name(trimToNull(response.getModelName(), 128));
        turn.setCreated_at(new Date());
        turnService.save(turn);
        return turn;
    }

    private AiInterviewAgentTurn persistEvaluationAndNextQuestion(
            AiInterviewAgentSession session,
            AiInterviewAgentTurn currentTurn,
            InterviewAgentResponse response,
            String action,
            AtomicBoolean cancelled
    ) {
        return persistUnlessCancelled(cancelled, () -> inTransaction(() -> {
            persistEvaluationRecord(currentTurn, response, action);
            return persistNextQuestionRecord(session, currentTurn, response, action);
        }));
    }

    private AlgorithmAssignment persistEvaluationAndAssignAlgorithm(
            AiInterviewAgentSession original,
            AiInterviewAgentTurn currentTurn,
            InterviewAgentResponse response,
            String action,
            AtomicBoolean cancelled
    ) {
        return persistUnlessCancelled(cancelled, () -> inTransaction(() -> {
            persistEvaluationRecord(currentTurn, response, action);
            AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
            if (session == null || !STATUS_EVALUATING.equals(session.getStatus())) {
                throw new IllegalStateException("Interview state changed before algorithm assignment");
            }

            String difficulty = adaptiveAlgorithmDifficulty(session.getId());
            List<AlgorithmProblemSummary> candidates = problemCatalogService.judgeableByDifficulty(difficulty);
            AlgorithmProblemSummary problem = candidates.get(
                    Math.floorMod(session.getId().hashCode(), candidates.size()));

            AiInterviewAgentTurn algorithmTurn = new AiInterviewAgentTurn();
            algorithmTurn.setSession_id(session.getId());
            algorithmTurn.setSequence_no(count(session.getTotal_question_count()) + 1);
            algorithmTurn.setQuestion_kind("ALGORITHM");
            algorithmTurn.setQuestion("算法终局题：" + problem.getFrontendId() + ". " + problem.getTitle());
            algorithmTurn.setAgent_action("ALGORITHM_CHALLENGE");
            algorithmTurn.setDecision_note("技术问答信号已足够，进入与候选人表现匹配的算法实战。");
            algorithmTurn.setModel_provider(session.getModel_provider());
            algorithmTurn.setModel_name(session.getModel_name());
            algorithmTurn.setCreated_at(new Date());
            turnService.save(algorithmTurn);

            Date startedAt = new Date();
            AlgorithmInterviewChallenge challenge = new AlgorithmInterviewChallenge();
            challenge.setInterview_session_id(session.getId());
            challenge.setTurn_id(algorithmTurn.getId());
            challenge.setUser_id(session.getUser_id());
            challenge.setProblem_slug(problem.getSlug());
            challenge.setDifficulty(problem.getDifficulty());
            challenge.setTime_limit_minutes(problem.getTimeLimitMinutes());
            challenge.setStatus("ASSIGNED");
            challenge.setStarted_at(startedAt);
            challenge.setDeadline_at(new Date(startedAt.getTime() + problem.getTimeLimitMinutes() * 60_000L));
            challenge.setCreate_time(startedAt);
            challenge.setUpdate_time(startedAt);
            challengeService.save(challenge);

            session.setTotal_question_count(count(session.getTotal_question_count()) + 1);
            session.setStatus(STATUS_AWAITING_ALGORITHM);
            session.setUpdate_time(new Date());
            sessionService.updateById(session);
            recordEvent(
                    session,
                    algorithmTurn.getId(),
                    "algorithm",
                    "algorithm_assignment",
                    "算法终局题已分配",
                    problem.getTitle() + " · " + problem.getDifficulty()
                            + " · 限时 " + problem.getTimeLimitMinutes() + " 分钟",
                    "candidate"
            );
            return new AlgorithmAssignment(session, algorithmTurn, challenge);
        }));
    }

    private String adaptiveAlgorithmDifficulty(Long sessionId) {
        List<AiInterviewAgentTurn> evaluated = turnService.lambdaQuery()
                .eq(AiInterviewAgentTurn::getSession_id, sessionId)
                .isNotNull(AiInterviewAgentTurn::getScore)
                .list();
        double average = evaluated.stream()
                .map(AiInterviewAgentTurn::getScore)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(5.0);
        if (average >= 8.0) {
            return "HARD";
        }
        if (average >= 5.0) {
            return "MEDIUM";
        }
        return "EASY";
    }

    private void persistEvaluationRecord(
            AiInterviewAgentTurn turn,
            InterviewAgentResponse response,
            String effectiveAction
    ) {
        AiInterviewAgentSession session = sessionMapper.lockById(turn.getSession_id());
        if (session == null || !STATUS_EVALUATING.equals(session.getStatus())) {
            throw new IllegalStateException("Interview state changed before evaluation persistence");
        }
        AiInterviewAgentTurn persistedTurn = turnService.getById(turn.getId());
        if (persistedTurn == null || persistedTurn.getEvaluated_at() != null) {
            throw new IllegalStateException("Interview turn was already evaluated");
        }
        persistedTurn.setScore(Math.max(0, Math.min(10, response.getScore())));
        persistedTurn.setEvaluation(trimToNull(response.getEvaluation(), 20_000));
        persistedTurn.setKnowledge_tags(trimToNull(response.getKnowledgeTags(), 4_000));
        persistedTurn.setReference_answer(trimToNull(response.getReferenceAnswer(), 20_000));
        persistedTurn.setAgent_action(trimToNull(effectiveAction, 32));
        persistedTurn.setDecision_note(displaySafeNote(response.getDecisionNote(), "已完成本题评估。"));
        persistedTurn.setEvaluated_at(new Date());
        turnService.updateById(persistedTurn);
    }

    private InterviewAgentResponse callAgent(
            AiInterviewAgentSession session,
            String operation,
            String currentQuestion,
            String answer,
            AtomicBoolean cancelled
    ) {
        ContextSlice resume = clipContext(session.getResume_text(), MAX_RESUME_CONTEXT_CHARS);
        ContextSlice dialogue = buildBoundedDialogueJson(session.getId());
        ContextSlice question = clipContext(currentQuestion, MAX_QUESTION_CONTEXT_CHARS);
        ContextSlice candidateAnswer = clipContext(answer, MAX_ANSWER_CONTEXT_CHARS);
        if (resume.truncated() || dialogue.truncated() || question.truncated() || candidateAnswer.truncated()) {
            recordEvent(
                    session,
                    null,
                    "context_window",
                    "context_budget",
                    "Context window compacted",
                    "The full resume and answers remain saved; only a bounded recent excerpt was sent to the model.",
                    "candidate"
            );
        }
        InterviewAgentRequest.Builder request = InterviewAgentRequest.newBuilder()
                .setOperation(operation)
                .setResumeText(resume.value())
                .setTargetRole(session.getTarget_role() == null ? "" : session.getTarget_role())
                .setDialogueJson(dialogue.value())
                .setCurrentQuestion(question.value())
                .setCandidateAnswer(candidateAnswer.value())
                .setTotalQuestionCount(count(session.getTotal_question_count()))
                .setPrimaryQuestionCount(count(session.getPrimary_question_count()))
                .setFollowUpCount(count(session.getFollow_up_count()))
                .setMaxTotalQuestions(MAX_CONVERSATIONAL_QUESTIONS)
                .setMinPrimaryQuestions(MIN_PRIMARY_QUESTIONS)
                .setMaxPrimaryQuestions(MAX_PRIMARY_QUESTIONS);

        if (StringUtils.hasText(session.getModel_provider())) {
            request.setProvider(session.getModel_provider());
        }
        if (StringUtils.hasText(session.getModel_name())) {
            request.setModelName(session.getModel_name());
        }
        if (session.getThinking_enabled() != null) {
            request.setEnableThinking(session.getThinking_enabled());
        }

        throwIfCancelled(cancelled);
        InterviewAgentResponse response = pythonAiGrpcClient.runInterviewAgent(request.build(), cancelled);
        throwIfCancelled(cancelled);
        if (!response.getSuccess()) {
            throw new IllegalStateException("AI agent call failed");
        }
        captureModelMetadata(session, response);
        recordEvent(
                session,
                null,
                "tool_result",
                "rag_search",
                "Knowledge retrieval completed",
                response.getRagHitCount() > 0
                        ? "Retrieved " + response.getRagHitCount() + " relevant knowledge context item(s)."
                        : "No additional knowledge context was required for this turn.",
                "candidate"
        );
        return response;
    }

    private void captureModelMetadata(AiInterviewAgentSession session, InterviewAgentResponse response) {
        inTransaction(() -> {
            AiInterviewAgentSession locked = sessionMapper.lockById(session.getId());
            if (locked == null) {
                throw new IllegalStateException("Interview session no longer exists");
            }
            String effectiveModel = trimToNull(response.getModelName(), 128);
            String effectiveProvider = trimToNull(response.getModelProvider(), 64);
            boolean providerChanged = StringUtils.hasText(effectiveProvider)
                    && !effectiveProvider.equals(locked.getModel_provider());
            boolean modelChanged = StringUtils.hasText(effectiveModel)
                    && !effectiveModel.equals(locked.getModel_name());
            boolean thinkingChanged = locked.getThinking_enabled() == null
                    || locked.getThinking_enabled() != response.getThinkingEnabled();
            if (providerChanged || modelChanged || thinkingChanged) {
                sessionService.lambdaUpdate()
                        .eq(AiInterviewAgentSession::getId, locked.getId())
                        .set(providerChanged, AiInterviewAgentSession::getModel_provider, effectiveProvider)
                        .set(modelChanged, AiInterviewAgentSession::getModel_name, effectiveModel)
                        .set(thinkingChanged, AiInterviewAgentSession::getThinking_enabled, response.getThinkingEnabled())
                        .set(AiInterviewAgentSession::getUpdate_time, new Date())
                        .update();
            }
        });
    }

    private String constrainAction(String rawAction, AiInterviewAgentSession session) {
        return FLOW_POLICY.constrain(
                rawAction,
                count(session.getTotal_question_count()),
                count(session.getPrimary_question_count())
        );
    }

    private void recoverStartFailure(AiInterviewAgentSession original, Exception exception) {
        inTransaction(() -> recoverStartFailureRecord(original, exception));
    }

    private void recoverStartCancellation(AiInterviewAgentSession original) {
        inTransaction(() -> {
            AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
            if (session != null && STATUS_GENERATING.equals(session.getStatus())) {
                session.setStatus(STATUS_READY);
                session.setUpdate_time(new Date());
                sessionService.updateById(session);
                recordEvent(session, null, "cancelled", "question_generation", "已停止首题生成", "已停止本次模型请求，简历会话仍可继续。", "candidate");
            }
        });
    }

    private void recoverStartFailureRecord(AiInterviewAgentSession original, Exception exception) {
        log.warn("Interview start failed for session {}", original.getPublic_id(), exception);
        AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
        if (session != null && STATUS_GENERATING.equals(session.getStatus())) {
            session.setStatus(STATUS_READY);
            session.setUpdate_time(new Date());
            sessionService.updateById(session);
            recordEvent(session, null, "error", "question_generation", "首题生成失败", "服务暂不可用，保留会话以便重试。", "candidate");
        }
    }

    private void recoverEvaluationFailure(AiInterviewAgentSession original, AiInterviewAgentTurn turn, Exception exception) {
        inTransaction(() -> recoverEvaluationFailureRecord(original, turn, exception));
    }

    private void recoverEvaluationCancellation(AiInterviewAgentSession original, AiInterviewAgentTurn turn) {
        inTransaction(() -> {
            AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
            if (session != null && STATUS_EVALUATING.equals(session.getStatus())) {
                session.setStatus(STATUS_EVALUATION_FAILED);
                session.setUpdate_time(new Date());
                sessionService.updateById(session);
                recordEvent(session, turn.getId(), "cancelled", "answer_evaluation", "已停止本题评估", "回答已保存，可在准备好后继续评估。", "candidate");
            }
        });
    }

    private void recoverSummaryCancellation(AiInterviewAgentSession original, AiInterviewAgentTurn turn) {
        inTransaction(() -> {
            AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
            if (session != null && STATUS_SUMMARIZING.equals(session.getStatus())) {
                session.setStatus(STATUS_SUMMARY_FAILED);
                session.setUpdate_time(new Date());
                sessionService.updateById(session);
                recordEvent(
                        session,
                        turn.getId(),
                        "cancelled",
                        "summary_generation",
                        "已停止面试总结生成",
                        "面试回答和评估已保留，可在准备好后重新生成总结。",
                        "candidate"
                );
            }
        });
    }

    private void recoverSummaryFailure(
            AiInterviewAgentSession original,
            AiInterviewAgentTurn turn,
            Exception exception
    ) {
        log.warn("Interview summary generation failed for session {}", original.getPublic_id(), exception);
        inTransaction(() -> {
            AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
            if (session != null && STATUS_SUMMARIZING.equals(session.getStatus())) {
                session.setStatus(STATUS_SUMMARY_FAILED);
                session.setUpdate_time(new Date());
                sessionService.updateById(session);
                recordEvent(
                        session,
                        turn.getId(),
                        "error",
                        "summary_generation",
                        "面试总结暂未完成",
                        "面试问答与评估已保存，可以安全重试总结生成。",
                        "candidate"
                );
            }
        });
    }

    private void recoverEvaluationFailureRecord(AiInterviewAgentSession original, AiInterviewAgentTurn turn, Exception exception) {
        log.warn("Interview evaluation failed for session {}, turn {}", original.getPublic_id(), turn.getId(), exception);
        AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
        if (session != null && STATUS_EVALUATING.equals(session.getStatus())) {
            session.setStatus(STATUS_EVALUATION_FAILED);
            session.setUpdate_time(new Date());
            sessionService.updateById(session);
            recordEvent(session, turn.getId(), "error", "answer_evaluation", "本题评估暂未完成", "回答已保存，可以安全重试。", "candidate");
        }
    }

    /**
     * A process restart cannot run the in-memory cancellation handler. Transient states therefore
     * use update_time as a processing lease. Once that lease expires, move the session to the
     * nearest durable/retryable state without creating another model request.
     */
    private AiInterviewAgentSession recoverExpiredProcessingState(AiInterviewAgentSession original) {
        if (!isExpiredProcessingState(original)) {
            return original;
        }
        return inTransaction(() -> {
            AiInterviewAgentSession session = sessionMapper.lockById(original.getId());
            if (session == null || !isExpiredProcessingState(session)) {
                return session == null ? original : session;
            }
            Date now = new Date();
            switch (session.getStatus()) {
                case STATUS_GENERATING -> {
                    session.setStatus(STATUS_READY);
                    session.setUpdate_time(now);
                    sessionService.updateById(session);
                    recordEvent(session, null, "recovered", "processing_lease", "已恢复首题生成", "检测到过期请求，简历会话可安全重试。", "candidate");
                }
                case STATUS_EVALUATING -> {
                    AiInterviewAgentTurn turn = latestUnevaluatedAnsweredTurn(session.getId());
                    session.setStatus(STATUS_EVALUATION_FAILED);
                    session.setUpdate_time(now);
                    sessionService.updateById(session);
                    recordEvent(
                            session,
                            turn == null ? null : turn.getId(),
                            "recovered",
                            "processing_lease",
                            "已恢复本题评估",
                            "检测到过期请求，已保留回答，可安全重试。",
                            "candidate"
                    );
                }
                case STATUS_SUMMARIZING -> {
                    session.setStatus(STATUS_SUMMARY_FAILED);
                    session.setUpdate_time(now);
                    sessionService.updateById(session);
                    recordEvent(session, null, "recovered", "summary_generation", "已恢复面试总结", "检测到过期总结请求，可安全重新生成总结。", "candidate");
                }
                default -> {
                    return session;
                }
            }
            return session;
        });
    }

    private boolean isExpiredProcessingState(AiInterviewAgentSession session) {
        if (session == null || !List.of(STATUS_GENERATING, STATUS_EVALUATING, STATUS_SUMMARIZING).contains(session.getStatus())) {
            return false;
        }
        Date updatedAt = session.getUpdate_time();
        return updatedAt == null || System.currentTimeMillis() - updatedAt.getTime() >= PROCESSING_LEASE_MILLIS;
    }

    private boolean transition(AiInterviewAgentSession session, String expected, String target) {
        return sessionService.lambdaUpdate()
                .eq(AiInterviewAgentSession::getId, session.getId())
                .eq(AiInterviewAgentSession::getStatus, expected)
                .set(AiInterviewAgentSession::getStatus, target)
                .set(AiInterviewAgentSession::getUpdate_time, new Date())
                .update();
    }

    private AiInterviewAgentSession lockOwnedSession(String publicId, int userId) {
        AiInterviewAgentSession owned = requireOwnedSession(publicId, userId);
        AiInterviewAgentSession locked = sessionMapper.lockById(owned.getId());
        if (locked == null || !Integer.valueOf(userId).equals(locked.getUser_id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该面试会话");
        }
        return locked;
    }

    private AiInterviewAgentSession requireOwnedSession(String publicId, int userId) {
        AiInterviewAgentSession session = sessionService.lambdaQuery()
                .eq(AiInterviewAgentSession::getPublic_id, publicId)
                .eq(AiInterviewAgentSession::getUser_id, userId)
                .one();
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该面试会话");
        }
        return session;
    }

    private AiInterviewAgentTurn latestPendingTurn(Long sessionId) {
        return turnService.lambdaQuery()
                .eq(AiInterviewAgentTurn::getSession_id, sessionId)
                .isNull(AiInterviewAgentTurn::getAnswer)
                .orderByDesc(AiInterviewAgentTurn::getSequence_no)
                .one();
    }

    private AiInterviewAgentTurn latestUnevaluatedAnsweredTurn(Long sessionId) {
        return turnService.lambdaQuery()
                .eq(AiInterviewAgentTurn::getSession_id, sessionId)
                .isNotNull(AiInterviewAgentTurn::getAnswer)
                .isNull(AiInterviewAgentTurn::getEvaluated_at)
                .orderByDesc(AiInterviewAgentTurn::getSequence_no)
                .one();
    }

    private AiInterviewAgentTurn latestEvaluatedAnsweredTurn(Long sessionId) {
        return turnService.lambdaQuery()
                .eq(AiInterviewAgentTurn::getSession_id, sessionId)
                .isNotNull(AiInterviewAgentTurn::getAnswer)
                .isNotNull(AiInterviewAgentTurn::getEvaluated_at)
                .orderByDesc(AiInterviewAgentTurn::getSequence_no)
                .one();
    }

    private ContextSlice buildBoundedDialogueJson(Long sessionId) {
        List<AiInterviewAgentTurn> turns = turnService.lambdaQuery()
                .eq(AiInterviewAgentTurn::getSession_id, sessionId)
                .orderByAsc(AiInterviewAgentTurn::getSequence_no)
                .list();
        AlgorithmSubmission algorithmReview =
                latestCompletedAlgorithmReview(sessionId, turns);
        List<Map<String, Object>> dialogue = new ArrayList<>();
        boolean truncated = false;
        int used = 2; // JSON array brackets

        // Retain a contiguous suffix: the latest turns carry the current interview context.
        for (int index = turns.size() - 1; index >= 0; index--) {
            AiInterviewAgentTurn turn = turns.get(index);
            ContextSlice question = clipContext(turn.getQuestion(), MAX_DIALOGUE_QUESTION_CHARS);
            ContextSlice answer = clipContext(turn.getAnswer(), MAX_DIALOGUE_ANSWER_CHARS);
            ContextSlice evaluation =
                    clipContext(turn.getEvaluation(), MAX_DIALOGUE_EVALUATION_CHARS);
            ContextSlice knowledgeTags =
                    clipContext(turn.getKnowledge_tags(), MAX_DIALOGUE_KNOWLEDGE_TAGS_CHARS);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sequence", turn.getSequence_no());
            item.put("kind", turn.getQuestion_kind());
            item.put("question", question.value());
            item.put("answer", answer.value());
            item.put("score", turn.getScore());
            item.put("evaluation", evaluation.value());
            item.put("knowledge_tags", knowledgeTags.value());
            if ("ALGORITHM".equals(turn.getQuestion_kind()) && algorithmReview != null) {
                ContextSlice aiEvaluation = clipContext(
                        algorithmReview.getAi_evaluation(),
                        MAX_DIALOGUE_EVALUATION_CHARS
                );
                item.put("algorithmAiScore", algorithmReview.getAi_score());
                item.put("algorithmAiEvaluation", aiEvaluation.value());
                truncated = truncated || aiEvaluation.truncated();
            }
            try {
                String serializedItem = objectMapper.writeValueAsString(item);
                int contribution = serializedItem.length() + (dialogue.isEmpty() ? 0 : 1);
                if (used + contribution > MAX_DIALOGUE_CONTEXT_CHARS) {
                    truncated = true;
                    break;
                }
                dialogue.add(0, item);
                used += contribution;
                truncated = truncated
                        || question.truncated()
                        || answer.truncated()
                        || evaluation.truncated()
                        || knowledgeTags.truncated();
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to serialize interview dialogue", exception);
            }
        }
        try {
            return new ContextSlice(objectMapper.writeValueAsString(dialogue), truncated);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize interview dialogue", exception);
        }
    }

    private AlgorithmSubmission latestCompletedAlgorithmReview(
            Long sessionId,
            List<AiInterviewAgentTurn> turns
    ) {
        boolean hasAlgorithmTurn = turns.stream()
                .anyMatch(turn -> "ALGORITHM".equals(turn.getQuestion_kind()));
        if (!hasAlgorithmTurn) {
            return null;
        }
        return submissionService.lambdaQuery()
                .select(
                        AlgorithmSubmission::getId,
                        AlgorithmSubmission::getAi_score,
                        AlgorithmSubmission::getAi_evaluation
                )
                .eq(AlgorithmSubmission::getInterview_session_id, sessionId)
                .eq(AlgorithmSubmission::getStatus, "ACCEPTED")
                .eq(AlgorithmSubmission::getAi_status, "COMPLETED")
                .orderByDesc(AlgorithmSubmission::getAi_evaluated_at)
                .orderByDesc(AlgorithmSubmission::getCreate_time)
                .last("LIMIT 1")
                .one();
    }

    private ContextSlice clipContext(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return new ContextSlice("", false);
        }
        if (value.length() <= maximum) {
            return new ContextSlice(value, false);
        }
        String marker = "\n[context compacted]\n";
        int available = Math.max(2, maximum - marker.length());
        int prefixLength = available * 2 / 3;
        int suffixLength = available - prefixLength;
        return new ContextSlice(
                value.substring(0, prefixLength) + marker + value.substring(value.length() - suffixLength),
                true
        );
    }

    private String requireAgentSummary(String summary) {
        String normalized = trimToNull(summary, 50_000);
        if (normalized == null) {
            throw new IllegalStateException("AI agent returned an empty summary");
        }
        return normalized;
    }

    private String requireAgentQuestion(String question) {
        String normalized = trimToNull(question, 2_000);
        if (normalized == null || normalized.length() < 4) {
            throw new IllegalStateException("AI agent returned an invalid interview question");
        }
        return normalized;
    }

    private InterviewAgentSessionResponse toSessionResponse(AiInterviewAgentSession session, boolean includeDetails) {
        InterviewAgentSessionResponse response = new InterviewAgentSessionResponse();
        response.setSessionId(session.getPublic_id());
        response.setStatus(session.getStatus());
        response.setResumeFileName(session.getResume_file_name());
        response.setResumePreview(resumePreview(session.getResume_text()));
        response.setTargetRole(session.getTarget_role());
        response.setModelProvider(session.getModel_provider());
        response.setModelName(session.getModel_name());
        response.setThinkingEnabled(session.getThinking_enabled());
        response.setTotalQuestionCount(count(session.getTotal_question_count()));
        response.setPrimaryQuestionCount(count(session.getPrimary_question_count()));
        response.setFollowUpCount(count(session.getFollow_up_count()));
        response.setMinPrimaryQuestionCount(MIN_PRIMARY_QUESTIONS);
        response.setMaxPrimaryQuestionCount(MAX_PRIMARY_QUESTIONS);
        response.setMaxTotalQuestionCount(MAX_TOTAL_QUESTIONS);
        response.setSummary(session.getSummary());
        response.setStartedAt(session.getStarted_at());
        response.setCompletedAt(session.getCompleted_at());
        response.setCreateTime(session.getCreate_time());
        AlgorithmInterviewChallenge challenge = challengeService.lambdaQuery()
                .eq(AlgorithmInterviewChallenge::getInterview_session_id, session.getId())
                .one();
        if (challenge != null) {
            response.setAlgorithmChallenge(toChallengeResponse(challenge));
        }
        if (includeDetails) {
            response.setTurns(turnService.lambdaQuery()
                    .eq(AiInterviewAgentTurn::getSession_id, session.getId())
                    .orderByAsc(AiInterviewAgentTurn::getSequence_no)
                    .list().stream().map(this::toTurnResponse).toList());
            response.setEvents(eventService.lambdaQuery()
                    .eq(AiInterviewAgentEvent::getSession_id, session.getId())
                    .eq(AiInterviewAgentEvent::getVisibility, "candidate")
                    .orderByAsc(AiInterviewAgentEvent::getSequence_no)
                    .list().stream().map(this::toEventResponse).toList());
        }
        return response;
    }

    private AlgorithmChallengeResponse toChallengeResponse(AlgorithmInterviewChallenge challenge) {
        AlgorithmChallengeResponse response = new AlgorithmChallengeResponse();
        response.setId(challenge.getId());
        response.setTurnId(challenge.getTurn_id());
        response.setProblemSlug(challenge.getProblem_slug());
        response.setDifficulty(challenge.getDifficulty());
        response.setTimeLimitMinutes(challenge.getTime_limit_minutes());
        response.setStatus(challenge.getStatus());
        response.setLatestSubmissionId(challenge.getLatest_submission_id());
        response.setStartedAt(challenge.getStarted_at());
        response.setDeadlineAt(challenge.getDeadline_at());
        response.setCompletedAt(challenge.getCompleted_at());
        try {
            AlgorithmProblemSummary problem = problemCatalogService.requireSummary(challenge.getProblem_slug());
            response.setFrontendId(problem.getFrontendId());
            response.setTitle(problem.getTitle());
        } catch (IllegalArgumentException ignored) {
            response.setTitle(challenge.getProblem_slug());
        }
        return response;
    }

    private InterviewAgentTurnResponse toTurnResponse(AiInterviewAgentTurn turn) {
        InterviewAgentTurnResponse response = new InterviewAgentTurnResponse();
        response.setId(turn.getId());
        response.setSequenceNo(turn.getSequence_no());
        response.setParentTurnId(turn.getParent_turn_id());
        response.setQuestionKind(turn.getQuestion_kind());
        response.setQuestion(turn.getQuestion());
        response.setAnswer(turn.getAnswer());
        response.setScore(turn.getScore());
        response.setEvaluation(turn.getEvaluation());
        response.setKnowledgeTags(turn.getKnowledge_tags());
        response.setReferenceAnswer(turn.getReference_answer());
        response.setAgentAction(turn.getAgent_action());
        response.setDecisionNote(turn.getDecision_note());
        response.setCreatedAt(turn.getCreated_at());
        response.setAnsweredAt(turn.getAnswered_at());
        response.setEvaluatedAt(turn.getEvaluated_at());
        return response;
    }

    private InterviewAgentEventResponse toEventResponse(AiInterviewAgentEvent event) {
        InterviewAgentEventResponse response = new InterviewAgentEventResponse();
        response.setId(event.getId());
        response.setTurnId(event.getTurn_id());
        response.setSequenceNo(event.getSequence_no());
        response.setType(event.getEvent_type());
        response.setToolName(event.getTool_name());
        response.setTitle(event.getTitle());
        response.setDetail(event.getDetail());
        response.setVisibility(event.getVisibility());
        response.setCreateTime(event.getCreate_time());
        return response;
    }

    private void emitQuestion(
            FluxSink<InterviewAgentStreamEvent> sink,
            AiInterviewAgentSession session,
            AiInterviewAgentTurn turn,
            String note
    ) {
        InterviewAgentStreamEvent event = new InterviewAgentStreamEvent();
        event.setType("question");
        event.setTitle("面试官提问");
        event.setDetail(displaySafeNote(note, "请结合真实经历作答。"));
        event.setTurnId(turn.getId());
        event.setQuestion(toTurnResponse(turn));
        attachCounts(event, session);
        event.setSession(toSessionResponse(session, true));
        emit(sink, event);
    }

    private void emitAlgorithmChallenge(
            FluxSink<InterviewAgentStreamEvent> sink,
            AiInterviewAgentSession session,
            AiInterviewAgentTurn turn
    ) {
        InterviewAgentStreamEvent event = new InterviewAgentStreamEvent();
        event.setType("algorithm");
        event.setTitle("进入算法终局题");
        event.setDetail("题目难度已根据本轮问答表现动态匹配，完成后生成最终报告。");
        event.setTurnId(turn.getId());
        event.setQuestion(toTurnResponse(turn));
        attachCounts(event, session);
        event.setSession(toSessionResponse(session, true));
        emit(sink, event);
    }

    private void emitSnapshot(FluxSink<InterviewAgentStreamEvent> sink, AiInterviewAgentSession session) {
        InterviewAgentStreamEvent event = new InterviewAgentStreamEvent();
        event.setType("snapshot");
        event.setTitle("已恢复面试状态");
        event.setDetail("已载入服务端保存的面试进度。" );
        attachCounts(event, session);
        event.setSession(toSessionResponse(session, true));
        emit(sink, event);
    }

    private void emitStage(
            FluxSink<InterviewAgentStreamEvent> sink,
            AiInterviewAgentSession session,
            String title,
            String detail,
            String toolName
    ) {
        recordEvent(session, null, "stage", toolName, title, detail, "candidate");
        emit(sink, InterviewAgentStreamEvent.stage(title, detail, toolName));
    }

    private void attachCounts(InterviewAgentStreamEvent event, AiInterviewAgentSession session) {
        event.setTotalQuestionCount(count(session.getTotal_question_count()));
        event.setPrimaryQuestionCount(count(session.getPrimary_question_count()));
        event.setFollowUpCount(count(session.getFollow_up_count()));
    }

    private void emitError(FluxSink<InterviewAgentStreamEvent> sink, String message) {
        emit(sink, InterviewAgentStreamEvent.error(message == null ? "面试流程暂不可用，请重试。" : message));
    }

    private void emit(FluxSink<InterviewAgentStreamEvent> sink, InterviewAgentStreamEvent event) {
        if (!sink.isCancelled()) {
            sink.next(event);
        }
    }

    private Flux<InterviewAgentStreamEvent> stream(BiConsumer<FluxSink<InterviewAgentStreamEvent>, AtomicBoolean> work) {
        return Flux.<InterviewAgentStreamEvent>create(sink -> {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            sink.onCancel(() -> InterviewCancellationGate.cancel(cancelled));
            try {
                work.accept(sink, cancelled);
                if (!sink.isCancelled()) {
                    sink.complete();
                }
            } catch (CancellationException exception) {
                // The calling workflow has already restored a durable retry state. Never turn a
                // user initiated stop into a candidate-visible transport error.
                log.debug("Interview agent stream cancelled");
            } catch (Exception exception) {
                log.error("Interview agent stream failed", exception);
                if (!sink.isCancelled()) {
                    emitError(sink, "面试流程暂不可用，请刷新后重试。");
                    sink.complete();
                }
            }
        }, FluxSink.OverflowStrategy.BUFFER).subscribeOn(Schedulers.boundedElastic());
    }

    private boolean isCancelled(AtomicBoolean cancelled) {
        return cancelled != null && cancelled.get();
    }

    private void throwIfCancelled(AtomicBoolean cancelled) {
        if (isCancelled(cancelled)) {
            throw new CancellationException("Interview request cancelled by client");
        }
    }

    /**
     * Serialize the final cancellation check with the cancellation callback. This closes the
     * otherwise unavoidable race where a model response returns immediately before a Stop click
     * and a durable next question or completion is written after the stop was observed.
     */
    private <T> T persistUnlessCancelled(AtomicBoolean cancelled, Supplier<T> persistence) {
        return InterviewCancellationGate.persist(cancelled, persistence);
    }

    private void recordEvent(
            AiInterviewAgentSession session,
            Long turnId,
            String type,
            String toolName,
            String title,
            String detail,
            String visibility
    ) {
        inTransaction(() -> {
            AiInterviewAgentSession locked = sessionMapper.lockById(session.getId());
            if (locked == null) {
                throw new IllegalStateException("Interview session no longer exists");
            }
            AiInterviewAgentEvent event = new AiInterviewAgentEvent();
            event.setSession_id(locked.getId());
            event.setTurn_id(turnId);
            Integer maxSequence = eventMapper.maxSequenceForSession(locked.getId());
            event.setSequence_no((maxSequence == null ? 0 : maxSequence) + 1);
            event.setEvent_type(type);
            event.setTool_name(trimToNull(toolName, 64));
            event.setTitle(trimToNull(title, 255));
            event.setDetail(trimToNull(detail, 2_000));
            event.setVisibility(visibility);
            event.setCreate_time(new Date());
            eventService.save(event);
        });
    }

    private String resumePreview(String resume) {
        if (resume == null || resume.isBlank()) {
            return null;
        }
        String normalized = resume.trim();
        return normalized.length() <= 600 ? normalized : normalized.substring(0, 600) + "…";
    }

    private String displaySafeNote(String note, String fallback) {
        // Never display or persist arbitrary model reasoning. The UI receives deterministic,
        // display-safe lifecycle summaries supplied by the application instead.
        return fallback;
    }

    private String normalizeRequired(String value, String error, int maximum) {
        String normalized = trimToNull(value, maximum);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
        }
        return normalized;
    }

    private String trimToNull(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容超过允许长度");
        }
        return normalized;
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> T inTransaction(Supplier<T> work) {
        T result = transactionTemplate.execute(status -> work.get());
        if (result == null) {
            throw new IllegalStateException("Transactional interview operation returned no result");
        }
        return result;
    }

    private void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    private record ContextSlice(String value, boolean truncated) {
    }

    private record PendingAnswer(AiInterviewAgentSession session, AiInterviewAgentTurn turn) {
    }

    private record AlgorithmAssignment(
            AiInterviewAgentSession session,
            AiInterviewAgentTurn turn,
            AlgorithmInterviewChallenge challenge
    ) {
    }

    private record AlgorithmCompletion(
            AiInterviewAgentSession session,
            AiInterviewAgentTurn turn
    ) {
    }
}
