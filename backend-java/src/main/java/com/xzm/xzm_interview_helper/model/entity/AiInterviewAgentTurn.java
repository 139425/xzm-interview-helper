package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * A question and its answer/evaluation. A follow-up points to the turn it probes.
 */
@Data
@TableName("ai_interview_agent_turn")
public class AiInterviewAgentTurn implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long session_id;
    private Integer sequence_no;
    private Long parent_turn_id;
    private String question_kind;
    private String question;
    private String answer;
    private Integer score;
    private String evaluation;
    private String knowledge_tags;
    private String reference_answer;
    private String agent_action;
    private String decision_note;
    private String model_provider;
    private String model_name;
    private Date created_at;
    private Date answered_at;
    private Date evaluated_at;

    private static final long serialVersionUID = 1L;
}
