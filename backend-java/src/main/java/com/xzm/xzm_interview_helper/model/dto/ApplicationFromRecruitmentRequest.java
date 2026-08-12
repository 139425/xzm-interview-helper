package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ApplicationFromRecruitmentRequest {
    @Positive
    private long recruitmentId;
}
