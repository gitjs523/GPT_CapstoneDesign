package org.example.snow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.example.snow.auth.config.AuthProperties;
import org.example.snow.user.domain.UserAccount;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class AccessTokenProvider {

    private final AuthProperties authProperties;
    private final SecretKey accessTokenKey;

    public AccessTokenProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.accessTokenKey = Keys.hmacShaKeyFor(authProperties.getJwt().getAccessTokenSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(UserAccount userAccount) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(authProperties.getJwt().getAccessTokenExpirationSeconds());

        return Jwts.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .subject(String.valueOf(userAccount.getUserId()))
                .claim("email", userAccount.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(accessTokenKey)
                .compact();
    }

    public Optional<AuthenticatedUserPrincipal> parseAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(accessTokenKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.parseLong(claims.getSubject());
            String email = claims.get("email", String.class);
            return Optional.of(new AuthenticatedUserPrincipal(userId, email));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
