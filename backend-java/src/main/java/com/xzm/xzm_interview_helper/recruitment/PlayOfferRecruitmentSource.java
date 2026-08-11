package com.xzm.xzm_interview_helper.recruitment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Component
public class PlayOfferRecruitmentSource implements RecruitmentSource {
    static final String BASE_URL = "https://job.playoffer.cn/";

    private final RecruitmentHttpClient httpClient;
    private final int maxPages;
    private final int graduateYear;

    public PlayOfferRecruitmentSource(
            RecruitmentHttpClient httpClient,
            @Value("${recruitment.crawler.playoffer-pages:20}") int maxPages,
            @Value("${recruitment.crawler.graduate-year:0}") int configuredGraduateYear
    ) {
        this.httpClient = httpClient;
        this.maxPages = Math.max(1, Math.min(maxPages, 50));
        this.graduateYear = configuredGraduateYear > 0 ? configuredGraduateYear : Year.now().getValue() + 1;
    }

    @Override
    public String sourceName() {
        return "Offer稳了";
    }

    @Override
    public List<RecruitmentCandidate> fetch() throws Exception {
        List<RecruitmentCandidate> result = new ArrayList<>();
        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = page == 1 ? BASE_URL : BASE_URL + "?paged=" + page;
            String html = httpClient.get(pageUrl);
            List<RecruitmentCandidate> pageItems = parse(html, pageUrl, graduateYear);
            result.addAll(pageItems);
            if (pageItems.isEmpty() || !html.contains("crt-companies-tbody")) break;
            if (page < maxPages) Thread.sleep(220L);
        }
        return result;
    }

    static List<RecruitmentCandidate> parse(String html, String pageUrl, int graduateYear) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl);
        List<RecruitmentCandidate> result = new ArrayList<>();
        for (Element row : document.select("#crt-companies-tbody > tr[data-id]")) {
            String recruitmentType = text(row, ".crt-col-recruitment-type");
            String target = text(row, ".crt-col-target");
            if (!isCampusOpportunity(recruitmentType, target, graduateYear)) continue;

            List<Element> companyCells = row.select("td.crt-col-company");
            String company = companyCells.isEmpty() ? "" : RecruitmentText.clean(companyCells.get(0).text(), 200);
            String rawIndustry = companyCells.size() > 1 ? RecruitmentText.clean(companyCells.get(1).text(), 100) : "";
            String applyUrl = href(row, ".crt-col-links a");
            String announcementUrl = href(row, ".crt-col-notice a");
            if (company.isBlank() || (applyUrl.isBlank() && announcementUrl.isBlank())) continue;

            String positions = text(row, ".crt-col-position");
            result.add(RecruitmentCandidate.builder()
                    .externalId(RecruitmentText.clean(row.attr("data-id"), 128))
                    .company(company)
                    .title(RecruitmentText.clean(company + " " + recruitmentType, 500))
                    .companyType(text(row, ".crt-col-type"))
                    .industry(RecruitmentClassifier.industry(rawIndustry, company, positions))
                    .locations(text(row, ".crt-col-location"))
                    .positions(positions)
                    .recruitmentType(recruitmentType)
                    .targetGraduates(target)
                    .publishedDate(RecruitmentText.parseDate(text(row, ".crt-col-update-time")))
                    .deadline(text(row, ".crt-col-deadline"))
                    .applyUrl(applyUrl)
                    .announcementUrl(announcementUrl)
                    .sourceName("Offer稳了")
                    .sourceUrl(pageUrl)
                    .sourceKind("AGGREGATOR")
                    .sourcePriority(78)
                    .build());
        }
        return result;
    }

    private static boolean isCampusOpportunity(String type, String target, int graduateYear) {
        boolean campusType = List.of("秋招", "春招", "校招", "实习", "提前批", "人才专项", "校园大使")
                .stream().anyMatch(type::contains);
        return campusType && (target.contains(String.valueOf(graduateYear)) || target.contains("在校生"));
    }

    private static String text(Element row, String selector) {
        Element element = row.selectFirst(selector);
        return RecruitmentText.clean(element == null ? "" : element.text(), 4000);
    }

    private static String href(Element row, String selector) {
        Element element = row.selectFirst(selector);
        return element == null ? "" : RecruitmentText.safeHttpUrl(element.absUrl("href"));
    }
}
