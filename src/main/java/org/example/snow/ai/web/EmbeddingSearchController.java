package org.example.snow.ai.web;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.application.EmbeddingSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class EmbeddingSearchController {

    private final EmbeddingSearchService embeddingSearchService;

    @PostMapping
    public List<String> search(@RequestBody SearchRequest request) {
        return embeddingSearchService.searchSimilarChunks(request.getQuery());
    }

    public static class SearchRequest {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}