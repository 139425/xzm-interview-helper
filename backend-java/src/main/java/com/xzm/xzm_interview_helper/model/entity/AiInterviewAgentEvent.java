package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Auditable, display-safe agent activity. This deliberately stores stages, not raw model reasoning.
 */
@Data
@TableName("ai_interview_agent_event")
public class AiInterviewAgentEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long session_id;
    private Long turn_id;
    private Integer sequence_no;
    private String event_type;
    private String tool_name;
    private String title;
    private String detail;
    private String payload_json;
    private String visibility;
    private Date create_time;

    private static final long serialVersionUID = 1L;
}
