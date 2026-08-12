package com.xzm.xzm_interview_helper.recruitment;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruitmentCrawlerServiceTest {
    @Test
    void isolatesFailuresAndMergesAuthorityWithDetailedPositions() throws Exception {
        RecruitmentPostingRepository repository = mock(RecruitmentPostingRepository.class);
        when(repository.upsertAll(any())).thenReturn(new RecruitmentPostingRepository.UpsertStats(1, 0));

        RecruitmentCandidate aggregator = candidate("AGGREGATOR", 78, "算法工程师、后端工程师", "https://jobs.example.com/campus");
        RecruitmentCandidate official = candidate("OFFICIAL", 100, "开放岗位请进入官网查看", "https://jobs.example.com/campus?official=1");
        RecruitmentSource good = source("good", List.of(aggregator, official));
        RecruitmentSource broken = mock(RecruitmentSource.class);
        when(broken.sourceName()).thenReturn("broken");
        when(broken.fetch()).thenThrow(new IllegalStateException("offline"));

        new RecruitmentCrawlerService(List.of(good, broken), repository).refresh();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecruitmentCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(item -> {
            assertThat(item.getSourceKind()).isEqualTo("OFFICIAL");
            assertThat(item.getPositions()).isEqualTo("算法工程师、后端工程师");
            assertThat(item.getApplyUrl()).contains("official=1");
        });
        verify(repository).markSucceeded(any(), anyInt(), anyInt(), anyLong());
    }

    private static RecruitmentSource source(String name, List<RecruitmentCandidate> candidates) throws Exception {
        RecruitmentSource source = mock(RecruitmentSource.class);
        when(source.sourceName()).thenReturn(name);
        when(source.fetch()).thenReturn(candidates);
        return source;
    }

    private static RecruitmentCandidate candidate(String kind, int priority, String positions, String applyUrl) {
        return RecruitmentCandidate.builder()
                .company("测试科技")
                .title("测试科技2027届秋招")
                .recruitmentType("秋招")
                .targetGraduates("2027届")
                .positions(positions)
                .applyUrl(applyUrl)
                .sourceName(kind)
                .sourceUrl(applyUrl)
                .sourceKind(kind)
                .sourcePriority(priority)
                .build();
    }
}
