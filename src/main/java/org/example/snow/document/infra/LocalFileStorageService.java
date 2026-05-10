package org.example.snow.document.infra;

import org.example.snow.document.application.port.FileStorageService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("!prod")
@Service
public class LocalFileStorageService implements FileStorageService {

    @Override
    public String upload(byte[] content, String contentType, String originalFilename, Long notebookId) {
        return originalFilename;
    }
}
