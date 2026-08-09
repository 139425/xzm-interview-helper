package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

@Data
@TableName("algorithm_submission")
public class AlgorithmSubmission {
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Integer user_id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long interview_session_id;
    private String problem_slug;
    private String problem_source;
    private String difficulty;
    private String language;
    private String source_code;
    private String status;
    private Integer passed_cases;
    private Integer total_cases;
    private Long runtime_ms;
    private String output;
    private String error_message;
    private String ai_status;
    private Integer ai_score;
    private String ai_evaluation;
    private Date ai_evaluated_at;
    private Date create_time;
}
