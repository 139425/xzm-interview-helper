package com.xzm.xzm_interview_helper.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthenticatedUser {
    private AuthenticatedUser() {
    }

    public static int id(HttpServletRequest request) {
        Object rawUserId = request.getAttribute("userId");
        if (rawUserId instanceof Number number) {
            try {
                return Math.toIntExact(number.longValue());
            } catch (ArithmeticException ignored) {
                // Fall through to the consistent authentication error below.
            }
        }
        if (rawUserId instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // Fall through to the consistent authentication error below.
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to identify the authenticated user");
    }
}
