package com.xzm.xzm_interview_helper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fails application startup before an interview can begin when an existing database has drifted
 * from the durable-state contract. {@code CREATE TABLE IF NOT EXISTS} creates clean installations
 * but cannot add a column to an older table, so an explicit post-initialization check is required.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.interview",
        name = "schema-validation-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class InterviewAgentSchemaValidator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Set<String>> actualColumns = new HashMap<>();
        for (String table : InterviewAgentSchemaContract.REQUIRED_COLUMNS.keySet()) {
            List<String> columns = jdbcTemplate.queryForList(
                    """
                    SELECT COLUMN_NAME
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                    """,
                    String.class,
                    table
            );
            actualColumns.put(table, new HashSet<>(columns));
        }

        List<String> missing = findMissing(actualColumns);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Interview-agent database schema is incomplete: "
                            + String.join(", ", missing)
                            + ". Apply the interview schema migration before starting the service."
            );
        }
        log.info("Interview-agent database schema validation passed");
    }

    static List<String> findMissing(Map<String, Set<String>> actualColumns) {
        List<String> missing = new ArrayList<>();
        InterviewAgentSchemaContract.REQUIRED_COLUMNS.forEach((table, requiredColumns) -> {
            Set<String> actual = new HashSet<>();
            for (String column : actualColumns.getOrDefault(table, Set.of())) {
                if (column != null) {
                    actual.add(column.toLowerCase(Locale.ROOT));
                }
            }
            if (actual.isEmpty()) {
                missing.add(table + " (table)");
                return;
            }
            for (String column : requiredColumns) {
                if (!actual.contains(column.toLowerCase(Locale.ROOT))) {
                    missing.add(table + "." + column);
                }
            }
        });
        return missing;
    }
}
