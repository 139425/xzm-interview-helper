package com.xzm.xzm_interview_helper.serveragent;

public record ToolExecutionResponse(
        String status,
        ServerToolName tool,
        ServerRisk risk,
        String output,
        Integer exitCode,
        long durationMs,
        boolean truncated,
        String approvalRequestId,
        String actionSummary,
        String message
) {
    public static ToolExecutionResponse approvalRequired(
            ServerToolName tool,
            String approvalRequestId,
            String actionSummary
    ) {
        return new ToolExecutionResponse(
                "APPROVAL_REQUIRED",
                tool,
                ServerRisk.DANGEROUS,
                "",
                null,
                0,
                false,
                approvalRequestId,
                actionSummary,
                "This exact action needs a second confirmation"
        );
    }
}
