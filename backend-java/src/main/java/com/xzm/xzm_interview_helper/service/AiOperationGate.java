package com.xzm.xzm_interview_helper.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Shared admission boundary for model-backed chat and interview operations.
 */
@Component
public class AiOperationGate {

    static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final int STARTS_PER_USER_PER_WINDOW = 12;
    private static final int USER_CONCURRENCY = 1;
    private static final int GLOBAL_CONCURRENCY = 6;
    private static final int MAX_TRACKED_USERS = 2_048;

    private final InMemoryAdmissionGate<Long> delegate;

    public AiOperationGate() {
        this(
                System::currentTimeMillis,
                STARTS_PER_USER_PER_WINDOW,
                USER_CONCURRENCY,
                GLOBAL_CONCURRENCY
        );
    }

    AiOperationGate(
            LongSupplier clock,
            int startsPerWindow,
            int userConcurrency,
            int globalConcurrency
    ) {
        delegate = new InMemoryAdmissionGate<>(
                clock,
                startsPerWindow,
                RATE_WINDOW_MILLIS,
                userConcurrency,
                globalConcurrency,
                MAX_TRACKED_USERS
        );
    }

    public <T> Flux<T> guardFlux(long userId, Supplier<Flux<T>> sourceFactory) {
        InMemoryAdmissionGate.Permit permit = acquire(userId);
        try {
            Flux<T> source = Objects.requireNonNull(sourceFactory.get(), "sourceFactory result");
            AtomicBoolean subscribed = new AtomicBoolean();
            // The permit belongs to the reactive subscription lifecycle. In particular, a browser
            // stop/cancel must release it just as reliably as completion and transport failure.
            return Flux.defer(() -> {
                if (!subscribed.compareAndSet(false, true)) {
                    return Flux.error(new IllegalStateException(
                            "A guarded AI stream cannot be subscribed more than once"
                    ));
                }
                return source.doFinally(ignored -> permit.close());
            });
        } catch (RuntimeException | Error exception) {
            permit.close();
            throw exception;
        }
    }

    public <T> T guardCall(long userId, Supplier<T> operation) {
        try (InMemoryAdmissionGate.Permit ignored = acquire(userId)) {
            return operation.get();
        }
    }

    private InMemoryAdmissionGate.Permit acquire(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return delegate.acquire(userId);
    }
}
