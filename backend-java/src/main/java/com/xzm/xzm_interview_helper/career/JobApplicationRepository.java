package com.xzm.xzm_interview_helper.career;

import com.xzm.xzm_interview_helper.model.dto.JobApplicationRequest;
import com.xzm.xzm_interview_helper.recruitment.RecruitmentPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class JobApplicationRepository {
    public static final String TO_APPLY = "TO_APPLY";
    public static final Set<String> STATUSES = Set.of(
            TO_APPLY, "APPLIED", "ASSESSMENT", "INTERVIEW_1", "INTERVIEW_2", "INTERVIEW_3",
            "INTERVIEW_FINAL", "HR_INTERVIEW", "NEGOTIATION", "OFFER", "REJECTED", "WITHDRAWN"
    );

    private final JdbcTemplate jdbcTemplate;

    public record Application(
            long id,
            Long recruitmentPostingId,
            String company,
            String roleName,
            String status,
            String location,
            String applyUrl,
            String sourceUrl,
            LocalDate deadline,
            String nextAction,
            LocalDateTime nextActionAt,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public List<Application> findAll(int userId, List<String> statuses, String keyword, String sort) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, recruitment_posting_id, company, role_name, status, location, apply_url, source_url,
                       deadline, next_action, next_action_at, notes, created_at, updated_at
                FROM job_application WHERE user_id = ?
                """);
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(userId);
        List<String> validStatuses = statuses == null ? List.of() : statuses.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(JobApplicationRepository::requireStatus)
                .distinct()
                .toList();
        if (!validStatuses.isEmpty()) {
            sql.append(" AND status IN (");
            sql.append(String.join(",", java.util.Collections.nCopies(validStatuses.size(), "?")));
            sql.append(")");
            params.addAll(validStatuses);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (company LIKE ? OR role_name LIKE ? OR notes LIKE ?)");
            String like = "%" + clip(keyword, 100) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(switch (sort == null ? "progress" : sort) {
            case "updated" -> " ORDER BY updated_at DESC, id DESC";
            case "company" -> " ORDER BY company ASC, updated_at DESC, id DESC";
            default -> """
                     ORDER BY CASE status
                       WHEN 'OFFER' THEN 110
                       WHEN 'NEGOTIATION' THEN 100
                       WHEN 'HR_INTERVIEW' THEN 90
                       WHEN 'INTERVIEW_FINAL' THEN 80
                       WHEN 'INTERVIEW_3' THEN 70
                       WHEN 'INTERVIEW_2' THEN 60
                       WHEN 'INTERVIEW_1' THEN 50
                       WHEN 'ASSESSMENT' THEN 40
                       WHEN 'APPLIED' THEN 30
                       WHEN 'TO_APPLY' THEN 20
                       WHEN 'REJECTED' THEN 10
                       WHEN 'WITHDRAWN' THEN 0
                       ELSE -1
                     END DESC, updated_at DESC, id DESC
                    """;
        });
        sql.append(" LIMIT 1000");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Map<String, Object> summary(int userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String status : STATUSES) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM job_application WHERE user_id = ? AND status = ?",
                    Long.class,
                    userId,
                    status
            );
            result.put(status, count == null ? 0 : count);
        }
        Long reminders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_application WHERE user_id = ? AND next_action_at IS NOT NULL "
                        + "AND next_action_at <= DATE_ADD(NOW(), INTERVAL 7 DAY) AND status NOT IN ('OFFER', 'REJECTED', 'WITHDRAWN')",
                Long.class,
                userId
        );
        result.put("upcomingReminders", reminders == null ? 0 : reminders);
        return result;
    }

    public Application createManual(int userId, JobApplicationRequest request) {
        jdbcTemplate.update("""
                        INSERT INTO job_application (
                            user_id, company, role_name, status, location, apply_url, source_url,
                            deadline, next_action, next_action_at, notes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                required(request.getCompany(), 200, "公司不能为空"),
                clip(request.getRoleName(), 300),
                requireStatus(request.getStatus()),
                clip(request.getLocation(), 300),
                requiredUrl(request.getApplyUrl()),
                safeUrl(request.getSourceUrl()),
                request.getDeadline(),
                clip(request.getNextAction(), 500),
                request.getNextActionAt(),
                clip(request.getNotes(), 10_000)
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return findOwned(userId, id == null ? 0 : id);
    }

    @Transactional
    public Application createFromRecruitment(int userId, RecruitmentPostingRepository.Posting posting) {
        String applyUrl = firstUrl(posting.applyUrl(), posting.announcementUrl(), posting.sourceUrl());
        try {
            jdbcTemplate.update("""
                            INSERT INTO job_application (
                                user_id, recruitment_posting_id, company, role_name, status, location,
                                apply_url, source_url, deadline, notes
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    userId,
                    posting.id(),
                    posting.company(),
                    posting.title(),
                    TO_APPLY,
                    posting.locations(),
                    requiredUrl(applyUrl),
                    safeUrl(posting.announcementUrl().isBlank() ? posting.sourceUrl() : posting.announcementUrl()),
                    posting.deadlineDate(),
                    "来源：" + posting.sourceName()
            );
        } catch (DuplicateKeyException duplicate) {
            // One-click add is idempotent: return the existing user-owned record.
        }
        return jdbcTemplate.queryForObject(
                "SELECT id, recruitment_posting_id, company, role_name, status, location, apply_url, source_url, "
                        + "deadline, next_action, next_action_at, notes, created_at, updated_at "
                        + "FROM job_application WHERE user_id = ? AND recruitment_posting_id = ?",
                ROW_MAPPER,
                userId,
                posting.id()
        );
    }

    public Application update(int userId, long id, JobApplicationRequest request) {
        int changed = jdbcTemplate.update("""
                        UPDATE job_application SET company = ?, role_name = ?, status = ?, location = ?,
                            apply_url = ?, source_url = ?, deadline = ?, next_action = ?, next_action_at = ?, notes = ?
                        WHERE id = ? AND user_id = ?
                        """,
                required(request.getCompany(), 200, "公司不能为空"),
                clip(request.getRoleName(), 300),
                requireStatus(request.getStatus()),
                clip(request.getLocation(), 300),
                requiredUrl(request.getApplyUrl()),
                safeUrl(request.getSourceUrl()),
                request.getDeadline(),
                clip(request.getNextAction(), 500),
                request.getNextActionAt(),
                clip(request.getNotes(), 10_000),
                id,
                userId
        );
        if (changed == 0) throw notFound();
        return findOwned(userId, id);
    }

    public Application updateStatus(int userId, long id, String status) {
        if (jdbcTemplate.update(
                "UPDATE job_application SET status = ? WHERE id = ? AND user_id = ?",
                requireStatus(status), id, userId
        ) == 0) {
            throw notFound();
        }
        return findOwned(userId, id);
    }

    public void delete(int userId, long id) {
        if (jdbcTemplate.update("DELETE FROM job_application WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw notFound();
        }
    }

    private Application findOwned(int userId, long id) {
        List<Application> rows = jdbcTemplate.query(
                "SELECT id, recruitment_posting_id, company, role_name, status, location, apply_url, source_url, "
                        + "deadline, next_action, next_action_at, notes, created_at, updated_at "
                        + "FROM job_application WHERE id = ? AND user_id = ?",
                ROW_MAPPER,
                id,
                userId
        );
        if (rows.isEmpty()) throw notFound();
        return rows.get(0);
    }

    private static final RowMapper<Application> ROW_MAPPER = (rs, rowNum) -> {
        long rawPostingId = rs.getLong("recruitment_posting_id");
        return new Application(
                rs.getLong("id"),
                rs.wasNull() ? null : rawPostingId,
                rs.getString("company"),
                rs.getString("role_name"),
                rs.getString("status"),
                rs.getString("location"),
                rs.getString("apply_url"),
                rs.getString("source_url"),
                rs.getDate("deadline") == null ? null : rs.getDate("deadline").toLocalDate(),
                rs.getString("next_action"),
                toLocalDateTime(rs.getTimestamp("next_action_at")),
                rs.getString("notes"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    };

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String requireStatus(String status) {
        String normalized = status == null || status.isBlank() ? TO_APPLY : status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的投递状态");
        }
        return normalized;
    }

    private static String required(String value, int max, String message) {
        String clipped = clip(value, max);
        if (clipped.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return clipped;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String normalized = value.strip();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static String safeUrl(String value) {
        String clipped = clip(value, 1024);
        if (clipped.isBlank()) return "";
        if (!clipped.startsWith("https://") && !clipped.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "链接必须以 http:// 或 https:// 开头");
        }
        return clipped;
    }

    private static String requiredUrl(String value) {
        String url = safeUrl(value);
        if (url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投递链接不能为空");
        }
        return url;
    }

    private static String firstUrl(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "投递记录不存在");
    }
}
