package org.example.snow.auth.infra;

import org.example.snow.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByTokenFamilyAndRevokedAtIsNull(UUID tokenFamily);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.user.userId = :userId AND r.revokedAt IS NULL")
    void revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
