package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiOperationGateTest {

    @Test
    void fluxCancellationReleasesUserAndGlobalPermits() {
        AiOperationGate gate = gate(10, 1, 2);
        Flux<String> guarded = gate.guardFlux(7, Flux::never);
        Disposable subscription = guarded.subscribe();

        assertRejected(
                InMemoryAdmissionGate.RejectionReason.KEY_BUSY,
                () -> gate.guardCall(7, () -> "blocked")
        );

        subscription.dispose();
        assertEquals("released", gate.guardCall(7, () -> "released"));
    }

    @Test
    void fluxCompletionAndErrorBothReleasePermits() {
        AiOperationGate gate = gate(10, 1, 1);

        List<String> values = gate.guardFlux(7, () -> Flux.just("done"))
                .collectList()
                .block();
        assertEquals(List.of("done"), values);
        assertEquals("after-complete", gate.guardCall(7, () -> "after-complete"));

        assertThrows(
                IllegalStateException.class,
                () -> gate.guardFlux(
                                8,
                                () -> Flux.error(new IllegalStateException("transport failed"))
                        )
                        .blockLast()
        );
        assertEquals("after-error", gate.guardCall(8, () -> "after-error"));
    }

    @Test
    void synchronousFactoryFailureReleasesPermit() {
        AiOperationGate gate = gate(10, 1, 1);

        assertThrows(
                IllegalStateException.class,
                () -> gate.guardFlux(7, () -> {
                    throw new IllegalStateException("source construction failed");
                })
        );

        assertEquals("released", gate.guardCall(7, () -> "released"));
    }

    @Test
    void guardedFluxIsSingleSubscriptionOnly() {
        AiOperationGate gate = gate(10, 1, 1);
        Flux<String> guarded = gate.guardFlux(7, () -> Flux.just("once"));

        assertEquals("once", guarded.blockLast());
        assertThrows(IllegalStateException.class, guarded::blockLast);
        assertEquals("available", gate.guardCall(7, () -> "available"));
    }

    @Test
    void rateAndGlobalLimitsAreSharedAcrossAiOperations() {
        AiOperationGate gate = gate(2, 1, 1);
        Flux<String> firstUser = gate.guardFlux(1, Flux::never);
        Disposable subscription = firstUser.subscribe();
        try {
            assertRejected(
                    InMemoryAdmissionGate.RejectionReason.GLOBAL_BUSY,
                    () -> gate.guardCall(2, () -> "blocked")
            );
        } finally {
            subscription.dispose();
        }

        assertEquals("one", gate.guardCall(2, () -> "one"));
        assertEquals("two", gate.guardCall(2, () -> "two"));
        assertRejected(
                InMemoryAdmissionGate.RejectionReason.RATE_LIMITED,
                () -> gate.guardCall(2, () -> "three")
        );
    }

    private AiOperationGate gate(int starts, int userConcurrency, int globalConcurrency) {
        return new AiOperationGate(
                new AtomicLong()::get,
                starts,
                userConcurrency,
                globalConcurrency
        );
    }

    private void assertRejected(
            InMemoryAdmissionGate.RejectionReason expected,
            Runnable operation
    ) {
        InMemoryAdmissionGate.RejectedException exception = assertThrows(
                InMemoryAdmissionGate.RejectedException.class,
                operation::run
        );
        assertEquals(expected, exception.getReason());
    }
}
