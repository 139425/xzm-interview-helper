package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewFlowPolicyTest {

    private final InterviewFlowPolicy policy = new InterviewFlowPolicy(3, 8, 14);

    @Test
    void requiresThreePrimaryQuestionsBeforeEnding() {
        assertEquals(InterviewFlowPolicy.ASK_PRIMARY, policy.constrain("END_INTERVIEW", 1, 1));
        assertEquals(InterviewFlowPolicy.ASK_PRIMARY, policy.constrain("END_INTERVIEW", 2, 2));
        assertEquals(InterviewFlowPolicy.END_INTERVIEW, policy.constrain("END_INTERVIEW", 3, 3));
    }

    @Test
    void reservesTheFifteenthQuestionForTheAlgorithmChallenge() {
        assertEquals(InterviewFlowPolicy.ASK_FOLLOW_UP, policy.constrain("ASK_FOLLOW_UP", 13, 8));
        assertEquals(InterviewFlowPolicy.END_INTERVIEW, policy.constrain("ASK_FOLLOW_UP", 14, 8));
    }

    @Test
    void reservesCapacityForTheMinimumPrimaryEvidence() {
        assertEquals(InterviewFlowPolicy.ASK_PRIMARY, policy.constrain("ASK_FOLLOW_UP", 13, 2));
    }

    @Test
    void capsPrimaryQuestionsAtEight() {
        assertEquals(InterviewFlowPolicy.ASK_PRIMARY, policy.constrain("ASK_PRIMARY", 7, 7));
        assertEquals(InterviewFlowPolicy.END_INTERVIEW, policy.constrain("ASK_PRIMARY", 8, 8));
    }

    @Test
    void normalizesLegacyEndAliasesAndUnknownModelOutputs() {
        assertEquals(InterviewFlowPolicy.END_INTERVIEW, policy.constrain("finish", 5, 3));
        assertEquals(InterviewFlowPolicy.END_INTERVIEW, policy.constrain("end", 5, 3));
        assertEquals(InterviewFlowPolicy.ASK_PRIMARY, policy.constrain("unrecognized", 5, 3));
    }

    @Test
    void damagedPrimaryCounterCannotCauseAnEarlyAlgorithmTransition() {
        assertEquals(
                InterviewFlowPolicy.ASK_PRIMARY,
                policy.constrain(InterviewFlowPolicy.END_INTERVIEW, 1, 99)
        );
    }

    @Test
    void acceptsLegacyNonNegativeValuesButRejectsMalformedNegativeValues() {
        new InterviewFlowPolicy(3, 8, 14);
        assertThrows(IllegalArgumentException.class, () -> new InterviewFlowPolicy(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new InterviewFlowPolicy(4, 3, 15));
        assertThrows(IllegalArgumentException.class, () -> new InterviewFlowPolicy(3, 8, 7));
    }
}
