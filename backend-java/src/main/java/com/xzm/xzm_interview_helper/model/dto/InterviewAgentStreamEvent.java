package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;

/**
 * Stable transport contract for the interview UI. `detail` is deliberately a display-safe summary,
 * never unfiltered model reasoning.
 */
@Data
public class InterviewAgentStreamEvent {
    private String type;
    private String title;
    private String detail;
    private String toolName;
    private Long turnId;
    private Integer totalQuestionCount;
    private Integer primaryQuestionCount;
    private Integer followUpCount;
    private InterviewAgentTurnResponse question;
    private String summary;
    private InterviewAgentSessionResponse session;

    public static InterviewAgentStreamEvent stage(String title, String detail, String toolName) {
        InterviewAgentStreamEvent event = new InterviewAgentStreamEvent();
        event.setType("stage");
        event.setTitle(title);
        event.setDetail(detail);
        event.setToolName(toolName);
        return event;
    }

    public static InterviewAgentStreamEvent error(String detail) {
        InterviewAgentStreamEvent event = new InterviewAgentStreamEvent();
        event.setType("error");
        event.setTitle("面试流程需要重试");
        event.setDetail(detail);
        return event;
    }
}
