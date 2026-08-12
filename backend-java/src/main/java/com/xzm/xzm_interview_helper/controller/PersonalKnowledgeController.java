package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.career.PersonalKnowledgeRepository;
import com.xzm.xzm_interview_helper.model.dto.PersonalKnowledgeRequest;
import com.xzm.xzm_interview_helper.security.AuthenticatedUser;
import com.xzm.xzm_interview_helper.service.ResumeTextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class PersonalKnowledgeController {
    private final PersonalKnowledgeRepository repository;
    private final ResumeTextExtractor textExtractor;

    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        return response(repository.findAll(AuthenticatedUser.id(request)));
    }

    @PostMapping("/text")
    public Map<String, Object> createText(
            @Valid @RequestBody PersonalKnowledgeRequest body,
            HttpServletRequest request
    ) {
        String content = normalize(body.getContent());
        return response(repository.create(
                AuthenticatedUser.id(request),
                body.getTitle(),
                body.getSourceType(),
                "",
                content
        ));
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", defaultValue = "") String title,
            HttpServletRequest request
    ) {
        ResumeTextExtractor.ExtractedResume extracted = textExtractor.extract(file);
        String resolvedTitle = title == null || title.isBlank() ? extracted.filename() : title;
        return response(repository.create(
                AuthenticatedUser.id(request),
                resolvedTitle,
                "DOCUMENT",
                extracted.filename(),
                extracted.text()
        ));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id, HttpServletRequest request) {
        repository.delete(AuthenticatedUser.id(request), id);
        return response(Map.of("deleted", true));
    }

    private static String normalize(String value) {
        return value.replace('\u0000', ' ').replaceAll("[ \\t]+", " ").strip();
    }

    private Map<String, Object> response(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "Success");
        result.put("data", data);
        return result;
    }
}
