package org.example.snow.ai.web;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.domain.Chunk;
import org.example.snow.ai.application.EmbeddingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/embed")
public class EmbeddingTestController {

    private final EmbeddingService embeddingService;

    @PostMapping("/save")
    public String save(@RequestBody Chunk chunk) {
        embeddingService.saveEmbedding(chunk);
        return "저장 완료!";
    }
}