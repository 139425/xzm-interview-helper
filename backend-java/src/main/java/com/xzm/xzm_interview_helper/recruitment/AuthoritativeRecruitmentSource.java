package com.xzm.xzm_interview_helper.recruitment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthoritativeRecruitmentSource implements RecruitmentSource {
    private static final Map<String, String> TRUSTED_HOSTS = new LinkedHashMap<>();

    static {
        TRUSTED_HOSTS.put("ncss.cn", "国家大学生就业服务平台");
        TRUSTED_HOSTS.put("mohrss.gov.cn", "人力资源和社会保障部");
        TRUSTED_HOSTS.put("12333.gov.cn", "全国人社政务服务平台");
        TRUSTED_HOSTS.put("moe.gov.cn", "教育部");
        TRUSTED_HOSTS.put("sasac.gov.cn", "国务院国资委");
        TRUSTED_HOSTS.put("iguopin.com", "国聘");
        TRUSTED_HOSTS.put("24365.smartedu.cn", "国家大学生就业服务平台");
        TRUSTED_HOSTS.put("chinajob.mohrss.gov.cn", "中国就业网");
    }

    private final RecruitmentHttpClient httpClient;
    private final int graduateYear;

    public AuthoritativeRecruitmentSource(
            RecruitmentHttpClient httpClient,
            @Value("${recruitment.crawler.graduate-year:0}") int configuredGraduateYear
    ) {
        this.httpClient = httpClient;
        this.graduateYear = configuredGraduateYear > 0 ? configuredGraduateYear : Year.now().getValue() + 1;
    }

    @Override
    public String sourceName() {
        return "国家就业与政府平台";
    }

    @Override
    public List<RecruitmentCandidate> fetch() throws Exception {
        List<String> queries = List.of(
                "site:ncss.cn " + graduateYear + "届 校园招聘",
                "site:mohrss.gov.cn 高校毕业生 招聘",
                "site:12333.gov.cn " + graduateYear + "届 招聘",
                "site:sasac.gov.cn " + graduateYear + "届 校园招聘",
                "site:iguopin.com " + graduateYear + "届 校园招聘",
                "site:24365.smartedu.cn " + graduateYear + "届 校园招聘",
                "site:chinajob.mohrss.gov.cn 高校毕业生 招聘"
        );
        List<RecruitmentCandidate> result = new ArrayList<>();
        for (String query : queries) {
            String sourceUrl = bingRss(query);
            result.addAll(parse(httpClient.get(sourceUrl), sourceUrl, graduateYear));
            Thread.sleep(180L);
        }
        return result;
    }

    static List<RecruitmentCandidate> parse(String xml, String sourceUrl, int graduateYear) {
        Document document = Jsoup.parse(xml == null ? "" : xml, sourceUrl, Parser.xmlParser());
        List<RecruitmentCandidate> result = new ArrayList<>();
        for (Element item : document.select("item")) {
            String title = text(item, "title", 500);
            String description = text(item, "description", 1800);
            String combined = title + " " + description;
            if ((!combined.contains(String.valueOf(graduateYear)) && !combined.contains("高校毕业生"))
                    || (!combined.contains("招聘") && !combined.contains("校招"))) continue;
            String link = RecruitmentText.safeHttpUrl(text(item, "link", 1200));
            String trustedHost = trustedHost(link);
            if (link.isEmpty() || trustedHost == null) continue;

            String sourceName = TRUSTED_HOSTS.get(trustedHost);
            String sourceKind = trustedHost.endsWith("gov.cn") ? "GOVERNMENT" : "PUBLIC_EMPLOYMENT";
            int priority = "GOVERNMENT".equals(sourceKind) ? 95 : 90;
            result.add(RecruitmentCandidate.builder()
                    .externalId(link)
                    .company(inferCompany(title))
                    .title(title)
                    .companyType(inferCompanyType(combined))
                    .industry(RecruitmentClassifier.industry(combined))
                    .locations(inferLocations(combined))
                    .positions(description)
                    .recruitmentType(combined.contains("实习") ? "实习" : "校园招聘")
                    .targetGraduates(combined.contains(String.valueOf(graduateYear)) ? graduateYear + "届" : "高校毕业生")
                    .publishedDate(parseRssDate(text(item, "pubDate", 128)))
                    .deadline("以原始公告为准")
                    .applyUrl(link)
                    .announcementUrl(link)
                    .sourceName(sourceName)
                    .sourceUrl("https://" + trustedHost + "/")
                    .sourceKind(sourceKind)
                    .sourcePriority(priority)
                    .build());
        }
        return result;
    }

    private static String bingRss(String query) {
        return "https://www.bing.com/search?format=rss&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    private static String trustedHost(String link) {
        try {
            String host = new URI(link).getHost();
            if (host == null) return null;
            String normalized = host.toLowerCase();
            return TRUSTED_HOSTS.keySet().stream()
                    .filter(allowed -> normalized.equals(allowed) || normalized.endsWith("." + allowed))
                    .findFirst().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String inferCompany(String title) {
        String cleaned = title.replaceAll("^[【\\[]([^】\\]]+)[】\\]]", "$1 ");
        String company = cleaned.split("[-|｜—_:：]", 2)[0].trim();
        return RecruitmentText.clean(company.isBlank() ? "公共就业招聘" : company, 200);
    }

    private static String inferCompanyType(String text) {
        if (text.contains("央企")) return "央企";
        if (text.contains("国企")) return "国企";
        if (text.contains("事业单位")) return "事业单位";
        if (text.contains("银行")) return "银行";
        return "企业";
    }

    private static String inferLocations(String text) {
        return List.of("全国", "北京", "上海", "深圳", "广州", "杭州", "成都", "武汉", "南京", "苏州", "西安", "重庆")
                .stream().filter(text::contains).limit(6).reduce((left, right) -> left + "、" + right).orElse("");
    }

    private static LocalDate parseRssDate(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String text(Element parent, String selector, int maxLength) {
        Element element = parent.selectFirst(selector);
        return RecruitmentText.clean(element == null ? "" : element.text(), maxLength);
    }
}
