package com.xzm.xzm_interview_helper.career;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleTrashCleanupService {
    private final AssessmentScheduleRepository schedules;

    @Scheduled(
            initialDelayString = "${app.schedule.trash-cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${app.schedule.trash-cleanup-delay-ms:3600000}"
    )
    public void purgeExpiredTrash() {
        int purged = schedules.purgeExpiredTrash();
        if (purged > 0) {
            log.info("Purged {} assessment schedules that exceeded the 14-day trash retention", purged);
        }
    }
}
