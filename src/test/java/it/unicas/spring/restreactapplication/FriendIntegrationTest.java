package it.unicas.spring.restreactapplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests: full CRUD flows exercised exclusively through
 * the HTTP layer (MockMvc) against a real PostgreSQL database (Testcontainers).
 *
 * ── WHAT INTEGRATION TESTS VERIFY ─────────────────────────────────────────────
 * Unlike unit tests (which isolate a single class), integration tests verify that
 * all layers — HTTP → Controller → Repository → Database — work correctly
 * together end-to-end. A bug in the wiring between any two layers will surface
 * here but not in a unit test.
 *
 * ── DEPENDENCY INJECTION & IoC IN THIS TEST CLASS ─────────────────────────────
 * @SpringBootTest boots the complete ApplicationContext, replicating the real
 * production startup sequence. Spring's IoC container:
 *
 *   1. Scans and instantiates all beans (FriendController, FriendRepository, …).
 *   2. Wires their dependencies via constructor injection — exactly as in production.
 *      FriendController receives FriendRepository through its constructor;
 *      FriendController never creates FriendRepository itself (IoC principle).
 *   3. Creates a MockMvc bean (thanks to @AutoConfigureMockMvc) and injects it
 *      into this test class via @Autowired.
 *   4. Injects FriendRepository into this test class via @Autowired for cleanup.
 *
 * The test class itself never calls "new" for any Spring-managed object.
 * Control of creation and wiring is entirely delegated to the framework — this
 * is Inversion of Control applied at every level: production beans, test beans,
 * and even infrastructure (the DataSource configured by @ServiceConnection).
 *
 * ── FIELD INJECTION IN TESTS (@Autowired) ─────────────────────────────────────
 * In production code, field injection is discouraged because it:
 *   - Makes dependencies invisible to callers.
 *   - Prevents using 'final'.
 *   - Requires Spring to instantiate the class (cannot use "new" in plain tests).
 *
 * In test classes it is acceptable because Spring's test runner always manages
 * the lifecycle and always populates @Autowired fields before @Test methods run.
 * There is no scenario where a test instance is created without Spring.
 *
 * ── TESTCONTAINERS ────────────────────────────────────────────────────────────
 * @Container starts a real Docker PostgreSQL container before the test suite.
 * @ServiceConnection reads its JDBC url/username/password and injects them into
 * Spring's DataSource — no changes to application.yaml are needed.
 *
 * ── TEST ISOLATION ────────────────────────────────────────────────────────────
 * @BeforeEach deleteAll() resets the database before every test, so each
 * test is completely independent of the others' side effects.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FriendIntegrationTest {

    private static final String BASE = "/api/v1/friends";

    /**
     * Real PostgreSQL 17 Docker container managed by Testcontainers.
     * Declared static → shared across all test methods in the class (one container
     * per class run, not per test method).
     * @ServiceConnection wires its JDBC coordinates into Spring's DataSource bean.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * MockMvc injected by Spring (IoC / field injection).
     *
     * MockMvc dispatches HTTP requests directly to the DispatcherServlet in-process,
     * exercising the complete stack — filters, controller, JSON serialisation,
     * JPA, and the actual PostgreSQL database — without opening a real TCP socket.
     * This makes tests fast while still being true end-to-end tests.
     */
    @Autowired MockMvc mockMvc;

    /**
     * FriendRepository injected by Spring (IoC / field injection).
     *
     * Used only in @BeforeEach for cleanup (deleteAll). All assertions are made
     * through MockMvc so that the HTTP and controller layers are always exercised.
     * Bypassing the HTTP layer here would turn an integration test into a
     * repository unit test.
     */
    @Autowired FriendRepository friendRepository;

    /** Empty the friends table before every test to ensure isolation. */
    @BeforeEach
    void cleanUp() {
        friendRepository.deleteAll();
    }

    /**
     * Convenience helper: sends POST /api/v1/friends and returns the
     * auto-generated id parsed from the Location response header.
     *
     * Example request body (with phone):
     * { "name": "Luca Bianchi", "email": "luca@x.it", "phone": "123" }
     *
     * Example request body (without phone):
     * { "name": "Sara", "email": "sara@x.it" }
     *
     * Example response 201 Created:
     * Location: /api/v1/friends/42
     * { "id": 42, "name": "Luca Bianchi", "email": "luca@x.it", "phone": "123" }
     *
     * @return the auto-generated id assigned by the database.
     */
    private long createFriend(String name, String email, String phone) throws Exception {
        String body = phone != null
                ? String.format("{\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}", name, email, phone)
                : String.format("{\"name\":\"%s\",\"email\":\"%s\"}", name, email);

        MvcResult result = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        // The Location header contains the URL of the new resource, e.g. /api/v1/friends/42.
        // We extract the id from the last path segment.
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    // ── flow: CREATE → READ ───────────────────────────────────────────────────

    /**
     * Full flow:
     *   Step 1 — POST /api/v1/friends
     *   Request:  { "name": "Luca Bianchi", "email": "luca@x.it", "phone": "123" }
     *   Response: 201 Created, Location: /api/v1/friends/{id}
     *
     *   Step 2 — GET /api/v1/friends/{id}
     *   Response: 200 OK
     *   { "id": <id>, "name": "Luca Bianchi", "email": "luca@x.it", "phone": "123" }
     *
     * Verifies that data written in step 1 is correctly persisted and readable
     * in step 2.
     */
    @Test
    @DisplayName("Flow CREATE → READ: friend created and retrieved correctly")
    void flow_createAndRead() throws Exception {
        long id = createFriend("Luca Bianchi", "luca@x.it", "123");

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name",  is("Luca Bianchi")))
                .andExpect(jsonPath("$.email", is("luca@x.it")))
                .andExpect(jsonPath("$.phone", is("123")));
    }

    // ── flow: CREATE → UPDATE → READ ─────────────────────────────────────────

    /**
     * Full flow:
     *   Step 1 — POST /api/v1/friends
     *   Request:  { "name": "Mario", "email": "mario@x.it", "phone": "100" }
     *   Response: 201 Created
     *
     *   Step 2 — PUT /api/v1/friends/{id}
     *   Request:  { "name": "Mario Rossi", "email": "mario@x.it", "phone": "999" }
     *   Response: 200 OK
     *   { "id": <id>, "name": "Mario Rossi", "email": "mario@x.it", "phone": "999" }
     *
     *   Step 3 — GET /api/v1/friends/{id}
     *   Response: 200 OK
     *   { "name": "Mario Rossi" }  ← confirms the update survived the DB round-trip
     *
     * Verifies that the PUT operation persists changes durably, not just in the
     * HTTP response object.
     */
    @Test
    @DisplayName("Flow CREATE → UPDATE → READ: updated data persisted correctly")
    void flow_createUpdateRead() throws Exception {
        long id = createFriend("Mario", "mario@x.it", "100");

        mockMvc.perform(put(BASE + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mario Rossi\",\"email\":\"mario@x.it\",\"phone\":\"999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name",  is("Mario Rossi")))
                .andExpect(jsonPath("$.phone", is("999")));

        // A second GET confirms the update is durable (not just in the response).
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(jsonPath("$.name", is("Mario Rossi")));
    }

    // ── flow: CREATE → DELETE → READ ─────────────────────────────────────────

    /**
     * Full flow:
     *   Step 1 — POST /api/v1/friends
     *   Request:  { "name": "Sara", "email": "sara@x.it" }
     *   Response: 201 Created
     *
     *   Step 2 — DELETE /api/v1/friends/{id}
     *   Response: 204 No Content  (empty body — the resource has been removed)
     *
     *   Step 3 — GET /api/v1/friends/{id}
     *   Response: 404 Not Found  (the record no longer exists in the database)
     *
     * Verifies that DELETE removes the record permanently.
     */
    @Test
    @DisplayName("Flow CREATE → DELETE → READ: deleted friend returns 404")
    void flow_createDeleteRead() throws Exception {
        long id = createFriend("Sara", "sara@x.it", null);

        mockMvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isNoContent()); // 204 — empty body

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isNotFound());  // 404 — record is gone
    }

    // ── pagination ────────────────────────────────────────────────────────────

    /**
     * Action: creates 6 friends, then fetches page 0 and page 1.
     * PAGE_SIZE = 5, so the 6 records are split across 2 pages.
     *
     * GET /api/v1/friends?page=0
     * Expected 200 OK:
     * {
     *   "content":       [ …5 friends sorted by name… ],
     *   "totalElements": 6,
     *   "totalPages":    2
     * }
     *
     * GET /api/v1/friends?page=1
     * Expected 200 OK:
     * { "content": [ …1 friend… ] }
     */
    @Test
    @DisplayName("Pagination: 6 friends → page 0 = 5 items, page 1 = 1 item")
    void pagination_sixFriends() throws Exception {
        for (int i = 1; i <= 6; i++) {
            createFriend("Amico" + i, "amico" + i + "@x.it", null);
        }

        mockMvc.perform(get(BASE).param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(6)))
                .andExpect(jsonPath("$.totalPages",    is(2)))
                .andExpect(jsonPath("$.content",       hasSize(5)));

        mockMvc.perform(get(BASE).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    /**
     * Action: creates 1 friend, then requests page 99 (far beyond the last page).
     *
     * Spring Data returns an empty page rather than an error for out-of-bound pages.
     *
     * Expected 200 OK:
     * { "content": [], "totalElements": 1 }
     */
    @Test
    @DisplayName("Pagination: page beyond the end returns empty content, not an error")
    void pagination_outOfBound() throws Exception {
        createFriend("Solo", "solo@x.it", null);

        mockMvc.perform(get(BASE).param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ── search ────────────────────────────────────────────────────────────────

    /**
     * Action: creates two friends, then searches with uppercase "GIO".
     * The search is case-insensitive (LOWER() in the JPQL query).
     *
     * GET /api/v1/friends?q=GIO
     * Expected 200 OK:
     * {
     *   "content":       [{ "name": "Giovanna Ferri" }],
     *   "totalElements": 1
     * }
     * "Roberto Blu" is excluded because neither his name nor email contains "gio".
     */
    @Test
    @DisplayName("Search: partial name match is case-insensitive")
    void search_partialNameCaseInsensitive() throws Exception {
        createFriend("Giovanna Ferri", "giovanna@x.it", null);
        createFriend("Roberto Blu",    "roberto@x.it",  null);

        mockMvc.perform(get(BASE).param("q", "GIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is("Giovanna Ferri")));
    }

    /**
     * Action: creates 3 friends (2 with @unicas.it, 1 with @gmail.com),
     * then searches by email domain "unicas".
     *
     * GET /api/v1/friends?q=unicas
     * Expected 200 OK:
     * {
     *   "content":       [{ "email": "a@unicas.it" }, { "email": "b@unicas.it" }],
     *   "totalElements": 2
     * }
     * "Studente" (s@gmail.com) is excluded.
     */
    @Test
    @DisplayName("Search: filter by email domain")
    void search_byEmailDomain() throws Exception {
        createFriend("Docente A", "a@unicas.it", null);
        createFriend("Docente B", "b@unicas.it", null);
        createFriend("Studente",  "s@gmail.com", null);

        mockMvc.perform(get(BASE).param("q", "unicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    /**
     * Action: creates a friend with phone "0776-123456", then searches by "0776".
     *
     * GET /api/v1/friends?q=0776
     * Expected 200 OK:
     * {
     *   "content":       [{ "name": "Tel Test", "phone": "0776-123456" }],
     *   "totalElements": 1
     * }
     */
    @Test
    @DisplayName("Search: filter by phone prefix")
    void search_byPhone() throws Exception {
        createFriend("Tel Test", "tel@x.it", "0776-123456");

        mockMvc.perform(get(BASE).param("q", "0776"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    // ── error handling ────────────────────────────────────────────────────────

    /**
     * Action: sends PUT /api/v1/friends/99999 when no friend with that id exists.
     *
     * Request body:
     * { "name": "X", "email": "x@x.it" }
     *
     * Expected response: 404 Not Found.
     * The controller calls findById(99999), gets Optional.empty(), and throws
     * ResponseStatusException(NOT_FOUND), which Spring maps to HTTP 404.
     */
    @Test
    @DisplayName("PUT on non-existent id returns 404")
    void update_nonExistentId_returns404() throws Exception {
        mockMvc.perform(put(BASE + "/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@x.it\"}"))
                .andExpect(status().isNotFound());
    }

    /**
     * Action: sends DELETE /api/v1/friends/99999 when no friend with that id exists.
     *
     * Expected response: 404 Not Found.
     * The controller calls existsById(99999), gets false, and throws
     * ResponseStatusException(NOT_FOUND).
     */
    @Test
    @DisplayName("DELETE on non-existent id returns 404")
    void delete_nonExistentId_returns404() throws Exception {
        mockMvc.perform(delete(BASE + "/99999"))
                .andExpect(status().isNotFound());
    }

    /**
     * Action: creates three friends with names "Zara", "Anna", "Marco"
     * (inserted in that order), then calls GET /api/v1/friends.
     *
     * The controller applies Sort.by("name"), so the database returns records
     * in alphabetical order regardless of insertion order.
     *
     * Expected 200 OK:
     * {
     *   "content": [
     *     { "name": "Anna"  },   ← A comes before M and Z
     *     { "name": "Marco" },
     *     { "name": "Zara"  }
     *   ]
     * }
     */
    @Test
    @DisplayName("Results are sorted alphabetically by name")
    void getAll_orderedByName() throws Exception {
        createFriend("Zara",  "zara@x.it",  null);
        createFriend("Anna",  "anna@x.it",  null);
        createFriend("Marco", "marco@x.it", null);

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", is("Anna")))
                .andExpect(jsonPath("$.content[1].name", is("Marco")))
                .andExpect(jsonPath("$.content[2].name", is("Zara")));
    }
}
