package com.expensetracker.model;

import java.util.List;

/**
 * One page of a keyset-paginated list. {@code nextCursor} is an opaque token to
 * pass back for the following page, or {@code null} when this is the last page.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {
}
