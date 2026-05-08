package org.example.snow.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.snow.auth.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    private final AuthProperties authProperties;

    public ResponseCookie createCookie(String refreshToken) {
        AuthProperties.RefreshToken.Cookie cookie = authProperties.getRefreshToken().getCookie();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookie.getName(), refreshToken)
                .httpOnly(true)
                .secure(cookie.isSecure())
                .sameSite(cookie.getSameSite())
                .path(cookie.getPath())
                .maxAge(authProperties.getRefreshToken().getExpirationSeconds());

        if (StringUtils.hasText(cookie.getDomain())) {
            builder.domain(cookie.getDomain());
        }

        return builder.build();
    }

    public ResponseCookie deleteCookie() {
        AuthProperties.RefreshToken.Cookie cookie = authProperties.getRefreshToken().getCookie();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookie.getName(), "")
                .httpOnly(true)
                .secure(cookie.isSecure())
                .sameSite(cookie.getSameSite())
                .path(cookie.getPath())
                .maxAge(0);

        if (StringUtils.hasText(cookie.getDomain())) {
            builder.domain(cookie.getDomain());
        }

        return builder.build();
    }

    public String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String cookieName = authProperties.getRefreshToken().getCookie().getName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
