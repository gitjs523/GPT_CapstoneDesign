package org.example.snow.notebook.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.snow.auth.security.AuthenticatedUserPrincipal;
import org.example.snow.notebook.application.NotebookService;
import org.example.snow.notebook.web.dto.CreateNotebookRequest;
import org.example.snow.notebook.web.dto.NotebookResponse;
import org.example.snow.notebook.web.dto.UpdateNotebookRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notebooks")
public class NotebookController {

    private final NotebookService notebookService;

    @GetMapping
    public ResponseEntity<List<NotebookResponse>> getNotebooks(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        List<NotebookResponse> notebooks = notebookService.getNotebooks(principal.userId())
                .stream()
                .map(NotebookResponse::from)
                .toList();
        return ResponseEntity.ok(notebooks);
    }

    @PostMapping
    public ResponseEntity<NotebookResponse> createNotebook(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody @Valid CreateNotebookRequest request) {
        NotebookResponse response = NotebookResponse.from(
                notebookService.createNotebook(principal.userId(), request.title())
        );
        return ResponseEntity.created(URI.create("/api/notebooks/" + response.notebookId()))
                .body(response);
    }

    @GetMapping("/{notebookId}")
    public ResponseEntity<NotebookResponse> getNotebook(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId) {
        return ResponseEntity.ok(NotebookResponse.from(
                notebookService.getNotebook(principal.userId(), notebookId)
        ));
    }

    @PatchMapping("/{notebookId}")
    public ResponseEntity<NotebookResponse> updateNotebook(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @RequestBody @Valid UpdateNotebookRequest request) {
        return ResponseEntity.ok(NotebookResponse.from(
                notebookService.updateNotebook(principal.userId(), notebookId, request.title())
        ));
    }

    @DeleteMapping("/{notebookId}")
    public ResponseEntity<Void> deleteNotebook(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId) {
        notebookService.deleteNotebook(principal.userId(), notebookId);
        return ResponseEntity.noContent().build();
    }
}
