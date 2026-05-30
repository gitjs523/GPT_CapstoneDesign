package org.example.snow.ai.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaJobStoreTest {

    private static final long RETENTION_MINUTES = 30L;

    private final QaJobStore qaJobStore = new QaJobStore();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qaJobStore, "retentionMinutes", RETENTION_MINUTES);
    }

    @Test
    void evict_removesTerminalJobOlderThanRetention() {
        QaJob job = qaJobStore.create(1L, 10L);
        qaJobStore.markCompleted(job.getQaJobId(), new NotebookQaResult(1L, "답변", true, List.of(100L)));
        // 종료 시각을 retention 경계 밖(31분 전)으로 되돌린다.
        ReflectionTestUtils.setField(job, "finishedAt", LocalDateTime.now().minusMinutes(RETENTION_MINUTES + 1));

        qaJobStore.evictExpiredJobs();

        assertThat(qaJobStore.get(job.getQaJobId())).isEmpty();
    }

    @Test
    void evict_keepsTerminalJobWithinRetention() {
        QaJob job = qaJobStore.create(1L, 10L);
        qaJobStore.markFailed(job.getQaJobId());
        // 방금 종료된 job — finishedAt이 now에 가까우므로 유지되어야 한다.

        qaJobStore.evictExpiredJobs();

        assertThat(qaJobStore.get(job.getQaJobId())).isPresent();
    }

    @Test
    void evict_keepsInProgressJobRegardlessOfAge() {
        QaJob job = qaJobStore.create(1L, 10L);
        qaJobStore.markRunning(job.getQaJobId());
        // RUNNING은 종료 상태가 아니므로 finishedAt이 null — 나이와 무관하게 제거 대상이 아니다.

        qaJobStore.evictExpiredJobs();

        assertThat(qaJobStore.get(job.getQaJobId())).isPresent();
        assertThat(job.isTerminal()).isFalse();
        assertThat(job.getFinishedAt()).isNull();
    }
}
