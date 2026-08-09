package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class InterviewAgentSessionResponse {
    private String sessionId;
    private String status;
    private String resumeFileName;
    private String resumePreview;
    private String targetRole;
    private String modelProvider;
    private String modelName;
    private Boolean thinkingEnabled;
    private Integer totalQuestionCount;
    private Integer primaryQuestionCount;
    private Integer followUpCount;
    private Integer minPrimaryQuestionCount = 0;
    private Integer maxPrimaryQuestionCount = 0;
    private Integer maxTotalQuestionCount = 0;
    private AlgorithmChallengeResponse algorithmChallenge;
    private String summary;
    private Date startedAt;
    private Date completedAt;
    private Date createTime;
    private List<InterviewAgentTurnResponse> turns = new ArrayList<>();
    private List<InterviewAgentEventResponse> events = new ArrayList<>();
}
