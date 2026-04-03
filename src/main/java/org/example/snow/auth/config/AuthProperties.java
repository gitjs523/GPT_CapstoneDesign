package org.example.snow.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();
    private RefreshToken refreshToken = new RefreshToken();

    @Getter
    @Setter
    public static class Jwt {
        private String issuer;
        private String accessTokenSecret;
        private long accessTokenExpirationSeconds;
    }

    @Getter
    @Setter
    public static class RefreshToken {
        private long expirationSeconds;
        private Cookie cookie = new Cookie();

        @Getter
        @Setter
        public static class Cookie {
            private String name;
            private boolean secure;
            private String sameSite;
            private String path;
            private String domain;
        }
    }
}
