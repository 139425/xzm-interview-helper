package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInterviewAgentSessionRequest {

    @NotBlank(message = "简历内容不能为空")
    @Size(max = 60000, message = "简历内容不能超过 60000 个字符")
    private String resumeText;

    @Size(max = 255, message = "目标岗位不能超过 255 个字符")
    private String targetRole;

    @Size(max = 64, message = "模型提供商标识过长")
    private String modelProvider;

    @Size(max = 128, message = "模型名称过长")
    private String modelName;

    private Boolean enableThinking;

}
