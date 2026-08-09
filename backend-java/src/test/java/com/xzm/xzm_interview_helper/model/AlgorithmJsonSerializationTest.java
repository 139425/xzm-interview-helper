package com.xzm.xzm_interview_helper.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmChallengeResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmSubmissionReviewResponse;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmSubmission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlgorithmJsonSerializationTest {

    private static final long SNOWFLAKE_ID = 2_082_470_948_977_692_674L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesEveryBrowserVisibleAlgorithmIdWithoutJavaScriptPrecisionLoss()
            throws Exception {
        AlgorithmExecutionResponse execution = new AlgorithmExecutionResponse();
        execution.setSubmissionId(SNOWFLAKE_ID);
        assertTextId(objectMapper.valueToTree(execution), "submissionId");

        AlgorithmSubmissionReviewResponse review =
                new AlgorithmSubmissionReviewResponse();
        review.setSubmissionId(SNOWFLAKE_ID);
        assertTextId(objectMapper.valueToTree(review), "submissionId");

        AlgorithmSubmission submission = new AlgorithmSubmission();
        submission.setId(SNOWFLAKE_ID);
        submission.setInterview_session_id(SNOWFLAKE_ID);
        JsonNode submissionJson = objectMapper.valueToTree(submission);
        assertTextId(submissionJson, "id");
        assertTextId(submissionJson, "interview_session_id");

        AlgorithmChallengeResponse challenge = new AlgorithmChallengeResponse();
        challenge.setId(SNOWFLAKE_ID);
        challenge.setTurnId(SNOWFLAKE_ID);
        challenge.setLatestSubmissionId(SNOWFLAKE_ID);
        JsonNode challengeJson = objectMapper.valueToTree(challenge);
        assertTextId(challengeJson, "id");
        assertTextId(challengeJson, "turnId");
        assertTextId(challengeJson, "latestSubmissionId");
    }

    private void assertTextId(JsonNode payload, String field) {
        assertTrue(payload.get(field).isTextual(), payload.toPrettyString());
        assertEquals(Long.toString(SNOWFLAKE_ID), payload.get(field).textValue());
    }
}
