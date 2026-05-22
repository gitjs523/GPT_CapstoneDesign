package org.example.snow.auth.application;

import org.example.snow.auth.config.AuthProperties;
import org.example.snow.auth.domain.RefreshToken;
import org.example.snow.auth.infra.RefreshTokenRepository;
import org.example.snow.auth.security.AccessTokenProvider;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.example.snow.user.infra.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessTokenProvider accessTokenProvider = mock(AccessTokenProvider.class);
    private final RefreshTokenHasher refreshTokenHasher = mock(RefreshTokenHasher.class);
    private final RefreshTokenFamilyRevoker familyRevoker = mock(RefreshTokenFamilyRevoker.class);
    private final AuthProperties authProperties = buildAuthProperties();

    private final AuthService authService = new AuthService(
            userAccountRepository,
            notebookRepository,
            refreshTokenRepository,
            passwordEncoder,
            accessTokenProvider,
            refreshTokenHasher,
            authProperties,
            familyRevoker
    );

    // ───────────────────────────────── login ─────────────────────────────────

    @Test
    void login_succeeds_forActiveUser() {
        UserAccount user = createUser(1L, "user@example.com", false);
        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", user.getPasswordHash())).thenReturn(true);
        when(accessTokenProvider.issueToken(user)).thenReturn("access-token");
        when(refreshTokenHasher.hash(any())).thenReturn("hashed");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login("user@example.com", "password", "agent");

        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_throwsInvalidCredentials_forWithdrawnUser() {
        UserAccount user = createUser(1L, "user@example.com", true);
        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("user@example.com", "password", "agent"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_CREDENTIALS.getMessage());

        verify(accessTokenProvider, never()).issueToken(any());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void login_throwsInvalidCredentials_forWrongPassword() {
        UserAccount user = createUser(1L, "user@example.com", false);
        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@example.com", "wrong", "agent"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_CREDENTIALS.getMessage());

        verify(accessTokenProvider, never()).issueToken(any());
    }

    @Test
    void login_throwsInvalidCredentials_forUnknownEmail() {
        when(userAccountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("unknown@example.com", "password", "agent"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_CREDENTIALS.getMessage());
    }

    // ─────────────────────────────── refresh ─────────────────────────────────

    @Test
    void refresh_revokesTokenFamily_whenRevokedTokenReused() {
        UUID family = UUID.randomUUID();
        UserAccount user = createUser(1L, "user@example.com", false);
        RefreshToken revokedToken = createRevokedToken(user, family);

        when(refreshTokenHasher.hash("stolen-token")).thenReturn("hashed-stolen");
        when(refreshTokenRepository.findByTokenHash("hashed-stolen")).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh("stolen-token", "agent"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_REFRESH_TOKEN.getMessage());

        verify(familyRevoker).revokeFamily(eq(family), any(LocalDateTime.class));
    }

    @Test
    void refresh_throwsExpired_whenTokenExpired() {
        UserAccount user = createUser(1L, "user@example.com", false);
        RefreshToken expiredToken = createExpiredToken(user, UUID.randomUUID());

        when(refreshTokenHasher.hash("expired-token")).thenReturn("hashed-expired");
        when(refreshTokenRepository.findByTokenHash("hashed-expired")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("expired-token", "agent"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EXPIRED_REFRESH_TOKEN.getMessage());

        verify(familyRevoker, never()).revokeFamily(any(), any());
    }

    // ─────────────────────────────── helpers ─────────────────────────────────

    private UserAccount createUser(Long userId, String email, boolean withdrawn) {
        UserAccount user = UserAccount.create(email, "hashed-password");
        ReflectionTestUtils.setField(user, "userId", userId);
        if (withdrawn) {
            user.softDelete();
        }
        return user;
    }

    private RefreshToken createRevokedToken(UserAccount user, UUID family) {
        RefreshToken token = RefreshToken.issue(user, family, "hashed-stolen",
                LocalDateTime.now().plusDays(7), "agent");
        token.revoke(LocalDateTime.now().minusHours(1));
        return token;
    }

    private RefreshToken createExpiredToken(UserAccount user, UUID family) {
        return RefreshToken.issue(user, family, "hashed-expired",
                LocalDateTime.now().minusSeconds(1), "agent");
    }

    private AuthProperties buildAuthProperties() {
        AuthProperties props = new AuthProperties();
        AuthProperties.Jwt jwt = new AuthProperties.Jwt();
        jwt.setIssuer("snow");
        jwt.setAccessTokenSecret("test-secret-key-must-be-at-least-32-bytes!!");
        jwt.setAccessTokenExpirationSeconds(900);
        props.setJwt(jwt);

        AuthProperties.RefreshToken rt = new AuthProperties.RefreshToken();
        rt.setExpirationSeconds(604800);
        AuthProperties.RefreshToken.Cookie cookie = new AuthProperties.RefreshToken.Cookie();
        cookie.setName("refresh_token");
        cookie.setSecure(false);
        cookie.setSameSite("Lax");
        cookie.setPath("/api/auth");
        rt.setCookie(cookie);
        props.setRefreshToken(rt);
        return props;
    }
}
