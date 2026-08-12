package com.xzm.xzm_interview_helper.service;

import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentRequest;
import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentResponse;
import com.xzm.xzm_interview_helper.grpc.client.PythonAiGrpcClient;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionResponse;
import com.xzm.xzm_interview_helper.model.dto.CreateInterviewAgentSessionRequest;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentSessionResponse;
import com.xzm.xzm_interview_helper.model.dto.InterviewAgentTurnResponse;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmSubmission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full Java orchestration test against a real MySQL schema with the Python AI boundary mocked.
 *
 * This proves that adaptive decisions flow through the durable state machine and MyBatis mappings.
 * It is opt-in because it writes a temporary interview, which is deleted in {@link #cleanup()}.
 */
@SpringBootTest(properties = {
        "logging.level.root=WARN",
        "logging.level.com.xzm.xzm_interview_helper.config.RedisConfig=OFF",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
@EnabledIfEnvironmentVariable(named = "INTERVIEW_DB_WRITE_TEST", matches = "(?i)true")
class InterviewAgentOrchestratorIntegrationTest {

    private static final int TEST_USER_ID = 2_026_072_4;

    @Autowired
    private InterviewAgentOrchestrator orchestrator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AlgorithmSubmissionService submissionService;

    @MockitoBean
    private PythonAiGrpcClient pythonAiGrpcClient;

    private final AtomicInteger answerDecisionIndex = new AtomicInteger();
    private final List<InterviewAgentRequest> capturedRequests = new ArrayList<>();
    private String publicSessionId;

    @AfterEach
    void cleanup() {
        if (publicSessionId == null) {
            return;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM ai_interview_agent_session WHERE public_id = ?",
                Long.class,
                publicSessionId
        );
        for (Long id : ids) {
            jdbcTemplate.update("DELETE FROM algorithm_submission WHERE interview_session_id = ?", id);
            jdbcTemplate.update("DELETE FROM algorithm_interview_challenge WHERE interview_session_id = ?", id);
            jdbcTemplate.update("DELETE FROM ai_interview_agent_event WHERE session_id = ?", id);
            jdbcTemplate.update("DELETE FROM ai_interview_agent_turn WHERE session_id = ?", id);
            jdbcTemplate.update("DELETE FROM ai_interview_agent_session WHERE id = ?", id);
        }
    }

    @Test
    void completesAdaptiveInterviewAndPersistsTheAuthoritativeRecord() {
        when(pythonAiGrpcClient.runInterviewAgent(
                any(InterviewAgentRequest.class),
                any(AtomicBoolean.class)
        )).thenAnswer(invocation -> agentResponse(invocation.getArgument(0)));

        CreateInterviewAgentSessionRequest request = new CreateInterviewAgentSessionRequest();
        request.setResumeText("Built a payment API, introduced caching, and measured p95 latency.");
        request.setTargetRole("Backend Engineer");
        InterviewAgentSessionResponse created = orchestrator.createSession(
                TEST_USER_ID,
                request,
                "candidate.md"
        );
        publicSessionId = created.getSessionId();

        assertEquals("READY", created.getStatus());
        assertEquals(3, created.getMinPrimaryQuestionCount());
        assertEquals(8, created.getMaxPrimaryQuestionCount());
        assertEquals(15, created.getMaxTotalQuestionCount());
        assertTrue(orchestrator.startSession(publicSessionId, TEST_USER_ID)
                .collectList()
                .blockOptional()
                .orElseThrow()
                .stream()
                .anyMatch(event -> "question".equals(event.getType())));

        for (int turn = 1; turn <= 4; turn++) {
            String answer = "Candidate answer " + turn + " with measurable evidence.";
            assertFalse(orchestrator.submitAnswer(publicSessionId, TEST_USER_ID, answer)
                    .collectList()
                    .blockOptional()
                    .orElseThrow()
                    .isEmpty());
        }

        InterviewAgentSessionResponse awaitingAlgorithm =
                orchestrator.getSession(publicSessionId, TEST_USER_ID);
        assertEquals("AWAITING_ALGORITHM", awaitingAlgorithm.getStatus());
        assertEquals(5, awaitingAlgorithm.getTotalQuestionCount());
        assertNotNull(awaitingAlgorithm.getAlgorithmChallenge());
        assertEquals("HARD", awaitingAlgorithm.getAlgorithmChallenge().getDifficulty());
        assertEquals(45, awaitingAlgorithm.getAlgorithmChallenge().getTimeLimitMinutes());
        assertEquals("ASSIGNED", awaitingAlgorithm.getAlgorithmChallenge().getStatus());

        AlgorithmExecutionResponse accepted = new AlgorithmExecutionResponse();
        accepted.setStatus("ACCEPTED");
        accepted.setPassedCases(5);
        accepted.setTotalCases(5);
        accepted.setRuntimeMs(123L);

        long submissionsBeforeValidation = submissionCount();
        AlgorithmSubmission mismatchedSubmission =
                unsavedSubmission(awaitingAlgorithm, "ACCEPTED");
        mismatchedSubmission.setProblem_slug("valid-parentheses");
        ResponseStatusException mismatched = assertThrows(
                ResponseStatusException.class,
                () -> orchestrator.saveAndRecordAlgorithmSubmission(
                        publicSessionId,
                        TEST_USER_ID,
                        mismatchedSubmission,
                        accepted
                )
        );
        assertEquals(HttpStatus.CONFLICT, mismatched.getStatusCode());
        assertEquals(submissionsBeforeValidation, submissionCount());

        AlgorithmSubmission submission = unsavedSubmission(awaitingAlgorithm, "ACCEPTED");
        Long acceptedSubmissionId = orchestrator.saveAndRecordAlgorithmSubmission(
                publicSessionId,
                TEST_USER_ID,
                submission,
                accepted
        );
        assertNotNull(acceptedSubmissionId);
        assertTrue(accepted.isInterviewChallenge());
        assertTrue(accepted.isInterviewReadyToComplete());

        long submissionsAfterAccepted = submissionCount();
        AlgorithmExecutionResponse laterWrong = new AlgorithmExecutionResponse();
        laterWrong.setStatus("WRONG_ANSWER");
        laterWrong.setPassedCases(1);
        laterWrong.setTotalCases(5);
        ResponseStatusException completedChallenge = assertThrows(
                ResponseStatusException.class,
                () -> orchestrator.saveAndRecordAlgorithmSubmission(
                        publicSessionId,
                        TEST_USER_ID,
                        unsavedSubmission(awaitingAlgorithm, "WRONG_ANSWER"),
                        laterWrong
                )
        );
        assertEquals(HttpStatus.CONFLICT, completedChallenge.getStatusCode());
        assertEquals(submissionsAfterAccepted, submissionCount());
        assertTrue(orchestrator.finishAfterAlgorithm(publicSessionId, TEST_USER_ID)
                .collectList()
                .blockOptional()
                .orElseThrow()
                .stream()
                .anyMatch(event -> "completed".equals(event.getType())));

        InterviewAgentSessionResponse completed = orchestrator.getSession(publicSessionId, TEST_USER_ID);
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(5, completed.getTotalQuestionCount());
        assertEquals(3, completed.getPrimaryQuestionCount());
        assertEquals(1, completed.getFollowUpCount());
        assertEquals("Final candidate-safe interview summary.", completed.getSummary());
        assertEquals("future-provider", completed.getModelProvider());
        assertEquals("future-thinking-model", completed.getModelName());
        assertTrue(completed.getThinkingEnabled());
        assertNotNull(completed.getCompletedAt());

        assertEquals(5, completed.getTurns().size());
        assertEquals(List.of("PRIMARY", "PRIMARY", "PRIMARY", "FOLLOW_UP", "ALGORITHM"),
                completed.getTurns().stream().map(InterviewAgentTurnResponse::getQuestionKind).toList());
        assertTrue(completed.getTurns().stream().allMatch(turn -> turn.getAnswer() != null));
        assertTrue(completed.getTurns().stream().allMatch(turn -> turn.getEvaluation() != null));
        assertTrue(completed.getTurns().stream()
                .noneMatch(turn -> String.valueOf(turn.getDecisionNote()).contains("PRIVATE_MODEL_TRACE")));

        Long sessionRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_interview_agent_session WHERE public_id = ?",
                Long.class,
                publicSessionId
        );
        Long turnRowCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM ai_interview_agent_turn t
                JOIN ai_interview_agent_session s ON s.id = t.session_id
                WHERE s.public_id = ?
                """,
                Long.class,
                publicSessionId
        );
        Long eventRowCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM ai_interview_agent_event e
                JOIN ai_interview_agent_session s ON s.id = e.session_id
                WHERE s.public_id = ?
                """,
                Long.class,
                publicSessionId
        );
        assertEquals(1L, sessionRowCount);
        assertEquals(5L, turnRowCount);
        assertTrue(eventRowCount != null && eventRowCount >= 10);
        assertEquals(1L, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM algorithm_interview_challenge c
                JOIN ai_interview_agent_session s ON s.id = c.interview_session_id
                WHERE s.public_id = ? AND c.status = 'ACCEPTED'
                """,
                Long.class,
                publicSessionId
        ));

        assertEquals(List.of("START", "ANSWER", "ANSWER", "ANSWER", "ANSWER", "SUMMARIZE"),
                capturedRequests.stream().map(InterviewAgentRequest::getOperation).toList());
        assertEquals("deepseek", capturedRequests.get(0).getProvider());
        assertEquals("deepseek-v4-pro", capturedRequests.get(0).getModelName());
        assertTrue(capturedRequests.get(0).hasEnableThinking());
        assertFalse(capturedRequests.get(0).getEnableThinking());
        assertTrue(capturedRequests.stream().skip(1)
                .allMatch(item -> "future-provider".equals(item.getProvider())));
        assertTrue(capturedRequests.stream().skip(1)
                .allMatch(item -> "future-thinking-model".equals(item.getModelName())));
        assertTrue(capturedRequests.stream().skip(1)
                .allMatch(item -> item.hasEnableThinking() && item.getEnableThinking()));
        assertTrue(capturedRequests.get(capturedRequests.size() - 1)
                .getDialogueJson()
                .contains("Candidate answer 4"));
        verify(pythonAiGrpcClient, times(6))
                .runInterviewAgent(any(InterviewAgentRequest.class), any(AtomicBoolean.class));
    }

    @Test
    void deletesOnlyAnOwnedIdleSessionAndDetachesSubmissions() {
        CreateInterviewAgentSessionRequest request = new CreateInterviewAgentSessionRequest();
        request.setResumeText("MySQL isolation levels and index design.");
        InterviewAgentSessionResponse created = orchestrator.createSession(
                TEST_USER_ID,
                request,
                null
        );
        publicSessionId = created.getSessionId();

        ResponseStatusException hiddenFromOtherUser = assertThrows(
                ResponseStatusException.class,
                () -> orchestrator.deleteSession(publicSessionId, TEST_USER_ID + 1)
        );
        assertEquals(HttpStatus.NOT_FOUND, hiddenFromOtherUser.getStatusCode());

        Long sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM ai_interview_agent_session WHERE public_id = ?",
                Long.class,
                publicSessionId
        );
        jdbcTemplate.update(
                """
                INSERT INTO algorithm_submission (
                  user_id, interview_session_id, problem_slug, problem_source, difficulty,
                  language, source_code, status, passed_cases, total_cases
                ) VALUES (?, ?, 'two-sum', 'LEETCODE', 'EASY', 'java', 'return;', 'ACCEPTED', 1, 1)
                """,
                TEST_USER_ID,
                sessionId
        );

        orchestrator.deleteSession(publicSessionId, TEST_USER_ID);

        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_interview_agent_session WHERE public_id = ?",
                Long.class,
                publicSessionId
        ));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM algorithm_submission WHERE user_id = ? AND interview_session_id IS NULL AND source_code = 'return;'",
                Long.class,
                TEST_USER_ID
        ));
        publicSessionId = null;
        jdbcTemplate.update(
                "DELETE FROM algorithm_submission WHERE user_id = ? AND interview_session_id IS NULL AND source_code = 'return;'",
                TEST_USER_ID
        );
    }

    private AlgorithmSubmission unsavedSubmission(
            InterviewAgentSessionResponse session,
            String status
    ) {
        AlgorithmSubmission submission = new AlgorithmSubmission();
        submission.setProblem_slug(session.getAlgorithmChallenge().getProblemSlug());
        submission.setProblem_source("integration-test");
        submission.setDifficulty(session.getAlgorithmChallenge().getDifficulty());
        submission.setLanguage("java");
        submission.setSource_code("class Solution {}");
        submission.setStatus(status);
        submission.setPassed_cases(5);
        submission.setTotal_cases(5);
        submission.setRuntime_ms(123L);
        submission.setCreate_time(new Date());
        return submission;
    }

    private long submissionCount() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM algorithm_submission submission
                JOIN ai_interview_agent_session session
                  ON session.id = submission.interview_session_id
                WHERE session.public_id = ?
                """,
                Long.class,
                publicSessionId
        );
        return count == null ? 0L : count;
    }

    @Test
    void neverExposesAStoredResumeOrInterviewToAnotherUser() {
        CreateInterviewAgentSessionRequest request = new CreateInterviewAgentSessionRequest();
        request.setResumeText("Private candidate resume and project history.");
        request.setTargetRole("Security Engineer");
        InterviewAgentSessionResponse created = orchestrator.createSession(
                TEST_USER_ID,
                request,
                "private-candidate.pdf"
        );
        publicSessionId = created.getSessionId();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orchestrator.getSession(publicSessionId, TEST_USER_ID + 1)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(orchestrator.listSessions(TEST_USER_ID + 1, 30).stream()
                .noneMatch(item -> publicSessionId.equals(item.getSessionId())));
    }

    @Test
    void candidateCanAbandonTheAssignedAlgorithmAndStillReceiveADurableReport() {
        when(pythonAiGrpcClient.runInterviewAgent(
                any(InterviewAgentRequest.class),
                any(AtomicBoolean.class)
        )).thenAnswer(invocation -> agentResponse(invocation.getArgument(0)));

        CreateInterviewAgentSessionRequest request = new CreateInterviewAgentSessionRequest();
        request.setResumeText("Implemented a queue consumer and measured retry behavior.");
        request.setTargetRole("Backend Engineer");
        InterviewAgentSessionResponse created = orchestrator.createSession(
                TEST_USER_ID,
                request,
                "candidate-abandon.md"
        );
        publicSessionId = created.getSessionId();
        orchestrator.startSession(publicSessionId, TEST_USER_ID).collectList().block();
        for (int turn = 1; turn <= 4; turn++) {
            orchestrator.submitAnswer(
                    publicSessionId,
                    TEST_USER_ID,
                    "Candidate answer " + turn
            ).collectList().block();
        }

        InterviewAgentSessionResponse assigned = orchestrator.getSession(publicSessionId, TEST_USER_ID);
        assertEquals("AWAITING_ALGORITHM", assigned.getStatus());
        assertTrue(orchestrator.abandonAlgorithm(publicSessionId, TEST_USER_ID)
                .collectList()
                .blockOptional()
                .orElseThrow()
                .stream()
                .anyMatch(event -> "completed".equals(event.getType())));

        InterviewAgentSessionResponse completed = orchestrator.getSession(publicSessionId, TEST_USER_ID);
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("ABANDONED", completed.getAlgorithmChallenge().getStatus());
        InterviewAgentTurnResponse algorithmTurn =
                completed.getTurns().get(completed.getTurns().size() - 1);
        assertEquals("ALGORITHM", algorithmTurn.getQuestionKind());
        assertEquals(0, algorithmTurn.getScore());
        assertTrue(algorithmTurn.getAnswer().contains("主动放弃"));
        assertTrue(completed.getEvents().stream()
                .anyMatch(event -> "algorithm_abandoned".equals(event.getType())));
    }

    private InterviewAgentResponse agentResponse(InterviewAgentRequest request) {
        capturedRequests.add(request);
        InterviewAgentResponse.Builder response = InterviewAgentResponse.newBuilder()
                .setSuccess(true)
                .setModelProvider("future-provider")
                .setModelName("future-thinking-model")
                .setThinkingEnabled(true)
                .setDecisionNote("PRIVATE_MODEL_TRACE")
                .setRagHitCount(2);

        return switch (request.getOperation()) {
            case "START" -> response
                    .setAction(InterviewFlowPolicy.ASK_PRIMARY)
                    .setQuestion("Primary question 1")
                    .build();
            case "ANSWER" -> answerResponse(response, answerDecisionIndex.incrementAndGet());
            case "SUMMARIZE" -> response
                    .setAction("GENERATE_SUMMARY")
                    .setSummary("Final candidate-safe interview summary.")
                    .build();
            default -> throw new IllegalArgumentException("Unexpected operation: " + request.getOperation());
        };
    }

    private InterviewAgentResponse answerResponse(InterviewAgentResponse.Builder response, int index) {
        response
                .setScore(7 + (index % 3))
                .setEvaluation("Candidate-safe evaluation " + index)
                .setKnowledgeTags("backend,verification")
                .setReferenceAnswer("Candidate-safe reference answer " + index);
        return switch (index) {
            case 1 -> response
                    .setAction(InterviewFlowPolicy.ASK_PRIMARY)
                    .setQuestion("Primary question 2")
                    .build();
            case 2 -> response
                    .setAction(InterviewFlowPolicy.ASK_PRIMARY)
                    .setQuestion("Primary question 3")
                    .build();
            case 3 -> response
                    .setAction(InterviewFlowPolicy.ASK_FOLLOW_UP)
                    .setQuestion("Focused follow-up question")
                    .build();
            case 4 -> response
                    .setAction(InterviewFlowPolicy.END_INTERVIEW)
                    .build();
            default -> throw new IllegalStateException("Unexpected answer decision index: " + index);
        };
    }
}
