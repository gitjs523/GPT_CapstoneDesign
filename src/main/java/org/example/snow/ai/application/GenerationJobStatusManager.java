package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationJobStatusManager {

    private final GenerationJobRepository generationJobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(Long jobId) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.start();
            generationJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.fail(0);
            generationJobRepository.save(job);
            log.error("Quiz generation failed and marked as FAILED. jobId={}", jobId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markComplete(Long jobId, int savedCount) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.complete(savedCount);
            generationJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPartialComplete(Long jobId, int savedCount) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.partialComplete(savedCount);
            generationJobRepository.save(job);
        });
    }
}
