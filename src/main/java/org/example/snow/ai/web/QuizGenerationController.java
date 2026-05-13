package org.example.snow.ai.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.snow.ai.application.QuizGenerationService;
import org.example.snow.ai.web.dto.GeneratedQuizResponse;
import org.example.snow.ai.web.dto.QuizGenerationJobResponse;
import org.example.snow.ai.web.dto.QuizGenerationRequest;
import org.example.snow.auth.security.AuthenticatedUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class QuizGenerationController {

    private final QuizGenerationService quizGenerationService;

    @PostMapping("/notebooks/{notebookId}/quiz-jobs")
    public ResponseEntity<QuizGenerationJobResponse> createQuizJob(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @Valid @RequestBody QuizGenerationRequest request
    ) {
        QuizGenerationJobResponse response = QuizGenerationJobResponse.from(
                quizGenerationService.generate(principal.userId(), notebookId, request.toCommand())
        );
        return ResponseEntity.created(URI.create("/api/quiz-jobs/" + response.jobId()))
                .body(response);
    }

    @GetMapping("/quiz-jobs/{jobId}")
    public ResponseEntity<QuizGenerationJobResponse> getQuizJob(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(QuizGenerationJobResponse.from(
                quizGenerationService.getJob(principal.userId(), jobId)
        ));
    }

    @GetMapping("/quiz-jobs/{jobId}/quizzes")
    public ResponseEntity<List<GeneratedQuizResponse>> getQuizzes(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long jobId
    ) {
        List<GeneratedQuizResponse> quizzes = quizGenerationService.getQuizzes(principal.userId(), jobId)
                .stream()
                .map(GeneratedQuizResponse::from)
                .toList();
        return ResponseEntity.ok(quizzes);
    }
}
