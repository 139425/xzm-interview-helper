package com.xzm.xzm_interview_helper.career;

import com.xzm.xzm_interview_helper.model.dto.JobApplicationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

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
        when(jdbcTemplate.update(contains("WHERE id = ? AND user_id = ?"), any(Object[].class))).thenReturn(0);
        try {
            repository.update(12, 77L, request);
        } catch (org.springframework.web.server.ResponseStatusException ignored) {
            // Zero affected rows is expected; the SQL ownership invariant is what this test proves.
        }
        verify(jdbcTemplate).update(contains("WHERE id = ? AND user_id = ?"), any(Object[].class));
    }
}
