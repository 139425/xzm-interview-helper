package com.xzm.xzm_interview_helper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzm.xzm_interview_helper.mapper.AlgorithmSubmissionMapper;
import com.xzm.xzm_interview_helper.model.entity.AlgorithmSubmission;
import com.xzm.xzm_interview_helper.service.AlgorithmSubmissionService;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmSubmissionServiceImpl
        extends ServiceImpl<AlgorithmSubmissionMapper, AlgorithmSubmission>
        implements AlgorithmSubmissionService {
}
