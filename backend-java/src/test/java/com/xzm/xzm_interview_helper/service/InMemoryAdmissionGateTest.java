package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryAdmissionGateTest {

    @Test
    void enforcesKeyAndGlobalConcurrencyAndReleasesIdempotently() {
        InMemoryAdmissionGate<String> gate = gate(new AtomicLong(), 10, 2, 2, 10);
        InMemoryAdmissionGate.Permit first = gate.acquire("user-a");
        InMemoryAdmissionGate.Permit second = gate.acquire("user-a");
        try {
            InMemoryAdmissionGate.RejectedException keyBusy = assertThrows(
                    InMemoryAdmissionGate.RejectedException.class,
                    () -> gate.acquire("user-a")
            );
            assertEquals(InMemoryAdmissionGate.RejectionReason.GLOBAL_BUSY, keyBusy.getReason());

            InMemoryAdmissionGate.RejectedException globalBusy = assertThrows(
                    InMemoryAdmissionGate.RejectedException.class,
                    () -> gate.acquire("user-b")
            );
            assertEquals(
                    InMemoryAdmissionGate.RejectionReason.GLOBAL_BUSY,
                    globalBusy.getReason()
            );
        } finally {
            first.close();
            first.close();
            second.close();
        }

        assertDoesNotThrow(() -> {
            try (InMemoryAdmissionGate.Permit ignored = gate.acquire("user-b")) {
                // Both global permits were released exactly once.
            }
        });
    }

    @Test
    void reportsKeyBusyWhenGlobalCapacityStillExists() {
        InMemoryAdmissionGate<String> gate = gate(new AtomicLong(), 10, 1, 3, 10);

        try (InMemoryAdmissionGate.Permit ignored = gate.acquire("user-a")) {
            InMemoryAdmissionGate.RejectedException exception = assertThrows(
                    InMemoryAdmissionGate.RejectedException.class,
                    () -> gate.acquire("user-a")
            );
            assertEquals(InMemoryAdmissionGate.RejectionReason.KEY_BUSY, exception.getReason());
        }
    }

    @Test
    void appliesRollingRateAndRecoversAfterWindow() {
        AtomicLong now = new AtomicLong(5_000L);
        InMemoryAdmissionGate<String> gate = gate(now, 2, 1, 2, 10);

        for (int attempt = 0; attempt < 2; attempt++) {
            try (InMemoryAdmissionGate.Permit ignored = gate.acquire("user-a")) {
                // Two starts fit in the active window.
            }
        }
        InMemoryAdmissionGate.RejectedException exception = assertThrows(
                InMemoryAdmissionGate.RejectedException.class,
                () -> gate.acquire("user-a")
        );
        assertEquals(InMemoryAdmissionGate.RejectionReason.RATE_LIMITED, exception.getReason());

        now.addAndGet(1_000L);
        assertDoesNotThrow(() -> {
            try (InMemoryAdmissionGate.Permit ignored = gate.acquire("user-a")) {
                // The two previous starts have expired.
            }
        });
    }

    @Test
    void rejectsNewKeysAtTrackingCapacityAndReusesExpiredSlots() {
        AtomicLong now = new AtomicLong();
        InMemoryAdmissionGate<String> gate = gate(now, 2, 1, 3, 2);
        try (InMemoryAdmissionGate.Permit ignored = gate.acquire("a")) {
            // Retain a recent budget.
        }
        try (InMemoryAdmissionGate.Permit ignored = gate.acquire("b")) {
            // Retain a second recent budget.
        }

        InMemoryAdmissionGate.RejectedException exception = assertThrows(
                InMemoryAdmissionGate.RejectedException.class,
                () -> gate.acquire("c")
        );
        assertEquals(
                InMemoryAdmissionGate.RejectionReason.TRACKING_CAPACITY,
                exception.getReason()
        );

        now.addAndGet(1_000L);
        assertDoesNotThrow(() -> {
            try (InMemoryAdmissionGate.Permit ignored = gate.acquire("c")) {
                // Cleanup removes idle expired keys before admitting a new one.
            }
        });
    }

    private InMemoryAdmissionGate<String> gate(
            AtomicLong now,
            int starts,
            int perKeyConcurrency,
            int globalConcurrency,
            int trackedKeys
    ) {
        return new InMemoryAdmissionGate<>(
                now::get,
                starts,
                1_000L,
                perKeyConcurrency,
                globalConcurrency,
                trackedKeys
        );
    }
}
