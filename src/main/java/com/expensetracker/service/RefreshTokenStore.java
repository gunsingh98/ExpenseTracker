package com.expensetracker.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory registry of currently-valid refresh-token ids (jti), keyed by jti.
 *
 * <p>Although the tokens themselves are stateless JWTs, tracking their jti here
 * gives the server real control over long-lived sessions: rotation on each use,
 * revocation on logout, and reuse detection — a rotated or logged-out token's
 * jti is dropped, so it stops working immediately. A refresh token is only
 * honoured while its jti is present and unexpired in this map.
 *
 * <p>The map lives only in heap, so a restart invalidates all refresh tokens
 * (every client must log in again). That is an acceptable trade-off for a small
 * two-account household app and avoids any external datastore.
 */
@Component
public class RefreshTokenStore {

    private record Entry(String username, long expiresAtEpochMs) {}

    private final ConcurrentHashMap<String, Entry> byJti = new ConcurrentHashMap<>();

    /** Registers a freshly issued refresh token id as valid until {@code expiresAtEpochMs}. */
    public void store(String jti, String username, long expiresAtEpochMs) {
        pruneExpired();
        byJti.put(jti, new Entry(username, expiresAtEpochMs));
    }

    /** True if {@code jti} is a known, unexpired token belonging to {@code username}. */
    public boolean isValid(String jti, String username) {
        if (jti == null) {
            return false;
        }
        Entry entry = byJti.get(jti);
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= entry.expiresAtEpochMs()) {
            byJti.remove(jti, entry);
            return false;
        }
        return entry.username().equals(username);
    }

    /** Invalidates a refresh token id (on rotation or logout). No-op if unknown. */
    public void remove(String jti) {
        if (jti != null) {
            byJti.remove(jti);
        }
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        byJti.values().removeIf(entry -> now >= entry.expiresAtEpochMs());
    }
}
