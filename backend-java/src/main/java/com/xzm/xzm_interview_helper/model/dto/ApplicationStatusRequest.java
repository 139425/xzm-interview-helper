package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplicationStatusRequest {
    @NotBlank
    @Size(max = 32)
    private String status;
}
