package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.career.AssessmentScheduleRepository;
import com.xzm.xzm_interview_helper.model.dto.AssessmentScheduleRequest;
import com.xzm.xzm_interview_helper.model.dto.ScheduleCompletionRequest;
import com.xzm.xzm_interview_helper.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class AssessmentScheduleController {
    private final AssessmentScheduleRepository schedules;

    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        int userId = AuthenticatedUser.id(request);
        return response(Map.of(
                "items", schedules.findAll(userId),
                "summary", schedules.summary(userId)
        ));
    }

    @PostMapping
    public Map<String, Object> create(
            @Valid @RequestBody AssessmentScheduleRequest body,
            HttpServletRequest request
    ) {
        return response(schedules.create(AuthenticatedUser.id(request), body));
    }

    @PatchMapping("/{id}/completed")
    public Map<String, Object> setCompleted(
            @PathVariable long id,
            @Valid @RequestBody ScheduleCompletionRequest body,
            HttpServletRequest request
    ) {
        return response(schedules.setCompleted(AuthenticatedUser.id(request), id, body.getCompleted()));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id, HttpServletRequest request) {
        schedules.delete(AuthenticatedUser.id(request), id);
        return response(Map.of("deleted", true));
    }

    private Map<String, Object> response(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "Success");
        result.put("data", data);
        return result;
    }
}
