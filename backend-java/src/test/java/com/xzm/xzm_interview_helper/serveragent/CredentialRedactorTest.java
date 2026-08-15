package com.xzm.xzm_interview_helper.serveragent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialRedactorTest {
    @Test
    void stripsSensitiveEnvironmentAndRedactsKnownValues() {
        CredentialRedactor redactor = new CredentialRedactor(Map.of(
                "DB_PASSWORD", "very-secret-value",
                "PATH", "/bin"
        ));
        Map<String, String> childEnvironment = new HashMap<>(Map.of(
                "DB_PASSWORD", "very-secret-value",
                "PATH", "/bin"
        ));

        redactor.sanitizeEnvironment(childEnvironment);

        assertThat(childEnvironment).containsOnlyKeys("PATH");
        assertThat(redactor.redact("password=visible very-secret-value"))
                .isEqualTo("password=[REDACTED] [REDACTED]");
    }
}
