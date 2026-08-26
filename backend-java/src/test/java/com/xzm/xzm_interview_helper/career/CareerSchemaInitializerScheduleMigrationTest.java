package com.xzm.xzm_interview_helper.career;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerSchemaInitializerScheduleMigrationTest {
    @Test
    void existingScheduleTablesReceiveTrashAndEventUrlColumns() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        new CareerSchemaInitializer(jdbcTemplate).afterPropertiesSet();

        verify(jdbcTemplate).execute("ALTER TABLE assessment_schedule ADD COLUMN "
                + "event_url VARCHAR(2048) NOT NULL DEFAULT '' AFTER end_at");
        verify(jdbcTemplate).execute("ALTER TABLE assessment_schedule ADD COLUMN "
                + "deleted_at DATETIME NULL AFTER completed_at");
        verify(jdbcTemplate).execute("ALTER TABLE assessment_schedule ADD KEY "
                + "idx_schedule_user_deleted (user_id, deleted_at)");
    }
}
