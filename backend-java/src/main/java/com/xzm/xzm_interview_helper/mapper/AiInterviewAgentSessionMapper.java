package com.xzm.xzm_interview_helper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentSession;
import org.apache.ibatis.annotations.Select;

public interface AiInterviewAgentSessionMapper extends BaseMapper<AiInterviewAgentSession> {

    /**
     * Serializes durable mutations for one interview session.  Event sequence allocation and
     * state/turn writes use this row lock inside a transaction, rather than racing on count().
     */
    @Select("SELECT * FROM ai_interview_agent_session WHERE id = #{id} FOR UPDATE")
    AiInterviewAgentSession lockById(Long id);

    @Select("SELECT * FROM ai_interview_agent_session WHERE public_id = #{publicId} FOR UPDATE")
    AiInterviewAgentSession lockByPublicId(String publicId);
}
