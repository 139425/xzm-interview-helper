package com.xzm.xzm_interview_helper.recruitment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Component
public class NowcoderRecruitmentSource implements RecruitmentSource {
    static final String SOURCE_URL = "https://www.nowcoder.com/jobs/school/schedule?firstscroll=true";

    private final RecruitmentHttpClient httpClient;
    private final int graduateYear;

    public NowcoderRecruitmentSource(
            RecruitmentHttpClient httpClient,
            @Value("${recruitment.crawler.graduate-year:0}") int configuredGraduateYear
    ) {
        this.httpClient = httpClient;
        this.graduateYear = configuredGraduateYear > 0 ? configuredGraduateYear : Year.now().getValue() + 1;
    }

    @Override
    public String sourceName() {
        return "牛客校招日程";
    }

    @Override
    public List<RecruitmentCandidate> fetch() throws Exception {
        return parse(httpClient.get(SOURCE_URL), SOURCE_URL, graduateYear);
    }

    static List<RecruitmentCandidate> parse(String html, String sourceUrl, int graduateYear) {
        Document document = Jsoup.parse(html == null ? "" : html, sourceUrl);
        List<RecruitmentCandidate> result = new ArrayList<>();
        for (Element row : document.select("li.list-item-box")) {
            String company = text(row, ".company .title", 200);
            Element linkElement = row.selectFirst("a[href]");
            String link = linkElement == null ? "" : unwrapNowcoderUrl(linkElement.absUrl("href"));
            List<String> topLine = row.select(".company-content .tw-top-2 span").eachText();
            String combined = RecruitmentText.clean(String.join(" ", topLine), 300);
            String recruitmentType = topLine.stream()
                    .map(value -> RecruitmentText.clean(value, 100))
                    .filter(value -> value.contains("届") || value.contains("秋招") || value.contains("实习") || value.contains("校招"))
                    .findFirst().orElse("校园招聘");
            if (company.isBlank() || link.isBlank() || !recruitmentType.contains(String.valueOf(graduateYear % 100))) continue;

            String locations = text(row, ".city-hidden", 500);
            String introduction = text(row, ".introduce", 1200);
            String collected = topLine.stream().filter(value -> value.contains("收录")).findFirst().orElse("");
            result.add(RecruitmentCandidate.builder()
                    .externalId(link)
                    .company(company)
                    .title(company + " " + recruitmentType)
                    .companyType(inferCompanyType(introduction))
                    .industry(RecruitmentClassifier.industry(company, introduction))
                    .locations(locations)
                    .positions(introduction.isBlank() ? "进入牛客查看开放岗位" : introduction)
                    .recruitmentType(recruitmentType)
                    .targetGraduates(graduateYear + "届")
                    .publishedDate(RecruitmentText.parseMonthDay(collected))
                    .deadline("以投递页面为准")
                    .applyUrl(link)
                    .announcementUrl(link)
                    .sourceName("牛客校招日程")
                    .sourceUrl(sourceUrl)
                    .sourceKind("AGGREGATOR")
                    .sourcePriority(76)
                    .build());
        }
        return result;
    }

    private static String unwrapNowcoderUrl(String value) {
        String safe = RecruitmentText.safeHttpUrl(value);
        try {
            URI uri = new URI(safe);
            if (!RecruitmentText.host(safe).endsWith("nowcoder.com") || uri.getRawQuery() == null) return safe;
            for (String pair : uri.getRawQuery().split("&")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && "url".equals(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8))) {
                    String target = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    String unwrapped = RecruitmentText.safeHttpUrl(target);
                    if (!unwrapped.isBlank()) return unwrapped;
                }
            }
        } catch (Exception ignored) {
            // Keep the safe Nowcoder redirect when its query string is malformed.
        }
        return safe;
    }

    private static String inferCompanyType(String text) {
        if (text.contains("央企")) return "央企";
        if (text.contains("国企")) return "国企";
        if (text.contains("外企") || text.contains("全球")) return "外企";
        if (text.contains("事业单位")) return "事业单位";
        return "企业";
    }

    private static String text(Element row, String selector, int maxLength) {
        Element element = row.selectFirst(selector);
        return RecruitmentText.clean(element == null ? "" : element.text(), maxLength);
    }
}
