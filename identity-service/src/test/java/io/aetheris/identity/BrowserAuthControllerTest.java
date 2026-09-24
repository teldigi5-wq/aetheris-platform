package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserAuthControllerTest {

    private static final AccountResponse ACCOUNT = new AccountResponse(
            7L,
            "Poojana",
            "poojana@aetheris.local",
            Role.API_CONSUMER,
            Set.of("users:read"));

    @Test
    void loginSetsHttpOnlyStrictSecureCookieAndDoesNotReturnRefreshCredential() {
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.login(new LoginRequest("poojana@aetheris.local", "Password123!")))
                .thenReturn(auth("access-1", "refresh-secret-1"));

        BrowserAuthController controller = new BrowserAuthController(identityService, true);
        ResponseEntity<BrowserAuthResponse> response = controller.login(
                BrowserAuthController.BROWSER_HEADER_VALUE,
                new LoginRequest("poojana@aetheris.local", "Password123!"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("access-1", response.getBody().accessToken());
        assertEquals(ACCOUNT, response.getBody().account());
        assertFalse(response.getBody().toString().contains("refresh-secret-1"));

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith(BrowserAuthController.REFRESH_COOKIE + "=refresh-secret-1"));
        assertTrue(cookie.contains("Path=/api/auth/browser"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("no-cache", response.getHeaders().getFirst(HttpHeaders.PRAGMA));
    }

    @Test
    void localHttpModeCanExplicitlyDisableSecureAttributeWithoutDroppingHttpOnlyOrSameSite() {
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.login(any())).thenReturn(auth("access-local", "refresh-local"));

        BrowserAuthController controller = new BrowserAuthController(identityService, false);
        ResponseEntity<BrowserAuthResponse> response = controller.login(
                BrowserAuthController.BROWSER_HEADER_VALUE,
                new LoginRequest("poojana@aetheris.local", "Password123!"));

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertFalse(cookie.contains("; Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
    }

    @Test
    void refreshReadsCookieRotatesTokenAndReturnsOnlyAccessCredential() {
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.refresh(new RefreshRequest("refresh-old")))
                .thenReturn(auth("access-2", "refresh-new"));

        BrowserAuthController controller = new BrowserAuthController(identityService, true);
        ResponseEntity<BrowserAuthResponse> response = controller.refresh(
                BrowserAuthController.BROWSER_HEADER_VALUE,
                "refresh-old");

        verify(identityService).refresh(new RefreshRequest("refresh-old"));
        assertEquals("access-2", response.getBody().accessToken());
        assertFalse(response.getBody().toString().contains("refresh-new"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).startsWith(
                BrowserAuthController.REFRESH_COOKIE + "=refresh-new"));
    }

    @Test
    void browserEndpointsRejectRequestsWithoutDashboardHeader() {
        IdentityService identityService = mock(IdentityService.class);
        BrowserAuthController controller = new BrowserAuthController(identityService, true);

        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.login(null, new LoginRequest("poojana@aetheris.local", "Password123!")));
        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.refresh("wrong-client", "refresh-old"));
        assertThrows(BrowserAuthController.BrowserRequestRejectedException.class,
                () -> controller.logout(null, "refresh-old"));
        verifyNoInteractions(identityService);
    }

    @Test
    void refreshWithoutCookieFailsClosed() {
        IdentityService identityService = mock(IdentityService.class);
        BrowserAuthController controller = new BrowserAuthController(identityService, true);

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class,
                () -> controller.refresh(BrowserAuthController.BROWSER_HEADER_VALUE, null));
        verifyNoInteractions(identityService);
    }

    @Test
    void logoutRevokesCookieTokenAndExpiresCookie() {
        IdentityService identityService = mock(IdentityService.class);
        BrowserAuthController controller = new BrowserAuthController(identityService, true);

        ResponseEntity<Void> response = controller.logout(
                BrowserAuthController.BROWSER_HEADER_VALUE,
                "refresh-current");

        verify(identityService).logout(new LogoutRequest("refresh-current"));
        assertEquals(204, response.getStatusCode().value());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith(BrowserAuthController.REFRESH_COOKIE + "="));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
    }

    private static AuthResponse auth(String accessToken, String refreshToken) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", 3600L, 604800L, ACCOUNT);
    }
}
