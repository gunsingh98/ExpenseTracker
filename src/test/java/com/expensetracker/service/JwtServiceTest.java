package com.expensetracker.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JWT signing/verification: round-trip, expiry, tamper
 * detection, and startup validation of the signing secret.
 */
class JwtServiceTest {

    private static final UserDetails ALICE =
            User.withUsername("alice").password("x").roles("USER").build();

    /** A fresh, valid base64 HS256 secret for each test. */
    private static String freshSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private static final long REFRESH_MS = 2_592_000_000L; // 30 days

    private static JwtService newService(String secret, long expirationMs, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        JwtService svc = new JwtService(env);
        ReflectionTestUtils.setField(svc, "secretKey", secret);
        ReflectionTestUtils.setField(svc, "jwtExpiration", expirationMs);
        ReflectionTestUtils.setField(svc, "refreshExpiration", REFRESH_MS);
        ReflectionTestUtils.invokeMethod(svc, "init");
        return svc;
    }

    @Test
    void generatesAndValidatesAToken() {
        JwtService svc = newService(freshSecret(), 3600_000L, "prod");

        String token = svc.generateToken("alice");

        assertThat(svc.extractUsername(token)).isEqualTo("alice");
        assertThat(svc.isTokenValid(token, ALICE)).isTrue();
    }

    @Test
    void generatesAndRecognisesARefreshToken() {
        JwtService svc = newService(freshSecret(), 3600_000L, "prod");

        String refresh = svc.generateRefreshToken("alice", "jti-123");

        assertThat(svc.isRefreshToken(refresh)).isTrue();
        assertThat(svc.extractUsername(refresh)).isEqualTo("alice");
        assertThat(svc.extractTokenId(refresh)).isEqualTo("jti-123");
    }

    @Test
    void doesNotAcceptAccessTokenAsRefresh() {
        JwtService svc = newService(freshSecret(), 3600_000L, "prod");

        String access = svc.generateToken("alice");

        assertThat(svc.isRefreshToken(access)).isFalse();
    }

    @Test
    void doesNotAcceptRefreshTokenAsAccess() {
        JwtService svc = newService(freshSecret(), 3600_000L, "prod");

        String refresh = svc.generateRefreshToken("alice", "jti-123");

        // A refresh token must never satisfy access-token validation.
        assertThat(svc.isTokenValid(refresh, ALICE)).isFalse();
    }

    @Test
    void rejectsTokenForADifferentUser() {
        JwtService svc = newService(freshSecret(), 3600_000L, "prod");
        String token = svc.generateToken("mallory");

        assertThat(svc.isTokenValid(token, ALICE)).isFalse();
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtService svc = newService(freshSecret(), 1L, "prod"); // 1ms lifetime
        String token = svc.generateToken("alice");

        Thread.sleep(20); // let it expire

        // An expired token throws during parsing; validation must not accept it.
        assertThatThrownBy(() -> svc.isTokenValid(token, ALICE)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService signer = newService(freshSecret(), 3600_000L, "prod");
        JwtService verifier = newService(freshSecret(), 3600_000L, "prod"); // different key
        String token = signer.generateToken("alice");

        assertThatThrownBy(() -> verifier.isTokenValid(token, ALICE)).isInstanceOf(JwtException.class);
    }

    @Test
    void failsStartupWhenSecretBlankInNonDevProfile() {
        JwtService svc = new JwtService(prodEnv());
        ReflectionTestUtils.setField(svc, "secretKey", "");
        ReflectionTestUtils.setField(svc, "jwtExpiration", 3600_000L);
        ReflectionTestUtils.setField(svc, "refreshExpiration", REFRESH_MS);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(svc, "init"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generatesEphemeralKeyWhenSecretBlankInDevProfile() {
        JwtService svc = new JwtService(new MockEnvironment()); // no active profile == dev
        ReflectionTestUtils.setField(svc, "secretKey", "");
        ReflectionTestUtils.setField(svc, "jwtExpiration", 3600_000L);
        ReflectionTestUtils.setField(svc, "refreshExpiration", REFRESH_MS);
        ReflectionTestUtils.invokeMethod(svc, "init");

        String token = svc.generateToken("alice");
        assertThat(svc.isTokenValid(token, ALICE)).isTrue();
    }

    @Test
    void rejectsWeakSecret() {
        JwtService svc = new JwtService(prodEnv());
        // 16 bytes = 128 bits, below the 256-bit HS256 minimum.
        ReflectionTestUtils.setField(svc, "secretKey",
                Encoders.BASE64.encode(new byte[16]));
        ReflectionTestUtils.setField(svc, "jwtExpiration", 3600_000L);
        ReflectionTestUtils.setField(svc, "refreshExpiration", REFRESH_MS);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(svc, "init"))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    private static MockEnvironment prodEnv() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }
}
