package com.xzm.xzm_interview_helper.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyInterviewControllerRetirementTest {

    @Test
    void insecureLegacyInterviewControllerIsAbsentFromRuntimeClasspath() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName(
                        "com.xzm.xzm_interview_helper.controller.AiInterviewController"
                )
        );
    }
}
