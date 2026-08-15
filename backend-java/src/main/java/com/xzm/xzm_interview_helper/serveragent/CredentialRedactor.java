package com.xzm.xzm_interview_helper.serveragent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Removes deployment credentials from process environments, outputs, and audit excerpts. */
@Component
public class CredentialRedactor {
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|secret|api[_-]?key|access[_-]?key|private[_-]?key|token|authorization)"
                    + "(\\s*[:=]\\s*)([^\\s,;]+)"
    );
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----"
    );
    private final List<String> sensitiveValues;

    public CredentialRedactor() {
        this(System.getenv());
    }

    CredentialRedactor(Map<String, String> environment) {
        sensitiveValues = new ArrayList<>();
        environment.forEach((name, value) -> {
            if (isSensitiveName(name) && value != null && value.length() >= 6) {
                sensitiveValues.add(value);
            }
        });
    }

    public void sanitizeEnvironment(Map<String, String> environment) {
        environment.keySet().removeIf(this::isSensitiveName);
    }

    public String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        String redacted = PRIVATE_KEY.matcher(value).replaceAll("[REDACTED PRIVATE KEY]");
        redacted = SECRET_ASSIGNMENT.matcher(redacted).replaceAll("$1$2[REDACTED]");
        for (String secret : sensitiveValues) {
            redacted = redacted.replace(secret, "[REDACTED]");
        }
        return redacted;
    }

    private boolean isSensitiveName(String name) {
        if (name == null) return false;
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("PASSWORD")
                || upper.contains("PASSWD")
                || upper.contains("SECRET")
                || upper.contains("TOKEN")
                || upper.contains("API_KEY")
                || upper.contains("ACCESS_KEY")
                || upper.contains("PRIVATE_KEY")
                || upper.endsWith("_KEY");
    }
}
