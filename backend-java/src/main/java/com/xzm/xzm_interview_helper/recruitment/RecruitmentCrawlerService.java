package com.xzm.xzm_interview_helper.recruitment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentCrawlerService {
    private final List<RecruitmentSource> sources;
    private final RecruitmentPostingRepository repository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${recruitment.crawler.minimum-refresh-hours:20}")
    private long minimumRefreshHours = 20;

    @Value("${recruitment.crawler.stale-after-days:60}")
    private int staleAfterDays = 60;

    @Scheduled(
            initialDelayString = "${recruitment.crawler.initial-delay-ms:20000}",
            fixedDelayString = "${recruitment.crawler.fixed-delay-ms:3600000}"
    )
    public void scheduledRefresh() {
        if (repository.refreshedWithin(Duration.ofHours(Math.max(1, minimumRefreshHours)))) {
            log.info("Recruitment crawl skipped because the current dataset is fresh");
            return;
        }
        refresh();
    }

    public void refresh() {
        if (!running.compareAndSet(false, true)) {
            log.info("Recruitment crawl skipped because another crawl is running");
            return;
        }
        long started = System.currentTimeMillis();
        repository.markStarted();
        int successfulSources = 0;
        int failedSources = 0;
        Map<String, RecruitmentCandidate> deduplicated = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        try {
            for (RecruitmentSource source : sources) {
                try {
                    List<RecruitmentCandidate> candidates = source.fetch();
                    for (RecruitmentCandidate candidate : candidates) {
                        if (!isUsable(candidate)) continue;
                        String key = RecruitmentText.opportunityKey(candidate);
                        deduplicated.merge(key, candidate, this::mergeCandidates);
                    }
                    successfulSources++;
                    log.info("Recruitment source {} returned {} candidates", source.sourceName(), candidates.size());
                } catch (Exception error) {
                    failedSources++;
                    errors.add(source.sourceName() + ": " + error.getClass().getSimpleName());
                    log.warn("Recruitment source {} failed: {}", source.sourceName(), error.getMessage());
                }
            }
            if (successfulSources == 0) {
                throw new IllegalStateException("All recruitment sources failed: " + String.join(", ", errors));
            }
            RecruitmentPostingRepository.UpsertStats stats = repository.upsertAll(new ArrayList<>(deduplicated.values()));
            int deactivated = repository.deactivateStale(staleAfterDays);
            repository.markSucceeded(stats, successfulSources, failedSources, System.currentTimeMillis() - started);
            log.info("Recruitment crawl completed: {} inserted, {} refreshed, {} stale deactivated, {} source failures",
                    stats.inserted(), stats.updated(), deactivated, failedSources);
        } catch (Exception error) {
            repository.markFailed(error.getMessage(), System.currentTimeMillis() - started);
            log.error("Recruitment crawl failed", error);
        } finally {
            running.set(false);
        }
    }

    private RecruitmentCandidate mergeCandidates(RecruitmentCandidate left, RecruitmentCandidate right) {
        RecruitmentCandidate primary = left.getSourcePriority() >= right.getSourcePriority() ? left : right;
        RecruitmentCandidate secondary = primary == left ? right : left;
        return RecruitmentCandidate.builder()
                .externalId(nonBlank(primary.getExternalId(), secondary.getExternalId()))
                .company(nonBlank(primary.getCompany(), secondary.getCompany()))
                .title(richer(primary.getTitle(), secondary.getTitle()))
                .companyType(nonGeneric(primary.getCompanyType(), secondary.getCompanyType(), "企业"))
                .industry(nonGeneric(primary.getIndustry(), secondary.getIndustry(), "其他行业"))
                .locations(richer(primary.getLocations(), secondary.getLocations()))
                .positions(richer(primary.getPositions(), secondary.getPositions()))
                .recruitmentType(nonBlank(primary.getRecruitmentType(), secondary.getRecruitmentType()))
                .targetGraduates(nonBlank(primary.getTargetGraduates(), secondary.getTargetGraduates()))
                .publishedDate(latest(primary.getPublishedDate(), secondary.getPublishedDate()))
                .deadline(specificDeadline(primary.getDeadline(), secondary.getDeadline()))
                .applyUrl(nonBlank(primary.getApplyUrl(), secondary.getApplyUrl()))
                .announcementUrl(nonBlank(primary.getAnnouncementUrl(), secondary.getAnnouncementUrl()))
                .sourceName(primary.getSourceName())
                .sourceUrl(primary.getSourceUrl())
                .sourceKind(primary.getSourceKind())
                .sourcePriority(primary.getSourcePriority())
                .build();
    }

    private boolean isUsable(RecruitmentCandidate candidate) {
        if (candidate == null || candidate.getCompany() == null || candidate.getCompany().isBlank()) return false;
        return !RecruitmentText.safeHttpUrl(candidate.getApplyUrl()).isEmpty()
                || !RecruitmentText.safeHttpUrl(candidate.getAnnouncementUrl()).isEmpty();
    }

    private static String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String nonGeneric(String preferred, String fallback, String generic) {
        if (preferred == null || preferred.isBlank() || generic.equals(preferred)) return nonBlank(fallback, generic);
        return preferred;
    }

    private static String richer(String left, String right) {
        String first = left == null ? "" : left.trim();
        String second = right == null ? "" : right.trim();
        if (isPlaceholder(first)) return second;
        if (isPlaceholder(second)) return first;
        return second.length() > first.length() ? second : first;
    }

    private static boolean isPlaceholder(String value) {
        return value.isBlank() || value.contains("以公告为准") || value.contains("持续更新") || value.contains("进入官网查看");
    }

    private static String specificDeadline(String left, String right) {
        String first = left == null ? "" : left;
        String second = right == null ? "" : right;
        if (first.matches(".*20\\d{2}.*")) return first;
        if (second.matches(".*20\\d{2}.*")) return second;
        return richer(first, second);
    }

    private static LocalDate latest(LocalDate left, LocalDate right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }
}
