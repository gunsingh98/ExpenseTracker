package com.expensetracker.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecureDigestAlgorithm;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Dev/test profiles where an auto-generated signing key is acceptable. */
    private static final java.util.Set<String> DEV_PROFILES = java.util.Set.of("dev", "test", "default");

    @Value("${security.jwt.secret-key:}")
    private String secretKey;

    @Value("${security.jwt.expiration-time:3600000}")
    private long jwtExpiration;

    @Value("${security.jwt.refresh-expiration-time:2592000000}")
    private long refreshExpiration;

    /** Claim name distinguishing an access token from a refresh token. */
    static final String TOKEN_TYPE_CLAIM = "type";
    static final String ACCESS_TOKEN = "access";
    static final String REFRESH_TOKEN = "refresh";

    private final Environment environment;

    /** Resolved once at startup (see {@link #init()}); never null afterwards. */
    private SecretKey signingKey;

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Resolves and validates the signing key at startup so misconfiguration
     * fails fast instead of at the first login. Requires a configured
     * {@code security.jwt.secret-key} (base64, ≥256-bit) in any non-dev profile;
     * in a dev/test profile it generates an ephemeral key with a warning.
     */
    @PostConstruct
    void init() {
        if (jwtExpiration <= 0) {
            throw new IllegalStateException(
                    "security.jwt.expiration-time must be a positive number of milliseconds");
        }
        if (refreshExpiration <= 0) {
            throw new IllegalStateException(
                    "security.jwt.refresh-expiration-time must be a positive number of milliseconds");
        }
        if (secretKey == null || secretKey.isBlank()) {
            if (isDevProfile()) {
                this.signingKey = Jwts.SIG.HS256.key().build();
                log.warn("No 'security.jwt.secret-key' configured — generated an ephemeral signing "
                        + "key for this run. Tokens will not survive a restart. Set "
                        + "SECURITY_JWT_SECRET_KEY (base64, >=256-bit) for real use.");
            } else {
                throw new IllegalStateException(
                        "security.jwt.secret-key (env SECURITY_JWT_SECRET_KEY) must be set to a "
                        + "base64-encoded, >=256-bit secret in a non-dev profile.");
            }
        } else {
            // hmacShaKeyFor throws WeakKeyException if the decoded key is < 256 bits.
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        }
    }

    private boolean isDevProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true; // no explicit profile == local/default dev run
        }
        return Arrays.stream(active).anyMatch(DEV_PROFILES::contains);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /** Issues a short-lived access token (sent as a Bearer header by the SPA). */
    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, ACCESS_TOKEN);
        return buildToken(claims, username, jwtExpiration, null);
    }

    /**
     * Issues a long-lived refresh token carrying the given {@code jti} (JWT id).
     * The jti lets the server track and revoke the token server-side via
     * {@link RefreshTokenStore}; the token itself lives only in an HttpOnly cookie.
     */
    public String generateRefreshToken(String username, String jti) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, REFRESH_TOKEN);
        return buildToken(claims, username, refreshExpiration, jti);
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    public long getRefreshExpirationTime() {
        return refreshExpiration;
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            String username,
            long expiration,
            String jti
    ) {
        long now = System.currentTimeMillis();
        var builder = Jwts
                .builder()
                .claims(extraClaims)
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration));
        if (jti != null) {
            builder.id(jti);
        }
        return builder
                .signWith(getSignInKey(), (SecureDigestAlgorithm) Jwts.SIG.HS256)
                .compact();
    }

    /** Validates an <em>access</em> token: right user, correct type, not expired. */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && ACCESS_TOKEN.equals(extractTokenType(token))
                && !isTokenExpired(token);
    }

    /** True if {@code token} is a well-formed, unexpired refresh token. */
    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN.equals(extractTokenType(token)) && !isTokenExpired(token);
    }

    public String extractTokenId(String token) {
        return extractClaim(token, Claims::getId);
    }

    private String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        return signingKey;
    }
}
