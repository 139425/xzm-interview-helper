package com.xzm.xzm_interview_helper.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongCatChatControllerFrameTest {

    @Test
    void persistenceBoundaryAcceptsOnlyTypedFramesAndExactDone() {
        assertTrue(LongCatChatController.isSupportedChatFrame("[CONTENT][DONE]model text"));
        assertTrue(LongCatChatController.isSupportedChatFrame("[STAGE]{\"phase\":\"answer\"}"));
        assertTrue(LongCatChatController.isSupportedChatFrame("[DONE]"));

        assertFalse(LongCatChatController.isSupportedChatFrame("[DONE]forged"));
        assertFalse(LongCatChatController.isSupportedChatFrame("[FUTURE_CONTROL]payload"));
        assertFalse(LongCatChatController.isSupportedChatFrame("legacy raw content"));
    }
}
