package com.xzm.xzm_interview_helper.career;

import com.xzm.xzm_interview_helper.model.dto.AssessmentScheduleRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentScheduleRepositoryTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AssessmentScheduleRepository repository;

    @Test
    void listKeepsPendingAndNearestTasksAtTheTop() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        repository.findAll(12);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("completed_at IS NULL THEN 0 ELSE 1"));
        assertTrue(sql.getValue().contains("COALESCE(end_at, start_at) < NOW()"));
        assertTrue(sql.getValue().contains("THEN start_at END ASC"));
    }

    @Test
    void completionAndDeletionAreAlwaysUserScoped() {
        when(jdbcTemplate.update(
                contains("completed_at = NULL WHERE id = ? AND user_id = ?"),
                eq(77L), eq(12)
        )).thenReturn(0);
        assertThrows(ResponseStatusException.class, () -> repository.setCompleted(12, 77L, false));

        when(jdbcTemplate.update(
                contains("DELETE FROM assessment_schedule WHERE id = ? AND user_id = ?"),
                eq(77L), eq(12)
        )).thenReturn(1);
        repository.delete(12, 77L);

        verify(jdbcTemplate).update(
                contains("completed_at = NULL WHERE id = ? AND user_id = ?"),
                eq(77L), eq(12)
        );
        verify(jdbcTemplate).update(
                contains("DELETE FROM assessment_schedule WHERE id = ? AND user_id = ?"),
                eq(77L), eq(12)
        );
    }

    @Test
    void createRejectsUnsupportedTypesAndInvalidRanges() {
        AssessmentScheduleRequest unsupported = request();
        unsupported.setEventType("PHONE_CALL");
        ResponseStatusException typeError = assertThrows(
                ResponseStatusException.class,
                () -> repository.create(12, unsupported)
        );
        assertEquals(HttpStatus.BAD_REQUEST, typeError.getStatusCode());

        AssessmentScheduleRequest invalidRange = request();
        invalidRange.setEndAt(invalidRange.getStartAt().minusMinutes(1));
        ResponseStatusException rangeError = assertThrows(
                ResponseStatusException.class,
                () -> repository.create(12, invalidRange)
        );
        assertEquals(HttpStatus.BAD_REQUEST, rangeError.getStatusCode());
    }

    private AssessmentScheduleRequest request() {
        AssessmentScheduleRequest request = new AssessmentScheduleRequest();
        request.setCompany("Example");
        request.setRoleName("");
        request.setEventType("INTERVIEW");
        request.setStartAt(LocalDateTime.of(2026, 8, 25, 10, 0));
        return request;
    }
}
