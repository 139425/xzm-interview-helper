package com.xzm.xzm_interview_helper.serveragent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServerToolRequest {
    @NotNull
    private ServerToolName tool;

    @Size(max = 1_000)
    private String command;

    @Size(max = 2_000)
    private String path;

    @Size(max = 1_000_000)
    private String content;

    @Size(max = 128)
    private String service;

    @Size(max = 32)
    private String action;

    @Size(max = 128)
    private String siteName;

    private Integer timeoutSeconds;

    @Size(max = 36)
    private String approvalRequestId;

    @Size(max = 256)
    private String approvalToken;
}
