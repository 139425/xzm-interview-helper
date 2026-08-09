package com.xzm.xzm_interview_helper.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xzm.xzm_interview_helper.model.entity.AiInterview;

/**
* @author 34631
* @description 针对表【ai_interview(存储 面试记录，每一次面试存在一条记录中)】的数据库操作Service
* @createDate 2025-08-31 13:10:34
*/
public interface AiInterviewService extends IService<AiInterview> {

    AiInterview getOneByUserIdAndInterviewId(int userId, long interviewId);

    void updateByUserIdAndInterviewId(AiInterview aiInterview);
}
