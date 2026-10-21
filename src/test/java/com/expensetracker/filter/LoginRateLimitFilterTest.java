package com.expensetracker.filter;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the per-IP brute-force lockout on {@code POST /api/login}.
 */
class LoginRateLimitFilterTest {

    private final LoginRateLimitFilter filter = new LoginRateLimitFilter();

    /** A chain that stamps the given status on the response, mimicking auth result. */
    private static FilterChain chainReturning(int status) {
        return (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
    }

    private MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/login");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void locksOutAfterMaxFailures() throws Exception {
        String ip = "203.0.113.9";

        for (int i = 0; i < LoginRateLimitFilter.MAX_FAILURES; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(loginRequest(ip), res, chainReturning(401));
            assertThat(res.getStatus()).isEqualTo(401); // still passed through to auth
        }

        // The next attempt is short-circuited with 429 before reaching auth.
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest(ip), blocked, chainReturning(401));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void successResetsTheFailureCounter() throws Exception {
        String ip = "203.0.113.10";

        for (int i = 0; i < LoginRateLimitFilter.MAX_FAILURES - 1; i++) {
            filter.doFilter(loginRequest(ip), new MockHttpServletResponse(), chainReturning(401));
        }
        // A success clears the count.
        filter.doFilter(loginRequest(ip), new MockHttpServletResponse(), chainReturning(200));

        // A fresh failure now must not be locked out.
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(loginRequest(ip), res, chainReturning(401));
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void ignoresNonLoginRequests() throws Exception {
        for (int i = 0; i < LoginRateLimitFilter.MAX_FAILURES + 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/expenses");
            req.setRemoteAddr("203.0.113.11");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chainReturning(401));
            assertThat(res.getStatus()).isEqualTo(401); // never rate-limited
        }
    }
}
