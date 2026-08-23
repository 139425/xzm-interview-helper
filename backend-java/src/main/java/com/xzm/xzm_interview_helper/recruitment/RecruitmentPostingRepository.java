package com.xzm.xzm_interview_helper.recruitment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RecruitmentPostingRepository {
    private static final String ACTIVE_SCOPE = "active = 1 AND target_graduates = ? AND recruitment_type LIKE '%秋招%'";
    private static final String UPSERT_SQL = """
            INSERT INTO recruitment_posting (
                fingerprint, external_id, company, title, company_type, industry, job_track, locations, positions,
                recruitment_type, target_graduates, published_date, deadline, deadline_date, apply_url,
                announcement_url, source_name, source_url, source_kind, source_priority,
                first_seen_at, last_seen_at, crawled_at, active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW(), 1)
            ON DUPLICATE KEY UPDATE
                external_id = VALUES(external_id),
                company = VALUES(company),
                title = VALUES(title),
                company_type = VALUES(company_type),
                industry = VALUES(industry),
                job_track = VALUES(job_track),
                locations = VALUES(locations),
                positions = VALUES(positions),
                recruitment_type = VALUES(recruitment_type),
                target_graduates = VALUES(target_graduates),
                published_date = COALESCE(VALUES(published_date), published_date),
                deadline = CASE
                    WHEN VALUES(deadline_date) IS NOT NULL THEN VALUES(deadline)
                    WHEN deadline_date IS NOT NULL THEN deadline
                    ELSE VALUES(deadline)
                END,
                deadline_date = COALESCE(VALUES(deadline_date), deadline_date),
                apply_url = VALUES(apply_url),
                announcement_url = VALUES(announcement_url),
                source_name = VALUES(source_name),
                source_url = VALUES(source_url),
                source_kind = VALUES(source_kind),
                source_priority = VALUES(source_priority),
                last_seen_at = NOW(), crawled_at = NOW(), active = 1
            """;

    private final JdbcTemplate jdbcTemplate;

    @Value("${recruitment.crawler.graduate-year:2027}")
    private int graduateYear = 2027;

    public record UpsertStats(int inserted, int updated) {
    }

    public record Posting(
            long id,
            String company,
            String title,
            String companyType,
            String industry,
            String jobTrack,
            String locations,
            String positions,
            String recruitmentType,
            String targetGraduates,
            LocalDate publishedDate,
            String deadline,
            LocalDate deadlineDate,
            String applyUrl,
            String announcementUrl,
            String sourceName,
            String sourceUrl,
            String sourceKind,
            int sourcePriority,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt
    ) {
    }

    @Transactional
    public UpsertStats upsertAll(List<RecruitmentCandidate> candidates) {
        int inserted = 0;
        int updated = 0;
        for (RecruitmentCandidate candidate : candidates) {
            int affected = jdbcTemplate.update(
                    UPSERT_SQL,
                    RecruitmentText.fingerprint(candidate),
                    RecruitmentText.clean(candidate.getExternalId(), 256),
                    RecruitmentText.clean(candidate.getCompany(), 200),
                    RecruitmentText.clean(candidate.getTitle(), 500),
                    fallback(RecruitmentText.clean(candidate.getCompanyType(), 64), "企业"),
                    fallback(RecruitmentText.clean(candidate.getIndustry(), 64),
                            RecruitmentClassifier.industry(candidate.getCompany(), candidate.getTitle(), candidate.getCompanyType(), candidate.getPositions())),
                    RecruitmentClassifier.jobTrack(candidate.getTitle(), candidate.getPositions()),
                    RecruitmentText.clean(candidate.getLocations(), 500),
                    RecruitmentText.clean(candidate.getPositions(), 4000),
                    RecruitmentText.isTargetAutumnRecruitment(candidate, graduateYear)
                            ? RecruitmentText.autumnRecruitmentType(candidate)
                            : fallback(RecruitmentText.clean(candidate.getRecruitmentType(), 64), "校园招聘"),
                    RecruitmentText.isTargetAutumnRecruitment(candidate, graduateYear)
                            ? targetGraduateLabel(graduateYear)
                            : RecruitmentText.clean(candidate.getTargetGraduates(), 128),
                    candidate.getPublishedDate(),
                    fallback(RecruitmentText.clean(candidate.getDeadline(), 128), "以公告为准"),
                    RecruitmentText.parseDeadlineDate(candidate.getDeadline()),
                    RecruitmentText.safeHttpUrl(candidate.getApplyUrl()),
                    RecruitmentText.safeHttpUrl(candidate.getAnnouncementUrl()),
                    RecruitmentText.clean(candidate.getSourceName(), 100),
                    RecruitmentText.safeHttpUrl(candidate.getSourceUrl()),
                    RecruitmentText.clean(candidate.getSourceKind(), 32),
                    Math.max(0, candidate.getSourcePriority())
            );
            if (affected == 1) inserted++;
            else updated++;
        }
        return new UpsertStats(inserted, updated);
    }

    public Map<String, Object> findPage(
            int page,
            int size,
            String keyword,
            String recruitmentType,
            String companyType,
            String city,
            boolean freshOnly,
            String industry,
            String jobTrack,
            String sourceKind,
            String targetGraduates,
            int publishedWithinDays,
            int deadlineWithinDays,
            boolean officialOnly,
            String sort
    ) {
        StringBuilder where = new StringBuilder(" WHERE ").append(ACTIVE_SCOPE);
        List<Object> params = new ArrayList<>();
        params.add(targetGraduateLabel(graduateYear));
        if (!keyword.isBlank()) {
            where.append(" AND (company LIKE ? OR title LIKE ? OR positions LIKE ?)");
            String like = "%" + escapeLike(keyword) + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (!recruitmentType.isBlank()) {
            where.append(" AND recruitment_type LIKE ?");
            params.add("%" + escapeLike(recruitmentType) + "%");
        }
        if (!companyType.isBlank()) {
            where.append(" AND company_type = ?");
            params.add(companyType);
        }
        if (!city.isBlank()) {
            where.append(" AND locations LIKE ?");
            params.add("%" + escapeLike(city) + "%");
        }
        if (!industry.isBlank()) {
            where.append(" AND industry = ?");
            params.add(industry);
        }
        if (!jobTrack.isBlank()) {
            where.append(" AND job_track = ?");
            params.add(jobTrack);
        }
        if (!sourceKind.isBlank()) {
            where.append(" AND source_kind = ?");
            params.add(sourceKind);
        }
        if (!targetGraduates.isBlank()) {
            where.append(" AND target_graduates LIKE ?");
            params.add("%" + escapeLike(targetGraduates) + "%");
        }
        if (publishedWithinDays > 0) {
            where.append(" AND COALESCE(published_date, DATE(first_seen_at)) >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY)");
            params.add(Math.min(publishedWithinDays, 365));
        }
        if (deadlineWithinDays > 0) {
            where.append(" AND deadline_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL ? DAY)");
            params.add(Math.min(deadlineWithinDays, 365));
        }
        if (officialOnly) {
            where.append(" AND source_kind IN ('OFFICIAL', 'GOVERNMENT', 'PUBLIC_EMPLOYMENT', 'UNIVERSITY')");
        }
        if (freshOnly) {
            where.append(" AND first_seen_at >= CURRENT_DATE");
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recruitment_posting" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) (page - 1) * size);
        List<Posting> items = jdbcTemplate.query(
                "SELECT id, company, title, company_type, industry, job_track, locations, positions, recruitment_type, "
                        + "target_graduates, published_date, deadline, deadline_date, apply_url, announcement_url, "
                        + "source_name, source_url, source_kind, source_priority, first_seen_at, last_seen_at "
                        + "FROM recruitment_posting" + where + orderBy(sort) + " LIMIT ? OFFSET ?",
                POSTING_ROW_MAPPER,
                pageParams.toArray()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        result.put("hasMore", total != null && (long) page * size < total);
        result.put("summary", summary());
        return result;
    }

    public Map<String, Object> facets() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("industries", groupedFacet("industry"));
        result.put("jobTracks", groupedFacet("job_track"));
        result.put("companyTypes", groupedFacet("company_type"));
        result.put("recruitmentTypes", groupedFacet("recruitment_type"));
        result.put("graduateYears", groupedFacet("target_graduates"));
        result.put("sourceKinds", groupedFacet("source_kind"));
        result.put("sources", jdbcTemplate.queryForList(
                "SELECT source_name AS name, source_kind AS kind, MAX(source_priority) AS priority, COUNT(*) AS count "
                        + "FROM recruitment_posting WHERE " + ACTIVE_SCOPE + " GROUP BY source_name, source_kind "
                        + "ORDER BY priority DESC, count DESC, name ASC",
                targetGraduateLabel(graduateYear)
        ));
        result.put("cities", popularCities());
        result.put("authorityPolicy", List.of(
                Map.of("kind", "OFFICIAL", "label", "企业官网", "priority", 100),
                Map.of("kind", "GOVERNMENT", "label", "政府部门", "priority", 95),
                Map.of("kind", "PUBLIC_EMPLOYMENT", "label", "公共就业平台", "priority", 90),
                Map.of("kind", "UNIVERSITY", "label", "高校就业网", "priority", 85),
                Map.of("kind", "AGGREGATOR", "label", "求职平台", "priority", 75),
                Map.of("kind", "WECHAT", "label", "微信公众号", "priority", 65),
                Map.of("kind", "WEB_SEARCH", "label", "公开检索", "priority", 40)
        ));
        return result;
    }

    public Optional<Posting> findById(long id) {
        List<Posting> rows = jdbcTemplate.query(
                "SELECT id, company, title, company_type, industry, job_track, locations, positions, recruitment_type, "
                        + "target_graduates, published_date, deadline, deadline_date, apply_url, announcement_url, "
                        + "source_name, source_url, source_kind, source_priority, first_seen_at, last_seen_at "
                        + "FROM recruitment_posting WHERE id = ? AND " + ACTIVE_SCOPE,
                POSTING_ROW_MAPPER,
                id,
                targetGraduateLabel(graduateYear)
        );
        return rows.stream().findFirst();
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        String target = targetGraduateLabel(graduateYear);
        result.put("total", count("SELECT COUNT(*) FROM recruitment_posting WHERE " + ACTIVE_SCOPE, target));
        result.put("newToday", count("SELECT COUNT(*) FROM recruitment_posting WHERE " + ACTIVE_SCOPE
                + " AND first_seen_at >= CURRENT_DATE", target));
        result.put("newWeek", count("SELECT COUNT(*) FROM recruitment_posting WHERE " + ACTIVE_SCOPE
                + " AND first_seen_at >= DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY)", target));
        result.put("newMonth", count("SELECT COUNT(*) FROM recruitment_posting WHERE " + ACTIVE_SCOPE
                + " AND first_seen_at >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY)", target));
        result.put("sourceCount", jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT source_name) FROM recruitment_posting WHERE " + ACTIVE_SCOPE,
                Integer.class,
                target
        ));
        result.put("officialCount", count("SELECT COUNT(*) FROM recruitment_posting WHERE " + ACTIVE_SCOPE
                + " AND source_kind IN ('OFFICIAL', 'GOVERNMENT', 'PUBLIC_EMPLOYMENT', 'UNIVERSITY')", target));
        result.put("closingSoon", count(
                "SELECT COUNT(*) FROM recruitment_posting WHERE " + ACTIVE_SCOPE
                        + " AND deadline_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY)",
                target
        ));
        Map<String, Object> status = jdbcTemplate.queryForMap(
                "SELECT running, last_started_at, last_success_at, last_error, last_inserted, last_updated, successful_sources, failed_sources, duration_ms "
                        + "FROM recruitment_crawl_status WHERE id = 1"
        );
        result.put("running", asBoolean(status.get("running")));
        result.put("lastStarted", status.get("last_started_at"));
        result.put("lastUpdated", status.get("last_success_at"));
        result.put("lastError", status.get("last_error"));
        result.put("lastInserted", status.get("last_inserted"));
        result.put("lastRefreshed", status.get("last_updated"));
        result.put("successfulSources", status.get("successful_sources"));
        result.put("failedSources", status.get("failed_sources"));
        result.put("durationMs", status.get("duration_ms"));
        return result;
    }

    public boolean refreshedWithin(Duration duration) {
        Timestamp timestamp = jdbcTemplate.queryForObject("SELECT last_success_at FROM recruitment_crawl_status WHERE id = 1", Timestamp.class);
        return timestamp != null && timestamp.toLocalDateTime().isAfter(LocalDateTime.now().minus(duration));
    }

    public int deactivateStale(int staleAfterDays) {
        return jdbcTemplate.update(
                "UPDATE recruitment_posting SET active = 0 WHERE active = 1 AND last_seen_at < DATE_SUB(NOW(), INTERVAL ? DAY)",
                Math.max(30, Math.min(staleAfterDays, 365))
        );
    }

    public int deactivateOutsideScope(int targetYear) {
        return jdbcTemplate.update(
                "UPDATE recruitment_posting SET active = 0 WHERE active = 1 "
                        + "AND (target_graduates <> ? OR recruitment_type NOT LIKE '%秋招%')",
                targetGraduateLabel(targetYear)
        );
    }

    public int deactivateDuplicateOpportunities(int targetYear) {
        List<Posting> active = jdbcTemplate.query(
                "SELECT id, company, title, company_type, industry, job_track, locations, positions, recruitment_type, "
                        + "target_graduates, published_date, deadline, deadline_date, apply_url, announcement_url, "
                        + "source_name, source_url, source_kind, source_priority, first_seen_at, last_seen_at "
                        + "FROM recruitment_posting WHERE " + ACTIVE_SCOPE,
                POSTING_ROW_MAPPER,
                targetGraduateLabel(targetYear)
        );
        Map<String, Posting> winners = new LinkedHashMap<>();
        List<Long> duplicateIds = new ArrayList<>();
        for (Posting posting : active) {
            String key = RecruitmentText.opportunityKey(
                    posting.company(), posting.title(), posting.recruitmentType(), posting.targetGraduates()
            );
            Posting previous = winners.get(key);
            if (previous == null) {
                winners.put(key, posting);
            } else if (isPreferred(posting, previous)) {
                duplicateIds.add(previous.id());
                winners.put(key, posting);
            } else {
                duplicateIds.add(posting.id());
            }
        }
        int deactivated = 0;
        for (Long id : duplicateIds) {
            deactivated += jdbcTemplate.update("UPDATE recruitment_posting SET active = 0 WHERE id = ? AND active = 1", id);
        }
        return deactivated;
    }

    public void markStarted() {
        jdbcTemplate.update("UPDATE recruitment_crawl_status SET running = 1, last_started_at = NOW(), last_error = '' WHERE id = 1");
    }

    public void markSucceeded(UpsertStats stats, int successfulSources, int failedSources, long durationMs) {
        jdbcTemplate.update(
                "UPDATE recruitment_crawl_status SET running = 0, last_success_at = NOW(), last_error = '', "
                        + "last_inserted = ?, last_updated = ?, successful_sources = ?, failed_sources = ?, duration_ms = ? WHERE id = 1",
                stats.inserted(), stats.updated(), successfulSources, failedSources, durationMs
        );
    }

    public void markFailed(String error, long durationMs) {
        jdbcTemplate.update(
                "UPDATE recruitment_crawl_status SET running = 0, last_error = ?, duration_ms = ? WHERE id = 1",
                RecruitmentText.clean(error, 1000), durationMs
        );
    }

    private static final RowMapper<Posting> POSTING_ROW_MAPPER = (resultSet, rowNum) -> new Posting(
            resultSet.getLong("id"), resultSet.getString("company"), resultSet.getString("title"),
            resultSet.getString("company_type"), resultSet.getString("industry"), resultSet.getString("job_track"),
            resultSet.getString("locations"),
            resultSet.getString("positions"), resultSet.getString("recruitment_type"), resultSet.getString("target_graduates"),
            toLocalDate(resultSet, "published_date"), resultSet.getString("deadline"),
            toLocalDate(resultSet, "deadline_date"), resultSet.getString("apply_url"),
            resultSet.getString("announcement_url"), resultSet.getString("source_name"), resultSet.getString("source_url"),
            resultSet.getString("source_kind"), resultSet.getInt("source_priority"),
            toLocalDateTime(resultSet, "first_seen_at"), toLocalDateTime(resultSet, "last_seen_at")
    );

    private static LocalDate toLocalDate(ResultSet resultSet, String column) throws SQLException {
        java.sql.Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime toLocalDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escapeLike(String value) {
        return RecruitmentText.clean(value, 100).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private long count(String sql, Object... params) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, params);
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> groupedFacet(String column) {
        return jdbcTemplate.queryForList(
                "SELECT " + column + " AS value, COUNT(*) AS count FROM recruitment_posting "
                        + "WHERE " + ACTIVE_SCOPE + " AND " + column + " <> '' GROUP BY " + column
                        + " ORDER BY count DESC, value ASC",
                targetGraduateLabel(graduateYear)
        );
    }

    private List<Map<String, Object>> popularCities() {
        List<String> cityNames = List.of(
                "全国", "北京", "上海", "深圳", "广州", "杭州", "成都", "武汉", "南京", "苏州",
                "西安", "重庆", "长沙", "合肥", "天津", "无锡", "厦门", "青岛", "郑州", "宁波", "珠海", "海外", "远程"
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (String city : cityNames) {
            Long cityCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM recruitment_posting WHERE " + ACTIVE_SCOPE + " AND locations LIKE ?",
                    Long.class,
                    targetGraduateLabel(graduateYear),
                    "%" + city + "%"
            );
            if (cityCount != null && cityCount > 0) result.add(Map.of("value", city, "count", cityCount));
        }
        result.sort((left, right) -> Long.compare(((Number) right.get("count")).longValue(), ((Number) left.get("count")).longValue()));
        return result;
    }

    private static boolean isPreferred(Posting candidate, Posting current) {
        if (candidate.sourcePriority() != current.sourcePriority()) {
            return candidate.sourcePriority() > current.sourcePriority();
        }
        int candidateCompleteness = completeness(candidate);
        int currentCompleteness = completeness(current);
        if (candidateCompleteness != currentCompleteness) return candidateCompleteness > currentCompleteness;
        if (candidate.lastSeenAt() != null && current.lastSeenAt() != null
                && !candidate.lastSeenAt().equals(current.lastSeenAt())) {
            return candidate.lastSeenAt().isAfter(current.lastSeenAt());
        }
        return candidate.id() > current.id();
    }

    private static int completeness(Posting posting) {
        int score = 0;
        if (posting.positions() != null && !posting.positions().isBlank() && !posting.positions().contains("以公告为准")) score++;
        if (posting.locations() != null && !posting.locations().isBlank()) score++;
        if (posting.deadlineDate() != null) score++;
        if (posting.applyUrl() != null && !posting.applyUrl().isBlank()) score++;
        if (posting.announcementUrl() != null && !posting.announcementUrl().isBlank()) score++;
        return score;
    }

    private static String targetGraduateLabel(int year) {
        int safeYear = year >= 2000 && year <= 2100 ? year : 2027;
        return safeYear + "届";
    }

    static String orderBy(String sort) {
        return switch (sort == null ? "" : sort) {
            case "authority" -> " ORDER BY source_priority DESC, COALESCE(published_date, DATE(first_seen_at)) DESC, id DESC";
            case "company" -> " ORDER BY company ASC, COALESCE(published_date, DATE(first_seen_at)) DESC, id DESC";
            case "newlyAdded" -> " ORDER BY first_seen_at DESC, id DESC";
            case "deadline" -> """
                     ORDER BY CASE
                       WHEN deadline_date >= CURRENT_DATE THEN 0
                       WHEN deadline_date IS NULL THEN 2
                       ELSE 1
                     END ASC,
                     CASE WHEN deadline_date >= CURRENT_DATE THEN deadline_date END ASC,
                     deadline_date DESC, source_priority DESC, id DESC
                    """;
            default -> " ORDER BY COALESCE(published_date, DATE(first_seen_at)) DESC, first_seen_at DESC, id DESC";
        };
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
