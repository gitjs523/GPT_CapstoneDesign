package org.example.snow.auth.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.auth.config.AuthProperties;
import org.example.snow.auth.domain.RefreshToken;
import org.example.snow.auth.infra.RefreshTokenRepository;
import org.example.snow.auth.security.AccessTokenProvider;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.example.snow.user.infra.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final NotebookRepository notebookRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthProperties authProperties;

    @Transactional
    public SignupResult signup(String rawEmail, String rawPassword, String userAgent) {
        String email = normalizeEmail(rawEmail);
        if (userAccountRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        UserAccount userAccount = userAccountRepository.save(
                UserAccount.create(email, passwordEncoder.encode(rawPassword))
        );
        Notebook defaultNotebook = notebookRepository.save(Notebook.createDefault(userAccount));

        String accessToken = accessTokenProvider.issueToken(userAccount);
        String refreshToken = issueRefreshToken(userAccount, UUID.randomUUID(), userAgent);

        return new SignupResult(
                userAccount.getUserId(),
                userAccount.getEmail(),
                defaultNotebook.getNotebookId(),
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public LoginResult login(String rawEmail, String rawPassword, String userAgent) {
        String email = normalizeEmail(rawEmail);
        UserAccount userAccount = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(rawPassword, userAccount.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = accessTokenProvider.issueToken(userAccount);
        String refreshToken = issueRefreshToken(userAccount, UUID.randomUUID(), userAgent);

        return new LoginResult(
                userAccount.getUserId(),
                userAccount.getEmail(),
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String userAgent) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REQUIRED);
        }

        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (refreshToken.isRevoked()) {
            revokeTokenFamily(refreshToken.getTokenFamily(), now);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (refreshToken.isExpired(now)) {
            refreshToken.revoke(now);
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        refreshToken.markUsed(now);
        refreshToken.revoke(now);

        String rotatedRefreshToken = issueRefreshToken(refreshToken.getUser(), refreshToken.getTokenFamily(), userAgent);
        String accessToken = accessTokenProvider.issueToken(refreshToken.getUser());

        return new RefreshResult(accessToken, rotatedRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }

        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(refreshToken -> {
            LocalDateTime now = LocalDateTime.now();
            if (refreshToken.isActive(now)) {
                refreshToken.revoke(now);
            }
        });
    }

    @Transactional(readOnly = true)
    public UserAccount getUserOrThrow(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private String issueRefreshToken(UserAccount userAccount, UUID tokenFamily, String userAgent) {
        String rawRefreshToken = generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.issue(
                userAccount,
                tokenFamily,
                refreshTokenHasher.hash(rawRefreshToken),
                LocalDateTime.now().plusSeconds(authProperties.getRefreshToken().getExpirationSeconds()),
                userAgent
        );
        refreshTokenRepository.save(refreshToken);
        return rawRefreshToken;
    }

    private void revokeTokenFamily(UUID tokenFamily, LocalDateTime now) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByTokenFamilyAndRevokedAtIsNull(tokenFamily);
        activeTokens.forEach(token -> token.revoke(now));
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
