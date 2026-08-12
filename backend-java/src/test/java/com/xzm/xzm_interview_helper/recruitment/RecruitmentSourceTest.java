package com.xzm.xzm_interview_helper.recruitment;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecruitmentSourceTest {
    @Test
    void parsesPlayOfferRowsIncludingWechatAnnouncement() {
        String html = """
                <table><tbody id="crt-companies-tbody">
                  <tr data-id="22322">
                    <td class="crt-col-company"><b>测试科技</b></td>
                    <td class="crt-col-type">民企</td>
                    <td class="crt-col-company">电子/半导体</td>
                    <td class="crt-col-recruitment-type">秋招提前批</td>
                    <td class="crt-col-target">2027届</td>
                    <td class="crt-col-location">深圳 上海</td>
                    <td class="crt-col-position"><script>alert(1)</script>数字芯片工程师</td>
                    <td class="crt-col-update-time">2026-08-11</td>
                    <td class="crt-col-deadline">2026-09-01</td>
                    <td class="crt-col-links"><a href="https://jobs.example.com/apply#campus">投递</a></td>
                    <td class="crt-col-notice"><a href="https://mp.weixin.qq.com/s/example">公告</a></td>
                  </tr>
                  <tr data-id="old"><td class="crt-col-company">旧招聘</td><td class="crt-col-recruitment-type">社招</td><td class="crt-col-target">2027届</td></tr>
                </tbody></table>
                """;

        List<RecruitmentCandidate> candidates = PlayOfferRecruitmentSource.parse(html, "https://job.playoffer.cn/", 2027);

        assertThat(candidates).hasSize(1);
        RecruitmentCandidate candidate = candidates.get(0);
        assertThat(candidate.getCompany()).isEqualTo("测试科技");
        assertThat(candidate.getPositions()).isEqualTo("数字芯片工程师");
        assertThat(candidate.getIndustry()).isEqualTo("硬件/半导体");
        assertThat(candidate.getAnnouncementUrl()).startsWith("https://mp.weixin.qq.com/");
        assertThat(candidate.getPublishedDate()).hasToString("2026-08-11");
    }

    @Test
    void parsesNowcoderCardsAndUnwrapsOfficialDeliveryUrl() {
        String html = """
                <ul><li class="list-item-box">
                  <a href="https://www.nowcoder.com/jump?type=ad&amp;url=https%3A%2F%2Fjobs.example.com%2Fcampus%3Ffrom%3Dnowcoder">
                    <div class="company"><div class="title">星河科技</div></div>
                    <div class="introduce">专注人工智能与芯片研发</div>
                    <div class="company-content">
                      <div class="tw-top-2"><span>27届秋招</span><span>丨</span><span>08.11收录</span></div>
                      <span class="city-hidden">北京、上海</span>
                    </div>
                  </a>
                </li></ul>
                """;

        List<RecruitmentCandidate> candidates = NowcoderRecruitmentSource.parse(html, NowcoderRecruitmentSource.SOURCE_URL, 2027);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.getCompany()).isEqualTo("星河科技");
            assertThat(candidate.getApplyUrl()).isEqualTo("https://jobs.example.com/campus?from=nowcoder");
            assertThat(candidate.getSourceName()).isEqualTo("牛客校招日程");
            assertThat(candidate.getLocations()).isEqualTo("北京、上海");
        });
    }

    @Test
    void authoritativeSourceRejectsLookalikeDomains() {
        String rss = """
                <rss><channel>
                  <item><title>某央企2027届校园招聘</title><link>https://www.sasac.gov.cn/n2588035/campus.html</link><description>北京、上海招聘高校毕业生</description><pubDate>Tue, 11 Aug 2026 08:00:00 GMT</pubDate></item>
                  <item><title>伪造的2027届招聘</title><link>https://sasac.gov.cn.example.com/fake</link><description>校园招聘</description></item>
                </channel></rss>
                """;

        List<RecruitmentCandidate> candidates = AuthoritativeRecruitmentSource.parse(rss, "https://bing.example/rss", 2027);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.getSourceName()).isEqualTo("国务院国资委");
            assertThat(candidate.getSourceKind()).isEqualTo("GOVERNMENT");
            assertThat(candidate.getSourcePriority()).isEqualTo(95);
        });
    }

    @Test
    void labelsIndexedWechatAndOfferShowResults() {
        String rss = """
                <rss><channel>
                  <item><title>星河科技2027届秋招正式启动</title><link>https://mp.weixin.qq.com/s/example</link><description>深圳算法工程师，网申截止：2026-09-30</description></item>
                  <item><title>月光公司2027届校园招聘</title><link>https://offershow.cn/jobs/example</link><description>北京产品岗位</description></item>
                </channel></rss>
                """;

        List<RecruitmentCandidate> candidates = IndexedRecruitmentSource.parse(rss, "https://bing.example/rss", 2027);

        assertThat(candidates).extracting(RecruitmentCandidate::getSourceKind)
                .containsExactly("WECHAT", "AGGREGATOR");
        assertThat(candidates.get(0).getDeadline()).isEqualTo("2026-09-30");
    }

    @Test
    void fingerprintMergesTheSameCompanyRoundAcrossSources() {
        RecruitmentCandidate official = RecruitmentCandidate.builder()
                .company("测试科技有限公司")
                .title("测试科技 2027届秋招")
                .recruitmentType("秋招")
                .targetGraduates("2027届")
                .applyUrl("https://jobs.example.com/apply")
                .build();
        RecruitmentCandidate wechat = RecruitmentCandidate.builder()
                .company("测试科技")
                .title("招聘岗位清单")
                .recruitmentType("秋招")
                .targetGraduates("2027届")
                .announcementUrl("https://mp.weixin.qq.com/s/example")
                .build();

        assertThat(RecruitmentText.fingerprint(official)).isEqualTo(RecruitmentText.fingerprint(wechat));
        assertThat(RecruitmentText.safeHttpUrl("javascript:alert(1)")).isEmpty();
    }

    @Test
    void parsesOfferShowAnonymousDirectoryRows() throws Exception {
        String json = """
                [{
                  "uuid":"plan-1","company_name":"阿里云","recruit_title":"阿里云2027届应届生招聘全球启动",
                  "recruit_city":"杭州 | 北京 | 上海","recruit_type":3,"create_time":1785911442,
                  "end_time":"20270201","notice_url":"https://mp.weixin.qq.com/s/example",
                  "company":{"character":0}
                }]
                """;

        List<RecruitmentCandidate> candidates = OfferShowRecruitmentSource.parse(
                new ObjectMapper().readTree(json),
                2027
        );

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.getCompany()).isEqualTo("阿里云");
            assertThat(candidate.getSourceName()).isEqualTo("微信公众号 · OfferShow收录");
            assertThat(candidate.getSourceKind()).isEqualTo("WECHAT");
            assertThat(candidate.getLocations()).isEqualTo("杭州、北京、上海");
            assertThat(candidate.getDeadline()).isEqualTo("2027-02-01");
        });
    }

}
