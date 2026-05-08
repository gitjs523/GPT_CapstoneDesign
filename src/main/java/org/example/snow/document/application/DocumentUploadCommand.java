package org.example.snow.document.application;

import org.example.snow.document.domain.ChunkStrategy;

public record DocumentUploadCommand(
        UploadedDocument file,
        ChunkStrategy chunkStrategy
) {

    public DocumentUploadCommand {
        if (chunkStrategy == null) {
            chunkStrategy = ChunkStrategy.AUTO;
        }
    }
}
