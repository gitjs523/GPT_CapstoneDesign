package org.example.snow.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.snow.auth.application.AuthService;
import org.example.snow.auth.application.LoginResult;
import org.example.snow.auth.application.RefreshResult;
import org.example.snow.auth.application.SignupResult;
import org.example.snow.auth.security.RefreshTokenCookieFactory;
import org.example.snow.auth.web.dto.LoginRequest;
import org.example.snow.auth.web.dto.LoginResponse;
import org.example.snow.auth.web.dto.LogoutResponse;
import org.example.snow.auth.web.dto.RefreshResponse;
import org.example.snow.auth.web.dto.SignupRequest;
import org.example.snow.auth.web.dto.SignupResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpServletRequest) {
        SignupResult result = authService.signup(request.email(), request.password(), httpServletRequest.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.createCookie(result.refreshToken()).toString())
                .body(SignupResponse.from(result));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        LoginResult result = authService.login(request.email(), request.password(), httpServletRequest.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.createCookie(result.refreshToken()).toString())
                .body(LoginResponse.from(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest httpServletRequest) {
        RefreshResult result = authService.refresh(
                refreshTokenCookieFactory.extractToken(httpServletRequest),
                httpServletRequest.getHeader(HttpHeaders.USER_AGENT)
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.createCookie(result.refreshToken()).toString())
                .body(RefreshResponse.from(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(HttpServletRequest httpServletRequest) {
        authService.logout(refreshTokenCookieFactory.extractToken(httpServletRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.deleteCookie().toString())
                .body(new LogoutResponse(true));
    }
}
