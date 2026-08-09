package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.grpc.client.PythonAiGrpcClient;
import com.xzm.xzm_interview_helper.model.dto.LongCatChatRequest;
import com.xzm.xzm_interview_helper.model.dto.SubmitInterviewAgentAnswerRequest;
import com.xzm.xzm_interview_helper.service.AiConversationService;
import com.xzm.xzm_interview_helper.service.AiOperationGate;
import com.xzm.xzm_interview_helper.service.InMemoryAdmissionGate;
import com.xzm.xzm_interview_helper.service.InterviewAgentOrchestrator;
import com.xzm.xzm_interview_helper.service.ResumeTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiControllerAdmissionTest {

    @Mock
    private PythonAiGrpcClient pythonAiGrpcClient;
    @Mock
    private AiConversationService aiConversationService;
    @Mock
    private InterviewAgentOrchestrator interviewAgentOrchestrator;
    @Mock
    private ResumeTextExtractor resumeTextExtractor;
    @Mock
    private AiOperationGate aiOperationGate;

    private LongCatChatController chatController;
    private InterviewAgentController interviewController;

    @BeforeEach
    void setUp() {
        chatController = new LongCatChatController(
                pythonAiGrpcClient,
                aiConversationService,
                aiOperationGate
        );
        interviewController = new InterviewAgentController(
                interviewAgentOrchestrator,
                resumeTextExtractor,
                aiOperationGate
        );
    }

    @Test
    void everyChatAndInterviewModelEndpointUsesTheSharedGate() {
        when(aiOperationGate.guardFlux(anyLong(), any())).thenReturn(Flux.empty());
        when(aiOperationGate.guardCall(anyLong(), any())).thenReturn("direct");

        LongCatChatRequest chatRequest = chatRequest();
        chatController.streamChat(chatRequest, requestForUser(7));
        chatController.streamThinkChat(chatRequest, requestForUser(7));
        assertEquals("direct", chatController.directChat(chatRequest, requestForUser(7)));

        SubmitInterviewAgentAnswerRequest answer = new SubmitInterviewAgentAnswerRequest();
        answer.setAnswer("candidate answer");
        interviewController.start("session", requestForUser(7));
        interviewController.submitAnswer("session", answer, requestForUser(7));
        interviewController.retry("session", requestForUser(7));

        verify(aiOperationGate, times(5)).guardFlux(eq(7L), any());
        verify(aiOperationGate).guardCall(eq(7L), any());
    }

    @Test
    void chatAndInterviewMapAdmissionRejectionToHttp429() {
        InMemoryAdmissionGate.RejectedException rejection = globalBusyRejection();
        when(aiOperationGate.guardFlux(anyLong(), any())).thenThrow(rejection);

        ResponseStatusException chatException = assertThrows(
                ResponseStatusException.class,
                () -> chatController.streamChat(chatRequest(), requestForUser(7))
        );
        ResponseStatusException interviewException = assertThrows(
                ResponseStatusException.class,
                () -> interviewController.start("session", requestForUser(7))
        );

        assertEquals(429, chatException.getStatusCode().value());
        assertEquals(429, interviewException.getStatusCode().value());
    }

    private InMemoryAdmissionGate.RejectedException globalBusyRejection() {
        InMemoryAdmissionGate<String> gate = new InMemoryAdmissionGate<>(
                new AtomicLong()::get,
                10,
                60_000L,
                1,
                1,
                10
        );
        InMemoryAdmissionGate.Permit permit = gate.acquire("held");
        try {
            return assertThrows(
                    InMemoryAdmissionGate.RejectedException.class,
                    () -> gate.acquire("other")
            );
        } finally {
            permit.close();
        }
    }

    private LongCatChatRequest chatRequest() {
        LongCatChatRequest request = new LongCatChatRequest();
        request.setUserMemoryId(1);
        request.setMessage("hello");
        return request;
    }

    private MockHttpServletRequest requestForUser(int userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        return request;
    }
}
