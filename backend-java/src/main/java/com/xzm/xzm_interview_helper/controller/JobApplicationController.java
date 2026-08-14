package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.career.JobApplicationRepository;
import com.xzm.xzm_interview_helper.model.dto.ApplicationFromRecruitmentRequest;
import com.xzm.xzm_interview_helper.model.dto.ApplicationStatusRequest;
import com.xzm.xzm_interview_helper.model.dto.JobApplicationRequest;
import com.xzm.xzm_interview_helper.recruitment.RecruitmentPostingRepository;
import com.xzm.xzm_interview_helper.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationController {
    private final JobApplicationRepository applications;
    private final RecruitmentPostingRepository recruitments;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String keyword,
            HttpServletRequest request
    ) {
        int userId = AuthenticatedUser.id(request);
        return response(Map.of(
                "items", applications.findAll(userId, status, keyword),
                "summary", applications.summary(userId)
        ));
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody JobApplicationRequest body, HttpServletRequest request) {
        return response(applications.createManual(AuthenticatedUser.id(request), body));
    }

    @PostMapping("/from-recruitment")
    public Map<String, Object> createFromRecruitment(
            @Valid @RequestBody ApplicationFromRecruitmentRequest body,
            HttpServletRequest request
    ) {
        RecruitmentPostingRepository.Posting posting = recruitments.findById(body.getRecruitmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "招聘信息不存在"));
        return response(applications.createFromRecruitment(AuthenticatedUser.id(request), posting));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable long id,
            @Valid @RequestBody JobApplicationRequest body,
            HttpServletRequest request
    ) {
        return response(applications.update(AuthenticatedUser.id(request), id, body));
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> updateStatus(
            @PathVariable long id,
            @Valid @RequestBody ApplicationStatusRequest body,
            HttpServletRequest request
    ) {
        return response(applications.updateStatus(AuthenticatedUser.id(request), id, body.getStatus()));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id, HttpServletRequest request) {
        applications.delete(AuthenticatedUser.id(request), id);
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
