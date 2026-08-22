package com.xzm.xzm_interview_helper.recruitment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentPostingRepositoryTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RecruitmentPostingRepository repository;

    @Test
    void upsertKeepsDeadlineTextAndStructuredDateAsOnePair() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        RecruitmentCandidate candidate = RecruitmentCandidate.builder()
                .company("星河科技")
                .title("2027届校园招聘")
                .recruitmentType("秋招")
                .targetGraduates("2027届")
                .deadline("以官网为准")
                .applyUrl("https://jobs.example.com/campus")
                .sourceName("星河科技招聘官网")
                .sourceUrl("https://jobs.example.com/campus")
                .sourceKind("OFFICIAL")
                .sourcePriority(100)
                .build();

        repository.upsertAll(List.of(candidate));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("WHEN VALUES(deadline_date) IS NOT NULL THEN VALUES(deadline)")
                .contains("WHEN deadline_date IS NOT NULL THEN deadline")
                .contains("deadline_date = COALESCE(VALUES(deadline_date), deadline_date)");
    }

    @Test
    void deadlineSortPlacesOpenDatesBeforeExpiredAndUnknownDates() {
        assertThat(RecruitmentPostingRepository.orderBy("deadline"))
                .contains("WHEN deadline_date >= CURRENT_DATE THEN 0")
                .contains("WHEN deadline_date IS NULL THEN 2")
                .contains("CASE WHEN deadline_date >= CURRENT_DATE THEN deadline_date END ASC");
    }
}
