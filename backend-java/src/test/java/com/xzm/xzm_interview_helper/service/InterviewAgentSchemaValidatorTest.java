package com.xzm.xzm_interview_helper.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewAgentSchemaValidatorTest {

    @Test
    void acceptsTheCompleteContractCaseInsensitively() {
        Map<String, Set<String>> actual = new HashMap<>();
        InterviewAgentSchemaContract.REQUIRED_COLUMNS.forEach((table, columns) ->
                actual.put(table, new HashSet<>(columns.stream().map(String::toUpperCase).toList()))
        );

        assertTrue(InterviewAgentSchemaValidator.findMissing(actual).isEmpty());
    }

    @Test
    void reportsMissingTablesAndColumnsPrecisely() {
        Map<String, Set<String>> actual = new HashMap<>();
        InterviewAgentSchemaContract.REQUIRED_COLUMNS.forEach((table, columns) ->
                actual.put(table, new HashSet<>(columns))
        );
        actual.remove("ai_interview_agent_event");
        actual.get("ai_interview_agent_session").remove("summary");

        List<String> missing = InterviewAgentSchemaValidator.findMissing(actual);

        assertEquals(List.of(
                "ai_interview_agent_event (table)",
                "ai_interview_agent_session.summary"
        ), missing.stream().sorted().toList());
    }
}
