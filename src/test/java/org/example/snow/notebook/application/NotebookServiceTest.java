package org.example.snow.notebook.application;

import org.example.snow.document.application.DocumentService;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.example.snow.user.infra.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotebookServiceTest {

    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final DocumentService documentService = mock(DocumentService.class);

    private final NotebookService notebookService = new NotebookService(
            notebookRepository,
            userAccountRepository,
            documentService
    );

    // ───────────────────────────── getNotebooks ─────────────────────────────

    @Test
    void getNotebooks_returnsUsersNotebooks() {
        Notebook nb1 = createNotebook(1L, 10L, "강의 노트");
        Notebook nb2 = createNotebook(1L, 11L, "시험 노트");
        when(notebookRepository.findAllByUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(nb1, nb2));

        List<Notebook> result = notebookService.getNotebooks(1L);

        assertThat(result).containsExactly(nb1, nb2);
    }

    @Test
    void getNotebooks_returnsEmptyListWhenNoneExist() {
        when(notebookRepository.findAllByUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        assertThat(notebookService.getNotebooks(1L)).isEmpty();
    }

    // ───────────────────────────── createNotebook ────────────────────────────

    @Test
    void createNotebook_savesAndReturnsNotebook() {
        UserAccount user = createUser(1L);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notebookRepository.save(any())).thenAnswer(inv -> {
            Notebook nb = inv.getArgument(0);
            ReflectionTestUtils.setField(nb, "notebookId", 20L);
            return nb;
        });

        Notebook result = notebookService.createNotebook(1L, "새 노트북");

        assertThat(result.getNotebookId()).isEqualTo(20L);
        assertThat(result.getTitle()).isEqualTo("새 노트북");
        verify(notebookRepository).save(any());
    }

    @Test
    void createNotebook_throwsWhenUserNotFound() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notebookService.createNotebook(1L, "노트북"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.UNAUTHORIZED.getMessage());

        verify(notebookRepository, never()).save(any());
    }

    // ───────────────────────────── getNotebook ───────────────────────────────

    @Test
    void getNotebook_returnsNotebookWhenOwnerRequests() {
        Notebook notebook = createNotebook(1L, 10L, "강의 노트");
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        Notebook result = notebookService.getNotebook(1L, 10L);

        assertThat(result).isSameAs(notebook);
    }

    @Test
    void getNotebook_throwsWhenNotFound() {
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notebookService.getNotebook(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_NOT_FOUND.getMessage());
    }

    @Test
    void getNotebook_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L, "남의 노트북");
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> notebookService.getNotebook(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_ACCESS_DENIED.getMessage());
    }

    // ───────────────────────────── updateNotebook ────────────────────────────

    @Test
    void updateNotebook_renamesTitleAndReturnsNotebook() {
        Notebook notebook = createNotebook(1L, 10L, "구 제목");
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        Notebook result = notebookService.updateNotebook(1L, 10L, "새 제목");

        assertThat(result.getTitle()).isEqualTo("새 제목");
    }

    @Test
    void updateNotebook_throwsWhenNotFound() {
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notebookService.updateNotebook(1L, 10L, "새 제목"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_NOT_FOUND.getMessage());
    }

    @Test
    void updateNotebook_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L, "남의 노트북");
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> notebookService.updateNotebook(1L, 10L, "새 제목"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_ACCESS_DENIED.getMessage());
    }

    // ───────────────────────────── deleteNotebook ────────────────────────────

    @Test
    void deleteNotebook_softDeletesAndCascades() {
        Notebook notebook = createNotebook(1L, 10L, "강의 노트");
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        notebookService.deleteNotebook(1L, 10L);

        verify(documentService).cascadeDeleteByNotebook(10L);
        assertThat(notebook.isDeleted()).isTrue();
    }

    @Test
    void deleteNotebook_throwsWhenNotFound() {
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notebookService.deleteNotebook(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_NOT_FOUND.getMessage());

        verify(documentService, never()).cascadeDeleteByNotebook(any());
    }

    @Test
    void deleteNotebook_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L, "남의 노트북");
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> notebookService.deleteNotebook(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_ACCESS_DENIED.getMessage());

        verify(documentService, never()).cascadeDeleteByNotebook(any());
    }

    // ───────────────────────────── helpers ───────────────────────────────────

    private UserAccount createUser(Long userId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Notebook createNotebook(Long userId, Long notebookId, String title) {
        UserAccount user = createUser(userId);
        Notebook notebook = Notebook.create(user, title);
        ReflectionTestUtils.setField(notebook, "notebookId", notebookId);
        return notebook;
    }
}
