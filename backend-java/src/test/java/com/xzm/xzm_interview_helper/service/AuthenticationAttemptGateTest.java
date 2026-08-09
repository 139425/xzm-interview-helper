package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationAttemptGateTest {

    @Test
    void loginAndRegistrationHaveIndependentBudgets() {
        AuthenticationAttemptGate gate =
                new AuthenticationAttemptGate(new AtomicLong()::get, 2, 1);

        for (int attempt = 0; attempt < 2; attempt++) {
            try (InMemoryAdmissionGate.Permit ignored =
                         gate.acquireLogin("198.51.100.10")) {
                // Two login attempts are admitted.
            }
        }
        assertReason(
                InMemoryAdmissionGate.RejectionReason.RATE_LIMITED,
                () -> gate.acquireLogin("198.51.100.10")
        );

        assertDoesNotThrow(() -> {
            try (InMemoryAdmissionGate.Permit ignored =
                         gate.acquireRegister("198.51.100.10")) {
                // Registration has a separate budget.
            }
        });
        assertReason(
                InMemoryAdmissionGate.RejectionReason.RATE_LIMITED,
                () -> gate.acquireRegister("198.51.100.10")
        );
    }

    @Test
    void addressesDoNotShareRateBudget() {
        AuthenticationAttemptGate gate =
                new AuthenticationAttemptGate(new AtomicLong()::get, 1, 1);

        try (InMemoryAdmissionGate.Permit ignored =
                     gate.acquireLogin("198.51.100.10")) {
            // First address consumes its budget.
        }

        assertDoesNotThrow(() -> {
            try (InMemoryAdmissionGate.Permit ignored =
                         gate.acquireLogin("198.51.100.11")) {
                // A different direct peer has its own budget.
            }
        });
    }

    private void assertReason(
            InMemoryAdmissionGate.RejectionReason expected,
            ThrowingSupplier operation
    ) {
        InMemoryAdmissionGate.RejectedException exception = assertThrows(
                InMemoryAdmissionGate.RejectedException.class,
                operation::get
        );
        assertEquals(expected, exception.getReason());
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        InMemoryAdmissionGate.Permit get();
    }
}
