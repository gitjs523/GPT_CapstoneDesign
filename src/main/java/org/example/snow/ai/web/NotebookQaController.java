package org.example.snow.ai.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.snow.ai.application.NotebookQaService;
import org.example.snow.ai.web.dto.NotebookQaHistoryResponse;
import org.example.snow.ai.web.dto.NotebookQaRequest;
import org.example.snow.ai.web.dto.QaJobResponse;
import org.example.snow.auth.security.AuthenticatedUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notebooks/{notebookId}")
public class NotebookQaController {

    private final NotebookQaService notebookQaService;

    /**
     * #30 노트북 Q&A 이력 조회
     */
    @GetMapping("/qa")
    public ResponseEntity<List<NotebookQaHistoryResponse>> getHistories(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId
    ) {
        List<NotebookQaHistoryResponse> histories = notebookQaService.getHistories(principal.userId(), notebookId)
                .stream()
                .map(NotebookQaHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(histories);
    }

    /**
     * #24 질문 입력 — 생성 큐 등록 후 202 Accepted + qaJobId 즉시 반환.
     * 클라이언트는 GET /qa-jobs/{qaJobId} 를 polling하여 완료 여부를 확인한다.
     */
    @PostMapping("/qa")
    public ResponseEntity<QaJobResponse> ask(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @Valid @RequestBody NotebookQaRequest request
    ) {
        return ResponseEntity.accepted().body(
                QaJobResponse.from(notebookQaService.ask(principal.userId(), notebookId, request.question()))
        );
    }

    /**
     * #24-1 Q&A 작업 상태 조회 (polling)
     * QUEUED / RUNNING 이면 상태만 반환, COMPLETED 이면 answer + citedSectionIds 포함.
     */
    @GetMapping("/qa-jobs/{qaJobId}")
    public ResponseEntity<QaJobResponse> getQaJob(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @PathVariable String qaJobId
    ) {
        return ResponseEntity.ok(
                QaJobResponse.from(notebookQaService.getQaJob(principal.userId(), notebookId, qaJobId))
        );
    }
}
