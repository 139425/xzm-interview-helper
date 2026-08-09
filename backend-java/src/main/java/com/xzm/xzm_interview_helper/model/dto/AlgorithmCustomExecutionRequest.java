package com.xzm.xzm_interview_helper.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A user-authored, sandboxed Java execution request.
 *
 * <p>The driver is inserted only inside the controlled {@code Main.main}
 * method. It is never mixed with the server-owned official judge cases.</p>
 */
@Data
public class AlgorithmCustomExecutionRequest {

    @NotBlank
    @Size(max = 120)
    private String problemSlug;

    @NotBlank
    @Size(max = 32)
    private String language;

    @NotBlank
    @Size(max = 30_000)
    private String code;

    @NotBlank
    @Size(max = 10_000)
    @JsonAlias("driver")
    private String driverCode;

    @Size(max = 12_000)
    private String expectedOutput;
}
