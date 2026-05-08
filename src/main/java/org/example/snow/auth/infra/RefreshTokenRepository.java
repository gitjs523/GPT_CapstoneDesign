package org.example.snow.auth.infra;

import org.example.snow.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByTokenFamilyAndRevokedAtIsNull(UUID tokenFamily);
}
