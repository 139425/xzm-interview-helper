package com.xzm.xzm_interview_helper.serveragent;

import java.util.List;

public record AgentRunResponse(
        String status,
        String answer,
        List<AgentStep> steps,
        ToolExecutionResponse pendingApproval
) {
    public record AgentStep(
            int step,
            String rationale,
            String action,
            String observation,
            String status
    ) {
    }
}
