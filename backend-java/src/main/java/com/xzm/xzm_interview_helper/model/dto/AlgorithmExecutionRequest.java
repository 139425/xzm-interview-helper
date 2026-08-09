package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlgorithmExecutionRequest {
    @NotBlank
    @Size(max = 120)
    private String problemSlug;

    @NotBlank
    @Size(max = 32)
    private String language;

    @NotBlank
    @Size(max = 30_000)
    private String code;

    @Size(max = 36)
    private String interviewSessionId;
}
