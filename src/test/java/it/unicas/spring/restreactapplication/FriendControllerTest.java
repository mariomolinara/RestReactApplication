package it.unicas.spring.restreactapplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests executed via MockMvc against a real PostgreSQL database
 * provided by Testcontainers.
 *
 * ── WHAT IS A MOCK? ───────────────────────────────────────────────────────────
 * A "mock" is a test double — a substitute object that simulates the behaviour
 * of a real component without actually being one.
 *
 * Mocks are used to:
 *   1. Replace slow or unavailable dependencies (network, filesystem, database).
 *   2. Isolate the unit under test so that failures pinpoint the right layer.
 *   3. Control what a dependency returns so edge cases can be reproduced reliably.
 *
 * The most popular mocking library in the Java ecosystem is Mockito.
 * Example of a Mockito mock (NOT used here — shown for comparison only):
 *
 *   FriendRepository repo = Mockito.mock(FriendRepository.class);
 *   when(repo.findById(1L)).thenReturn(Optional.of(new Friend("Alice", …)));
 *   // repo.findById(1L) now returns the fake Optional without touching the DB.
 *
 * In this test class we do NOT mock FriendRepository because we want to test
 * the full stack all the way to a real PostgreSQL database.
 * The only "mocks" used here are:
 *   - MockMvc      → mocks the HTTP transport layer (no TCP socket)
 *   - @WithMockUser → mocks the authenticated principal (no real login)
 *
 * ── WHAT IS MockMvc? ──────────────────────────────────────────────────────────
 * MockMvc is a Spring Test utility that lets you fire HTTP requests against the
 * DispatcherServlet in-process, without starting a real HTTP server or opening
 * any network socket.
 *
 * It is NOT a full mock of the HTTP stack: filters, interceptors, controller
 * advice, JSON serialisation, and Spring Security filters all run normally.
 * What is "mocked" (i.e., bypassed) is only the TCP transport — the request
 * never leaves the JVM. This makes tests:
 *   - Fast:         no port binding, no socket overhead.
 *   - Deterministic: no flakiness from random port allocation or OS limits.
 *   - Inspectable:  MockMvc gives direct access to status, headers and body.
 *
 * Typical usage pattern:
 *   mockMvc.perform(get("/api/v1/friends"))    // build & dispatch the request
 *          .andExpect(status().isOk())          // assert HTTP status
 *          .andExpect(jsonPath("$.content", hasSize(2)));  // assert JSON body
 *
 * ── WHAT IS @WithMockUser? ────────────────────────────────────────────────────
 * Spring Security requires an authenticated principal for every request to
 * protected endpoints. In tests, performing a real login (POST /api/auth/login,
 * receive cookie, attach cookie to every request) would be verbose and slow.
 *
 * @WithMockUser is a Spring Security Test annotation that injects a synthetic
 * UserDetails object directly into the SecurityContext before each @Test method
 * runs, completely bypassing the authentication filter chain.
 *
 * By default it creates a user with:
 *   - username: "user"
 *   - password: "password" (never verified — authentication is skipped)
 *   - roles:    ["USER"]  → authorities: ["ROLE_USER"]
 *
 * You can customise it:
 *   @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
 *
 * Applied at the class level (as here) it affects every @Test method in the
 * class. It can also be applied per-method to override the class-level default.
 *
 * ── DEPENDENCY INJECTION IN TESTS ─────────────────────────────────────────────
 * @SpringBootTest tells Spring to start the full ApplicationContext (all beans,
 * auto-configuration, JPA, web layer, security, …) exactly as it would in production.
 *
 * @AutoConfigureMockMvc asks Spring to also create a MockMvc bean and add it to
 * the context. MockMvc simulates HTTP requests without opening a real TCP socket,
 * making tests faster and deterministic.
 *
 * @Autowired on the fields below is field injection. In production code this
 * style is discouraged (harder to test without Spring), but in test classes it
 * is perfectly fine: Spring's test framework manages the lifecycle and populates
 * annotated fields before any @Test method is called.
 *
 * ── INVERSION OF CONTROL IN TESTS ─────────────────────────────────────────────
 * This class never calls "new MockMvc()" or "new FriendRepository()".
 * Spring's IoC container creates both beans and injects them here — the same
 * principle that applies in production code. The test author only declares what
 * is needed; the framework decides how and when to provide it.
 *
 * ── DATABASE ISOLATION ────────────────────────────────────────────────────────
 * @Testcontainers + @Container start a real Docker PostgreSQL instance for the
 * entire test class. @ServiceConnection wires its JDBC URL and credentials into
 * Spring's DataSource automatically — IoC applied at the infrastructure level.
 * @BeforeEach deleteAll() guarantees every test starts with an empty table.
 *
 * ── AUTHENTICATION IN TESTS ───────────────────────────────────────────────────
 * All /api/v1/** endpoints require an authenticated session (Spring Security).
 * @WithMockUser at the class level injects a synthetic "user" principal into the
 * SecurityContext for every @Test method, bypassing the real login flow.
 * This keeps tests focused on controller behaviour rather than the auth protocol,
 * which is covered separately by integration tests for AuthController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser   // injects a mock USER-role principal into every test's SecurityContext
class FriendControllerTest {

    private static final String BASE = "/api/v1/friends";

    /**
     * Testcontainers manages a real PostgreSQL 17 Docker container.
     * @ServiceConnection reads the container's host/port/credentials and injects
     * them into Spring's DataSource bean — IoC at the infrastructure level.
     *
     * Note: this is NOT a mock. Testcontainers starts an actual Docker container
     * running real PostgreSQL. SQL statements, constraints, and transactions are
     * all executed against a genuine database engine.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * MockMvc is injected by Spring (IoC/DI).
     *
     * It dispatches requests directly to the DispatcherServlet in-process,
     * exercising the full HTTP stack (Security filters, controller, JSON
     * serialisation, JPA, real PostgreSQL) without opening a real network socket.
     *
     * Think of MockMvc as a "mock HTTP client + mock HTTP server transport":
     * the request/response objects are constructed in memory, but every Spring
     * component in between (filters, interceptors, controller) runs for real.
     */
    @Autowired MockMvc mockMvc;

    /**
     * FriendRepository is injected by Spring (IoC/DI).
     *
     * This is the REAL Spring Data JPA proxy — not a Mockito mock.
     * It is used only for test setup/teardown (deleteAll and direct inserts)
     * to avoid routing setup data through the HTTP layer.
     * All HTTP-level assertions go through MockMvc so the controller is always
     * part of the test.
     */
    @Autowired FriendRepository friendRepository;

    /** Ensure a clean database state before every test method. */
    @BeforeEach
    void cleanUp() {
        friendRepository.deleteAll();
    }

    // ── GET /api/v1/friends ────────────────────────────────────────────────────

    /**
     * Action: sends GET /api/v1/friends with an empty database.
     *
     * Expected response 200 OK:
     * {
     *   "content":       [],
     *   "totalElements": 0,
     *   "totalPages":    0
     * }
     */
    @Test
    @DisplayName("GET /friends - empty list")
    void getAll_emptyList() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    /**
     * Action: inserts two friends directly via JPA, then calls GET /api/v1/friends.
     *
     * Expected response 200 OK:
     * {
     *   "content": [
     *     { "id": 1, "name": "Alice", "email": "alice@x.it", "phone": "111" },
     *     { "id": 2, "name": "Bob",   "email": "bob@x.it",   "phone": "222" }
     *   ],
     *   "totalElements": 2
     * }
     */
    @Test
    @DisplayName("GET /friends - returns all inserted friends")
    void getAll_withFriends() throws Exception {
        friendRepository.saveAll(List.of(
                new Friend("Alice", "alice@x.it", "111"),
                new Friend("Bob",   "bob@x.it",   "222")
        ));
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[*].name", hasItems("Alice", "Bob")));
    }

    /**
     * Action: inserts 6 friends, then fetches page 0 (PAGE_SIZE = 5) and page 1.
     *
     * GET /api/v1/friends?page=0
     * Expected 200 OK:
     * { "content": [ …5 friends… ], "totalElements": 6, "totalPages": 2 }
     *
     * GET /api/v1/friends?page=1
     * Expected 200 OK:
     * { "content": [ …1 friend… ] }
     */
    @Test
    @DisplayName("GET /friends - pagination: page 0 has 5 items, page 1 has 1")
    void getAll_pagination() throws Exception {
        for (int i = 1; i <= 6; i++) {
            friendRepository.save(new Friend("Amico" + i, "amico" + i + "@x.it", null));
        }
        mockMvc.perform(get(BASE).param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(6)))
                .andExpect(jsonPath("$.content", hasSize(5)));

        mockMvc.perform(get(BASE).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    /**
     * Action: inserts two friends, then calls GET /api/v1/friends?q=alice.
     * Only "Alice Rossi" matches; "Marco Blu" is excluded.
     *
     * Expected response 200 OK:
     * {
     *   "content":       [{ "name": "Alice Rossi", "email": "alice@x.it" }],
     *   "totalElements": 1
     * }
     */
    @Test
    @DisplayName("GET /friends?q=alice - search by name")
    void getAll_searchByName() throws Exception {
        friendRepository.saveAll(List.of(
                new Friend("Alice Rossi", "alice@x.it", null),
                new Friend("Marco Blu",   "marco@x.it", null)
        ));
        mockMvc.perform(get(BASE).param("q", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is("Alice Rossi")));
    }

    /**
     * Action: inserts two friends with different email domains, then calls
     * GET /api/v1/friends?q=unicas.
     * Only "Paola" (paola@unicas.it) matches; "Marco" (gmail) is excluded.
     *
     * Expected response 200 OK:
     * {
     *   "content":       [{ "name": "Paola", "email": "paola@unicas.it" }],
     *   "totalElements": 1
     * }
     */
    @Test
    @DisplayName("GET /friends?q=unicas - search by email domain")
    void getAll_searchByEmail() throws Exception {
        friendRepository.saveAll(List.of(
                new Friend("Paola", "paola@unicas.it", null),
                new Friend("Marco", "marco@gmail.com", null)
        ));
        mockMvc.perform(get(BASE).param("q", "unicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is("Paola")));
    }

    /**
     * Action: inserts two friends (one with phone "333-333", one with null phone),
     * then calls GET /api/v1/friends?q=333.
     * Only "Diana" matches; "Carlo" has no phone and the COALESCE guard in the
     * JPQL query prevents any NullPointerException.
     *
     * Expected response 200 OK:
     * {
     *   "content":       [{ "name": "Diana", "phone": "333-333" }],
     *   "totalElements": 1
     * }
     */
    @Test
    @DisplayName("GET /friends?q=333 - search by phone number")
    void getAll_searchByPhone() throws Exception {
        friendRepository.saveAll(List.of(
                new Friend("Diana", "diana@x.it", "333-333"),
                new Friend("Carlo", "carlo@x.it", null)
        ));
        mockMvc.perform(get(BASE).param("q", "333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is("Diana")));
    }

    /**
     * Action: inserts one friend, then calls GET /api/v1/friends?q=xyz_nessuno.
     * The search term matches no name, email or phone.
     *
     * Expected response 200 OK:
     * { "content": [], "totalElements": 0 }
     */
    @Test
    @DisplayName("GET /friends?q=xyz - no match returns empty list")
    void getAll_searchNoMatch() throws Exception {
        friendRepository.save(new Friend("Luca", "luca@x.it", null));
        mockMvc.perform(get(BASE).param("q", "xyz_nessuno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    // ── GET /api/v1/friends/{id} ───────────────────────────────────────────────

    /**
     * Action: inserts a friend via JPA (to get a real auto-generated id),
     * then calls GET /api/v1/friends/{id}.
     *
     * Expected response 200 OK:
     * {
     *   "id":    <auto-generated>,
     *   "name":  "Elena",
     *   "email": "elena@x.it",
     *   "phone": "555"
     * }
     */
    @Test
    @DisplayName("GET /friends/{id} - friend found")
    void getById_found() throws Exception {
        Friend saved = friendRepository.save(new Friend("Elena", "elena@x.it", "555"));
        mockMvc.perform(get(BASE + "/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Elena")))
                .andExpect(jsonPath("$.email", is("elena@x.it")));
    }

    /**
     * Action: calls GET /api/v1/friends/9999 on an empty database.
     * No friend with id 9999 exists.
     *
     * Expected response: 404 Not Found.
     */
    @Test
    @DisplayName("GET /friends/{id} - non-existent id → 404")
    void getById_notFound() throws Exception {
        mockMvc.perform(get(BASE + "/9999"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/friends ───────────────────────────────────────────────────

    /**
     * Action: sends POST /api/v1/friends with a valid JSON body.
     *
     * Request body:
     * { "name": "Nuovo Amico", "email": "nuovo@x.it", "phone": "999" }
     *
     * Expected response 201 Created:
     * Location header: /api/v1/friends/<new-id>
     * {
     *   "id":    <auto-generated>,
     *   "name":  "Nuovo Amico",
     *   "email": "nuovo@x.it",
     *   "phone": "999"
     * }
     */
    @Test
    @DisplayName("POST /friends - creates friend → 201 Created")
    void create_success() throws Exception {
        String body = """
                {"name":"Nuovo Amico","email":"nuovo@x.it","phone":"999"}
                """;
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/friends/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("Nuovo Amico")));
    }

    // ── PUT /api/v1/friends/{id} ───────────────────────────────────────────────

    /**
     * Action: inserts "Mario" via JPA, then sends PUT /api/v1/friends/{id}
     * to change his name and phone.
     *
     * Request body:
     * { "name": "Mario Rossi", "email": "mario@x.it", "phone": "200" }
     *
     * Expected response 200 OK:
     * {
     *   "id":    <same as before>,
     *   "name":  "Mario Rossi",
     *   "email": "mario@x.it",
     *   "phone": "200"
     * }
     */
    @Test
    @DisplayName("PUT /friends/{id} - successful update")
    void update_success() throws Exception {
        Friend saved = friendRepository.save(new Friend("Mario", "mario@x.it", "100"));
        String updated = """
                {"name":"Mario Rossi","email":"mario@x.it","phone":"200"}
                """;
        mockMvc.perform(put(BASE + "/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Mario Rossi")))
                .andExpect(jsonPath("$.phone", is("200")));
    }

    /**
     * Action: sends PUT /api/v1/friends/9999 when no friend with that id exists.
     *
     * Request body:
     * { "name": "X", "email": "x@x.it" }
     *
     * Expected response: 404 Not Found.
     */
    @Test
    @DisplayName("PUT /friends/{id} - non-existent id → 404")
    void update_notFound() throws Exception {
        mockMvc.perform(put(BASE + "/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"x@x.it"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/v1/friends/{id} ───────────────────────────────────────────

    /**
     * Action: inserts "Sara" via JPA, sends DELETE /api/v1/friends/{id},
     * then verifies the resource is no longer reachable.
     *
     * DELETE /api/v1/friends/{id}  → 204 No Content  (empty body)
     * GET    /api/v1/friends/{id}  → 404 Not Found   (resource is gone)
     */
    @Test
    @DisplayName("DELETE /friends/{id} - deletion → 204, subsequent GET → 404")
    void delete_success() throws Exception {
        Friend saved = friendRepository.save(new Friend("Sara", "sara@x.it", null));
        mockMvc.perform(delete(BASE + "/" + saved.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + saved.getId()))
                .andExpect(status().isNotFound());
    }

    /**
     * Action: sends DELETE /api/v1/friends/9999 when no friend with that id exists.
     *
     * Expected response: 404 Not Found.
     */
    @Test
    @DisplayName("DELETE /friends/{id} - non-existent id → 404")
    void delete_notFound() throws Exception {
        mockMvc.perform(delete(BASE + "/9999"))
                .andExpect(status().isNotFound());
    }
}
