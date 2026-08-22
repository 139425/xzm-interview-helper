package com.xzm.xzm_interview_helper.recruitment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RecruitmentSchemaInitializer implements InitializingBean {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recruitment_posting (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    fingerprint CHAR(64) NOT NULL,
                    external_id VARCHAR(256) NULL,
                    company VARCHAR(200) NOT NULL,
                    title VARCHAR(500) NOT NULL,
                    company_type VARCHAR(64) NOT NULL DEFAULT '企业',
                    industry VARCHAR(64) NOT NULL DEFAULT '其他行业',
                    job_track VARCHAR(64) NOT NULL DEFAULT '综合岗位',
                    locations VARCHAR(500) NOT NULL DEFAULT '',
                    positions TEXT NULL,
                    recruitment_type VARCHAR(64) NOT NULL DEFAULT '校园招聘',
                    target_graduates VARCHAR(128) NOT NULL DEFAULT '',
                    published_date DATE NULL,
                    deadline VARCHAR(128) NOT NULL DEFAULT '以公告为准',
                    deadline_date DATE NULL,
                    apply_url VARCHAR(1024) NOT NULL DEFAULT '',
                    announcement_url VARCHAR(1024) NOT NULL DEFAULT '',
                    source_name VARCHAR(100) NOT NULL,
                    source_url VARCHAR(1024) NOT NULL DEFAULT '',
                    source_kind VARCHAR(32) NOT NULL,
                    source_priority INT NOT NULL DEFAULT 0,
                    first_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    crawled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    active TINYINT(1) NOT NULL DEFAULT 1,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_recruitment_fingerprint (fingerprint),
                    KEY idx_recruitment_published (published_date, id),
                    KEY idx_recruitment_first_seen (first_seen_at, id),
                    KEY idx_recruitment_type (recruitment_type),
                    KEY idx_recruitment_company_type (company_type),
                    KEY idx_recruitment_industry (industry),
                    KEY idx_recruitment_job_track (job_track),
                    KEY idx_recruitment_deadline (deadline_date),
                    KEY idx_recruitment_source_kind (source_kind),
                    KEY idx_recruitment_active_seen (active, last_seen_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        ensureColumn("recruitment_posting", "industry",
                "ALTER TABLE recruitment_posting ADD COLUMN industry VARCHAR(64) NOT NULL DEFAULT '其他行业' AFTER company_type");
        ensureColumn("recruitment_posting", "job_track",
                "ALTER TABLE recruitment_posting ADD COLUMN job_track VARCHAR(64) NOT NULL DEFAULT '综合岗位' AFTER industry");
        ensureColumn("recruitment_posting", "deadline_date",
                "ALTER TABLE recruitment_posting ADD COLUMN deadline_date DATE NULL AFTER deadline");
        ensureIndex("recruitment_posting", "idx_recruitment_industry",
                "ALTER TABLE recruitment_posting ADD KEY idx_recruitment_industry (industry)");
        ensureIndex("recruitment_posting", "idx_recruitment_job_track",
                "ALTER TABLE recruitment_posting ADD KEY idx_recruitment_job_track (job_track)");
        ensureIndex("recruitment_posting", "idx_recruitment_deadline",
                "ALTER TABLE recruitment_posting ADD KEY idx_recruitment_deadline (deadline_date)");
        ensureIndex("recruitment_posting", "idx_recruitment_source_kind",
                "ALTER TABLE recruitment_posting ADD KEY idx_recruitment_source_kind (source_kind)");
        ensureIndex("recruitment_posting", "idx_recruitment_active_seen",
                "ALTER TABLE recruitment_posting ADD KEY idx_recruitment_active_seen (active, last_seen_at)");
        backfillDerivedFields();

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recruitment_crawl_status (
                    id TINYINT UNSIGNED NOT NULL,
                    running TINYINT(1) NOT NULL DEFAULT 0,
                    last_started_at DATETIME NULL,
                    last_success_at DATETIME NULL,
                    last_error VARCHAR(1000) NOT NULL DEFAULT '',
                    last_inserted INT NOT NULL DEFAULT 0,
                    last_updated INT NOT NULL DEFAULT 0,
                    successful_sources INT NOT NULL DEFAULT 0,
                    failed_sources INT NOT NULL DEFAULT 0,
                    duration_ms BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.update("INSERT IGNORE INTO recruitment_crawl_status (id) VALUES (1)");
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column
        );
        if (Objects.requireNonNullElse(count, 0) == 0) jdbcTemplate.execute(ddl);
    }

    private void ensureIndex(String table, String index, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                table,
                index
        );
        if (Objects.requireNonNullElse(count, 0) == 0) jdbcTemplate.execute(ddl);
    }

    private void backfillDerivedFields() {
        List<DerivedRow> rows = jdbcTemplate.query(
                "SELECT id, title, positions, deadline, job_track, deadline_date FROM recruitment_posting "
                        + "WHERE job_track IS NULL OR job_track = '' OR job_track IN ('综合岗位', '软件研发') "
                        + "OR deadline_date IS NULL",
                (resultSet, rowNum) -> {
                    java.sql.Date deadlineDate = resultSet.getDate("deadline_date");
                    return new DerivedRow(
                            resultSet.getLong("id"),
                            resultSet.getString("title"),
                            resultSet.getString("positions"),
                            resultSet.getString("deadline"),
                            resultSet.getString("job_track"),
                            deadlineDate == null ? null : deadlineDate.toLocalDate()
                    );
                }
        );
        for (DerivedRow row : rows) {
            String jobTrack = row.jobTrack();
            if (jobTrack == null || jobTrack.isBlank()
                    || "综合岗位".equals(jobTrack) || "软件研发".equals(jobTrack)) {
                jobTrack = RecruitmentClassifier.jobTrack(row.title(), row.positions());
            }
            LocalDate deadlineDate = row.deadlineDate() == null
                    ? RecruitmentText.parseDeadlineDate(row.deadline())
                    : row.deadlineDate();
            if (!Objects.equals(jobTrack, row.jobTrack()) || !Objects.equals(deadlineDate, row.deadlineDate())) {
                jdbcTemplate.update(
                        "UPDATE recruitment_posting SET job_track = ?, deadline_date = ? WHERE id = ?",
                        jobTrack,
                        deadlineDate,
                        row.id()
                );
            }
        }
    }

    private record DerivedRow(
            long id,
            String title,
            String positions,
            String deadline,
            String jobTrack,
            LocalDate deadlineDate
    ) {
    }
}
