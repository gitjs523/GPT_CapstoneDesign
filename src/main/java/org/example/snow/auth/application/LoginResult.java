package org.example.snow.auth.application;

public record LoginResult(
        Long userId,
        String email,
        String accessToken,
        String refreshToken
) {
}
