package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class InterviewCancellationGateTest {

    @Test
    void cancellationThatWinsTheGatePreventsPersistence() {
        AtomicBoolean cancellation = new AtomicBoolean(false);
        AtomicInteger writes = new AtomicInteger();
        InterviewCancellationGate.cancel(cancellation);

        assertThrows(CancellationException.class, () ->
                InterviewCancellationGate.persist(cancellation, writes::incrementAndGet)
        );
        assertEquals(0, writes.get());
    }

    @Test
    void persistenceThatWinsTheGateFinishesAtomicallyBeforeCancellation() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            AtomicBoolean cancellation = new AtomicBoolean(false);
            CountDownLatch writeEntered = new CountDownLatch(1);
            CountDownLatch releaseWrite = new CountDownLatch(1);
            CountDownLatch stopAttempted = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> write = executor.submit(() ->
                        InterviewCancellationGate.persist(cancellation, () -> {
                            writeEntered.countDown();
                            await(releaseWrite);
                            return "saved";
                        })
                );
                assertTrue(writeEntered.await(1, TimeUnit.SECONDS));

                Future<?> stop = executor.submit(() -> {
                    stopAttempted.countDown();
                    InterviewCancellationGate.cancel(cancellation);
                });
                assertTrue(stopAttempted.await(1, TimeUnit.SECONDS));
                assertFalse(stop.isDone(), "Stop must wait for the atomic persistence section");

                releaseWrite.countDown();
                assertEquals("saved", write.get(1, TimeUnit.SECONDS));
                stop.get(1, TimeUnit.SECONDS);
                assertTrue(cancellation.get());
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test gate");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test gate", exception);
        }
    }
}
