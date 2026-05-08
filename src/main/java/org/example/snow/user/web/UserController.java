package org.example.snow.user.web;

import lombok.RequiredArgsConstructor;
import org.example.snow.auth.application.AuthService;
import org.example.snow.auth.security.AuthenticatedUserPrincipal;
import org.example.snow.user.web.dto.CurrentUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return ResponseEntity.ok(CurrentUserResponse.from(authService.getUserOrThrow(principal.userId())));
    }
}
