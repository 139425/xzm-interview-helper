package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.recruitment.RecruitmentPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/recruitments")
@RequiredArgsConstructor
public class RecruitmentController {
    private final RecruitmentPostingRepository repository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String recruitmentType,
            @RequestParam(defaultValue = "") String companyType,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "false") boolean freshOnly,
            @RequestParam(defaultValue = "") String industry,
            @RequestParam(defaultValue = "") String jobTrack,
            @RequestParam(defaultValue = "") String sourceKind,
            @RequestParam(defaultValue = "") String targetGraduates,
            @RequestParam(defaultValue = "0") int publishedWithinDays,
            @RequestParam(defaultValue = "0") int deadlineWithinDays,
            @RequestParam(defaultValue = "false") boolean officialOnly,
            @RequestParam(defaultValue = "latest") String sort
    ) {
        int safePage = Math.max(1, Math.min(page, 10_000));
        int safeSize = Math.max(1, Math.min(size, 50));
        Map<String, Object> body = response(repository.findPage(
                safePage, safeSize,
                trimmed(keyword), trimmed(recruitmentType), trimmed(companyType), trimmed(city), freshOnly,
                trimmed(industry), trimmed(jobTrack), trimmed(sourceKind), trimmed(targetGraduates),
                Math.max(0, publishedWithinDays), Math.max(0, deadlineWithinDays), officialOnly,
                trimmed(sort).isEmpty() ? "latest" : trimmed(sort)
        ));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic()).body(body);
    }

    @GetMapping("/facets")
    public ResponseEntity<Map<String, Object>> facets() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(response(repository.facets()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(response(repository.summary()));
    }

    private static Map<String, Object> response(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", "Success");
        body.put("data", data);
        return body;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
