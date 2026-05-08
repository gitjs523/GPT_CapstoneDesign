package org.example.snow.auth.web.dto;

import org.example.snow.auth.application.SignupResult;

public record SignupResponse(
        Long userId,
        String email,
        Long defaultNotebookId,
        String accessToken
) {

    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(
                result.userId(),
                result.email(),
                result.defaultNotebookId(),
                result.accessToken()
        );
    }
}
