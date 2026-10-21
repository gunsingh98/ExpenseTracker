package com.expensetracker.web;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.expensetracker.service.JwtService;
import com.expensetracker.service.RefreshTokenStore;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * JWT auth with silent refresh:
 * <ul>
 *   <li>{@code POST /api/login} validates credentials, returns a short-lived
 *       access token in the body (held in SPA memory) and sets a long-lived
 *       refresh token in a {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}
 *       cookie.</li>
 *   <li>{@code POST /api/refresh} reads that cookie, rotates it, and returns a
 *       fresh access token — this is how the SPA recovers from a 401 and restores
 *       a session after a page reload.</li>
 *   <li>{@code POST /api/logout} revokes the refresh token server-side and clears
 *       the cookie.</li>
 *   <li>{@code GET /api/me} reports the current user.</li>
 * </ul>
 *
 * <p>The refresh cookie's {@code SameSite=Strict} attribute is load-bearing: it
 * stops a cross-site page from driving {@code /api/refresh} with the victim's
 * cookie, which is why CSRF protection can stay disabled (all other state-changing
 * endpoints authenticate via the Bearer header, which a cross-site page cannot read).
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    /** Name of the refresh-token cookie. */
    static final String REFRESH_COOKIE = "refresh_token";
    /** Scope the cookie to the API so it is only sent to /api/refresh and /api/logout. */
    private static final String COOKIE_PATH = "/api";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;

    @Value("${security.jwt.refresh-cookie-secure:true}")
    private boolean cookieSecure;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          RefreshTokenStore refreshTokenStore) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletResponse response) {
        // Throws AuthenticationException (→ 401) on bad credentials; no need to
        // check isAuthenticated() afterwards.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        String username = authentication.getName();
        issueRefreshCookie(username, response);
        return new LoginResponse(jwtService.generateToken(username), username);
    }

    /**
     * Exchanges a valid refresh cookie for a new access token, rotating the
     * refresh token (old jti revoked, new one issued) on every call. Any problem
     * — missing/expired/forged cookie, or a jti the server no longer recognises
     * (reuse after rotation, or after logout) — clears the cookie and 401s.
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = readRefreshCookie(request);
        if (token == null) {
            throw new BadCredentialsException("Missing refresh token");
        }

        String username;
        String jti;
        try {
            if (!jwtService.isRefreshToken(token)) {
                throw new BadCredentialsException("Not a refresh token");
            }
            username = jwtService.extractUsername(token);
            jti = jwtService.extractTokenId(token);
        } catch (JwtException ex) {
            clearRefreshCookie(response);
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (!refreshTokenStore.isValid(jti, username)) {
            clearRefreshCookie(response);
            throw new BadCredentialsException("Refresh token not recognized");
        }

        // Rotate: drop the presented jti and mint a brand-new refresh cookie.
        refreshTokenStore.remove(jti);
        issueRefreshCookie(username, response);
        return new LoginResponse(jwtService.generateToken(username), username);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readRefreshCookie(request);
        if (token != null) {
            try {
                refreshTokenStore.remove(jwtService.extractTokenId(token));
            } catch (JwtException ignored) {
                // Garbage cookie: nothing to revoke, just clear it below.
            }
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return new UserResponse(authentication.getName());
    }

    private void issueRefreshCookie(String username, HttpServletResponse response) {
        String jti = UUID.randomUUID().toString();
        long lifetimeMs = jwtService.getRefreshExpirationTime();
        refreshTokenStore.store(jti, username, System.currentTimeMillis() + lifetimeMs);

        String refreshToken = jwtService.generateRefreshToken(username, jti);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofMillis(lifetimeMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String username) {
    }

    public record UserResponse(String username) {
    }
}
