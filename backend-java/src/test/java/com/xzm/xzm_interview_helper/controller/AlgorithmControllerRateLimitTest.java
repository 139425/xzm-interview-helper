package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.model.dto.AlgorithmCustomExecutionRequest;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionRequest;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemSummary;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmSubmissionReviewResponse;
import com.xzm.xzm_interview_helper.service.AiOperationGate;
import com.xzm.xzm_interview_helper.service.AlgorithmJudgeService;
import com.xzm.xzm_interview_helper.service.AlgorithmOperationGate;
import com.xzm.xzm_interview_helper.service.AlgorithmProblemCatalogService;
import com.xzm.xzm_interview_helper.service.AlgorithmSubmissionAiReviewService;
import com.xzm.xzm_interview_helper.service.AlgorithmSubmissionService;
import com.xzm.xzm_interview_helper.service.InterviewAgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmControllerRateLimitTest {

    @Mock
    private AlgorithmProblemCatalogService catalogService;
    @Mock
    private AlgorithmJudgeService judgeService;
    @Mock
    private AlgorithmSubmissionService submissionService;
    @Mock
    private AlgorithmSubmissionAiReviewService submissionAiReviewService;
    @Mock
    private InterviewAgentOrchestrator interviewAgentOrchestrator;
    @Mock
    private AiOperationGate aiOperationGate;

    private AlgorithmController controller;
    private AlgorithmExecutionRequest executionRequest;
    private AlgorithmExecutionResponse executionResponse;

    @BeforeEach
    void setUp() {
        controller = new AlgorithmController(
                catalogService,
                judgeService,
                submissionService,
                submissionAiReviewService,
                interviewAgentOrchestrator,
                new AlgorithmOperationGate(),
                aiOperationGate
        );

        AlgorithmProblemSummary problem = new AlgorithmProblemSummary();
        problem.setSlug("two-sum");
        problem.setDifficulty("EASY");
        problem.setSources(List.of("leetcode"));
        problem.setJudgeable(true);
        lenient().when(catalogService.requireSummary("two-sum")).thenReturn(problem);

        executionRequest = new AlgorithmExecutionRequest();
        executionRequest.setProblemSlug("two-sum");
        executionRequest.setLanguage("java");
        executionRequest.setCode("class Solution {}");

        executionResponse = new AlgorithmExecutionResponse();
        executionResponse.setStatus("ACCEPTED");
    }

    @Test
    void returns429AfterSixJudgeStartsInTheRollingMinute() {
        when(judgeService.execute(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn(executionResponse);

        for (int attempt = 0; attempt < 6; attempt++) {
            assertEquals(
                    "ACCEPTED",
                    controller.run(executionRequest, requestForUser(7)).getStatus()
            );
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.run(executionRequest, requestForUser(7))
        );
        assertEquals(429, exception.getStatusCode().value());
    }

    @Test
    void runCustomAndSubmitShareTheSameJudgeBudget() {
        AlgorithmCustomExecutionRequest customRequest = new AlgorithmCustomExecutionRequest();
        customRequest.setProblemSlug("two-sum");
        customRequest.setLanguage("java");
        customRequest.setCode("class Solution {}");
        customRequest.setDriverCode("System.out.println(1);");
        customRequest.setExpectedOutput("1");

        when(judgeService.execute(any(), anyString(), anyString(), anyBoolean()))
                .thenReturn(executionResponse);
        when(judgeService.executeCustom(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(executionResponse);
        when(submissionService.save(any())).thenReturn(true);

        for (int attempt = 0; attempt < 4; attempt++) {
            controller.run(executionRequest, requestForUser(7));
        }
        controller.runCustom(customRequest, requestForUser(7));
        controller.submit(executionRequest, requestForUser(7));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.run(executionRequest, requestForUser(7))
        );
        assertEquals(429, exception.getStatusCode().value());
    }

    @Test
    void releasesJudgePermitWhenExecutionThrows() {
        when(judgeService.execute(any(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new IllegalStateException("sandbox failed"))
                .thenReturn(executionResponse);

        assertThrows(
                IllegalStateException.class,
                () -> controller.run(executionRequest, requestForUser(7))
        );

        assertEquals(
                "ACCEPTED",
                controller.run(executionRequest, requestForUser(7)).getStatus()
        );
    }

    @Test
    void returns429ForConcurrentJudgeRequestFromSameUser() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(judgeService.execute(any(), anyString(), anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return executionResponse;
                });

        CompletableFuture<AlgorithmExecutionResponse> first = CompletableFuture.supplyAsync(
                () -> controller.run(executionRequest, requestForUser(7))
        );
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> controller.run(executionRequest, requestForUser(7))
            );
            assertEquals(429, exception.getStatusCode().value());
        } finally {
            release.countDown();
        }
        assertEquals("ACCEPTED", first.get(5, TimeUnit.SECONDS).getStatus());
    }

    @Test
    void differentSubmissionIdsCannotBypassPerUserAiReviewConcurrency() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AlgorithmSubmissionReviewResponse reviewResponse = new AlgorithmSubmissionReviewResponse();
        reviewResponse.setAiStatus("COMPLETED");
        when(submissionAiReviewService.review(anyLong(), anyInt()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return reviewResponse;
                });

        CompletableFuture<AlgorithmSubmissionReviewResponse> first =
                CompletableFuture.supplyAsync(
                        () -> controller.reviewSubmission(101L, requestForUser(7))
                );
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> controller.reviewSubmission(202L, requestForUser(7))
            );
            assertEquals(429, exception.getStatusCode().value());
            verify(submissionAiReviewService, never()).review(eq(202L), eq(7));
        } finally {
            release.countDown();
        }
        assertEquals("COMPLETED", first.get(5, TimeUnit.SECONDS).getAiStatus());
    }

    @Test
    void returns429AfterThreeAiReviewStartsInTheRollingMinute() {
        AlgorithmSubmissionReviewResponse response = new AlgorithmSubmissionReviewResponse();
        response.setAiStatus("COMPLETED");
        when(submissionAiReviewService.review(anyLong(), eq(7))).thenReturn(response);

        for (long submissionId = 1; submissionId <= 3; submissionId++) {
            assertEquals(
                    "COMPLETED",
                    controller.reviewSubmission(submissionId, requestForUser(7)).getAiStatus()
            );
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.reviewSubmission(4L, requestForUser(7))
        );
        assertEquals(429, exception.getStatusCode().value());
        verify(submissionAiReviewService, never()).review(eq(4L), eq(7));
    }

    @Test
    void readOnlyReviewStatusPollingDoesNotConsumeTriggerQuota() {
        AlgorithmSubmissionReviewResponse response = new AlgorithmSubmissionReviewResponse();
        response.setAiStatus("PROCESSING");
        when(submissionAiReviewService.review(anyLong(), eq(7))).thenReturn(response);
        when(submissionAiReviewService.getStatus(anyLong(), eq(7))).thenReturn(response);

        for (long submissionId = 1; submissionId <= 3; submissionId++) {
            controller.reviewSubmission(submissionId, requestForUser(7));
        }
        ResponseStatusException rejectedTrigger = assertThrows(
                ResponseStatusException.class,
                () -> controller.reviewSubmission(4L, requestForUser(7))
        );
        assertEquals(429, rejectedTrigger.getStatusCode().value());

        for (int poll = 0; poll < 10; poll++) {
            assertEquals(
                    "PROCESSING",
                    controller.reviewSubmissionStatus(4L, requestForUser(7)).getAiStatus()
            );
        }
        verify(submissionAiReviewService, never()).review(eq(4L), eq(7));
        verify(submissionAiReviewService, times(10)).getStatus(4L, 7);
    }

    @Test
    void releasesAiReviewPermitWhenReviewThrows() {
        AlgorithmSubmissionReviewResponse response = new AlgorithmSubmissionReviewResponse();
        response.setAiStatus("COMPLETED");
        when(submissionAiReviewService.review(anyLong(), eq(7)))
                .thenThrow(new IllegalStateException("model failed"))
                .thenReturn(response);

        assertThrows(
                IllegalStateException.class,
                () -> controller.reviewSubmission(101L, requestForUser(7))
        );

        assertEquals(
                "COMPLETED",
                controller.reviewSubmission(202L, requestForUser(7)).getAiStatus()
        );
    }

    @Test
    void algorithmFinishAndAbandonSummaryStreamsUseSharedAiGate() {
        when(aiOperationGate.guardFlux(anyLong(), any())).thenReturn(reactor.core.publisher.Flux.empty());

        controller.finishInterviewChallenge("session", requestForUser(7));
        controller.abandonInterviewChallenge("session", requestForUser(7));

        verify(aiOperationGate, times(2)).guardFlux(eq(7L), any());
    }

    private MockHttpServletRequest requestForUser(int userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        return request;
    }
}
