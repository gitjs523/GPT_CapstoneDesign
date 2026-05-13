package org.example.snow.ai.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.snow.ai.application.NotebookQaService;
import org.example.snow.ai.web.dto.NotebookQaHistoryResponse;
import org.example.snow.ai.web.dto.NotebookQaRequest;
import org.example.snow.ai.web.dto.NotebookQaResponse;
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
@RequestMapping("/api/notebooks/{notebookId}/qa")
public class NotebookQaController {

    private final NotebookQaService notebookQaService;

    @GetMapping
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

    @PostMapping
    public ResponseEntity<NotebookQaResponse> ask(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @Valid @RequestBody NotebookQaRequest request
    ) {
        return ResponseEntity.ok(NotebookQaResponse.from(
                notebookQaService.ask(principal.userId(), notebookId, request.question())
        ));
    }
}
