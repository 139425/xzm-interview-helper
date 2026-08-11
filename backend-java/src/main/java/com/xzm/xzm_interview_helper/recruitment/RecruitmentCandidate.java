package com.xzm.xzm_interview_helper.recruitment;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class RecruitmentCandidate {
    String externalId;
    String company;
    String title;
    String companyType;
    String industry;
    String locations;
    String positions;
    String recruitmentType;
    String targetGraduates;
    LocalDate publishedDate;
    String deadline;
    String applyUrl;
    String announcementUrl;
    String sourceName;
    String sourceUrl;
    String sourceKind;
    int sourcePriority;
}
