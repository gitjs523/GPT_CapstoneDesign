package org.example.snow.document.application.port;

public interface FileStorageService {
    String upload(byte[] content, String contentType, String originalFilename, Long notebookId);
}
