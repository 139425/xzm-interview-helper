package com.xzm.xzm_interview_helper.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentRequest;
import com.xzm.xzm_interview_helper.grpc.client.InterviewAgentResponse;
import com.xzm.xzm_interview_helper.grpc.client.PythonAiGrpcClient;
import com.xzm.xzm_interview_helper.mapper.AlgorithmSubmissionMapper;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemDetail;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmSubmissionReviewResponse;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmSubmissionAiReviewServiceTest {

    @Mock
    private AlgorithmSubmissionMapper submissionMapper;
    @Mock
    private AlgorithmProblemCatalogService catalogService;
    @Mock
    private AiInterviewAgentSessionService interviewSessionService;
    @Mock
    private PythonAiGrpcClient pythonAiGrpcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AlgorithmSubmissionAiReviewService service;
    private AlgorithmSubmission submission;

    @BeforeEach
    void setUp() {
        service = new AlgorithmSubmissionAiReviewService(
                submissionMapper,
                catalogService,
                interviewSessionService,
                pythonAiGrpcClient,
                objectMapper
        );
        submission = new AlgorithmSubmission();
        submission.setId(41L);
        submission.setUser_id(7);
        submission.setProblem_slug("two-sum");
        submission.setStatus("ACCEPTED");
        submission.setPassed_cases(5);
        submission.setTotal_cases(5);
        submission.setRuntime_ms(31L);
        submission.setOutput("PRIVATE_HIDDEN_TEST_OUTPUT");
        submission.setError_message("PRIVATE_INTERNAL_ERROR");
        submission.setSource_code(
                "class Solution { int[] twoSum(int[] values, int target) { return new int[]{0, 1}; } }"
        );
    }

    @Test
    void reviewsOwnedSubmissionWithoutSendingHiddenJudgeMaterial() throws Exception {
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(submissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        AlgorithmProblemDetail problem = problem();
        when(catalogService.detail("two-sum")).thenReturn(problem);
        when(pythonAiGrpcClient.runInterviewAgent(any())).thenReturn(
                InterviewAgentResponse.newBuilder()
                        .setSuccess(true)
                        .setAction("GENERATE_SUMMARY")
                        .setScore(91)
                        .setEvaluation("## 评价\n时间复杂度合理，边界处理可以更明确。")
                        .build()
        );

        AlgorithmSubmissionReviewResponse response = service.review(41L, 7);

        assertEquals("ACCEPTED", response.getJudgeStatus());
        assertEquals("COMPLETED", response.getAiStatus());
        assertEquals(91, response.getAiScore());
        assertEquals("ACCEPTED", submission.getStatus());
        assertEquals("PRIVATE_HIDDEN_TEST_OUTPUT", submission.getOutput());

        ArgumentCaptor<InterviewAgentRequest> requestCaptor =
                ArgumentCaptor.forClass(InterviewAgentRequest.class);
        verify(pythonAiGrpcClient).runInterviewAgent(requestCaptor.capture());
        InterviewAgentRequest request = requestCaptor.getValue();
        assertEquals("ALGORITHM_EVALUATE", request.getOperation());
        JsonNode judgeResult = objectMapper.readTree(request.getDialogueJson());
        assertEquals("ACCEPTED", judgeResult.get("status").asText());
        assertEquals(5, judgeResult.get("passed_cases").asInt());
        assertFalse(judgeResult.has("output"));
        assertFalse(judgeResult.has("error_message"));
        assertFalse(request.getDialogueJson().contains("PRIVATE"));
        assertFalse(request.getCurrentQuestion().contains("PRIVATE"));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> updateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionMapper, times(2)).update(isNull(), updateCaptor.capture());
        for (Wrapper<?> wrapper : updateCaptor.getAllValues()) {
            String sqlSet = ((UpdateWrapper<?>) wrapper).getSqlSet();
            assertFalse(sqlSet.contains("passed_cases"));
            assertFalse(sqlSet.contains("total_cases"));
            assertFalse(sqlSet.contains("runtime_ms"));
            assertFalse(sqlSet.contains("output"));
            assertFalse(sqlSet.contains("error_message"));
            assertTrue(sqlSet.contains("ai_status"));
        }
    }

    @Test
    void aiFailurePersistsOnlyReviewFailureAndLeavesJudgeOutcomeIntact() {
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(submissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(catalogService.detail("two-sum")).thenReturn(problem());
        when(pythonAiGrpcClient.runInterviewAgent(any())).thenReturn(
                InterviewAgentResponse.newBuilder()
                        .setSuccess(false)
                        .setError("provider secret diagnostic")
                        .build()
        );

        AlgorithmSubmissionReviewResponse response = service.review(41L, 7);

        assertEquals("FAILED", response.getAiStatus());
        assertEquals("ACCEPTED", response.getJudgeStatus());
        assertEquals("ACCEPTED", submission.getStatus());
        assertTrue(response.getAiEvaluation().contains("判题结果未改变"));
        assertFalse(response.getAiEvaluation().contains("provider secret"));
    }

    @Test
    void foreignOrMissingSubmissionIsIndistinguishableAndNeverCallsAi() {
        when(submissionMapper.selectOne(any())).thenReturn(null);

        assertThrows(NoSuchElementException.class, () -> service.review(41L, 999));
        verify(pythonAiGrpcClient, never()).runInterviewAgent(any());
        verify(submissionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void statusLookupIsOwnedReadOnlyAndNeverCallsAi() {
        submission.setAi_status("PROCESSING");
        when(submissionMapper.selectOne(any())).thenReturn(submission);

        AlgorithmSubmissionReviewResponse response = service.getStatus(41L, 7);

        assertEquals("PROCESSING", response.getAiStatus());
        assertEquals("ACCEPTED", response.getJudgeStatus());
        verify(pythonAiGrpcClient, never()).runInterviewAgent(any());
        verify(submissionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void statusLookupRejectsForeignOrMissingSubmissionWithoutCallingAi() {
        when(submissionMapper.selectOne(any())).thenReturn(null);

        assertThrows(NoSuchElementException.class, () -> service.getStatus(41L, 999));
        verify(pythonAiGrpcClient, never()).runInterviewAgent(any());
        verify(submissionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void completedReviewIsReturnedWithoutCallingAiAgain() {
        submission.setAi_status("COMPLETED");
        submission.setAi_score(88);
        submission.setAi_evaluation("cached");
        submission.setAi_evaluated_at(new Date());
        when(submissionMapper.selectOne(any())).thenReturn(submission);

        AlgorithmSubmissionReviewResponse response = service.review(41L, 7);

        assertEquals("COMPLETED", response.getAiStatus());
        assertEquals(88, response.getAiScore());
        verify(pythonAiGrpcClient, never()).runInterviewAgent(any());
        verify(submissionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void activeProcessingLeaseReturnsImmediatelyWithoutDuplicateModelCall() {
        submission.setAi_status("PROCESSING");
        submission.setAi_evaluated_at(new Date());
        when(submissionMapper.selectOne(any())).thenReturn(submission);

        AlgorithmSubmissionReviewResponse response = service.review(41L, 7);

        assertEquals("PROCESSING", response.getAiStatus());
        verify(pythonAiGrpcClient, never()).runInterviewAgent(any());
        verify(submissionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void losingTheClaimRaceReloadsWinnerStateWithoutCallingModel() {
        AlgorithmSubmission winner = new AlgorithmSubmission();
        winner.setId(41L);
        winner.setUser_id(7);
        winner.setProblem_slug("two-sum");
        winner.setStatus("ACCEPTED");
        winner.setAi_status("PROCESSING");
        winner.setAi_evaluated_at(new Date());
        when(submissionMapper.selectOne(any())).thenReturn(submission, winner);
        when(submissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        AlgorithmSubmissionReviewResponse response = service.review(41L, 7);

        assertEquals("PROCESSING", response.getAiStatus());
        verify(pythonAiGrpcClient, never()).runInterviewAgent(any());
        verify(submissionMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    private AlgorithmProblemDetail problem() {
        AlgorithmProblemDetail problem = new AlgorithmProblemDetail();
        problem.setSlug("two-sum");
        problem.setTitle("两数之和");
        problem.setDifficulty("EASY");
        problem.setTags(List.of("数组", "哈希表"));
        problem.setContentHtml("<p>给定数组与目标值，返回两个下标。</p>");
        return problem;
    }
}
