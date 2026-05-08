package org.example.snow.auth.web.dto;

import org.example.snow.auth.application.RefreshResult;

public record RefreshResponse(
        String accessToken
) {

    public static RefreshResponse from(RefreshResult result) {
        return new RefreshResponse(result.accessToken());
    }
}
