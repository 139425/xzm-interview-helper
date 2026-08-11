package com.xzm.xzm_interview_helper.recruitment;

import java.util.List;

public interface RecruitmentSource {
    String sourceName();

    List<RecruitmentCandidate> fetch() throws Exception;
}
