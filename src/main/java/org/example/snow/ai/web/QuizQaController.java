package org.example.snow.ai.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.snow.ai.application.QuizQaService;
import org.example.snow.ai.web.dto.QuizQaHistoryResponse;
import org.example.snow.ai.web.dto.QuizQaRequest;
import org.example.snow.ai.web.dto.QuizQaResponse;
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
@RequestMapping("/api/quizzes/{quizId}/qa")
public class QuizQaController {

    private final QuizQaService quizQaService;

    @GetMapping
    public ResponseEntity<List<QuizQaHistoryResponse>> getHistories(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long quizId
    ) {
        List<QuizQaHistoryResponse> histories = quizQaService.getHistories(principal.userId(), quizId)
                .stream()
                .map(QuizQaHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(histories);
    }

    @PostMapping
    public ResponseEntity<QuizQaResponse> ask(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long quizId,
            @Valid @RequestBody QuizQaRequest request
    ) {
        return ResponseEntity.ok(QuizQaResponse.from(
                quizQaService.ask(principal.userId(), quizId, request.question())
        ));
    }
}
