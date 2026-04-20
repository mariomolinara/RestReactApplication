package it.unicas.spring.restreactapplication;

import java.net.URI;

import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * REST controller that exposes CRUD operations for the Friend resource.
 * Base URL: /api/v1/friends
 *
 * ── INVERSION OF CONTROL (IoC) ────────────────────────────────────────────────
 * Traditionally a class is responsible for creating the objects it depends on:
 *
 *   // Without IoC — tight coupling:
 *   FriendRepository repo = new FriendRepositoryImpl();
 *
 * With IoC the control over object creation is "inverted": the framework
 * (Spring) — not the class itself — decides when to create objects and how to
 * wire them together. The class simply declares what it needs.
 *
 * ── DEPENDENCY INJECTION (DI) ─────────────────────────────────────────────────
 * DI is the most common mechanism used to implement IoC.
 * Instead of creating its collaborators, a class receives them from outside.
 * Spring supports three injection styles:
 *   1. Constructor injection  ← used here (recommended)
 *   2. Setter injection
 *   3. Field injection (@Autowired on a field — discouraged; hurts testability)
 *
 * Spring's IoC container (ApplicationContext) is responsible for:
 *   a) Scanning the classpath for @Component, @Service, @Repository,
 *      @RestController, etc. and registering them as singleton beans.
 *   b) Resolving each bean's constructor parameters and injecting them.
 *
 * Here FriendRepository is the dependency. Spring detects this constructor,
 * finds the FriendRepository bean it already created (a JPA proxy), and injects
 * it automatically. The controller never calls "new" — Spring handles it all.
 */
@RestController  // Registers this class as a Spring-managed bean.
                 // Combines @Controller + @ResponseBody: return values are
                 // automatically serialised to JSON by Jackson.
@RequestMapping("/api/v1/friends")
public class FriendController {

    /** Maximum number of friends returned per page. */
    private static final int PAGE_SIZE = 5;

    /**
     * The repository is declared as a dependency — not instantiated here.
     * 'final' enforces single assignment (only in the constructor), making the
     * class immutable with respect to this dependency — a best practice with
     * constructor injection.
     */
    private final FriendRepository friendRepository;

    /**
     * Constructor injection — the preferred DI style in Spring.
     *
     * When Spring instantiates FriendController it inspects this constructor,
     * sees that a FriendRepository is required, finds the matching bean in the
     * ApplicationContext, and passes it in automatically.
     *
     * Advantages over field injection:
     *   - Dependencies are explicit and mandatory (no silent null surprises).
     *   - Enables the 'final' modifier → immutability.
     *   - The class is testable without Spring:
     *       FriendRepository mock = Mockito.mock(FriendRepository.class);
     *       FriendController ctrl = new FriendController(mock);
     *
     * @param friendRepository injected by Spring's IoC container at startup.
     */
    public FriendController(FriendRepository friendRepository) {
        this.friendRepository = friendRepository;
    }

    /**
     * GET /api/v1/friends?page=0&q=
     *
     * Returns a paginated list of friends sorted alphabetically by name.
     * When 'q' is provided, only friends whose name, email or phone contain
     * that string (case-insensitive) are included.
     *
     * Example — no filter, page 0:
     * Response 200 OK:
     * {
     *   "content": [
     *     { "id": 1, "name": "Alice Rossi", "email": "alice@x.it", "phone": "111" },
     *     { "id": 2, "name": "Bob Bianchi",  "email": "bob@x.it",   "phone": "222" }
     *   ],
     *   "totalElements": 2,
     *   "totalPages":    1,
     *   "number":        0,
     *   "size":          5
     * }
     *
     * Example — with search:  GET /api/v1/friends?q=alice&page=0
     *
     * @param page zero-based page index (default 0).
     * @param q   optional search term (default empty = no filter).
     */
    @GetMapping
    public Page<Friend> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("name"));

        if (q.isBlank()) {
            return friendRepository.findAll(pageable);
        }
        return friendRepository.search(q, pageable);
    }

    /**
     * GET /api/v1/friends/{id}
     *
     * Returns the friend identified by the given id.
     *
     * Example — GET /api/v1/friends/1
     * Response 200 OK:
     * {
     *   "id":    1,
     *   "name":  "Alice Rossi",
     *   "email": "alice@x.it",
     *   "phone": "111-111"
     * }
     *
     * If no friend with that id exists → 404 Not Found.
     *
     * @param id the friend's primary key, extracted from the URL path.
     */
    @GetMapping("/{id}")
    public Friend findById(@PathVariable Long id) {
        return friendRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend not found"));
    }

    /**
     * POST /api/v1/friends
     *
     * Creates a new friend from the JSON request body and persists it.
     *
     * Request body:
     * {
     *   "name":  "Alice Rossi",
     *   "email": "alice@x.it",
     *   "phone": "111-111"       ← optional, may be omitted
     * }
     *
     * Response 201 Created:
     * Location header: /api/v1/friends/7
     * {
     *   "id":    7,
     *   "name":  "Alice Rossi",
     *   "email": "alice@x.it",
     *   "phone": "111-111"
     * }
     *
     * @param friend deserialised from the JSON request body by Jackson.
     */
    @PostMapping
    public ResponseEntity<Friend> create(@RequestBody Friend friend) {
        // Force id to null so Hibernate always calls persist() (INSERT) rather than
        // merge() (UPDATE). If the client accidentally sends a stale id from a
        // previously deleted record, merge() would return null in Hibernate 6+
        // because the entity no longer exists in the database, causing a
        // NullPointerException on saved.getId() below.
        friend.setId(null);

        Friend saved = friendRepository.save(friend);
        return ResponseEntity
                .created(URI.create("/api/v1/friends/" + saved.getId()))
                .body(saved);
    }

    /**
     * PUT /api/v1/friends/{id}
     *
     * Fully replaces the editable fields of an existing friend.
     *
     * Example — PUT /api/v1/friends/1
     * Request body:
     * {
     *   "name":  "Alice Verdi",
     *   "email": "alice.verdi@x.it",
     *   "phone": "999-999"
     * }
     *
     * Response 200 OK:
     * {
     *   "id":    1,
     *   "name":  "Alice Verdi",
     *   "email": "alice.verdi@x.it",
     *   "phone": "999-999"
     * }
     *
     * If no friend with that id exists → 404 Not Found.
     *
     * @param id     id of the friend to update, extracted from the URL path.
     * @param friend new field values, deserialised from the JSON request body.
     */
    @PutMapping("/{id}")
    public Friend update(@PathVariable Long id, @RequestBody Friend friend) {
        // Load the existing JPA-managed entity; throw 404 if absent.
        Friend current = friendRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend not found"));

        // Overwrite only the mutable fields; the database id is kept from 'current'.
        current.setName(friend.getName());
        current.setEmail(friend.getEmail());
        current.setPhone(friend.getPhone());

        return friendRepository.save(current);
    }

    /**
     * DELETE /api/v1/friends/{id}
     *
     * Deletes the friend with the given id.
     *
     * Example — DELETE /api/v1/friends/1
     * Response 204 No Content  (empty body)
     *
     * If no friend with that id exists → 404 Not Found.
     *
     * @param id the id of the friend to delete, extracted from the URL path.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!friendRepository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Friend not found");
        }
        friendRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
