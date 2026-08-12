package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.media.LocalOcrService;
import com.xzm.xzm_interview_helper.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
    private final LocalOcrService ocrService;

    @PostMapping("/ocr")
    public Map<String, Object> recognize(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        AuthenticatedUser.id(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "Success");
        result.put("data", ocrService.recognize(file));
        return result;
    }
}
