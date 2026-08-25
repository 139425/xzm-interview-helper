package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScheduleCompletionRequest {
    @NotNull
    private Boolean completed;
}
