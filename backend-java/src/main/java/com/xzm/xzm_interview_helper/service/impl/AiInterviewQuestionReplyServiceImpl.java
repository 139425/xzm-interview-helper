package com.xzm.xzm_interview_helper.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.xzm.xzm_interview_helper.mapper.AiInterviewQuestionReplyMapper;
import com.xzm.xzm_interview_helper.model.entity.AiInterviewQuestionReply;
import com.xzm.xzm_interview_helper.service.AiInterviewQuestionReplyService;
import org.springframework.stereotype.Service;

/**
* @author 34631
* @description 针对表【ai_interview_question_reply(存储每一个问题，以及对应用户的回答，ai的打分，评价，参考回答，相关问题)】的数据库操作Service实现
* @createDate 2025-08-31 14:01:41
*/
@Service
public class AiInterviewQuestionReplyServiceImpl extends ServiceImpl<AiInterviewQuestionReplyMapper, AiInterviewQuestionReply>
implements AiInterviewQuestionReplyService {

}
