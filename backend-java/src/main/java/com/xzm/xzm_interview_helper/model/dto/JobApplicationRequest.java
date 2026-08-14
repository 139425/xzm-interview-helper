package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class JobApplicationRequest {
    @NotBlank
    @Size(max = 200)
    private String company;

    @Size(max = 300)
    private String roleName;

    @Size(max = 32)
    private String status;

    @Size(max = 300)
    private String location;

    @NotBlank
    @Size(max = 1024)
    private String applyUrl;

    @Size(max = 1024)
    private String sourceUrl;

    private LocalDate deadline;

    @Size(max = 500)
    private String nextAction;

    private LocalDateTime nextActionAt;

    @Size(max = 10_000)
    private String notes;
}
