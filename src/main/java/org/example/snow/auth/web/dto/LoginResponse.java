package org.example.snow.auth.web.dto;

import org.example.snow.auth.application.LoginResult;

public record LoginResponse(
        Long userId,
        String email,
        String accessToken
) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.userId(),
                result.email(),
                result.accessToken()
        );
    }
}
