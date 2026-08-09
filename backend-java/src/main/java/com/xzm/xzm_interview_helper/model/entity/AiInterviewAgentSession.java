package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Durable, server-owned state for one adaptive interview.
 */
@Data
@TableName("ai_interview_agent_session")
public class AiInterviewAgentSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String public_id;
    private Integer user_id;
    private String status;
    private String resume_text;
    private String resume_file_name;
    private String target_role;
    private String model_provider;
    private String model_name;
    private Boolean thinking_enabled;
    private Integer total_question_count;
    private Integer primary_question_count;
    private Integer follow_up_count;
    private String summary;
    private Date started_at;
    private Date completed_at;
    private Date create_time;
    private Date update_time;

    private static final long serialVersionUID = 1L;
}
