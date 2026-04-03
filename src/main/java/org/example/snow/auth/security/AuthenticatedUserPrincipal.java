package org.example.snow.auth.security;

import java.security.Principal;

public record AuthenticatedUserPrincipal(
        Long userId,
        String email
) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
