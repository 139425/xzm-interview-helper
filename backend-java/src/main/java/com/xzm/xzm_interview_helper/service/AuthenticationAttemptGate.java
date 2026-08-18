package com.xzm.xzm_interview_helper.service;

import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

/**
 * Separate rolling budgets for anonymous login and registration attempts.
 */
@Component
public class AuthenticationAttemptGate {

    static final long LOGIN_WINDOW_MILLIS = 60_000L;
    static final long REGISTER_WINDOW_MILLIS = 60L * 60L * 1000L;
    static final long VERIFICATION_WINDOW_MILLIS = 60L * 60L * 1000L;
    private static final int LOGIN_STARTS_PER_WINDOW = 10;
    private static final int REGISTER_STARTS_PER_WINDOW = 3;
    private static final int CHALLENGE_STARTS_PER_WINDOW = 60;
    private static final int EMAIL_STARTS_PER_WINDOW = 5;
    private static final int MAX_TRACKED_ADDRESSES = 4_096;

    private final InMemoryAdmissionGate<String> loginGate;
    private final InMemoryAdmissionGate<String> registerGate;
    private final InMemoryAdmissionGate<String> challengeGate;
    private final InMemoryAdmissionGate<String> emailGate;

    public AuthenticationAttemptGate() {
        this(
                System::currentTimeMillis,
                LOGIN_STARTS_PER_WINDOW,
                REGISTER_STARTS_PER_WINDOW
        );
    }

    AuthenticationAttemptGate(
            LongSupplier clock,
            int loginStartsPerWindow,
            int registerStartsPerWindow
    ) {
        loginGate = new InMemoryAdmissionGate<>(
                clock,
                loginStartsPerWindow,
                LOGIN_WINDOW_MILLIS,
                4,
                64,
                MAX_TRACKED_ADDRESSES
        );
        registerGate = new InMemoryAdmissionGate<>(
                clock,
                registerStartsPerWindow,
                REGISTER_WINDOW_MILLIS,
                2,
                16,
                MAX_TRACKED_ADDRESSES
        );
        challengeGate = new InMemoryAdmissionGate<>(
                clock,
                CHALLENGE_STARTS_PER_WINDOW,
                VERIFICATION_WINDOW_MILLIS,
                4,
                64,
                MAX_TRACKED_ADDRESSES
        );
        emailGate = new InMemoryAdmissionGate<>(
                clock,
                EMAIL_STARTS_PER_WINDOW,
                VERIFICATION_WINDOW_MILLIS,
                2,
                16,
                MAX_TRACKED_ADDRESSES
        );
    }

    public InMemoryAdmissionGate.Permit acquireLogin(String clientAddress) {
        return loginGate.acquire(normalizeKey(clientAddress));
    }

    public InMemoryAdmissionGate.Permit acquireRegister(String clientAddress) {
        return registerGate.acquire(normalizeKey(clientAddress));
    }

    public InMemoryAdmissionGate.Permit acquireChallenge(String clientAddress) {
        return challengeGate.acquire(normalizeKey(clientAddress));
    }

    public InMemoryAdmissionGate.Permit acquireEmail(String clientAddress) {
        return emailGate.acquire(normalizeKey(clientAddress));
    }

    private String normalizeKey(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        return clientAddress.trim();
    }
}
