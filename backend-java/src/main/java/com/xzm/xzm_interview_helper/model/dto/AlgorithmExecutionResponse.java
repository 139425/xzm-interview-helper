package com.xzm.xzm_interview_helper.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AlgorithmExecutionResponse {
    private String status;
    private int passedCases;
    private int totalCases;
    private Long runtimeMs;
    private String output;
    private String error;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;
    private boolean interviewChallenge;
    private boolean interviewReadyToComplete;
    private List<String> caseResults = new ArrayList<>();
}
