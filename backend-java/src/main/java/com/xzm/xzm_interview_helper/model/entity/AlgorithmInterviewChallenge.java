package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("algorithm_interview_challenge")
public class AlgorithmInterviewChallenge {
    @TableId
    private Long id;
    private Long interview_session_id;
    private Long turn_id;
    private Integer user_id;
    private String problem_slug;
    private String difficulty;
    private Integer time_limit_minutes;
    private String status;
    private Long latest_submission_id;
    private Date started_at;
    private Date deadline_at;
    private Date completed_at;
    private Date create_time;
    private Date update_time;
}
