package it.unicas.spring.restreactapplication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * ── WHAT IS A SPRING BEAN? ────────────────────────────────────────────────────
 * A "bean" is simply an object whose entire lifecycle — creation, configuration,
 * wiring, and destruction — is managed by the Spring IoC (Inversion of Control)
 * container rather than by application code.
 *
 * Without Spring you would write:
 *
 *   PasswordEncoder encoder = new BCryptPasswordEncoder();       // you create it
 *   UserDetailsService uds  = new InMemoryUserDetailsManager(…); // you create it
 *   // you must also pass encoder to uds manually — tight coupling
 *
 * With Spring beans you declare WHAT you need; Spring decides HOW and WHEN to
 * build it and wires everything together automatically (Dependency Injection):
 *
 *   @Bean PasswordEncoder passwordEncoder()        { return new BCryptPasswordEncoder(); }
 *   @Bean UserDetailsService userDetailsService(PasswordEncoder encoder) { … }
 *   //                                             ↑ Spring injects the bean above
 *
 * Key properties of Spring beans:
 *
 *   Singleton by default — Spring creates ONE instance per bean definition and
 *   reuses it everywhere it is needed. You can change the scope to "prototype"
 *   (new instance per injection), "request", or "session" if needed.
 *
 *   Lazy or eager — by default beans are created eagerly at startup. The
 *   @Lazy annotation defers creation until the bean is first requested.
 *
 *   Named — each bean has a name (defaults to the method name). Two beans of
 *   the same type but different names can coexist; @Qualifier selects which one.
 *
 * How Spring discovers beans in this project:
 *
 *   @SpringBootApplication on RestReactApplication triggers a component scan of
 *   the package tree. It finds:
 *     @Configuration classes  → treated as factory classes; every @Bean method
 *                               inside is called once and the return value is
 *                               registered in the ApplicationContext.
 *     @Component / @Service /
 *     @Repository / @RestController → each annotated class is itself registered
 *                               as a singleton bean.
 *
 * This class (@Configuration) declares four beans:
 *   1. SecurityFilterChain  — the HTTP security rules applied to every request
 *   2. UserDetailsService   — how Spring Security loads user data for authentication
 *   3. PasswordEncoder      — the algorithm used to hash and verify passwords
 *   4. AuthenticationManager— the entry point for programmatic authentication
 *
 * ── STRATEGY ──────────────────────────────────────────────────────────────────
 * HTTP session-based authentication (JSESSIONID cookie):
 *   1. The client sends POST /api/auth/login with {username, password}.
 *   2. The server validates the credentials and creates a session; the browser
 *      receives the JSESSIONID cookie and sends it automatically on every
 *      subsequent same-origin request.
 *   3. All calls to /api/v1/** require a valid session.
 *      If absent → 401 JSON (not a redirect to /login as classic form login would do).
 *
 * ── PUBLIC RESOURCES ─────────────────────────────────────────────────────────
 *   - Static assets (index.html, app.js, styles.css) → always accessible.
 *   - /api/auth/**  → login, logout, me (handled by AuthController).
 *   - /actuator/health → Docker HEALTHCHECK probe.
 *
 * ── CSRF ──────────────────────────────────────────────────────────────────────
 * CSRF is disabled because the SPA is served from the same origin as the backend
 * (no cross-origin) and does not use traditional HTML forms. In production with
 * the frontend on a separate domain it should be re-enabled and the token passed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Bean 1 — SecurityFilterChain
     *
     * A bean of type SecurityFilterChain is the central object Spring Security
     * looks for to know how to protect the application. It is essentially a list
     * of servlet filters that are applied in order to every incoming HTTP request.
     *
     * Spring Security auto-detects this bean in the ApplicationContext and inserts
     * the filter chain into the servlet pipeline — no XML, no manual registration.
     *
     * The HttpSecurity parameter is itself a bean provided by Spring Security's
     * auto-configuration. Spring injects it here automatically (IoC/DI).
     *
     * Rule ordering: more specific matchers must come before more generic ones.
     * Spring Security evaluates rules top-to-bottom and stops at the first match.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF disabled (same-origin SPA, JSON API) ─────────────────
            // CSRF (Cross-Site Request Forgery) protection works by embedding a
            // secret token in every HTML form. Because this API is consumed by
            // a same-origin SPA using JSON (not HTML forms), CSRF is not applicable.
            .csrf(csrf -> csrf.disable())

            // ── Authorization rules ────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // SPA static resources — must be public so the browser can load
                // the application before the user has a session.
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/app.js",
                    "/styles.css",
                    "/favicon.ico"
                ).permitAll()
                // Docker health-check endpoint — must be public so the Docker
                // HEALTHCHECK can probe the app without a session cookie.
                .requestMatchers("/actuator/health").permitAll()
                // Authentication endpoints — must be public because the login
                // endpoint is called before any session exists.
                .requestMatchers("/api/auth/**").permitAll()
                // Everything else (i.e. /api/v1/**) requires an authenticated session.
                // If the request has no valid JSESSIONID → authenticationEntryPoint below.
                .anyRequest().authenticated()
            )

            // ── Return 401 JSON instead of redirecting to /login ───────────
            // Spring Security's default AuthenticationEntryPoint sends an HTTP
            // 302 redirect to a /login HTML page. A JSON SPA does not need an
            // HTML login page — it renders its own. So we replace the entry point
            // with a lambda that writes a 401 JSON body instead.
            //
            // The lambda receives:
            //   request       — the original HttpServletRequest
            //   response      — the HttpServletResponse we write to
            //   authException — the exception that triggered the 401
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Login required\"}");
                })
            )

            // ── Session management (server-side sessions, default IF_REQUIRED) ──
            // Spring Security creates a server-side HttpSession when a user logs in.
            // The session id is sent to the browser as the JSESSIONID cookie.
            // maximumSessions(5) limits how many concurrent sessions one user can have;
            // the oldest session is invalidated when the limit is exceeded.
            .sessionManagement(session -> session
                .maximumSessions(5)
            );

        return http.build();
    }

    /**
     * Bean 2 — UserDetailsService
     *
     * UserDetailsService is a Spring Security interface with a single method:
     *   UserDetails loadUserByUsername(String username)
     *
     * Spring Security calls it during authentication to fetch the stored user
     * data (hashed password, roles, account status) for the username that was
     * submitted. It then compares the submitted password (after hashing) against
     * the stored hash to decide whether to grant access.
     *
     * InMemoryUserDetailsManager is a built-in implementation that stores users
     * in a plain HashMap. It is appropriate for demos and single-user apps.
     * In production, replace it with a JPA-backed implementation that reads
     * users from the database.
     *
     * The @Value annotations inject property values from application.yaml (or
     * environment variables) at the time Spring constructs this bean:
     *   ${app.security.username} → "admin" (default) or APP_SECURITY_USERNAME env var
     *   ${app.security.password} → "admin" (default) or APP_SECURITY_PASSWORD env var
     *
     * The PasswordEncoder parameter is resolved by Spring from Bean 3 below —
     * another example of IoC: we declare what we need; Spring provides it.
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.username}") String username,
            @Value("${app.security.password}") String password,
            PasswordEncoder encoder) {  // ← Spring injects Bean 3 here

        UserDetails user = User.builder()
                .username(username)
                // The password is hashed here at startup, not stored in plain text.
                // Every login attempt re-hashes the submitted password and compares
                // it against this stored hash — BCrypt handles the salt automatically.
                .password(encoder.encode(password))
                // roles("USER") is shorthand for authorities("ROLE_USER").
                // Spring Security prefixes role names with "ROLE_" by convention.
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }

    /**
     * Bean 3 — PasswordEncoder
     *
     * PasswordEncoder is a Spring Security interface with two methods:
     *   String  encode(CharSequence rawPassword)         — hashes a plain-text password
     *   boolean matches(CharSequence raw, String encoded) — checks a login attempt
     *
     * BCryptPasswordEncoder uses the BCrypt adaptive hashing algorithm:
     *   - Automatically generates and embeds a random 128-bit salt in the hash,
     *     so two identical passwords produce different hashes (prevents rainbow tables).
     *   - Has a configurable "work factor" (default 10) that controls how many
     *     hashing rounds are performed, making brute-force attacks progressively slower
     *     as hardware improves.
     *
     * Example:
     *   encoder.encode("admin")
     *   → "$2a$10$Ei7v9Qz...XkW" (a 60-character BCrypt hash — different every time)
     *
     *   encoder.matches("admin", "$2a$10$Ei7v9Qz...XkW") → true
     *   encoder.matches("wrong", "$2a$10$Ei7v9Qz...XkW") → false
     *
     * This bean is declared separately (not inline) so that Spring can inject it
     * wherever a PasswordEncoder is needed — here in userDetailsService(), and
     * potentially in other beans in the future.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Bean 4 — AuthenticationManager
     *
     * AuthenticationManager is the central Spring Security interface for
     * programmatic authentication:
     *   Authentication authenticate(Authentication token) throws AuthenticationException
     *
     * It is used by AuthController.login() to validate the credentials submitted
     * via POST /api/auth/login. The controller passes a
     * UsernamePasswordAuthenticationToken; the manager delegates to the configured
     * UserDetailsService (Bean 2) and PasswordEncoder (Bean 3) to verify them.
     *
     * Spring Security creates and configures the AuthenticationManager internally,
     * but does NOT expose it as a bean by default. This method retrieves it from
     * AuthenticationConfiguration (itself a bean provided by Spring Security's
     * auto-configuration) and re-registers it so that AuthController can inject it.
     *
     * Without this @Bean method, AuthController could not use constructor injection
     * for AuthenticationManager and would have to use the now-deprecated
     * WebSecurityConfigurerAdapter approach.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

