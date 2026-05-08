package org.example.snow.document.web.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.snow.document.application.DocumentUploadCommand;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Getter
@Setter
public class DocumentUploadRequest {

    private MultipartFile file;
    private ChunkStrategy chunkStrategy = ChunkStrategy.AUTO;

    public void setChunkStrategy(ChunkStrategy chunkStrategy) {
        this.chunkStrategy = chunkStrategy == null ? ChunkStrategy.AUTO : chunkStrategy;
    }

    public DocumentUploadCommand toCommand() {
        try {
            UploadedDocument uploadedDocument = file == null ? null : new UploadedDocument(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
            return new DocumentUploadCommand(uploadedDocument, chunkStrategy);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.UPLOADED_FILE_READ_FAILED, exception);
        }
    }
}
