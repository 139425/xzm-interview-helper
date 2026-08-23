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

    @Test
    void prefersAWechatAnnouncementWhenSourcePriorityIsTied() throws Exception {
        RecruitmentPostingRepository repository = mock(RecruitmentPostingRepository.class);
        when(repository.upsertAll(any())).thenReturn(new RecruitmentPostingRepository.UpsertStats(1, 0));
        RecruitmentCandidate aggregator = candidate(
                "AGGREGATOR", 78, "岗位清单", "https://jobs.example.com/campus"
        );
        RecruitmentCandidate wechat = RecruitmentCandidate.builder()
                .company("测试科技")
                .title("测试科技2027届秋招")
                .recruitmentType("秋招")
                .targetGraduates("2027届")
                .positions("岗位清单")
                .applyUrl("https://offershow.cn/recruit-detail?uuid=1")
                .announcementUrl("https://mp.weixin.qq.com/s/example")
                .sourceName("微信公众号 · OfferShow收录")
                .sourceUrl("https://offershow.cn/recruit")
                .sourceKind("WECHAT")
                .sourcePriority(78)
                .build();

        new RecruitmentCrawlerService(List.of(source("all", List.of(aggregator, wechat))), repository).refresh();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecruitmentCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(item -> {
            assertThat(item.getSourceKind()).isEqualTo("WECHAT");
            assertThat(item.getAnnouncementUrl()).contains("mp.weixin.qq.com");
        });
    }

    @Test
    void keepsOnly2027AutumnAndMergesOfficialWithASeparateAnnouncement() throws Exception {
        RecruitmentPostingRepository repository = mock(RecruitmentPostingRepository.class);
        when(repository.upsertAll(any())).thenReturn(new RecruitmentPostingRepository.UpsertStats(1, 0));
        RecruitmentCandidate official = scopedCandidate(
                "OFFICIAL", 100, "官网岗位", "校园招聘", "2027届",
                "https://jobs.example.com/campus", "https://jobs.example.com/campus"
        );
        RecruitmentCandidate wechat = scopedCandidate(
                "WECHAT", 79, "岗位清单", "秋招", "2027届",
                "https://offershow.cn/recruit/1", "https://mp.weixin.qq.com/s/example"
        );
        RecruitmentCandidate internship = scopedCandidate(
                "AGGREGATOR", 70, "暑期实习", "实习", "2027届",
                "https://jobs.example.com/intern", ""
        );
        RecruitmentCandidate wrongYear = scopedCandidate(
                "AGGREGATOR", 70, "2026届秋招", "秋招", "2026届",
                "https://jobs.example.com/2026", ""
        );

        new RecruitmentCrawlerService(
                List.of(source("all", List.of(official, wechat, internship, wrongYear))), repository
        ).refresh();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecruitmentCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(item -> {
            assertThat(item.getSourceKind()).isEqualTo("OFFICIAL");
            assertThat(item.getAnnouncementUrl()).isEqualTo("https://mp.weixin.qq.com/s/example");
        });
        verify(repository).deactivateOutsideScope(2027);
        verify(repository).deactivateDuplicateOpportunities(2027);
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

    private static RecruitmentCandidate scopedCandidate(
            String kind,
            int priority,
            String positions,
            String recruitmentType,
            String targetGraduates,
            String applyUrl,
            String announcementUrl
    ) {
        return RecruitmentCandidate.builder()
                .company("测试科技")
                .title("测试科技" + targetGraduates + recruitmentType)
                .recruitmentType(recruitmentType)
                .targetGraduates(targetGraduates)
                .positions(positions)
                .applyUrl(applyUrl)
                .announcementUrl(announcementUrl)
                .sourceName(kind)
                .sourceUrl(applyUrl)
                .sourceKind(kind)
                .sourcePriority(priority)
                .build();
    }
}
