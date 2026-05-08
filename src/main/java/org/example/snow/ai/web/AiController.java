package org.example.snow.ai.web;

import org.example.snow.ai.application.OllamaService;
import org.example.snow.ai.web.dto.GenerateAnswerRequest;
import org.example.snow.ai.web.dto.GenerateSectionSummaryRequest;
import org.example.snow.ai.web.dto.GenerateSummaryRequest;
import org.example.snow.ai.web.dto.GeneratedAnswerResponse;
import org.example.snow.ai.web.dto.GeneratedSectionSummaryResponse;
import org.example.snow.ai.web.dto.GeneratedSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class AiController {

    private final OllamaService ollamaService;

    public AiController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @GetMapping("/api/ai/ask")
    public String ask(@RequestParam String q) {
        return ollamaService.ask(q);
    }

    @PostMapping("/api/ai/answers")
    public ResponseEntity<GeneratedAnswerResponse> generateAnswer(@Valid @RequestBody GenerateAnswerRequest request) {
        return ResponseEntity.ok(GeneratedAnswerResponse.from(ollamaService.generateGroundedAnswer(request.toCommand())));
    }

    @PostMapping("/api/ai/summaries")
    public ResponseEntity<GeneratedSummaryResponse> generateSummary(
            @Valid @RequestBody GenerateSummaryRequest request
    ) {
        return ResponseEntity.ok(
                GeneratedSummaryResponse.from(
                        ollamaService.generateSummary(request.content())
                )
        );
    }

    @PostMapping("/api/ai/section-summaries")
    public ResponseEntity<GeneratedSectionSummaryResponse> generateSectionSummary(
            @Valid @RequestBody GenerateSectionSummaryRequest request
    ) {
        return ResponseEntity.ok(
                GeneratedSectionSummaryResponse.from(
                        ollamaService.generateSectionSummary(request.topic(), request.content())
                )
        );
    }
}
