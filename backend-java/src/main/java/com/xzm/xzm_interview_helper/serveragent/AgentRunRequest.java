package com.xzm.xzm_interview_helper.serveragent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRunRequest {
    @NotBlank
    @Size(max = 8_000)
    private String objective;

    private Integer maxSteps;

    @Size(max = 36)
    private String approvalRequestId;

    @Size(max = 256)
    private String approvalToken;
}
