package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;

/**
 * 面试总结请求DTO
 * 用于接收生成面试总结的请求参数
 */
@Data
public class InterviewSummaryRequestDTO {
    /**
     * 面试记录（包含所有问答和评价）
     */
    private String record;
    
    /**
     * 用户ID
     */
    private Integer userId;
    
    /**
     * 面试ID
     */
    private Long interviewId;
}
