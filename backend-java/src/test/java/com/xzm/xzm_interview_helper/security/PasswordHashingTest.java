package com.xzm.xzm_interview_helper.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashingTest {
    @Test
    void encodesAndVerifiesWithBcrypt() {
        String encoded = PasswordHashing.encode("candidate-secret");

        assertNotEquals("candidate-secret", encoded);
        assertTrue(PasswordHashing.isEncoded(encoded));
        assertTrue(PasswordHashing.matches("candidate-secret", encoded));
        assertFalse(PasswordHashing.matches("wrong", encoded));
    }

    @Test
    void acceptsLegacyPlaintextOnlyForLoginMigration() {
        assertTrue(PasswordHashing.matches("legacy-secret", "legacy-secret"));
        assertFalse(PasswordHashing.matches("wrong", "legacy-secret"));
        assertFalse(PasswordHashing.isEncoded("legacy-secret"));
    }
}
