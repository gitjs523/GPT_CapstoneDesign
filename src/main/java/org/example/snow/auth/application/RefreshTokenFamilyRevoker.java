package org.example.snow.auth.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.auth.infra.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID tokenFamily, LocalDateTime now) {
        refreshTokenRepository.findAllByTokenFamilyAndRevokedAtIsNull(tokenFamily)
                .forEach(token -> token.revoke(now));
    }
}
