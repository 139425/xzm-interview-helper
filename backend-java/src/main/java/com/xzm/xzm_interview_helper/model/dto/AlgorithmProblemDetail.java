package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AlgorithmProblemDetail extends AlgorithmProblemSummary {
    private String contentHtml;
    private String sampleTestCase;
    private String officialUrl;
    private List<String> tags = new ArrayList<>();
    private Map<String, String> codeTemplates = new LinkedHashMap<>();
}
