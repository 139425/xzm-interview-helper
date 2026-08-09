package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in integration test for the complete durable interview record.
 *
 * The inserts and reads run in one transaction which is always rolled back, so
 * no candidate data or test fixture remains in the target database.
 */
@EnabledIfEnvironmentVariable(named = "INTERVIEW_DB_WRITE_TEST", matches = "(?i)true")
class InterviewDatabasePersistenceIntegrationTest {

    @Test
    void writesReadsAndRollsBackACompleteInterviewRecord() throws Exception {
        String url = requireEnvironment("DB_URL");
        String username = requireEnvironment("DB_USERNAME");
        String password = requireEnvironment("DB_PASSWORD");
        DriverManager.setLoginTimeout(10);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                long sessionId = insertSession(connection);
                long turnId = insertTurn(connection, sessionId);
                insertEvent(connection, sessionId, turnId);
                long algorithmTurnId = insertAlgorithmTurn(connection, sessionId);
                long submissionId = insertAlgorithmSubmission(connection, sessionId);
                insertAlgorithmChallenge(connection, sessionId, algorithmTurnId, submissionId);
                verifyCompleteRecord(connection, sessionId, turnId, algorithmTurnId, submissionId);
            } finally {
                connection.rollback();
            }
        }
    }

    private long insertSession(Connection connection) throws Exception {
        String sql = """
                INSERT INTO ai_interview_agent_session (
                    public_id, user_id, status, resume_text, resume_file_name, target_role,
                    model_provider, model_name, thinking_enabled, total_question_count,
                    primary_question_count, follow_up_count, summary, started_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setString(1, UUID.randomUUID().toString());
            statement.setInt(2, 42);
            statement.setString(3, "COMPLETED");
            statement.setString(4, "Candidate resume with project and skill evidence.");
            statement.setString(5, "candidate.md");
            statement.setString(6, "Backend Engineer");
            statement.setString(7, "test-provider");
            statement.setString(8, "test-model");
            statement.setBoolean(9, true);
            statement.setInt(10, 4);
            statement.setInt(11, 3);
            statement.setInt(12, 1);
            statement.setString(13, "Durable interview summary.");
            statement.setTimestamp(14, now);
            statement.setTimestamp(15, now);
            assertEquals(1, statement.executeUpdate());
            return generatedId(statement, "session");
        }
    }

    private long insertTurn(Connection connection, long sessionId) throws Exception {
        String sql = """
                INSERT INTO ai_interview_agent_turn (
                    session_id, sequence_no, parent_turn_id, question_kind, question, answer,
                    score, evaluation, knowledge_tags, reference_answer, agent_action,
                    decision_note, model_provider, model_name, answered_at, evaluated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setLong(1, sessionId);
            statement.setInt(2, 4);
            statement.setNull(3, java.sql.Types.BIGINT);
            statement.setString(4, "PRIMARY");
            statement.setString(5, "How did you validate the architecture trade-off?");
            statement.setString(6, "I used load tests and production metrics.");
            statement.setInt(7, 8);
            statement.setString(8, "The answer included measurable validation evidence.");
            statement.setString(9, "architecture,load-testing");
            statement.setString(10, "A strong answer compares constraints, alternatives, and evidence.");
            statement.setString(11, "END_INTERVIEW");
            statement.setString(12, "The required competency range has been covered.");
            statement.setString(13, "test-provider");
            statement.setString(14, "test-model");
            statement.setTimestamp(15, now);
            statement.setTimestamp(16, now);
            assertEquals(1, statement.executeUpdate());
            return generatedId(statement, "turn");
        }
    }

    private void insertEvent(Connection connection, long sessionId, long turnId) throws Exception {
        String sql = """
                INSERT INTO ai_interview_agent_event (
                    session_id, turn_id, sequence_no, event_type, tool_name, title,
                    detail, payload_json, visibility
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, turnId);
            statement.setInt(3, 1);
            statement.setString(4, "completed");
            statement.setString(5, "summary_generation");
            statement.setString(6, "Interview completed");
            statement.setString(7, "A candidate-safe lifecycle event.");
            statement.setString(8, "{\"version\":1}");
            statement.setString(9, "candidate");
            assertEquals(1, statement.executeUpdate());
        }
    }

    private long insertAlgorithmTurn(Connection connection, long sessionId) throws Exception {
        String sql = """
                INSERT INTO ai_interview_agent_turn (
                    session_id, sequence_no, question_kind, question, answer, score,
                    evaluation, knowledge_tags, agent_action, decision_note, answered_at, evaluated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setLong(1, sessionId);
            statement.setInt(2, 5);
            statement.setString(3, "ALGORITHM");
            statement.setString(4, "Two Sum");
            statement.setString(5, "class Solution { int[] twoSum(int[] nums, int target) { return null; } }");
            statement.setInt(6, 10);
            statement.setString(7, "Accepted 5/5 hidden cases.");
            statement.setString(8, "算法实战,MEDIUM");
            statement.setString(9, "END_INTERVIEW");
            statement.setString(10, "Completed within the server deadline.");
            statement.setTimestamp(11, now);
            statement.setTimestamp(12, now);
            assertEquals(1, statement.executeUpdate());
            return generatedId(statement, "algorithm turn");
        }
    }

    private long insertAlgorithmSubmission(Connection connection, long sessionId) throws Exception {
        String sql = """
                INSERT INTO algorithm_submission (
                    user_id, interview_session_id, problem_slug, problem_source, difficulty,
                    language, source_code, status, passed_cases, total_cases, runtime_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, 42);
            statement.setLong(2, sessionId);
            statement.setString(3, "two-sum");
            statement.setString(4, "leetcode-hot100");
            statement.setString(5, "MEDIUM");
            statement.setString(6, "java");
            statement.setString(7, "class Solution {}");
            statement.setString(8, "ACCEPTED");
            statement.setInt(9, 5);
            statement.setInt(10, 5);
            statement.setLong(11, 123L);
            assertEquals(1, statement.executeUpdate());
            return generatedId(statement, "algorithm submission");
        }
    }

    private void insertAlgorithmChallenge(
            Connection connection,
            long sessionId,
            long turnId,
            long submissionId
    ) throws Exception {
        String sql = """
                INSERT INTO algorithm_interview_challenge (
                    interview_session_id, turn_id, user_id, problem_slug, difficulty,
                    time_limit_minutes, status, latest_submission_id, started_at,
                    deadline_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setLong(1, sessionId);
            statement.setLong(2, turnId);
            statement.setInt(3, 42);
            statement.setString(4, "two-sum");
            statement.setString(5, "MEDIUM");
            statement.setInt(6, 30);
            statement.setString(7, "ACCEPTED");
            statement.setLong(8, submissionId);
            statement.setTimestamp(9, now);
            statement.setTimestamp(10, Timestamp.from(now.toInstant().plusSeconds(30 * 60L)));
            statement.setTimestamp(11, now);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void verifyCompleteRecord(
            Connection connection,
            long sessionId,
            long turnId,
            long algorithmTurnId,
            long submissionId
    ) throws Exception {
        String sql = """
                SELECT
                    s.resume_text, s.target_role, s.model_provider, s.model_name,
                    s.thinking_enabled, s.summary,
                    t.question, t.answer, t.score, t.evaluation, t.knowledge_tags,
                    t.reference_answer, t.agent_action,
                    e.event_type, e.tool_name, e.detail, e.visibility
                FROM ai_interview_agent_session s
                JOIN ai_interview_agent_turn t ON t.session_id = s.id
                JOIN ai_interview_agent_event e ON e.session_id = s.id AND e.turn_id = t.id
                WHERE s.id = ? AND t.id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, turnId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next(), "Complete interview record was not readable after persistence");
                assertEquals("Candidate resume with project and skill evidence.", row.getString("resume_text"));
                assertEquals("Backend Engineer", row.getString("target_role"));
                assertEquals("test-provider", row.getString("model_provider"));
                assertEquals("test-model", row.getString("model_name"));
                assertTrue(row.getBoolean("thinking_enabled"));
                assertEquals("Durable interview summary.", row.getString("summary"));
                assertEquals("How did you validate the architecture trade-off?", row.getString("question"));
                assertEquals("I used load tests and production metrics.", row.getString("answer"));
                assertEquals(8, row.getInt("score"));
                assertEquals("The answer included measurable validation evidence.", row.getString("evaluation"));
                assertEquals("architecture,load-testing", row.getString("knowledge_tags"));
                assertEquals("A strong answer compares constraints, alternatives, and evidence.",
                        row.getString("reference_answer"));
                assertEquals("END_INTERVIEW", row.getString("agent_action"));
                assertEquals("completed", row.getString("event_type"));
                assertEquals("summary_generation", row.getString("tool_name"));
                assertEquals("A candidate-safe lifecycle event.", row.getString("detail"));
                assertEquals("candidate", row.getString("visibility"));
            }
        }
        String algorithmSql = """
                SELECT t.question_kind, t.score,
                       c.problem_slug, c.time_limit_minutes, c.status challenge_status,
                       sub.status submission_status, sub.passed_cases, sub.total_cases
                FROM algorithm_interview_challenge c
                JOIN ai_interview_agent_turn t ON t.id = c.turn_id
                JOIN algorithm_submission sub ON sub.id = c.latest_submission_id
                WHERE c.interview_session_id = ? AND t.id = ? AND sub.id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(algorithmSql)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, algorithmTurnId);
            statement.setLong(3, submissionId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next(), "Algorithm finale record was not readable after persistence");
                assertEquals("ALGORITHM", row.getString("question_kind"));
                assertEquals(10, row.getInt("score"));
                assertEquals("two-sum", row.getString("problem_slug"));
                assertEquals(30, row.getInt("time_limit_minutes"));
                assertEquals("ACCEPTED", row.getString("challenge_status"));
                assertEquals("ACCEPTED", row.getString("submission_status"));
                assertEquals(5, row.getInt("passed_cases"));
                assertEquals(5, row.getInt("total_cases"));
            }
        }
    }

    private long generatedId(PreparedStatement statement, String entity) throws Exception {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertTrue(keys.next(), "Missing generated " + entity + " id");
            return keys.getLong(1);
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
