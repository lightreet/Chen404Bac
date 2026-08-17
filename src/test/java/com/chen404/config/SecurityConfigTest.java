package com.chen404.config;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.entity.User;
import com.chen404.filter.JwtAuthenticationFilter;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AuthSessionService;
import com.chen404.service.UserService;
import com.chen404.util.CurrentUserUtil;
import com.chen404.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityConfigTest.TestApplication.class)
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final Long TEST_USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthSessionService authSessionService;

    @Test
    void shouldAllowPublicArticleListWithoutToken() throws Exception {
        mockMvc.perform(get("/articles"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    void shouldAllowPublicMusicWithoutToken() throws Exception {
        mockMvc.perform(get("/music/tracks"))
                .andExpect(status().isOk())
                .andExpect(content().string("music"));
    }

    @Test
    void shouldRejectMusicPlayerStateWithoutToken() throws Exception {
        mockMvc.perform(get("/music/player/state"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowMusicPlayerStateWithValidToken() throws Exception {
        stubValidToken("user");

        mockMvc.perform(get("/music/player/state")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(TEST_USER_ID)));
    }

    @Test
    void shouldAllowPublicBookshelfWithoutToken() throws Exception {
        mockMvc.perform(get("/reader/books"))
                .andExpect(status().isOk())
                .andExpect(content().string("public-books"));
    }

    @Test
    void shouldKeepAuthenticatedViewerForPublicBookshelf() throws Exception {
        stubValidToken("user");

        mockMvc.perform(get("/reader/books")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(TEST_USER_ID)));
    }

    @Test
    void shouldRejectBookshelfImportWithoutToken() throws Exception {
        mockMvc.perform(post("/reader/books/import"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBookshelfProgressWithoutToken() throws Exception {
        mockMvc.perform(get("/reader/books/7/progress"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectReaderNotesWithoutToken() throws Exception {
        mockMvc.perform(get("/reader/books/7/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowReaderNotesWithValidToken() throws Exception {
        stubValidToken("user");

        mockMvc.perform(get("/reader/books/7/notes")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(TEST_USER_ID)));
    }

    @Test
    void shouldRejectProtectedArticleWriteWithoutToken() throws Exception {
        mockMvc.perform(post("/articles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUploadEndpointWithoutToken() throws Exception {
        mockMvc.perform(post("/upload/image"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAdminEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowEmailApprovalRedirectWithoutToken() throws Exception {
        mockMvc.perform(get("/trust-requests/email-approve"))
                .andExpect(status().isOk())
                .andExpect(content().string("confirmation"));
    }

    @Test
    void shouldRejectEmailApprovalMutationWithoutAdminToken() throws Exception {
        mockMvc.perform(post("/admin/trust-requests/email-approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowProtectedRequestWithValidToken() throws Exception {
        stubValidToken("user");

        mockMvc.perform(post("/articles")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(TEST_USER_ID)));
    }

    @Test
    void shouldRejectAdminRequestForNonAdminUser() throws Exception {
        stubValidToken("user");

        mockMvc.perform(get("/admin/ping")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminRequestForAdminUser() throws Exception {
        stubValidToken("admin");

        mockMvc.perform(get("/admin/ping")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(TEST_USER_ID)));
    }

    @Test
    void shouldIgnoreInvalidTokenOnPublicRequest() throws Exception {
        when(jwtUtil.verifyAccessToken(anyString())).thenThrow(new JWTVerificationException("invalid"));

        mockMvc.perform(get("/articles")
                        .header("Authorization", "Bearer broken-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    void shouldInjectAuthenticatedPrincipalIntoController() throws Exception {
        stubValidToken("user");

        mockMvc.perform(get("/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(TEST_USER_ID)));
    }

    @Test
    void shouldExposePasswordEncoderAsIndependentBean() {
        assertNotNull(passwordEncoder);
        assertTrue(passwordEncoder.matches("chen404", passwordEncoder.encode("chen404")));
    }

    private void stubValidToken(String roleCode) {
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        when(jwtUtil.verifyAccessToken(VALID_TOKEN)).thenReturn(decodedJWT);
        when(jwtUtil.getUserId(decodedJWT)).thenReturn(TEST_USER_ID);
        when(authSessionService.isCurrent(TEST_USER_ID, decodedJWT)).thenReturn(true);

        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername("chen404");
        user.setStatus(1);
        user.setRoleCode(roleCode);
        when(userService.getCurrentUser(TEST_USER_ID)).thenReturn(user);
    }

    @RestController
    static class TestController {

        @GetMapping("/articles")
        public ResponseEntity<String> publicArticles() {
            return ResponseEntity.ok("public");
        }

        @GetMapping("/music/tracks")
        public ResponseEntity<String> publicMusicTracks() {
            return ResponseEntity.ok("music");
        }

        @GetMapping("/music/player/state")
        public ResponseEntity<String> musicPlayerState(
                @AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @GetMapping("/reader/books")
        public ResponseEntity<String> publicBookshelf(
                @AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(currentUser == null
                    ? "public-books"
                    : String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @GetMapping("/reader/books/{bookId}/progress")
        public ResponseEntity<String> bookshelfProgress(
                @AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @GetMapping("/reader/books/{bookId}/notes")
        public ResponseEntity<String> readerNotes(
                @AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @PostMapping("/articles")
        public ResponseEntity<String> createArticle(@AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @PostMapping("/upload/image")
        public ResponseEntity<String> uploadImage(@AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @RequireAdmin
        @GetMapping("/admin/ping")
        public ResponseEntity<String> adminPing(@AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @GetMapping("/trust-requests/email-approve")
        public ResponseEntity<String> emailApprovalConfirmation() {
            return ResponseEntity.ok("confirmation");
        }

        @RequireAdmin
        @PostMapping("/admin/trust-requests/email-approve")
        public ResponseEntity<String> emailApprovalMutation(
                @AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }

        @GetMapping("/me")
        public ResponseEntity<String> currentUser(@AuthenticationPrincipal AuthenticatedUser currentUser) {
            return ResponseEntity.ok(String.valueOf(CurrentUserUtil.requireUserId(currentUser)));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class,
            SecurityBeanConfig.class,
            PublicApiRequestMatcher.class,
            JwtAuthenticationFilter.class,
            TestController.class
    })
    static class TestApplication {
    }
}
