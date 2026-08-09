package com.xzm.xzm_interview_helper.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * A bounded, process-local rolling-window admission gate.
 *
 * <p>The gate intentionally rejects instead of queuing. Queuing expensive
 * upstream work would only move the denial-of-service pressure into server
 * memory and request threads.</p>
 */
public final class InMemoryAdmissionGate<K> {

    private final LongSupplier clock;
    private final int maxStartsPerWindow;
    private final long windowMillis;
    private final int maxInFlightPerKey;
    private final int maxTrackedKeys;
    private final Semaphore globalPermits;
    private final ConcurrentHashMap<K, KeyBudget> budgets = new ConcurrentHashMap<>();

    public InMemoryAdmissionGate(
            LongSupplier clock,
            int maxStartsPerWindow,
            long windowMillis,
            int maxInFlightPerKey,
            int globalConcurrency,
            int maxTrackedKeys
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxStartsPerWindow <= 0
                || windowMillis <= 0
                || maxInFlightPerKey <= 0
                || globalConcurrency <= 0
                || maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("Admission gate limits must be positive");
        }
        this.maxStartsPerWindow = maxStartsPerWindow;
        this.windowMillis = windowMillis;
        this.maxInFlightPerKey = maxInFlightPerKey;
        this.maxTrackedKeys = maxTrackedKeys;
        this.globalPermits = new Semaphore(globalConcurrency, true);
    }

    public Permit acquire(K key) {
        Objects.requireNonNull(key, "key");
        long now = clock.getAsLong();
        cleanupExpiredBudgets(now);
        if (!globalPermits.tryAcquire()) {
            throw new RejectedException(RejectionReason.GLOBAL_BUSY);
        }

        AtomicReference<RejectionReason> rejection = new AtomicReference<>();
        AtomicReference<KeyBudget> reservedBudget = new AtomicReference<>();
        try {
            budgets.compute(key, (ignored, existing) -> {
                if (existing == null && budgets.size() >= maxTrackedKeys) {
                    rejection.set(RejectionReason.TRACKING_CAPACITY);
                    return null;
                }
                KeyBudget budget = existing == null ? new KeyBudget() : existing;
                RejectionReason reason = budget.tryReserve(
                        now,
                        maxStartsPerWindow,
                        windowMillis,
                        maxInFlightPerKey
                );
                rejection.set(reason);
                if (reason == null) {
                    reservedBudget.set(budget);
                }
                return budget;
            });
        } catch (RuntimeException exception) {
            globalPermits.release();
            throw exception;
        }

        if (rejection.get() != null) {
            globalPermits.release();
            throw new RejectedException(rejection.get());
        }
        KeyBudget budget = reservedBudget.get();
        if (budget == null) {
            globalPermits.release();
            throw new IllegalStateException("Admission permit was not reserved");
        }
        return new GatePermit(globalPermits, budget);
    }

    private void cleanupExpiredBudgets(long now) {
        if (budgets.size() < maxTrackedKeys) {
            return;
        }
        for (Map.Entry<K, KeyBudget> entry : budgets.entrySet()) {
            budgets.computeIfPresent(
                    entry.getKey(),
                    (ignored, budget) -> budget.isIdleAndExpired(now, windowMillis)
                            ? null
                            : budget
            );
        }
    }

    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    public enum RejectionReason {
        KEY_BUSY,
        RATE_LIMITED,
        GLOBAL_BUSY,
        TRACKING_CAPACITY
    }

    public static final class RejectedException extends RuntimeException {
        private final RejectionReason reason;

        private RejectedException(RejectionReason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public RejectionReason getReason() {
            return reason;
        }
    }

    private static final class GatePermit implements Permit {
        private final Semaphore globalPermits;
        private final KeyBudget budget;
        private final AtomicBoolean closed = new AtomicBoolean();

        private GatePermit(Semaphore globalPermits, KeyBudget budget) {
            this.globalPermits = globalPermits;
            this.budget = budget;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                budget.release();
            } finally {
                globalPermits.release();
            }
        }
    }

    private static final class KeyBudget {
        private final Deque<Long> starts = new ArrayDeque<>();
        private int inFlight;

        private synchronized RejectionReason tryReserve(
                long now,
                int maxStarts,
                long windowMillis,
                int maxInFlight
        ) {
            discardExpiredStarts(now, windowMillis);
            if (inFlight >= maxInFlight) {
                return RejectionReason.KEY_BUSY;
            }
            if (starts.size() >= maxStarts) {
                return RejectionReason.RATE_LIMITED;
            }
            starts.addLast(now);
            inFlight++;
            return null;
        }

        private synchronized void release() {
            if (inFlight <= 0) {
                throw new IllegalStateException("Admission permit released without reservation");
            }
            inFlight--;
        }

        private synchronized boolean isIdleAndExpired(long now, long windowMillis) {
            discardExpiredStarts(now, windowMillis);
            return inFlight == 0 && starts.isEmpty();
        }

        private void discardExpiredStarts(long now, long windowMillis) {
            while (!starts.isEmpty() && now - starts.peekFirst() >= windowMillis) {
                starts.removeFirst();
            }
        }
    }
}
