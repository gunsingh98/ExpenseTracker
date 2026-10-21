package com.expensetracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.expensetracker.filter.JwtAuthFilter;
import com.expensetracker.filter.LoginRateLimitFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Stateless JWT auth for the two household accounts. Users come from
 * {@code app.users[*]} (username + BCrypt hash). If none are configured, a dev
 * {@code admin/admin} account is created <em>only</em> under a dev/test profile;
 * in any other profile a missing user list fails startup.
 *
 * <p>No server-side session is kept ({@link SessionCreationPolicy#STATELESS}):
 * the client authenticates by sending the login-issued bearer token in the
 * {@code Authorization} header. CSRF protection is therefore unnecessary (there
 * is no ambient cookie an attacker's site could ride) and is disabled.
 * Unauthenticated {@code /api/**} calls get a 401 (not a redirect) so the SPA
 * can show its login screen.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Profiles where the throwaway {@code admin/admin} fallback is allowed. */
    private static final Set<String> DEV_PROFILES = Set.of("dev", "test", "default");

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AppProperties props, PasswordEncoder encoder,
                                                 Environment environment) {
        List<UserDetails> users = new ArrayList<>();
        for (AppProperties.Account account : props.getUsers()) {
            // Skip placeholder entries whose env vars are unset (blank username/hash).
            if (account.getUsername() == null || account.getUsername().isBlank()
                    || account.getPasswordHash() == null || account.getPasswordHash().isBlank()) {
                continue;
            }
            users.add(User.withUsername(account.getUsername().trim())
                    .password(account.getPasswordHash().trim())
                    .roles("USER")
                    .build());
        }
        if (users.isEmpty()) {
            if (!isDevProfile(environment)) {
                throw new IllegalStateException(
                        "No 'app.users' configured. Set USER1_NAME/USER1_HASH (and optionally "
                        + "USER2_*) — the admin/admin fallback is only available in a dev/test profile.");
            }
            log.warn("No 'app.users' configured — creating a dev account admin/admin. "
                    + "Set app.users for real use.");
            users.add(User.withUsername("admin").password(encoder.encode("admin")).roles("USER").build());
        }
        return new InMemoryUserDetailsManager(users);
    }

    private static boolean isDevProfile(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true; // no explicit profile == local/default dev run
        }
        return Arrays.stream(active).anyMatch(DEV_PROFILES::contains);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthenticationFilter,
                                           LoginRateLimitFilter loginRateLimitFilter) throws Exception {
        http
                // CSRF stays disabled: every state-changing endpoint authenticates via
                // the Bearer header (which a cross-site page cannot read or forge). The
                // only cookie — the refresh token — is SameSite=Strict, so a cross-site
                // page cannot drive /api/refresh with it either.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; "
                                + "style-src 'self' 'unsafe-inline'; object-src 'none'; "
                                + "base-uri 'self'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        // Auth entry points: login proves credentials; refresh/logout
                        // are authenticated by the refresh cookie, not the access token.
                        .requestMatchers("/api/login", "/api/refresh", "/api/logout").permitAll()
                        // Static SPA assets and client-side routes.
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**",
                                "/vite.svg", "/dashboard", "/expenses", "/investments", "/lists", "/login")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                        .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Dev: the Vite dev server (5173) calls the API on 8080. In production the
        // SPA is served from the same origin as the API, so CORS does not apply.
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**",configuration);

        return source;
    }
}
