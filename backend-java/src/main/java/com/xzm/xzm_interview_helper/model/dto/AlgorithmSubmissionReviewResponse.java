package com.xzm.xzm_interview_helper.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

@Data
public class AlgorithmSubmissionReviewResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;
    private String judgeStatus;
    private String aiStatus;
    private Integer aiScore;
    private String aiEvaluation;
    private Date aiEvaluatedAt;
}
