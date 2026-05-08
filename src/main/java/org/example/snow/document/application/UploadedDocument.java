package org.example.snow.document.application;

public record UploadedDocument(
        String originalFilename,
        String contentType,
        byte[] content
) {

    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}
