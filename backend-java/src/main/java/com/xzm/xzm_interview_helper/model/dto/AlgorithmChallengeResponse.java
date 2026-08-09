package com.xzm.xzm_interview_helper.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

@Data
public class AlgorithmChallengeResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long turnId;
    private String problemSlug;
    private String frontendId;
    private String title;
    private String difficulty;
    private Integer timeLimitMinutes;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long latestSubmissionId;
    private Date startedAt;
    private Date deadlineAt;
    private Date completedAt;
}
