package com.xzm.xzm_interview_helper.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzm.xzm_interview_helper.mapper.AiInterviewMapper;
import com.xzm.xzm_interview_helper.model.entity.AiInterview;
import com.xzm.xzm_interview_helper.service.AiInterviewService;
import org.springframework.stereotype.Service;

/**
* @author 34631
* @description 针对表【ai_interview(存储 面试记录，每一次面试存在一条记录中)】的数据库操作Service实现
* @createDate 2025-08-31 13:10:34
*/
@Service
public class AiInterviewServiceImpl extends ServiceImpl<AiInterviewMapper, AiInterview>
implements AiInterviewService {

    @Override
    public AiInterview getOneByUserIdAndInterviewId(int userId, long interviewId) {
        AiInterview aiInterview = lambdaQuery()
                .eq(AiInterview::getUser_id, userId)
                .eq(AiInterview::getInterview_id, interviewId)
                .one();
        return aiInterview;
    }

    @Override
    public void updateByUserIdAndInterviewId(AiInterview aiInterview) {
        lambdaUpdate()
                .eq(AiInterview::getUser_id, aiInterview.getUser_id())
                .eq(AiInterview::getInterview_id, aiInterview.getInterview_id())
                .set(AiInterview::getIs_finish, aiInterview.getIs_finish())
                .set(AiInterview::getUser_description, aiInterview.getUser_description())
                .update();
    }
}
