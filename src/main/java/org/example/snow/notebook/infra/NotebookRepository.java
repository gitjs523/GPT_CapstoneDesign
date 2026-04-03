package org.example.snow.notebook.infra;

import org.example.snow.notebook.domain.Notebook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotebookRepository extends JpaRepository<Notebook, Long> {

    long countByUser_UserId(Long userId);
}
