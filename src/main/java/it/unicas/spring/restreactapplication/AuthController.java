package it.unicas.spring.restreactapplication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller that handles authentication operations.
 *
 * Exposes three public endpoints (permitted by SecurityConfig):
 *
 *   POST /api/auth/login   → authenticates the user and creates the session
 *   POST /api/auth/logout  → invalidates the current session
 *   GET  /api/auth/me      → returns the authenticated user (or 401)
 *
 * ── LOGIN FLOW ────────────────────────────────────────────────────────────────
 *   1. The client sends { "username": "admin", "password": "admin" } as JSON.
 *   2. AuthenticationManager validates the credentials against UserDetailsService.
 *   3. If valid, the Authentication object is stored in the SecurityContext.
 *   4. The SecurityContext is written into the HTTP session (JSESSIONID).
 *   5. The browser receives the cookie and sends it on every subsequent request.
 *   6. Spring Security reads the session and restores the SecurityContext
 *      automatically on each request.
 *
 * ── LOGOUT FLOW ───────────────────────────────────────────────────────────────
 *   1. The session is invalidated on the server → the JSESSIONID is no longer valid.
 *   2. The SecurityContext is cleared.
 *   3. The browser will discard the expired cookie on the next response.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * AuthenticationManager is exposed as a bean in SecurityConfig
     * and injected here via constructor injection.
     */
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * POST /api/auth/login
     *
     * Authenticates the user with username and password.
     *
     * Request body:
     * { "username": "admin", "password": "admin" }
     *
     * Response 200 OK (session created, JSESSIONID cookie set):
     * { "username": "admin" }
     *
     * Response 401 Unauthorized (wrong credentials):
     * { "error": "Invalid credentials" }
     *
     * @param request  used to create the HTTP session and write the context into it
     * @param body     map containing "username" and "password" from the JSON body
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String username = body.get("username");
        String password = body.get("password");

        try {
            // Validate credentials. Throws BadCredentialsException if wrong.
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));

            // Store the authentication in the current thread's SecurityContext.
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            // Persist the SecurityContext in the HTTP session so that
            // Spring Security can restore it on subsequent requests.
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context);

            return ResponseEntity.ok(Map.of("username", auth.getName()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }
    }

    /**
     * POST /api/auth/logout
     *
     * Invalidates the current session and clears the SecurityContext.
     *
     * Response 200 OK:
     * { "message": "Logged out successfully" }
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        // Invalidate the session → the JSESSIONID will no longer be accepted.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        // Clear the SecurityContext for the current thread.
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * GET /api/auth/me
     *
     * Returns information about the currently authenticated user.
     * Called by the SPA on mount to check whether an active session already exists
     * (e.g. after a page refresh).
     *
     * Response 200 OK (valid session):
     * { "username": "admin" }
     *
     * Response 401 (no session):
     * → handled by SecurityConfig.authenticationEntryPoint
     *
     * @param user  injected by Spring Security with the user from the current session
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(Map.of("username", user.getUsername()));
    }
}

