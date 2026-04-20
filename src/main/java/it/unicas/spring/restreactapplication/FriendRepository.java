package it.unicas.spring.restreactapplication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data-access interface for the Friend entity.
 *
 * ── ROLE IN THE APPLICATION ───────────────────────────────────────────────────
 * FriendRepository is the data-access layer. It sits between the controller
 * (HTTP world) and the database (SQL world):
 *
 *   FriendController  → calls methods on FriendRepository
 *   FriendRepository  → translated by Hibernate into SQL queries
 *   PostgreSQL        → executes SQL, returns rows
 *   Hibernate         → maps rows back to Friend objects
 *   FriendController  ← receives Friend / Page<Friend> objects
 *
 * ── WHAT IS A REPOSITORY? ─────────────────────────────────────────────────────
 * In the Spring Data vocabulary a "repository" is an interface that provides
 * data-access methods. The developer only writes the interface; Spring Data JPA
 * generates a concrete implementation at runtime using Java dynamic proxies.
 * That generated class is registered as a Spring bean in the ApplicationContext
 * and can therefore be injected anywhere via constructor injection or @Autowired.
 *
 * ── WHAT DOES JpaRepository PROVIDE? ─────────────────────────────────────────
 * By extending JpaRepository<Friend, Long> this interface inherits a complete
 * set of CRUD and paging operations for free — no SQL is needed:
 *
 *   save(entity)              → INSERT or UPDATE (depending on whether id is null)
 *   findById(id)              → SELECT … WHERE id = ?   returns Optional<Friend>
 *   findAll()                 → SELECT * FROM friends
 *   findAll(Pageable)         → SELECT … LIMIT ? OFFSET ?  returns Page<Friend>
 *   existsById(id)            → SELECT COUNT(*) > 0 WHERE id = ?
 *   deleteById(id)            → DELETE FROM friends WHERE id = ?
 *   deleteAll()               → DELETE FROM friends
 *   count()                   → SELECT COUNT(*) FROM friends
 *
 * The two generic type parameters tell Spring Data which entity class to manage
 * (Friend) and what type its primary key is (Long).
 *
 * ── DEPENDENCY INJECTION & IoC ────────────────────────────────────────────────
 * Spring Data registers the generated proxy as a singleton bean. When Spring
 * creates FriendController it sees that the constructor requires a FriendRepository,
 * looks up this bean in the ApplicationContext, and injects it automatically
 * (constructor injection / IoC). The controller never calls "new" for the
 * repository — the framework handles it.
 */
public interface FriendRepository extends JpaRepository<Friend, Long> {

    /**
     * Full-text search across name, email and phone (case-insensitive, paginated).
     *
     * ── HOW CUSTOM QUERIES WORK ───────────────────────────────────────────────
     * Spring Data can derive simple queries from method names (e.g., findByName),
     * but for complex multi-field searches a custom JPQL query is needed.
     * @Query accepts JPQL (Java Persistence Query Language), which looks like SQL
     * but operates on Java entity classes and their fields rather than database
     * table and column names.
     *
     * ── THE QUERY EXPLAINED ───────────────────────────────────────────────────
     *   SELECT f FROM Friend f
     *   WHERE LOWER(f.name)  LIKE LOWER(CONCAT('%', :q, '%'))
     *      OR LOWER(f.email) LIKE LOWER(CONCAT('%', :q, '%'))
     *      OR LOWER(COALESCE(f.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
     *
     *   LOWER(…) LIKE LOWER(…)
     *     Converts both sides to lowercase before comparing, achieving
     *     case-insensitive matching without database-specific functions.
     *
     *   CONCAT('%', :q, '%')
     *     Wraps the search term with SQL wildcards so LIKE matches any substring.
     *     Example: q = "alice"  →  LIKE '%alice%'  matches "Alice Rossi".
     *
     *   COALESCE(f.phone, '')
     *     Replaces NULL with an empty string. Without this guard, applying LIKE
     *     to a NULL value would yield NULL (not FALSE), which could silently
     *     exclude records from the result set even when name or email match.
     *
     *   :q
     *     A named parameter placeholder. Hibernate replaces it with the value of
     *     the 'query' argument (bound via @Param("q")) using a prepared statement,
     *     preventing SQL injection.
     *
     * ── PAGINATION ────────────────────────────────────────────────────────────
     * The Pageable parameter tells Hibernate to add LIMIT / OFFSET clauses and
     * to run a COUNT(*) query in parallel, returning a Page<Friend> that contains:
     *   - content:       the current page of Friend objects
     *   - totalElements: total matching rows (across all pages)
     *   - totalPages:    how many pages exist at the requested page size
     *
     * @param query    the search string (matched against name, email and phone).
     * @param pageable pagination and sorting settings (page index, page size, sort).
     * @return a Page containing the matching friends for the requested page.
     */
    @Query("""
           SELECT f FROM Friend f
           WHERE LOWER(f.name)  LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(f.email) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(COALESCE(f.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           """)
    Page<Friend> search(@Param("q") String query, Pageable pageable);
}
