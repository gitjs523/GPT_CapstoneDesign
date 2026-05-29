package org.example.snow.global.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.ai.application.GenerationJobStatusManager;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.GenerationJobStatus;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.document.application.DocumentAnalysisStatusManager;
import org.example.snow.document.domain.AnalysisStatus;
import org.example.snow.document.domain.Document;
import org.example.snow.document.infra.DocumentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 서버 재시작 시 중단된 작업을 FAILED로 처리한다.
 *
 * 서버가 비정상 종료되면 큐에 대기 중이던 task가 소실되어
 * 분석/생성 파이프라인을 이어갈 수 없다.
 * 재시작 후 ANALYZING/QUEUED/RUNNING 상태로 남은 레코드는
 * 클라이언트 입장에서 영구 stuck이 되므로 즉시 FAILED 처리한다.
 *
 * 처리 시점: Spring 컨텍스트 완전 초기화 직후, 큐에 새 작업이 들어오기 전.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRecoveryRunner implements ApplicationRunner {

    private static final String DOCUMENT_RECOVERY_REASON =
            "서버 재시작으로 분석이 중단되었습니다. 문서를 다시 업로드해주세요.";

    private final DocumentRepository documentRepository;
    private final GenerationJobRepository generationJobRepository;
    private final DocumentAnalysisStatusManager documentAnalysisStatusManager;
    private final GenerationJobStatusManager generationJobStatusManager;

    @Override
    public void run(ApplicationArguments args) {
        recoverAnalyzingDocuments();
        recoverStuckGenerationJobs();
    }

    /**
     * ANALYZING / SUMMARIZING 상태로 중단된 문서를 FAILED + soft delete 처리한다.
     * DocumentAnalysisStatusManager.markFailed()를 재사용하므로
     * section/chunk soft delete 및 embedding NULL 초기화도 함께 수행된다.
     */
    private void recoverAnalyzingDocuments() {
        List<Document> stuck = documentRepository
                .findAllByAnalysisStatusInAndDeletedAtIsNull(
                        List.of(AnalysisStatus.ANALYZING, AnalysisStatus.SUMMARIZING));

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("[startup-recovery] ANALYZING/SUMMARIZING 상태 문서 {}개 감지 — FAILED 처리 시작", stuck.size());
        for (Document doc : stuck) {
            try {
                documentAnalysisStatusManager.markFailed(doc.getDocumentId(), DOCUMENT_RECOVERY_REASON);
                log.warn("[startup-recovery] documentId={} FAILED 처리 완료", doc.getDocumentId());
            } catch (Exception e) {
                log.error("[startup-recovery] documentId={} FAILED 처리 실패", doc.getDocumentId(), e);
            }
        }
    }

    /**
     * QUEUED/RUNNING 상태로 중단된 generation_job을 FAILED 처리한다.
     * 큐 task가 소실되어 재개 불가능하므로 실패 확정한다.
     */
    private void recoverStuckGenerationJobs() {
        List<GenerationJob> stuck = generationJobRepository
                .findAllByStatusInAndDeletedAtIsNull(
                        List.of(GenerationJobStatus.QUEUED, GenerationJobStatus.RUNNING)
                );

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("[startup-recovery] QUEUED/RUNNING 상태 generation_job {}개 감지 — FAILED 처리 시작", stuck.size());
        for (GenerationJob job : stuck) {
            try {
                generationJobStatusManager.markFailed(job.getJobId());
                log.warn("[startup-recovery] jobId={} FAILED 처리 완료", job.getJobId());
            } catch (Exception e) {
                log.error("[startup-recovery] jobId={} FAILED 처리 실패", job.getJobId(), e);
            }
        }
    }
}
