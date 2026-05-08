package org.example.snow.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.snow.user.domain.UserAccount;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_token_user"))
    private UserAccount user;

    @Column(name = "token_family", nullable = false)
    private UUID tokenFamily;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    private RefreshToken(UserAccount user, UUID tokenFamily, String tokenHash, LocalDateTime expiresAt, String userAgent) {
        this.user = user;
        this.tokenFamily = tokenFamily;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
    }

    public static RefreshToken issue(UserAccount user, UUID tokenFamily, String tokenHash, LocalDateTime expiresAt, String userAgent) {
        return new RefreshToken(user, tokenFamily, tokenHash, expiresAt, userAgent);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isActive(LocalDateTime now) {
        return !isRevoked() && !isExpired(now);
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void markUsed(LocalDateTime now) {
        lastUsedAt = now;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
