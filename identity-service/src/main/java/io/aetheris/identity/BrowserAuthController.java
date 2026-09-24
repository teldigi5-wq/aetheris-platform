package io.aetheris.identity;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/browser")
public class BrowserAuthController {
    static final String BROWSER_HEADER = "X-Aetheris-Browser";
    static final String BROWSER_HEADER_VALUE = "aetheris-dashboard-v1";
    static final String REFRESH_COOKIE = "aetheris_refresh";
    private static final String COOKIE_PATH = "/api/auth/browser";

    private final IdentityService identityService;
    private final boolean secureCookie;

    public BrowserAuthController(
            IdentityService identityService,
            @Value("${aetheris.browser-auth.secure-cookie:true}") boolean secureCookie) {
        this.identityService = identityService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<BrowserAuthResponse> login(
            @RequestHeader(name = BROWSER_HEADER, required = false) String browserHeader,
            @Valid @RequestBody LoginRequest request) {
        requireBrowserRequest(browserHeader);
        return authenticated(identityService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BrowserAuthResponse> refresh(
            @RequestHeader(name = BROWSER_HEADER, required = false) String browserHeader,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        requireBrowserRequest(browserHeader);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RefreshTokenService.InvalidRefreshTokenException();
        }
        return authenticated(identityService.refresh(new RefreshRequest(refreshToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = BROWSER_HEADER, required = false) String browserHeader,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        requireBrowserRequest(browserHeader);
        if (refreshToken != null && !refreshToken.isBlank()) {
            identityService.logout(new LogoutRequest(refreshToken));
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .build();
    }

    private ResponseEntity<BrowserAuthResponse> authenticated(AuthResponse auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(auth.refreshToken(), auth.refreshExpiresInSeconds()).toString())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(BrowserAuthResponse.from(auth));
    }

    private ResponseCookie refreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    private static void requireBrowserRequest(String browserHeader) {
        if (!BROWSER_HEADER_VALUE.equals(browserHeader)) {
            throw new BrowserRequestRejectedException();
        }
    }

    @ExceptionHandler(BrowserRequestRejectedException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, Object> browserRequestRejected() {
        return Map.of("timestamp", Instant.now().toString(), "status", 403, "error", "Forbidden", "message", "Browser auth request rejected");
    }

    @ExceptionHandler(IdentityService.InvalidCredentialsException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, Object> invalidCredentials() {
        return Map.of("timestamp", Instant.now().toString(), "status", 401, "error", "Unauthorized", "message", "Invalid email or password");
    }

    @ExceptionHandler(RefreshTokenService.InvalidRefreshTokenException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, Object> invalidRefreshToken() {
        return Map.of("timestamp", Instant.now().toString(), "status", 401, "error", "Unauthorized", "message", "Invalid, expired, or already used refresh token");
    }

    static class BrowserRequestRejectedException extends RuntimeException {}
}
