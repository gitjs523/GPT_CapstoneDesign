package org.example.snow.notebook.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.example.snow.user.infra.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotebookService {

    private final NotebookRepository notebookRepository;
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public List<Notebook> getNotebooks(Long userId) {
        return notebookRepository.findAllByUser_UserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional
    public Notebook createNotebook(Long userId, String title) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return notebookRepository.save(Notebook.create(user, title));
    }

    @Transactional(readOnly = true)
    public Notebook getNotebook(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findById(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        validateOwnership(notebook, userId);
        return notebook;
    }

    @Transactional
    public Notebook updateNotebook(Long userId, Long notebookId, String title) {
        Notebook notebook = notebookRepository.findById(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        validateOwnership(notebook, userId);
        notebook.rename(title);
        return notebook;
    }

    @Transactional
    public void deleteNotebook(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findById(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        validateOwnership(notebook, userId);
        notebookRepository.delete(notebook);
    }

    private void validateOwnership(Notebook notebook, Long userId) {
        if (!notebook.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTEBOOK_ACCESS_DENIED);
        }
    }
}
