package com.xzm.xzm_interview_helper.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Public OfferShow recruitment directory, consumed through its anonymous web endpoint. */
@Component
public class OfferShowRecruitmentSource implements RecruitmentSource {
    static final String API_URL = "https://offershow.cn/api/od/search_plan_vip";
    static final String DIRECTORY_URL = "https://offershow.cn/recruit";

    private final RecruitmentHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int graduateYear;
    private final int maxPages;

    public OfferShowRecruitmentSource(
            RecruitmentHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${recruitment.crawler.graduate-year:0}") int configuredGraduateYear,
            @Value("${recruitment.crawler.offershow-pages:20}") int maxPages
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.graduateYear = configuredGraduateYear > 0
                ? configuredGraduateYear
                : java.time.Year.now().getValue() + 1;
        this.maxPages = Math.max(1, Math.min(maxPages, 50));
    }

    @Override
    public String sourceName() {
        return "OfferShow";
    }

    @Override
    public List<RecruitmentCandidate> fetch() throws Exception {
        List<RecruitmentCandidate> result = new ArrayList<>();
        int fetched = 0;
        for (int page = 1; page <= maxPages; page++) {
            int size = 24;
            String body = "page=" + page
                    + "&size=" + size
                    + "&recruit_plan_type=1&recruit_type=0&edu=0&is_recommend=0&filter_type=1";
            String url = API_URL + "?size=" + size + "&page=" + page;
            JsonNode root = objectMapper.readTree(httpClient.postForm(url, body));
            JsonNode data = root.path("data");
            JsonNode plans = data.path("plans");
            if (!plans.isArray() || plans.isEmpty()) break;
            result.addAll(parse(plans, graduateYear));
            fetched += plans.size();
            if (fetched >= data.path("total").asInt(0)) break;
            Thread.sleep(140L);
        }
        return result;
    }

    static List<RecruitmentCandidate> parse(JsonNode plans, int graduateYear) {
        List<RecruitmentCandidate> result = new ArrayList<>();
        for (JsonNode plan : plans) {
            String company = cleanText(plan, "company_name", 200);
            String title = cleanText(plan, "recruit_title", 500);
            String combined = company + " " + title;
            if (company.isBlank() || title.isBlank()) continue;
            if (!combined.contains(String.valueOf(graduateYear))
                    && !combined.contains((graduateYear % 100) + "届")) continue;

            String uuid = cleanText(plan, "uuid", 100);
            String noticeUrl = RecruitmentText.safeHttpUrl(plan.path("notice_url").asText(""));
            String detailUrl = uuid.isBlank()
                    ? DIRECTORY_URL
                    : "https://offershow.cn/recruit?uuid=" + URLEncoder.encode(uuid, StandardCharsets.UTF_8);
            String primaryUrl = noticeUrl.isBlank() ? detailUrl : noticeUrl;
            boolean discoveredWechat = "mp.weixin.qq.com".equals(RecruitmentText.host(noticeUrl));
            String locations = cleanText(plan, "recruit_city", 500).replace(" | ", "、");
            LocalDate published = timestampDate(plan.path("create_time").asLong(0));

            result.add(RecruitmentCandidate.builder()
                    .externalId(uuid.isBlank() ? primaryUrl : uuid)
                    .company(company)
                    .title(title)
                    .companyType(companyType(plan, combined))
                    .industry(RecruitmentClassifier.industry(company, title, locations))
                    .locations(locations)
                    .positions("进入 OfferShow 查看岗位清单与投递要求")
                    .recruitmentType(recruitmentType(plan.path("recruit_type").asInt(0), title))
                    .targetGraduates(graduateYear + "届")
                    .publishedDate(published)
                    .deadline(compactDate(cleanText(plan, "end_time", 16)))
                    .applyUrl(detailUrl)
                    .announcementUrl(primaryUrl)
                    .sourceName(discoveredWechat ? "微信公众号 · OfferShow收录" : "OfferShow")
                    .sourceUrl(DIRECTORY_URL)
                    .sourceKind(discoveredWechat ? "WECHAT" : "AGGREGATOR")
                    .sourcePriority(discoveredWechat ? 79 : 78)
                    .build());
        }
        return result;
    }

    private static String cleanText(JsonNode node, String field, int limit) {
        return RecruitmentText.clean(node.path(field).asText(""), limit);
    }

    private static LocalDate timestampDate(long epochSeconds) {
        if (epochSeconds <= 0) return null;
        try {
            return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String compactDate(String value) {
        if (!value.matches("20\\d{6}")) return "以公告为准";
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).toString();
        } catch (RuntimeException ignored) {
            return "以公告为准";
        }
    }

    private static String recruitmentType(int code, String title) {
        String normalized = title.toLowerCase(Locale.ROOT);
        if (normalized.contains("提前批")) return "秋招提前批";
        if (normalized.contains("实习") || code == 8) return "实习";
        if (normalized.contains("春招")) return "春招";
        return "秋招";
    }

    private static String companyType(JsonNode plan, String combined) {
        int character = plan.path("company").path("character").asInt(0);
        if (combined.contains("央企")) return "央企";
        if (combined.contains("国企")) return "国企";
        if (combined.toLowerCase(Locale.ROOT).contains("apple") || character == 5) return "外企";
        return "企业";
    }
}
