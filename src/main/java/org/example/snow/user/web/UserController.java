package org.example.snow.user.web;

import lombok.RequiredArgsConstructor;
import org.example.snow.auth.application.AuthService;
import org.example.snow.auth.security.AuthenticatedUserPrincipal;
import org.example.snow.auth.security.RefreshTokenCookieFactory;
import org.example.snow.user.application.UserService;
import org.example.snow.user.web.dto.CurrentUserResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return ResponseEntity.ok(CurrentUserResponse.from(authService.getUserOrThrow(principal.userId())));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        userService.withdraw(principal.userId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.deleteCookie().toString())
                .build();
    }
}
