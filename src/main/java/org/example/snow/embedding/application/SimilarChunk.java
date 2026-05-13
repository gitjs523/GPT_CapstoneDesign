package org.example.snow.embedding.application;

public record SimilarChunk(
        Long chunkId,
        String content,
        Long sectionId,
        double distance
) {}
