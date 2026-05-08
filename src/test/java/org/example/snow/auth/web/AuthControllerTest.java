package org.example.snow.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.infra.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Test
    void signupCreatesUserDefaultNotebookAndRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "tester@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("snow_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.email").value("tester@example.com"))
                .andExpect(jsonPath("$.defaultNotebookId", notNullValue()))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Long userId = response.get("userId").asLong();

        assertThat(userAccountRepository.existsByEmail("tester@example.com")).isTrue();
        assertThat(notebookRepository.countByUser_UserId(userId)).isEqualTo(1);
    }

    @Test
    void loginReturnsAccessTokenAndRefreshCookie() throws Exception {
        signUp("login-user@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "login-user@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("snow_refresh=")))
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.email").value("login-user@example.com"))
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void refreshRotatesRefreshCookieAndReturnsNewAccessToken() throws Exception {
        MvcResult signupResult = signUp("refresh-user@example.com");
        String initialRefreshToken = extractCookieValue(signupResult, "snow_refresh");

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("snow_refresh", initialRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("snow_refresh=")))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();

        String rotatedRefreshToken = extractCookieValue(refreshResult, "snow_refresh");
        assertThat(rotatedRefreshToken).isNotBlank();
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);
    }

    @Test
    void logoutClearsCookieAndRevokesRefreshToken() throws Exception {
        MvcResult signupResult = signUp("logout-user@example.com");
        String refreshToken = extractCookieValue(signupResult, "snow_refresh");

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("snow_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("snow_refresh", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    void meReturnsCurrentUserWhenAccessTokenIsValid() throws Exception {
        MvcResult signupResult = signUp("me-user@example.com");
        String accessToken = objectMapper.readTree(signupResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.email").value("me-user@example.com"));
    }

    @Test
    void meReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    private MvcResult signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(cookieName + "=");

        String valuePart = setCookie.substring(setCookie.indexOf(cookieName + "=") + cookieName.length() + 1);
        return valuePart.substring(0, valuePart.indexOf(';'));
    }
}
