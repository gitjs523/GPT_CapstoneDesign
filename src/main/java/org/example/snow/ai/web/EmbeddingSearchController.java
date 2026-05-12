package org.example.snow.ai.web;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.application.EmbeddingSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/embed")
@RequiredArgsConstructor
public class EmbeddingSearchController {

    private final EmbeddingSearchService embeddingSearchService;

    /**
     * 질문 기반 유사 Chunk 검색 API
     */
    @PostMapping("/search")
    public List<Map<String, Object>> searchSimilarChunks(
            @RequestBody SearchRequest request
    ) {
        return embeddingSearchService.searchSimilarChunks(request.getQuestion());
    }

    /**
     * request DTO (같은 파일 내부 or 따로 분리 가능)
     */
    public static class SearchRequest {
        private String question;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }
}