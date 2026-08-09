package com.xzm.xzm_interview_helper.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Process-local admission control for expensive algorithm operations.
 *
 * <p>Judge execution and AI review use separate pools because they consume
 * different upstream resources. Each pool combines a rolling per-user rate
 * limit, one in-flight operation per user, and a global concurrency cap.</p>
 */
@Component
public class AlgorithmOperationGate {

    static final long RATE_WINDOW_MILLIS = 60_000L;
    static final int MAX_TRACKED_USERS = 2_048;

    private static final int JUDGE_STARTS_PER_MINUTE = 6;
    private static final int JUDGE_GLOBAL_CONCURRENCY = 4;
    private static final int AI_REVIEW_STARTS_PER_MINUTE = 3;
    private static final int AI_REVIEW_GLOBAL_CONCURRENCY = 4;

    private final OperationPool judgePool;
    private final OperationPool aiReviewPool;

    public AlgorithmOperationGate() {
        this(
                System::currentTimeMillis,
                JUDGE_STARTS_PER_MINUTE,
                JUDGE_GLOBAL_CONCURRENCY,
                AI_REVIEW_STARTS_PER_MINUTE,
                AI_REVIEW_GLOBAL_CONCURRENCY
        );
    }

    AlgorithmOperationGate(
            LongSupplier clock,
            int judgeStartsPerWindow,
            int judgeGlobalConcurrency,
            int aiReviewStartsPerWindow,
            int aiReviewGlobalConcurrency
    ) {
        this.judgePool = new OperationPool(
                clock,
                judgeStartsPerWindow,
                judgeGlobalConcurrency
        );
        this.aiReviewPool = new OperationPool(
                clock,
                aiReviewStartsPerWindow,
                aiReviewGlobalConcurrency
        );
    }

    public Permit acquireJudge(long userId) {
        return judgePool.acquire(userId);
    }

    public Permit acquireAiReview(long userId) {
        return aiReviewPool.acquire(userId);
    }

    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    public enum RejectionReason {
        USER_IN_FLIGHT,
        RATE_LIMITED,
        GLOBAL_BUSY
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

    private static final class OperationPool {
        private final LongSupplier clock;
        private final int startsPerWindow;
        private final Semaphore globalPermits;
        private final ConcurrentHashMap<Long, UserBudget> userBudgets =
                new ConcurrentHashMap<>();

        private OperationPool(
                LongSupplier clock,
                int startsPerWindow,
                int globalConcurrency
        ) {
            if (startsPerWindow <= 0 || globalConcurrency <= 0) {
                throw new IllegalArgumentException(
                        "Rate and concurrency limits must be positive"
                );
            }
            this.clock = clock;
            this.startsPerWindow = startsPerWindow;
            this.globalPermits = new Semaphore(globalConcurrency, true);
        }

        private Permit acquire(long userId) {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }

            long now = clock.getAsLong();
            cleanupExpiredBudgets(now);
            if (!globalPermits.tryAcquire()) {
                throw new RejectedException(RejectionReason.GLOBAL_BUSY);
            }

            AtomicReference<RejectionReason> rejection = new AtomicReference<>();
            AtomicReference<UserBudget> reservedBudget = new AtomicReference<>();
            try {
                userBudgets.compute(userId, (ignored, existing) -> {
                    UserBudget budget = existing == null ? new UserBudget() : existing;
                    RejectionReason reason = budget.tryReserve(now, startsPerWindow);
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

            UserBudget budget = reservedBudget.get();
            if (budget == null) {
                globalPermits.release();
                throw new IllegalStateException("Algorithm operation permit was not reserved");
            }
            return new PoolPermit(globalPermits, budget);
        }

        private void cleanupExpiredBudgets(long now) {
            if (userBudgets.size() <= MAX_TRACKED_USERS) {
                return;
            }
            for (Map.Entry<Long, UserBudget> entry : userBudgets.entrySet()) {
                Long userId = entry.getKey();
                userBudgets.computeIfPresent(
                        userId,
                        (ignored, budget) -> budget.isIdleAndExpired(now) ? null : budget
                );
            }
        }
    }

    private static final class PoolPermit implements Permit {
        private final Semaphore globalPermits;
        private final UserBudget userBudget;
        private final AtomicBoolean closed = new AtomicBoolean();

        private PoolPermit(Semaphore globalPermits, UserBudget userBudget) {
            this.globalPermits = globalPermits;
            this.userBudget = userBudget;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                userBudget.release();
            } finally {
                globalPermits.release();
            }
        }
    }

    private static final class UserBudget {
        private final Deque<Long> operationStarts = new ArrayDeque<>();
        private boolean inFlight;

        private synchronized RejectionReason tryReserve(long now, int startsPerWindow) {
            discardExpiredStarts(now);
            if (inFlight) {
                return RejectionReason.USER_IN_FLIGHT;
            }
            if (operationStarts.size() >= startsPerWindow) {
                return RejectionReason.RATE_LIMITED;
            }
            operationStarts.addLast(now);
            inFlight = true;
            return null;
        }

        private synchronized void release() {
            inFlight = false;
        }

        private synchronized boolean isIdleAndExpired(long now) {
            discardExpiredStarts(now);
            return !inFlight && operationStarts.isEmpty();
        }

        private void discardExpiredStarts(long now) {
            while (!operationStarts.isEmpty()
                    && now - operationStarts.peekFirst() >= RATE_WINDOW_MILLIS) {
                operationStarts.removeFirst();
            }
        }
    }
}
