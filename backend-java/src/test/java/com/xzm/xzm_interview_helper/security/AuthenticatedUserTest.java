package com.xzm.xzm_interview_helper.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedUserTest {
    @Test
    void acceptsOnlyVerifiedRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 17L);
        request.addParameter("userId", "999");
        assertEquals(17, AuthenticatedUser.id(request));
    }

    @Test
    void rejectsMissingIdentity() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> AuthenticatedUser.id(new MockHttpServletRequest())
        );
        assertEquals(401, error.getStatusCode().value());
    }
}
