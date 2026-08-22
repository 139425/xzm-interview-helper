package com.xzm.xzm_interview_helper.recruitment;

import com.xzm.xzm_interview_helper.controller.RecruitmentController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentControllerTest {
    @Mock
    private RecruitmentPostingRepository repository;

    @InjectMocks
    private RecruitmentController controller;

    @Test
    void boundsPublicPaginationBeforeQueryingTheDatabase() {
        when(repository.findPage(10_000, 50, "", "", "", "", false, "", "", "", "", 0, 0, false, "latest"))
                .thenReturn(Map.of("items", java.util.List.of(), "total", 0));

        controller.list(
                Integer.MAX_VALUE, Integer.MAX_VALUE, "", "", "", "", false,
                "", "", "", "", 0, 0, false, "latest"
        );

        verify(repository).findPage(
                eq(10_000), eq(50), eq(""), eq(""), eq(""), eq(""), eq(false),
                eq(""), eq(""), eq(""), eq(""), eq(0), eq(0), eq(false), eq("latest")
        );
    }

    @Test
    void forwardsJobTrackAndDeadlineFilters() {
        when(repository.findPage(
                1, 30, "", "", "", "", false,
                "", "AI应用/Agent", "", "", 0, 14, false, "deadline"
        )).thenReturn(Map.of("items", java.util.List.of(), "total", 0));

        controller.list(
                1, 30, "", "", "", "", false,
                "", " AI应用/Agent ", "", "", 0, 14, false, "deadline"
        );

        verify(repository).findPage(
                1, 30, "", "", "", "", false,
                "", "AI应用/Agent", "", "", 0, 14, false, "deadline"
        );
    }
}
