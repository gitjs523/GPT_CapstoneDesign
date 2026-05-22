package org.example.snow.user.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.auth.infra.RefreshTokenRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.application.NotebookService;
import org.example.snow.user.domain.UserAccount;
import org.example.snow.user.infra.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotebookService notebookService;

    @Transactional
    public void withdraw(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.revokeAllActiveByUserId(userId, now);
        notebookService.cascadeDeleteByUser(userId);
        user.softDelete();
    }
}
