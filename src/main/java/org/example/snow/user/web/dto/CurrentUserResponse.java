package org.example.snow.user.web.dto;

import org.example.snow.auth.security.AuthenticatedUserPrincipal;
import org.example.snow.user.domain.UserAccount;

public record CurrentUserResponse(
        Long userId,
        String email
) {

    public static CurrentUserResponse from(UserAccount userAccount) {
        return new CurrentUserResponse(userAccount.getUserId(), userAccount.getEmail());
    }

    public static CurrentUserResponse from(AuthenticatedUserPrincipal principal) {
        return new CurrentUserResponse(principal.userId(), principal.email());
    }
}
