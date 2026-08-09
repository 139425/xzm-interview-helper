package com.xzm.xzm_interview_helper.service;

import java.util.Locale;

/**
 * Constrains model recommendations with deterministic interview boundaries.
 *
 * <p>The model chooses the useful next action, while the application guarantees enough primary
 * evidence for a fair assessment and prevents an unexpectedly long interview.</p>
 */
public final class InterviewFlowPolicy {

    public static final String ASK_PRIMARY = "ASK_PRIMARY";
    public static final String ASK_FOLLOW_UP = "ASK_FOLLOW_UP";
    public static final String END_INTERVIEW = "END_INTERVIEW";

    private final int minPrimaryQuestions;
    private final int maxPrimaryQuestions;
    private final int maxTotalQuestions;

    public InterviewFlowPolicy(int minPrimaryQuestions, int maxPrimaryQuestions, int maxTotalQuestions) {
        if (minPrimaryQuestions <= 0
                || maxPrimaryQuestions < minPrimaryQuestions
                || maxTotalQuestions < maxPrimaryQuestions) {
            throw new IllegalArgumentException("Invalid interview question limits");
        }
        this.minPrimaryQuestions = minPrimaryQuestions;
        this.maxPrimaryQuestions = maxPrimaryQuestions;
        this.maxTotalQuestions = maxTotalQuestions;
    }

    public String constrain(String rawAction, int totalQuestionCount, int primaryQuestionCount) {
        int total = Math.max(0, totalQuestionCount);
        // A primary question is also a conversational question.  Clamp a damaged/legacy
        // denormalized counter instead of letting primary > total satisfy the minimum early and
        // send a candidate to the algorithm challenge before enough answered turns exist.
        int primary = Math.min(total, Math.max(0, primaryQuestionCount));
        String action = rawAction == null ? "" : rawAction.trim().toUpperCase(Locale.ROOT);
        if ("FINISH".equals(action) || "END".equals(action)) {
            action = END_INTERVIEW;
        }
        if (total >= maxTotalQuestions) {
            return END_INTERVIEW;
        }
        // Reserve enough remaining conversational slots to reach the minimum number of
        // independent primary questions. Otherwise repeated follow-ups could consume the cap.
        int remainingSlots = maxTotalQuestions - total;
        int missingPrimaryQuestions = Math.max(0, minPrimaryQuestions - primary);
        if (missingPrimaryQuestions >= remainingSlots) {
            return ASK_PRIMARY;
        }
        if (END_INTERVIEW.equals(action) && primary < minPrimaryQuestions) {
            return ASK_PRIMARY;
        }
        if (ASK_PRIMARY.equals(action) && primary >= maxPrimaryQuestions) {
            return END_INTERVIEW;
        }
        if (ASK_FOLLOW_UP.equals(action)) {
            return ASK_FOLLOW_UP;
        }
        if (ASK_PRIMARY.equals(action)) {
            return ASK_PRIMARY;
        }
        if (END_INTERVIEW.equals(action)) {
            return END_INTERVIEW;
        }
        return primary >= maxPrimaryQuestions ? END_INTERVIEW : ASK_PRIMARY;
    }
}
