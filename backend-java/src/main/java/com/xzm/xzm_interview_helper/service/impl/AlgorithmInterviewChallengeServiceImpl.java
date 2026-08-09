package com.xzm.xzm_interview_helper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzm.xzm_interview_helper.mapper.AlgorithmInterviewChallengeMapper;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmInterviewChallenge;
import com.xzm.xzm_interview_helper.service.AlgorithmInterviewChallengeService;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmInterviewChallengeServiceImpl
        extends ServiceImpl<AlgorithmInterviewChallengeMapper, AlgorithmInterviewChallenge>
        implements AlgorithmInterviewChallengeService {
}
