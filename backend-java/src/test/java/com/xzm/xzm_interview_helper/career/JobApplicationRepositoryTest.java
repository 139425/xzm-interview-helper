package com.xzm.xzm_interview_helper.career;

import com.xzm.xzm_interview_helper.model.dto.JobApplicationRequest;
import com.xzm.xzm_interview_helper.recruitment.RecruitmentPostingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
class JobApplicationRepositoryTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private JobApplicationRepository repository;

    @Test
    void deleteAlwaysScopesByAuthenticatedUser() {
        when(jdbcTemplate.update(any(String.class), eq(77L), eq(12))).thenReturn(1);
        repository.delete(12, 77L);
        verify(jdbcTemplate).update(contains("id = ? AND user_id = ?"), eq(77L), eq(12));
    }

    @Test
    void updateAlwaysScopesByAuthenticatedUser() {
        JobApplicationRequest request = new JobApplicationRequest();
        request.setCompany("Example");
        request.setRoleName("Java Engineer");
        request.setStatus("APPLIED");
        request.setApplyUrl("https://jobs.example.com/apply");
        when(jdbcTemplate.update(contains("WHERE id = ? AND user_id = ?"), any(Object[].class))).thenReturn(0);
        try {
            repository.update(12, 77L, request);
        } catch (org.springframework.web.server.ResponseStatusException ignored) {
            // Zero affected rows is expected; the SQL ownership invariant is what this test proves.
        }
        verify(jdbcTemplate).update(contains("WHERE id = ? AND user_id = ?"), any(Object[].class));
    }

    @Test
    void applicationRequiresCompanyAndApplyUrlButNotRole() {
        JobApplicationRequest missingLink = new JobApplicationRequest();
        missingLink.setCompany("Example");
        missingLink.setRoleName("");
        missingLink.setStatus("APPLIED");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> repository.update(12, 77L, missingLink)
        );
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());

        JobApplicationRequest optionalRole = new JobApplicationRequest();
        optionalRole.setCompany("Example");
        optionalRole.setRoleName("");
        optionalRole.setStatus("INTERVIEW_2");
        optionalRole.setApplyUrl("https://jobs.example.com/apply");
        when(jdbcTemplate.update(contains("WHERE id = ? AND user_id = ?"), any(Object[].class))).thenReturn(0);

        ResponseStatusException notFound = assertThrows(
                ResponseStatusException.class,
                () -> repository.update(12, 77L, optionalRole)
        );
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
    }

    @Test
    void statusUpdatesAreUserScopedAndSupportDetailedInterviewStages() {
        assertTrue(JobApplicationRepository.STATUSES.contains("INTERVIEW_1"));
        assertTrue(JobApplicationRepository.STATUSES.contains("INTERVIEW_2"));
        assertTrue(JobApplicationRepository.STATUSES.contains("INTERVIEW_3"));
        assertTrue(JobApplicationRepository.STATUSES.contains("HR_INTERVIEW"));
        assertTrue(JobApplicationRepository.STATUSES.contains("NEGOTIATION"));

        when(jdbcTemplate.update(any(String.class), eq("INTERVIEW_3"), eq(77L), eq(12))).thenReturn(0);
        assertThrows(ResponseStatusException.class, () -> repository.updateStatus(12, 77L, "INTERVIEW_3"));
        verify(jdbcTemplate).update(
                contains("status = ? WHERE id = ? AND user_id = ?"),
                eq("INTERVIEW_3"), eq(77L), eq(12)
        );
    }

    @Test
    void creatingFromRecruitmentCarriesTheStructuredDeadline() {
        LocalDate deadline = LocalDate.of(2026, 9, 30);
        RecruitmentPostingRepository.Posting posting = new RecruitmentPostingRepository.Posting(
                9L, "Example", "AI Agent Engineer", "民企", "IT/互联网", "AI应用/Agent",
                "杭州", "Agent platform", "秋招", "2027届", LocalDate.of(2026, 8, 20),
                "2026-09-30", deadline, "https://jobs.example.com/apply", "https://jobs.example.com/notice",
                "Example招聘官网", "https://jobs.example.com/", "OFFICIAL", 100,
                LocalDateTime.of(2026, 8, 20, 10, 0), LocalDateTime.of(2026, 8, 20, 10, 0)
        );

        repository.createFromRecruitment(12, posting);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO job_application"), values.capture());
        assertEquals(deadline, values.getValue()[8]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSupportsMultiStatusAndDefaultsToProgressFirstOrdering() {
        when(jdbcTemplate.query(
                any(String.class),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.of());

        repository.findAll(12, List.of("APPLIED", "TO_APPLY"), "", "progress");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("status IN (?,?)"));
        assertTrue(sql.getValue().contains("WHEN 'OFFER' THEN 110"));
        assertTrue(sql.getValue().contains("WHEN 'INTERVIEW_2' THEN 60"));
        assertTrue(sql.getValue().contains("END DESC, updated_at DESC"));
    }
}
