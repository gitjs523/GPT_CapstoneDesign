package org.example.snow.ai.infra;

import org.example.snow.ai.domain.NotebookQaHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotebookQaHistoryRepository extends JpaRepository<NotebookQaHistory, Long> {

    List<NotebookQaHistory> findAllByNotebook_NotebookIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long notebookId,
            Long userId
    );
}
