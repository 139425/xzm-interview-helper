package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlgorithmOperationGateTest {

    @Test
    void rejectsASecondInFlightOperationForTheSameUser() {
        AlgorithmOperationGate gate = gate(new AtomicLong(), 6, 4, 3, 4);

        try (AlgorithmOperationGate.Permit ignored = gate.acquireJudge(7)) {
            AlgorithmOperationGate.RejectedException exception = assertThrows(
                    AlgorithmOperationGate.RejectedException.class,
                    () -> gate.acquireJudge(7)
            );

            assertEquals(
                    AlgorithmOperationGate.RejectionReason.USER_IN_FLIGHT,
                    exception.getReason()
            );
        }

        assertDoesNotThrow(() -> {
            try (AlgorithmOperationGate.Permit ignored = gate.acquireJudge(7)) {
                // Closing the first permit releases both user and global capacity.
            }
        });
    }

    @Test
    void enforcesAndReleasesGlobalConcurrencyWithoutChargingRejectedUser() {
        AlgorithmOperationGate gate = gate(new AtomicLong(), 6, 1, 3, 1);

        try (AlgorithmOperationGate.Permit ignored = gate.acquireJudge(1)) {
            AlgorithmOperationGate.RejectedException exception = assertThrows(
                    AlgorithmOperationGate.RejectedException.class,
                    () -> gate.acquireJudge(2)
            );
            assertEquals(
                    AlgorithmOperationGate.RejectionReason.GLOBAL_BUSY,
                    exception.getReason()
            );
        }

        assertDoesNotThrow(() -> {
            try (AlgorithmOperationGate.Permit ignored = gate.acquireJudge(2)) {
                // A global-busy rejection must not consume user 2's rate budget.
            }
        });
    }

    @Test
    void resetsRollingAiReviewRateAfterOneMinute() {
        AtomicLong now = new AtomicLong(10_000L);
        AlgorithmOperationGate gate = gate(now, 6, 4, 3, 4);

        for (int attempt = 0; attempt < 3; attempt++) {
            try (AlgorithmOperationGate.Permit ignored = gate.acquireAiReview(9)) {
                // Three starts are allowed in the active window.
            }
        }

        AlgorithmOperationGate.RejectedException exception = assertThrows(
                AlgorithmOperationGate.RejectedException.class,
                () -> gate.acquireAiReview(9)
        );
        assertEquals(
                AlgorithmOperationGate.RejectionReason.RATE_LIMITED,
                exception.getReason()
        );

        now.addAndGet(AlgorithmOperationGate.RATE_WINDOW_MILLIS);
        assertDoesNotThrow(() -> {
            try (AlgorithmOperationGate.Permit ignored = gate.acquireAiReview(9)) {
                // The oldest starts have left the rolling window.
            }
        });
    }

    @Test
    void judgeAndAiReviewHaveIndependentBudgetsAndCapacity() {
        AlgorithmOperationGate gate = gate(new AtomicLong(), 1, 1, 1, 1);

        try (AlgorithmOperationGate.Permit ignored = gate.acquireJudge(11)) {
            assertDoesNotThrow(() -> {
                try (AlgorithmOperationGate.Permit review = gate.acquireAiReview(11)) {
                    // Separate upstream resources must not block each other.
                }
            });
        }

        AlgorithmOperationGate.RejectedException exception = assertThrows(
                AlgorithmOperationGate.RejectedException.class,
                () -> gate.acquireJudge(11)
        );
        assertEquals(
                AlgorithmOperationGate.RejectionReason.RATE_LIMITED,
                exception.getReason()
        );
    }

    @Test
    void closingPermitTwiceDoesNotOverReleaseGlobalCapacity() {
        AlgorithmOperationGate gate = gate(new AtomicLong(), 6, 1, 3, 1);
        AlgorithmOperationGate.Permit permit = gate.acquireJudge(1);

        permit.close();
        permit.close();

        AlgorithmOperationGate.Permit second = gate.acquireJudge(2);
        try {
            AlgorithmOperationGate.RejectedException exception = assertThrows(
                    AlgorithmOperationGate.RejectedException.class,
                    () -> gate.acquireJudge(3)
            );
            assertEquals(
                    AlgorithmOperationGate.RejectionReason.GLOBAL_BUSY,
                    exception.getReason()
            );
        } finally {
            second.close();
        }
    }

    private AlgorithmOperationGate gate(
            AtomicLong now,
            int judgeStarts,
            int judgeConcurrency,
            int aiReviewStarts,
            int aiReviewConcurrency
    ) {
        return new AlgorithmOperationGate(
                now::get,
                judgeStarts,
                judgeConcurrency,
                aiReviewStarts,
                aiReviewConcurrency
        );
    }
}
