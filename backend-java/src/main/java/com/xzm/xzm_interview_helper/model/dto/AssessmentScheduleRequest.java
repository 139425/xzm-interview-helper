package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssessmentScheduleRequest {
    @NotBlank
    @Size(max = 200)
    private String company;

    @Size(max = 300)
    private String roleName;

    @NotBlank
    @Size(max = 32)
    private String eventType;

    @NotNull
    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @Size(max = 1_000)
    private String notes;
}
