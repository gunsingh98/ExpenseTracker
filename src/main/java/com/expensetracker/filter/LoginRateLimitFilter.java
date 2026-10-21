package com.expensetracker.filter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * In-memory brute-force guard for {@code POST /api/login}. Counts consecutive
 * failed attempts per client IP; after {@link #MAX_FAILURES} failures within the
 * window the IP is locked out for {@link #LOCKOUT_MS} and gets a 429. A
 * successful login clears the counter. Suitable for a tiny single-household app
 * (no external store needed); the map self-prunes as windows expire.
 *
 * <p>With {@code server.forward-headers-strategy=framework}, Spring's forwarded
 * header handling means {@link HttpServletRequest#getRemoteAddr()} already
 * reflects the real client IP behind the proxy.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    static final int MAX_FAILURES = 5;
    static final long LOCKOUT_MS = 15 * 60 * 1000L; // 15 minutes

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final class Attempt {
        int failures;
        long windowStartMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String key = request.getRemoteAddr();
        final long now = System.currentTimeMillis();

        if (isLockedOut(key, now)) {
            long retryAfter = retryAfterSeconds(key, now);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"detail\":\"Too many failed login attempts. Try again later.\"}");
            log.warn("Login rate limit hit for client {}", key);
            return;
        }

        filterChain.doFilter(request, response);

        // 200 = authenticated; 401 (bad credentials) = failure to count.
        int status = response.getStatus();
        if (status == HttpStatus.OK.value()) {
            attempts.remove(key);
        } else if (status == HttpStatus.UNAUTHORIZED.value()) {
            recordFailure(key, now);
        }
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/login".equals(request.getRequestURI());
    }

    private boolean isLockedOut(String key, long now) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return false;
        }
        synchronized (a) {
            if (now - a.windowStartMs >= LOCKOUT_MS) {
                attempts.remove(key); // window elapsed; forget
                return false;
            }
            return a.failures >= MAX_FAILURES;
        }
    }

    private long retryAfterSeconds(String key, long now) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            long remainingMs = Math.max(0, a.windowStartMs + LOCKOUT_MS - now);
            return (remainingMs + 999) / 1000;
        }
    }

    private void recordFailure(String key, long now) {
        attempts.compute(key, (k, a) -> {
            if (a == null || now - a.windowStartMs >= LOCKOUT_MS) {
                a = new Attempt();
                a.windowStartMs = now;
                a.failures = 0;
            }
            a.failures++;
            return a;
        });
    }
}
