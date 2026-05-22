package org.example.snow.user.application;

import org.example.snow.auth.infra.RefreshTokenRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.application.NotebookService;
import org.example.snow.user.domain.UserAccount;
import org.example.snow.user.infra.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final NotebookService notebookService = mock(NotebookService.class);

    private final UserService userService = new UserService(
            userAccountRepository,
            refreshTokenRepository,
            notebookService
    );

    // ───────────────────────────── withdraw ──────────────────────────────────

    @Test
    void withdraw_softDeletesUserAndCascades() {
        UserAccount user = createUser(1L);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.withdraw(1L);

        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(1L), any());
        verify(notebookService).cascadeDeleteByUser(1L);
        assertThat(user.isDeleted()).isTrue();
    }

    @Test
    void withdraw_throwsWhenUserNotFound() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());

        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
        verify(notebookService, never()).cascadeDeleteByUser(any());
    }

    @Test
    void withdraw_throwsWhenUserAlreadyDeleted() {
        UserAccount user = createUser(1L);
        user.softDelete();
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());

        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
        verify(notebookService, never()).cascadeDeleteByUser(any());
    }

    // ───────────────────────────── helpers ───────────────────────────────────

    private UserAccount createUser(Long userId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
