package com.xzm.xzm_interview_helper.career;

import com.xzm.xzm_interview_helper.model.dto.AssessmentScheduleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class AssessmentScheduleRepository {
    public static final Set<String> EVENT_TYPES = Set.of("WRITTEN_TEST", "INTERVIEW", "ASSESSMENT");
    public static final int TRASH_RETENTION_DAYS = 14;

    private static final String SELECT_FIELDS = """
            id, company, role_name, event_type, start_at, end_at, event_url, notes,
            completed_at, deleted_at, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public record Schedule(
            long id,
            String company,
            String roleName,
            String eventType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String eventUrl,
            String notes,
            LocalDateTime completedAt,
            LocalDateTime deletedAt,
            LocalDateTime purgeAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public List<Schedule> findAll(int userId) {
        return jdbcTemplate.query("""
                SELECT %s
                FROM assessment_schedule
                WHERE user_id = ? AND deleted_at IS NULL
                ORDER BY
                    CASE WHEN completed_at IS NULL THEN 0 ELSE 1 END,
                    CASE WHEN completed_at IS NULL AND COALESCE(end_at, start_at) < NOW() THEN 0 ELSE 1 END,
                    CASE WHEN completed_at IS NULL AND COALESCE(end_at, start_at) < NOW() THEN start_at END DESC,
                    CASE WHEN completed_at IS NULL THEN start_at END ASC,
                    completed_at DESC,
                    id DESC
                LIMIT 1000
                """.formatted(SELECT_FIELDS), ROW_MAPPER, userId);
    }

    public List<Schedule> findTrash(int userId) {
        return jdbcTemplate.query("""
                SELECT %s
                FROM assessment_schedule
                WHERE user_id = ? AND deleted_at IS NOT NULL
                  AND deleted_at >= DATE_SUB(NOW(), INTERVAL %d DAY)
                ORDER BY deleted_at DESC, id DESC
                LIMIT 1000
                """.formatted(SELECT_FIELDS, TRASH_RETENTION_DAYS), ROW_MAPPER, userId);
    }

    public Map<String, Object> summary(int userId) {
        Map<String, Object> result = jdbcTemplate.queryForObject("""
                SELECT
                    SUM(CASE WHEN completed_at IS NULL THEN 1 ELSE 0 END) AS pending,
                    SUM(CASE WHEN completed_at IS NULL AND COALESCE(end_at, start_at) < NOW() THEN 1 ELSE 0 END) AS overdue,
                    SUM(CASE WHEN completed_at IS NULL AND DATE(start_at) = CURDATE() THEN 1 ELSE 0 END) AS due_today,
                    SUM(CASE WHEN completed_at IS NULL AND start_at >= NOW()
                             AND start_at < DATE_ADD(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS upcoming_week,
                    SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END) AS completed
                FROM assessment_schedule
                WHERE user_id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("pending", rs.getLong("pending"));
            values.put("overdue", rs.getLong("overdue"));
            values.put("dueToday", rs.getLong("due_today"));
            values.put("upcomingWeek", rs.getLong("upcoming_week"));
            values.put("completed", rs.getLong("completed"));
            return values;
        }, userId);
        return result == null ? Map.of() : result;
    }

    public Schedule create(int userId, AssessmentScheduleRequest request) {
        String eventType = requireEventType(request.getEventType());
        validateRange(request.getStartAt(), request.getEndAt());
        jdbcTemplate.update("""
                        INSERT INTO assessment_schedule (
                            user_id, company, role_name, event_type, start_at, end_at, event_url, notes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                required(request.getCompany(), 200, "公司不能为空"),
                clip(request.getRoleName(), 300),
                eventType,
                request.getStartAt(),
                request.getEndAt(),
                safeUrl(request.getEventUrl()),
                clip(request.getNotes(), 1_000)
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return findOwnedActive(userId, id == null ? 0 : id);
    }

    public Schedule setCompleted(int userId, long id, boolean completed) {
        jdbcTemplate.update(
                "UPDATE assessment_schedule SET completed_at = "
                        + (completed ? "COALESCE(completed_at, NOW())" : "NULL")
                        + " WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                id,
                userId
        );
        return findOwnedActive(userId, id);
    }

    public void softDelete(int userId, long id) {
        if (jdbcTemplate.update("""
                UPDATE assessment_schedule SET deleted_at = NOW()
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, id, userId) == 0) {
            throw notFound();
        }
    }

    public Schedule restore(int userId, long id) {
        if (jdbcTemplate.update("""
                UPDATE assessment_schedule SET deleted_at = NULL
                WHERE id = ? AND user_id = ? AND deleted_at IS NOT NULL
                  AND deleted_at >= DATE_SUB(NOW(), INTERVAL 14 DAY)
                """, id, userId) == 0) {
            throw notFound();
        }
        return findOwnedActive(userId, id);
    }

    public void permanentDelete(int userId, long id) {
        if (jdbcTemplate.update("""
                DELETE FROM assessment_schedule
                WHERE id = ? AND user_id = ? AND deleted_at IS NOT NULL
                """, id, userId) == 0) {
            throw notFound();
        }
    }

    public int purgeExpiredTrash() {
        return jdbcTemplate.update(
                "DELETE FROM assessment_schedule WHERE deleted_at IS NOT NULL "
                        + "AND deleted_at < DATE_SUB(NOW(), INTERVAL " + TRASH_RETENTION_DAYS + " DAY)"
        );
    }

    private Schedule findOwnedActive(int userId, long id) {
        List<Schedule> rows = jdbcTemplate.query("""
                        SELECT %s
                        FROM assessment_schedule
                        WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                        """.formatted(SELECT_FIELDS), ROW_MAPPER, id, userId);
        if (rows.isEmpty()) throw notFound();
        return rows.get(0);
    }

    private static final RowMapper<Schedule> ROW_MAPPER = (rs, rowNum) -> {
        LocalDateTime deletedAt = toLocalDateTime(rs.getTimestamp("deleted_at"));
        return new Schedule(
                rs.getLong("id"),
                rs.getString("company"),
                rs.getString("role_name"),
                rs.getString("event_type"),
                toLocalDateTime(rs.getTimestamp("start_at")),
                toLocalDateTime(rs.getTimestamp("end_at")),
                rs.getString("event_url"),
                rs.getString("notes"),
                toLocalDateTime(rs.getTimestamp("completed_at")),
                deletedAt,
                deletedAt == null ? null : deletedAt.plusDays(TRASH_RETENTION_DAYS),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    };

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String requireEventType(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase();
        if (!EVENT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的日程类型");
        }
        return normalized;
    }

    private static void validateRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始时间不能为空");
        }
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
    }

    private static String required(String value, int max, String message) {
        String clipped = clip(value, max);
        if (clipped.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return clipped;
    }

    private static String safeUrl(String value) {
        String clipped = clip(value, 2_048);
        if (clipped.isBlank()) return "";
        if (!clipped.startsWith("https://") && !clipped.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "链接必须以 http:// 或 https:// 开头");
        }
        return clipped;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String normalized = value.strip();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "日程不存在或已被清除");
    }
}
