package com.xzm.xzm_interview_helper.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** BCrypt policy plus a constant-time compatibility check for legacy plaintext rows. */
public final class PasswordHashing {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private PasswordHashing() {
    }

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        if (isEncoded(storedPassword)) return ENCODER.matches(rawPassword, storedPassword);
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean isEncoded(String storedPassword) {
        return storedPassword != null && storedPassword.matches("^\\$2[aby]\\$\\d{2}\\$.*");
    }
}
