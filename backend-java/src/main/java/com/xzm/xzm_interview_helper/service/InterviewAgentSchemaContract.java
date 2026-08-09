package com.xzm.xzm_interview_helper.service;

import java.util.List;
import java.util.Map;

/**
 * Authoritative minimum database contract for durable interview-agent state.
 */
public final class InterviewAgentSchemaContract {

    public static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
            "ai_interview_agent_session", List.of(
                    "id", "public_id", "user_id", "status", "resume_text", "resume_file_name",
                    "target_role", "model_provider", "model_name", "thinking_enabled",
                    "total_question_count", "primary_question_count", "follow_up_count",
                    "summary", "started_at", "completed_at", "create_time", "update_time"
            ),
            "ai_interview_agent_turn", List.of(
                    "id", "session_id", "sequence_no", "parent_turn_id", "question_kind",
                    "question", "answer", "score", "evaluation", "knowledge_tags",
                    "reference_answer", "agent_action", "decision_note", "model_provider",
                    "model_name", "created_at", "answered_at", "evaluated_at"
            ),
            "ai_interview_agent_event", List.of(
                    "id", "session_id", "turn_id", "sequence_no", "event_type", "tool_name",
                    "title", "detail", "payload_json", "visibility", "create_time"
            ),
            "algorithm_submission", List.of(
                    "id", "user_id", "interview_session_id", "problem_slug", "problem_source",
                    "difficulty", "language", "source_code", "status", "passed_cases",
                    "total_cases", "runtime_ms", "output", "error_message", "ai_status",
                    "ai_score", "ai_evaluation", "ai_evaluated_at", "create_time"
            ),
            "algorithm_interview_challenge", List.of(
                    "id", "interview_session_id", "turn_id", "user_id", "problem_slug",
                    "difficulty", "time_limit_minutes", "status", "latest_submission_id",
                    "started_at", "deadline_at", "completed_at", "create_time", "update_time"
            )
    );

    private InterviewAgentSchemaContract() {
    }
}
