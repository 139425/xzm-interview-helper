package com.xzm.xzm_interview_helper.serveragent;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerAgentSchemaInitializerTest {
    @Test
    void createsApprovalAndAuditTablesIdempotently() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(1);

        new ServerAgentSchemaInitializer(jdbcTemplate).afterPropertiesSet();

        verify(jdbcTemplate, times(2)).execute(anyString());
    }
}
