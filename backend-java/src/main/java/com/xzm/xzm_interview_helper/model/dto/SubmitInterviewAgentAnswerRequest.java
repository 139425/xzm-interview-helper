package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitInterviewAgentAnswerRequest {

    @NotBlank(message = "回答不能为空")
    @Size(max = 30000, message = "回答不能超过 30000 个字符")
    private String answer;
}
