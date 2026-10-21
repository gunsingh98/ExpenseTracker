package com.expensetracker.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies refresh-token id tracking: validity, user binding, expiry, revocation. */
class RefreshTokenStoreTest {

    private final RefreshTokenStore store = new RefreshTokenStore();

    private static long inOneHour() {
        return System.currentTimeMillis() + 3_600_000L;
    }

    @Test
    void acceptsAStoredUnexpiredTokenForItsOwner() {
        store.store("jti-1", "alice", inOneHour());

        assertThat(store.isValid("jti-1", "alice")).isTrue();
    }

    @Test
    void rejectsAnUnknownId() {
        assertThat(store.isValid("never-seen", "alice")).isFalse();
    }

    @Test
    void rejectsAnIdBoundToADifferentUser() {
        store.store("jti-1", "alice", inOneHour());

        assertThat(store.isValid("jti-1", "mallory")).isFalse();
    }

    @Test
    void rejectsAnExpiredId() {
        store.store("jti-1", "alice", System.currentTimeMillis() - 1);

        assertThat(store.isValid("jti-1", "alice")).isFalse();
    }

    @Test
    void removedIdIsNoLongerValid() {
        store.store("jti-1", "alice", inOneHour());
        store.remove("jti-1");

        assertThat(store.isValid("jti-1", "alice")).isFalse();
    }

    @Test
    void rejectsANullId() {
        assertThat(store.isValid(null, "alice")).isFalse();
    }
}
