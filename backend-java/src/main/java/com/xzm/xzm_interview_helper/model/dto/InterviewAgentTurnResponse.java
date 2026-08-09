package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;

import java.util.Date;

@Data
public class InterviewAgentTurnResponse {
    private Long id;
    private Integer sequenceNo;
    private Long parentTurnId;
    private String questionKind;
    private String question;
    private String answer;
    private Integer score;
    private String evaluation;
    private String knowledgeTags;
    private String referenceAnswer;
    private String agentAction;
    private String decisionNote;
    private Date createdAt;
    private Date answeredAt;
    private Date evaluatedAt;
}
