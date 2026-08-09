package com.xzm.xzm_interview_helper.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordControllerAuthorizationTest {

    private final RecordController controller = new RecordController();

    @Test
    void acceptsOnlyTheJwtIdentityForLegacyUserIdParameters() {
        MockHttpServletRequest request = requestForUser(42L);

        Integer result = ReflectionTestUtils.invokeMethod(
                controller, "requireMatchingUserId", request, 42);

        assertEquals(42, result);
    }

    @Test
    void rejectsAPathOrBodyUserIdThatDoesNotMatchTheJwtIdentity() {
        MockHttpServletRequest request = requestForUser(42L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                ReflectionTestUtils.invokeMethod(controller, "requireMatchingUserId", request, 7));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void rejectsRequestsWithoutAnAuthenticatedIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                ReflectionTestUtils.invokeMethod(controller, "currentUserId", request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void obsoleteRecordInterviewWorkflowIsNoLongerExposed() {
        assertFalse(
                Arrays.stream(RecordController.class.getDeclaredMethods())
                        .map(method -> method.getName().toLowerCase())
                        .anyMatch(name -> name.contains("interview")
                                || name.contains("evaluate")
                                || name.contains("summary")
                                || name.contains("questions"))
        );
    }

    private MockHttpServletRequest requestForUser(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        return request;
    }
}
