package org.example.snow.auth.application;

public record SignupResult(
        Long userId,
        String email,
        Long defaultNotebookId,
        String accessToken,
        String refreshToken
) {
}
