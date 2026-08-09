package com.xzm.xzm_interview_helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentEvent;
import org.apache.ibatis.annotations.Select;

public interface AiInterviewAgentEventMapper extends BaseMapper<AiInterviewAgentEvent> {

    @Select("SELECT COALESCE(MAX(sequence_no), 0) FROM ai_interview_agent_event WHERE session_id = #{sessionId}")
    Integer maxSequenceForSession(Long sessionId);
}
