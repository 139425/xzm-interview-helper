package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AlgorithmProblemSummary {
    private String frontendId;
    private String slug;
    private String title;
    private String difficulty;
    private List<String> sources = new ArrayList<>();
    private Integer hot100Rank;
    private Integer codeTopRank;
    private boolean judgeable;
    private int timeLimitMinutes;
}
