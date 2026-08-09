package com.xzm.xzm_interview_helper.service.impl;



import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzm.xzm_interview_helper.mapper.AiConversationMapper;
import com.xzm.xzm_interview_helper.model.entity.AiConversation;
import com.xzm.xzm_interview_helper.service.AiConversationService;
import org.springframework.stereotype.Service;

/**
* @author 34631
* @description 针对表【ai_conversation(存储 AI 对话记录：用户提问与 AI 回复)】的数据库操作Service实现
* @createDate 2025-08-02 19:35:10
*/
@Service
public class AiConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation>
implements AiConversationService {

}
