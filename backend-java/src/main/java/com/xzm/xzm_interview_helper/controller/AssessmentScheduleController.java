package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.career.AssessmentScheduleRepository;
import com.xzm.xzm_interview_helper.career.ScheduleImageImportService;
import com.xzm.xzm_interview_helper.model.dto.AssessmentScheduleRequest;
import com.xzm.xzm_interview_helper.model.dto.ScheduleCompletionRequest;
import com.xzm.xzm_interview_helper.security.AuthenticatedUser;
import com.xzm.xzm_interview_helper.service.AiOperationGate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class AssessmentScheduleController {
    private final AssessmentScheduleRepository schedules;
    private final ScheduleImageImportService imageImportService;
    private final AiOperationGate aiOperationGate;

    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        int userId = AuthenticatedUser.id(request);
        return response(Map.of(
                "items", schedules.findAll(userId),
                "summary", schedules.summary(userId),
                "trash", schedules.findTrash(userId)
        ));
    }

    @PostMapping("/parse-image")
    public Map<String, Object> parseImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        int userId = AuthenticatedUser.id(request);
        return response(aiOperationGate.guardCall(userId, () -> imageImportService.parse(file)));
    }

    @PostMapping
    public Map<String, Object> create(
            @Valid @RequestBody AssessmentScheduleRequest body,
            HttpServletRequest request
    ) {
        return response(schedules.create(AuthenticatedUser.id(request), body));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable long id,
            @Valid @RequestBody AssessmentScheduleRequest body,
            HttpServletRequest request
    ) {
        return response(schedules.update(AuthenticatedUser.id(request), id, body));
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
        schedules.softDelete(AuthenticatedUser.id(request), id);
        return response(Map.of("deleted", true));
    }

    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable long id, HttpServletRequest request) {
        return response(schedules.restore(AuthenticatedUser.id(request), id));
    }

    @DeleteMapping("/trash/{id}")
    public Map<String, Object> permanentDelete(@PathVariable long id, HttpServletRequest request) {
        schedules.permanentDelete(AuthenticatedUser.id(request), id);
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
