package com.xzm.xzm_interview_helper.recruitment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IndexedRecruitmentSource implements RecruitmentSource {
    private static final List<String> SEGMENTS = List.of(
            "互联网 软件 AI 游戏",
            "芯片 半导体 硬件 通信",
            "央企 国企 事业单位",
            "银行 证券 金融",
            "汽车 新能源 自动驾驶",
            "机械 制造 能源 化工",
            "外企 快消 咨询 医疗"
    );
    private static final List<String> CITIES = List.of(
            "北京", "上海", "深圳", "广州", "杭州", "南京", "苏州", "成都", "武汉", "西安",
            "重庆", "天津", "长沙", "合肥", "无锡", "厦门", "青岛", "郑州", "宁波", "珠海", "全国", "海外", "远程"
    );

    private final RecruitmentHttpClient httpClient;
    private final int graduateYear;

    public IndexedRecruitmentSource(
            RecruitmentHttpClient httpClient,
            @Value("${recruitment.crawler.graduate-year:0}") int configuredGraduateYear
    ) {
        this.httpClient = httpClient;
        this.graduateYear = configuredGraduateYear > 0 ? configuredGraduateYear : Year.now().getValue() + 1;
    }

    @Override
    public String sourceName() {
        return "公开招聘索引";
    }

    @Override
    public List<RecruitmentCandidate> fetch() throws Exception {
        List<String> queries = new ArrayList<>();
        for (String segment : SEGMENTS) {
            queries.add(graduateYear + "届 校园招聘 秋招 实习 " + segment);
        }
        queries.add("site:nowcoder.com/discuss " + graduateYear + "届 秋招 招聘");
        queries.add("site:offershow.cn " + graduateYear + "届 校园招聘");
        queries.add("site:mp.weixin.qq.com/s " + graduateYear + "届 校园招聘");
        queries.add("site:mp.weixin.qq.com/s " + graduateYear + "届 秋招 提前批");

        List<RecruitmentCandidate> result = new ArrayList<>();
        for (String query : queries) {
            String url = "https://www.bing.com/search?format=rss&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            result.addAll(parse(httpClient.get(url), url, graduateYear));
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
            if (!combined.contains(String.valueOf(graduateYear))
                    || List.of("招聘", "秋招", "校招", "实习").stream().noneMatch(combined::contains)) continue;
            String link = RecruitmentText.safeHttpUrl(text(item, "link", 1200));
            if (link.isEmpty()) continue;

            SourceMeta sourceMeta = sourceMeta(link);
            String company = inferCompany(title, graduateYear);
            result.add(RecruitmentCandidate.builder()
                    .externalId(link)
                    .company(company)
                    .title(title)
                    .companyType(inferCompanyType(combined))
                    .industry(RecruitmentClassifier.industry(company, combined))
                    .locations(inferLocations(combined))
                    .positions(description)
                    .recruitmentType(inferRecruitmentType(combined))
                    .targetGraduates(graduateYear + "届")
                    .publishedDate(parseRssDate(text(item, "pubDate", 128)))
                    .deadline(inferDeadline(combined))
                    .applyUrl(link)
                    .announcementUrl(link)
                    .sourceName(sourceMeta.name())
                    .sourceUrl(sourceUrl)
                    .sourceKind(sourceMeta.kind())
                    .sourcePriority(sourceMeta.priority())
                    .build());
        }
        return result;
    }

    private static SourceMeta sourceMeta(String link) {
        String host = RecruitmentText.host(link);
        if (host.endsWith("mp.weixin.qq.com")) return new SourceMeta("微信公众号公开文章", "WECHAT", 65);
        if (host.endsWith("nowcoder.com")) return new SourceMeta("牛客社区", "AGGREGATOR", 72);
        if (host.endsWith("offershow.cn")) return new SourceMeta("OfferShow", "AGGREGATOR", 72);
        if (host.endsWith("playoffer.cn")) return new SourceMeta("Offer稳了", "AGGREGATOR", 72);
        return new SourceMeta("公开招聘检索", "WEB_SEARCH", 40);
    }

    private static String inferCompany(String title, int graduateYear) {
        String cleaned = title.replaceAll("^[【\\[]([^】\\]]+)[】\\]]", "$1 ");
        Pattern beforeYear = Pattern.compile("^(.*?)(?:" + graduateYear + "届|" + (graduateYear % 100) + "届)");
        Matcher matcher = beforeYear.matcher(cleaned);
        String company = matcher.find() ? matcher.group(1) : cleaned.split("[-|｜—_:：]", 2)[0];
        company = company.replaceAll("(校园招聘|招聘|秋招|春招)$", "").trim();
        return RecruitmentText.clean(company.isBlank() ? "待确认企业" : company, 200);
    }

    private static String inferCompanyType(String text) {
        if (text.contains("央企")) return "央企";
        if (text.contains("国企")) return "国企";
        if (text.contains("银行")) return "银行";
        if (text.contains("外企") || text.toLowerCase(Locale.ROOT).contains("global")) return "外企";
        if (text.contains("事业单位")) return "事业单位";
        return "企业";
    }

    private static String inferRecruitmentType(String text) {
        if (text.contains("提前批")) return "秋招提前批";
        if (text.contains("实习")) return "实习";
        if (text.contains("春招")) return "春招";
        if (text.contains("秋招")) return "秋招";
        return "校园招聘";
    }

    private static String inferLocations(String text) {
        return CITIES.stream().filter(text::contains).distinct().limit(8).reduce((a, b) -> a + "、" + b).orElse("");
    }

    private static String inferDeadline(String text) {
        Matcher matcher = Pattern.compile("(?:截止(?:时间)?|网申截止)[:： ]*(20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)").matcher(text);
        return matcher.find() ? RecruitmentText.clean(matcher.group(1), 100) : "以公告为准";
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

    private record SourceMeta(String name, String kind, int priority) {
    }
}
