package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserAuthControllerTest {
    private static final String HOST = "localhost:3000";
    private static final String ORIGIN = "http://localhost:3000";
    private static final LoginRequest LOGIN = new LoginRequest("user@example.test", "test-input");
    private static final AccountResponse ACCOUNT = new AccountResponse(
            7L, "Test User", "user@example.test", Role.API_CONSUMER, Set.of("users:read"));

    @Test
    void loginSetsProtectedCookieAndReturnsOnlyBindingSentinel() {
        IdentityService service = mock(IdentityService.class);
        when(service.login(LOGIN)).thenReturn(auth("access-one", "opaque-refresh-one"));
        BrowserAuthController controller = new BrowserAuthController(service, true);

        ResponseEntity<BrowserAuthResponse> response = controller.login(
                BrowserAuthController.BROWSER_HEADER_VALUE, HOST, ORIGIN, LOGIN);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("access-one", response.getBody().accessToken());
        assertEquals(BrowserAuthResponse.COOKIE_BOUND_REFRESH, response.getBody().refreshToken());
        assertFalse(response.getBody().toString().contains("opaque-refresh-one"));
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith(BrowserAuthController.REFRESH_COOKIE + "=opaque-refresh-one"));
        assertTrue(cookie.contains("Path=/api/auth"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void registrationUsesSameCookieBoundary() {
        IdentityService service = mock(IdentityService.class);
        RegisterRequest request = new RegisterRequest("Test User", "user@example.test", "test-input");
        when(service.register(request)).thenReturn(auth("access-register", "opaque-register"));
        BrowserAuthController controller = new BrowserAuthController(service, true);

        ResponseEntity<BrowserAuthResponse> response = controller.register(
                BrowserAuthController.BROWSER_HEADER_VALUE, HOST, ORIGIN, request);

        verify(service).register(request);
        assertEquals(BrowserAuthResponse.COOKIE_BOUND_REFRESH, response.getBody().refreshToken());
        assertFalse(response.getBody().toString().contains("opaque-register"));
    }

    @Test
    void refreshUsesCookieAndRotatesWithoutReturningSecret() {
        IdentityService service = mock(IdentityService.class);
        when(service.refresh(new RefreshRequest("opaque-old"))).thenReturn(auth("access-two", "opaque-new"));
        BrowserAuthController controller = new BrowserAuthController(service, true);

        ResponseEntity<BrowserAuthResponse> response = controller.refresh(
                BrowserAuthController.BROWSER_HEADER_VALUE, HOST, ORIGIN, "opaque-old");

        verify(service).refresh(new RefreshRequest("opaque-old"));
        assertEquals(BrowserAuthResponse.COOKIE_BOUND_REFRESH, response.getBody().refreshToken());
        assertFalse(response.getBody().toString().contains("opaque-new"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)
                .startsWith(BrowserAuthController.REFRESH_COOKIE + "=opaque-new"));
    }

    @Test
    void rejectsMissingOrCrossOriginBrowserRequest() {
        IdentityService service = mock(IdentityService.class);
        BrowserAuthController controller = new BrowserAuthController(service, true);

        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.login(null, HOST, ORIGIN, LOGIN));
        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.login(BrowserAuthController.BROWSER_HEADER_VALUE, HOST, null, LOGIN));
        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.login(BrowserAuthController.BROWSER_HEADER_VALUE, HOST, "http://other.example.test", LOGIN));
        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.login(BrowserAuthController.BROWSER_HEADER_VALUE, HOST, "not a uri", LOGIN));
        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.login(BrowserAuthController.BROWSER_HEADER_VALUE, "localhost", ORIGIN, LOGIN));
        verifyNoInteractions(service);
    }

    @Test
    void refreshWithoutCookieFailsClosed() {
        IdentityService service = mock(IdentityService.class);
        BrowserAuthController controller = new BrowserAuthController(service, true);
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class,
                () -> controller.refresh(BrowserAuthController.BROWSER_HEADER_VALUE, HOST, ORIGIN, null));
        verifyNoInteractions(service);
    }

    @Test
    void logoutRevokesCookieTokenAndExpiresCookie() {
        IdentityService service = mock(IdentityService.class);
        BrowserAuthController controller = new BrowserAuthController(service, true);

        ResponseEntity<Void> response = controller.logout(
                BrowserAuthController.BROWSER_HEADER_VALUE, HOST, ORIGIN, "opaque-current");

        verify(service).logout(new LogoutRequest("opaque-current"));
        assertEquals(204, response.getStatusCode().value());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.contains("Path=/api/auth"));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
    }

    @Test
    void localHttpOverrideOnlyDropsSecureAttribute() {
        IdentityService service = mock(IdentityService.class);
        when(service.login(LOGIN)).thenReturn(auth("access-local", "opaque-local"));
        BrowserAuthController controller = new BrowserAuthController(service, false);

        String cookie = controller.login(BrowserAuthController.BROWSER_HEADER_VALUE, HOST, ORIGIN, LOGIN)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertNotNull(cookie);
        assertFalse(cookie.contains("; Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
    }

    private static AuthResponse auth(String accessToken, String refreshToken) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", 3600L, 604800L, ACCOUNT);
    }
}
