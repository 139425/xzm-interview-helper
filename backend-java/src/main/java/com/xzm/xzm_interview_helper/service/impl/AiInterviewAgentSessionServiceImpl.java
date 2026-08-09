package com.xzm.xzm_interview_helper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzm.xzm_interview_helper.mapper.AiInterviewAgentSessionMapper;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentSession;
import com.xzm.xzm_interview_helper.service.AiInterviewAgentSessionService;
import org.springframework.stereotype.Service;

@Service
public class AiInterviewAgentSessionServiceImpl
        extends ServiceImpl<AiInterviewAgentSessionMapper, AiInterviewAgentSession>
        implements AiInterviewAgentSessionService {
}
