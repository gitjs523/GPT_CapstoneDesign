package org.example.snow.auth.application;

public record RefreshResult(
        String accessToken,
        String refreshToken
) {
}
