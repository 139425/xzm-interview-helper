package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;

import java.util.Date;

@Data
public class InterviewAgentEventResponse {
    private Long id;
    private Long turnId;
    private Integer sequenceNo;
    private String type;
    private String toolName;
    private String title;
    private String detail;
    private String visibility;
    private Date createTime;
}
