package it.unicas.spring.restreactapplication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity that represents a single friend record stored in the database.
 *
 * ── ROLE IN THE APPLICATION ───────────────────────────────────────────────────
 * This class sits at the bottom of the application stack:
 *
 *   Browser (React SPA)
 *       ↓ JSON over HTTP
 *   FriendController  — receives HTTP requests, delegates to the repository
 *       ↓ Java objects
 *   FriendRepository  — translates method calls to SQL via Hibernate
 *       ↓ SQL rows
 *   Friend (this class) — the Java representation of one row in the 'friends' table
 *       ↓ persisted in
 *   PostgreSQL database
 *
 * ── WHAT IS A JPA ENTITY? ─────────────────────────────────────────────────────
 * JPA (Jakarta Persistence API) is a standard that defines how Java objects are
 * mapped to relational database tables. Hibernate is the JPA implementation used
 * here (bundled with Spring Boot).
 *
 * When Spring Boot starts, Hibernate reads all classes annotated with @Entity
 * and creates (or validates) the corresponding database schema automatically.
 * At runtime, Hibernate converts:
 *   - Java objects → INSERT / UPDATE statements  (when you call repository.save())
 *   - SQL result rows → Java objects             (when you call repository.findById())
 *
 * This mapping eliminates the need to write raw SQL for basic CRUD operations.
 *
 * ── NO-ARGS CONSTRUCTOR ───────────────────────────────────────────────────────
 * JPA requires a public or protected no-args constructor so that Hibernate can
 * instantiate entity objects via reflection when reading rows from the database.
 * The convenience constructor (name, email, phone) is provided for application
 * code that creates new friends programmatically.
 */
@Entity  // Tells Hibernate: "this class is a persistent entity — map it to a table".
@Table(name = "friends")  // Maps this entity to the table named 'friends'.
                           // Without this annotation the table name defaults to the
                           // class name ("Friend"), but explicit naming is clearer.
public class Friend {

    /**
     * Primary key of the database row.
     *
     * @Id marks this field as the primary key column.
     * @GeneratedValue(strategy = IDENTITY) delegates id generation to the database:
     * PostgreSQL uses a BIGSERIAL column (auto-increment) to assign a unique id to
     * every new row. The application never sets this field manually; Hibernate reads
     * the generated value back after the INSERT and populates this field.
     *
     * The type Long (object, nullable) rather than long (primitive) is intentional:
     * a null id indicates a new entity that has not been persisted yet, which is how
     * Hibernate distinguishes INSERT from UPDATE when you call repository.save().
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The friend's full name.
     *
     * @Column(nullable = false) adds a NOT NULL constraint to this column in the
     * database schema. Hibernate will refuse to INSERT or UPDATE a row where this
     * field is null, and PostgreSQL enforces the same constraint at the SQL level.
     */
    @Column(nullable = false)
    private String name;

    /**
     * The friend's email address — must be unique across all rows.
     *
     * @Column(nullable = false, unique = true) adds both a NOT NULL constraint and
     * a UNIQUE index on this column. Attempting to insert two friends with the same
     * email will raise a ConstraintViolationException from Hibernate, backed by a
     * PostgreSQL UNIQUE constraint violation at the SQL level.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * The friend's phone number — optional (nullable).
     *
     * No @Column annotation is needed for nullable string columns: by default JPA
     * maps a String field to a VARCHAR column with no NOT NULL constraint.
     * When phone is null in Java, Hibernate stores NULL in the database column.
     *
     * The custom search query in FriendRepository uses COALESCE(f.phone, '') to
     * handle this null safely inside a LIKE comparison.
     */
    private String phone;

    /**
     * No-args constructor required by the JPA specification.
     *
     * Hibernate uses reflection to create entity instances when loading rows from
     * the database (e.g., during findById or findAll). Java's reflection API needs
     * a no-args constructor to do so. Without it, Hibernate throws an exception at
     * startup.
     *
     * This constructor is intentionally left empty; Hibernate sets field values
     * directly after instantiation using its own internal mechanisms.
     */
    public Friend() {
    }

    /**
     * Convenience constructor for creating new Friend instances in application code.
     *
     * The id is intentionally omitted: for a new entity the id should be null so
     * that Hibernate treats the next repository.save() call as an INSERT and lets
     * the database assign the id via the IDENTITY strategy.
     *
     * @param name  the friend's full name (must not be null).
     * @param email the friend's email address (must not be null, must be unique).
     * @param phone the friend's phone number (may be null).
     */
    public Friend(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    // ── Getters and setters ───────────────────────────────────────────────────
    // Standard JavaBean accessors. Jackson (the JSON library used by Spring Boot)
    // uses these methods to serialise Friend objects to JSON (for HTTP responses)
    // and to deserialise JSON payloads back to Friend objects (for HTTP requests).

    /** Returns the database-assigned primary key. Null for a not-yet-persisted entity. */
    public Long getId() { return id; }

    /**
     * Allows setting the id manually — rarely needed in application code.
     * Used internally by Hibernate and occasionally in tests.
     */
    public void setId(Long id) { this.id = id; }

    /** Returns the friend's full name. */
    public String getName() { return name; }

    /** Updates the friend's full name. Used by FriendController.update(). */
    public void setName(String name) { this.name = name; }

    /** Returns the friend's email address. */
    public String getEmail() { return email; }

    /** Updates the friend's email address. Used by FriendController.update(). */
    public void setEmail(String email) { this.email = email; }

    /** Returns the friend's phone number, or null if not set. */
    public String getPhone() { return phone; }

    /** Updates the friend's phone number. Accepts null to clear the value. */
    public void setPhone(String phone) { this.phone = phone; }
}
