package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in, read-only verification against the deployment database.
 *
 * Run with DB_URL, DB_USERNAME and DB_PASSWORD injected. The test never prints
 * credentials or row contents and is skipped in ordinary local/unit builds.
 */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class InterviewDatabaseIntegrationTest {

    @Test
    void connectsReadOnlyAndVerifiesInterviewTables() throws Exception {
        String url = requireEnvironment("DB_URL");
        String username = requireEnvironment("DB_USERNAME");
        String password = requireEnvironment("DB_PASSWORD");
        DriverManager.setLoginTimeout(10);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setReadOnly(true);
            for (var tableDefinition : InterviewAgentSchemaContract.REQUIRED_COLUMNS.entrySet()) {
                String table = tableDefinition.getKey();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + table
                ); ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "Missing readable table: " + table);
                    assertTrue(rows.getLong(1) >= 0, "Invalid count for table: " + table);
                }

                String columns = String.join(", ", tableDefinition.getValue());
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + columns + " FROM " + table + " WHERE 1 = 0"
                ); ResultSet ignored = statement.executeQuery()) {
                    assertTrue(ignored.getMetaData().getColumnCount() == tableDefinition.getValue().size(),
                            "Missing required columns in table: " + table);
                }
            }
        }
    }

    private String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be injected for the database integration test");
        }
        return value;
    }
}
