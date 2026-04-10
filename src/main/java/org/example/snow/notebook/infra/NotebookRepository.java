package org.example.snow.notebook.infra;

import org.example.snow.notebook.domain.Notebook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotebookRepository extends JpaRepository<Notebook, Long> {

    long countByUser_UserId(Long userId);

    List<Notebook> findAllByUser_UserIdOrderByCreatedAtAsc(Long userId);
}
