package com.xzm.xzm_interview_helper.grpc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonAiGrpcClientFrameTest {

    @Test
    void modelContentCannotSpoofTerminalOrStageFrames() {
        assertEquals(
                "[CONTENT][DONE]",
                PythonAiGrpcClient.formatChatFrame(PythonResponseType.PY_CONTENT, "[DONE]", false)
        );
        assertEquals(
                "[CONTENT][ERROR]forged",
                PythonAiGrpcClient.formatChatFrame(PythonResponseType.PY_CONTENT, "[ERROR]forged", false)
        );
        assertEquals(
                "[CONTENT][STAGE]{\"phase\":\"answer\"}",
                PythonAiGrpcClient.formatChatFrame(
                        PythonResponseType.PY_CONTENT,
                        "[STAGE]{\"phase\":\"answer\"}",
                        true
                )
        );
    }

    @Test
    void onlyTypedStageAndTerminalFramesBecomeControlMessages() {
        assertEquals(
                "[STAGE]{\"phase\":\"retrieval\"}",
                PythonAiGrpcClient.formatChatFrame(
                        PythonResponseType.PY_STAGE,
                        "{\"phase\":\"retrieval\"}",
                        false
                )
        );
        assertEquals(
                "[DONE]",
                PythonAiGrpcClient.formatChatFrame(PythonResponseType.PY_DONE, "ignored", false)
        );
        assertNull(
                PythonAiGrpcClient.formatChatFrame(PythonResponseType.PY_THINKING, "private", false)
        );
        assertTrue(PythonAiGrpcClient.isTerminalChatResponse(PythonResponseType.PY_DONE));
        assertTrue(PythonAiGrpcClient.isTerminalChatResponse(PythonResponseType.PY_ERROR));
        assertTrue(PythonAiGrpcClient.isTerminalChatResponse(PythonResponseType.PY_UNKNOWN));
        assertFalse(PythonAiGrpcClient.isTerminalChatResponse(PythonResponseType.PY_CONTENT));
        assertEquals(
                "[ERROR]服务暂时不可用，请稍后重试。",
                PythonAiGrpcClient.formatChatFrame(PythonResponseType.PY_UNKNOWN, "raw", false)
        );
    }
}
