package com.xzm.xzm_interview_helper.service;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Defines the linearization point between a user stop request and durable persistence.
 *
 * If cancellation acquires the gate first, no subsequent write is allowed. If a write has already
 * acquired the gate, that atomic write is allowed to finish and cancellation becomes visible
 * immediately afterwards. The client can then recover the authoritative durable snapshot instead
 * of observing a half-written interview turn.
 */
final class InterviewCancellationGate {

    private InterviewCancellationGate() {
    }

    static void cancel(AtomicBoolean cancellation) {
        if (cancellation == null) {
            return;
        }
        synchronized (cancellation) {
            cancellation.set(true);
        }
    }

    static <T> T persist(AtomicBoolean cancellation, Supplier<T> persistence) {
        Objects.requireNonNull(persistence, "persistence");
        if (cancellation == null) {
            return persistence.get();
        }
        synchronized (cancellation) {
            if (cancellation.get()) {
                throw new CancellationException("Interview request cancelled by client");
            }
            return persistence.get();
        }
    }
}
