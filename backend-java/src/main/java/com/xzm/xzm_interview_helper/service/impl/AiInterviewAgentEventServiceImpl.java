package com.xzm.xzm_interview_helper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzm.xzm_interview_helper.mapper.AiInterviewAgentEventMapper;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewAgentEvent;
import com.xzm.xzm_interview_helper.service.AiInterviewAgentEventService;
import org.springframework.stereotype.Service;

@Service
public class AiInterviewAgentEventServiceImpl
        extends ServiceImpl<AiInterviewAgentEventMapper, AiInterviewAgentEvent>
        implements AiInterviewAgentEventService {
}
