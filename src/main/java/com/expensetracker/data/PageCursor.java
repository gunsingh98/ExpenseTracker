package com.expensetracker.data;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Keyset-pagination position: the sort key and id of the last row on the previous
 * page. The key is the sort column's value as text (an ISO date or an amount); the
 * {@link ExpenseSort} the page was walked in parses and validates it. The next page
 * is every row strictly beyond {@code (key, id)} in that order — an index seek rather
 * than an {@code OFFSET} scan, and stable when rows are inserted mid-walk.
 *
 * <p>Clients see it only as an opaque URL-safe token ({@link #encode()}).
 */
public record PageCursor(String key, int id) {

    public String encode() {
        String raw = key + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Parses a token from {@link #encode()}; throws {@link IllegalArgumentException} if malformed. */
    public static PageCursor decode(String token) {
        String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        int sep = raw.lastIndexOf('|');
        if (sep < 0) {
            throw new IllegalArgumentException("Malformed cursor");
        }
        return new PageCursor(raw.substring(0, sep), Integer.parseInt(raw.substring(sep + 1)));
    }
}
