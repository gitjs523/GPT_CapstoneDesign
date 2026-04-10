package org.example.snow.document.infra;

import org.example.snow.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByNotebook_NotebookIdOrderByUploadedAtAsc(Long notebookId);
}
