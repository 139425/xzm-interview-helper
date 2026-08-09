package com.xzm.xzm_interview_helper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for the authenticated LongCat chat endpoints.
 *
 * User identity is deliberately absent: it is taken exclusively from the
 * verified JWT attached to the HTTP request.
 */
@Data
public class LongCatChatRequest {

    @NotNull(message = "会话标识不能为空")
    @Positive(message = "会话标识必须为正整数")
    private Integer userMemoryId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 20000, message = "消息不能超过 20000 个字符")
    private String message;

    @Size(max = 64, message = "提示词模式过长")
    private String promptMode;

    @Size(max = 64, message = "模型提供商标识过长")
    private String provider;

    @Size(max = 128, message = "模型名称过长")
    private String modelName;
}
