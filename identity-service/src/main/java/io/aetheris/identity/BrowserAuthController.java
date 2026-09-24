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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/browser")
public class BrowserAuthController {
    static final String BROWSER_HEADER = "X-Aetheris-Browser";
    static final String BROWSER_HEADER_VALUE = "aetheris-dashboard-v1";
    static final String BROWSER_HOST_HEADER = "X-Aetheris-Browser-Host";
    static final String REFRESH_COOKIE = "aetheris_refresh";
    private static final String COOKIE_PATH = "/api/auth";

    private final IdentityService identityService;
    private final boolean secureCookie;

    public BrowserAuthController(
            IdentityService identityService,
            @Value("${aetheris.browser-auth.secure-cookie:true}") boolean secureCookie) {
        this.identityService = identityService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<BrowserAuthResponse> register(
            @RequestHeader(name = BROWSER_HEADER, required = false) String browserHeader,
            @RequestHeader(name = BROWSER_HOST_HEADER, required = false) String browserHost,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @Valid @RequestBody RegisterRequest request) {
        requireBrowserRequest(browserHeader, browserHost, origin);
        return authenticated(identityService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<BrowserAuthResponse> login(
            @RequestHeader(name = BROWSER_HEADER, required = false) String browserHeader,
            @RequestHeader(name = BROWSER_HOST_HEADER, required = false) String browserHost,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @Valid @RequestBody LoginRequest request) {
        requireBrowserRequest(browserHeader, browserHost, origin);
        return authenticated(identityService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BrowserAuthResponse> refresh(
            @RequestHeader(name = BROWSER_HEADER, required = false) String browserHeader,
            @RequestHeader(name = BROWSER_HOST_HEADER, required = false) String browserHost,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        requireBrowserRequest(browserHeader, browserHost, origin);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RefreshTokenService.InvalidRefreshTokenException();
        }
        return authenticated(identityService.refresh(new RefreshRequest(refreshToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = BROWSER_HEADER, required = false) String browserHeader,
            @RequestHeader(name = BROWSER_HOST_HEADER, required = false) String browserHost,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        requireBrowserRequest(browserHeader, browserHost, origin);
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

    private static void requireBrowserRequest(String browserHeader, String browserHost, String origin) {
        if (!BROWSER_HEADER_VALUE.equals(browserHeader)
                || browserHost == null || browserHost.isBlank()
                || origin == null || origin.isBlank()) {
            throw new BrowserRequestRejectedException();
        }

        try {
            URI parsedOrigin = URI.create(origin);
            String scheme = parsedOrigin.getScheme();
            String authority = parsedOrigin.getRawAuthority();
            boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!supportedScheme || authority == null || !authority.equalsIgnoreCase(browserHost)) {
                throw new BrowserRequestRejectedException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BrowserRequestRejectedException();
        }
    }

    @ExceptionHandler(BrowserRequestRejectedException.class)
    public ResponseEntity<Map<String, Object>> browserRequestRejected() {
        return noStoreError(HttpStatus.FORBIDDEN, "Forbidden", "Browser auth request rejected");
    }

    @ExceptionHandler(IdentityService.EmailAlreadyRegisteredException.class)
    public ResponseEntity<Map<String, Object>> duplicateEmail() {
        return noStoreError(HttpStatus.CONFLICT, "Conflict", "Email already registered");
    }

    @ExceptionHandler(IdentityService.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> invalidCredentials() {
        return noStoreError(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }

    @ExceptionHandler(RefreshTokenService.InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, Object>> invalidRefreshToken() {
        return noStoreError(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid, expired, or already used refresh token");
    }

    private ResponseEntity<Map<String, Object>> noStoreError(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", status.value(),
                        "error", error,
                        "message", message));
    }

    static class BrowserRequestRejectedException extends RuntimeException {}
}
