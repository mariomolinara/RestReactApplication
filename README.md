# Friends Manager — Spring Boot + React + PostgreSQL

Single-page application to manage friends with full CRUD, session-based authentication, REST API, and Docker deployment.

## Project Structure

```
RestReactApplication/
├── docker/
│   └── init.sql                  # PostgreSQL init script (runs once on first start)
├── src/main/
│   ├── java/.../
│   │   ├── Friend.java           # JPA entity (maps to the "friends" table)
│   │   ├── FriendRepository.java # Spring Data JPA repository (CRUD + custom search)
│   │   ├── FriendController.java # REST controller (/api/v1/friends)
│   │   ├── AuthController.java   # Authentication endpoints (/api/auth/login, logout, me)
│   │   ├── SecurityConfig.java   # Spring Security: access rules, session, 401 handler
│   │   └── RestReactApplication.java
│   └── resources/
│       ├── application.yaml      # Spring config (PostgreSQL, HikariCP, security credentials)
│       └── static/               # React SPA served as static resources (index.html, app.js, styles.css)
├── .env                          # Docker Compose environment variables (do not commit with real passwords)
├── Dockerfile                    # Multi-stage build (Maven build → JRE 21 Alpine runtime)
├── docker-compose.yml            # Orchestrates app + PostgreSQL with health checks and a named volume
└── pom.xml
```

## Features
- REST API at `/api/v1/friends` with separate endpoints for each CRUD operation
- **Session-based authentication** via Spring Security: all `/api/v1/**` endpoints require a valid session; the SPA shows a login page when no session exists and handles session expiry automatically
- In-memory user configurable via environment variables (`APP_SECURITY_USERNAME` / `APP_SECURITY_PASSWORD`)
- PostgreSQL persistence with HikariCP connection pool
- React 18 SPA served directly by Spring Boot as static resources (same origin — no CORS required)
- Docker Compose: app + database with a dedicated network, health checks, and a persistent named volume
- Full test suite: repository tests, controller tests, and end-to-end integration tests (Testcontainers + MockMvc + `@WithMockUser`)

## Stack
| Layer     | Technology                                              |
|-----------|---------------------------------------------------------|
| Language  | Java 21                                                 |
| Framework | Spring Boot 4, Spring Web, Spring Data JPA, Spring Security |
| Database  | PostgreSQL 17 (Alpine)                                  |
| Frontend  | React 18 (CDN, Babel in-browser)                        |
| Deploy    | Docker Compose                                          |

## Technology Overview

### Java 21

The programming language used for the entire backend.
Java 21 is an **LTS (Long-Term Support)** release — Oracle guarantees security patches until 2031,
making it the recommended choice for production systems.

#### Why Java 21?
Key features exploited in this project:
- **Records** — immutable data carriers with auto-generated constructor, getters, `equals`, `hashCode`.
- **Text blocks** — multi-line string literals used in tests (`"""…"""`).
- **Pattern matching** — cleaner `instanceof` checks.

```java
// Text block used in test bodies
String body = """
        {"name":"Alice","email":"alice@example.com","phone":"123"}
        """;
```

All server-side logic — HTTP request handling, business rules, database access, security — is written in Java.

---

### Spring Boot 4

An opinionated framework that **auto-configures** a production-ready Spring application
from the dependencies declared in `pom.xml`.
It embeds a Tomcat HTTP server, so the application ships as a self-contained JAR:

```bash
java -jar target/RestReactApplication-0.0.1-SNAPSHOT.jar
# No external application server required.
```

#### Key modules

| Module | Role |
|---|---|
| **Spring Web (MVC)** | Maps HTTP requests to Java methods. Serialises/deserialises JSON via Jackson. |
| **Spring Data JPA** | Generates SQL from method names and JPQL. Manages the `EntityManager` lifecycle. |
| **Spring Security** | Filter chain that enforces auth rules, manages sessions, returns 401 JSON for unauthenticated calls. |
| **Spring Boot Actuator** | Exposes `/actuator/health` — probed by the Docker `HEALTHCHECK`. |

#### How auto-configuration works

```xml
<!-- pom.xml: declaring the dependency is enough -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<!-- Spring Boot automatically configures: DataSource, EntityManagerFactory,
     TransactionManager, and a Spring Data repository proxy for every
     interface that extends JpaRepository. No XML or @Bean definitions needed. -->
```

#### Constructor injection example

```java
// Spring creates FriendRepository and passes it here automatically.
// The controller never calls "new FriendRepositoryImpl()".
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {
    private final FriendRepository friendRepository;

    public FriendController(FriendRepository friendRepository) {
        this.friendRepository = friendRepository;   // injected by Spring IoC
    }
}
```

---

### HikariCP

A high-performance JDBC **connection pool** bundled with Spring Boot.

#### Why a connection pool?

Opening a raw TCP connection to PostgreSQL for every query is expensive (~50 ms).
HikariCP maintains a pool of ready connections that are leased to threads and returned after use:

```
Thread A ──► borrows connection ──► executes SQL ──► returns connection ──► pool
Thread B ──► borrows connection ──► executes SQL ──► returns connection ──► pool
              (no new TCP handshake — reuses an existing socket)
```

#### Configuration in `application.yaml`

```yaml
hikari:
  maximum-pool-size: 10    # max simultaneous connections to PostgreSQL
  minimum-idle: 2          # connections kept warm when traffic is low
  idle-timeout: 30000      # 30 s — evict an idle connection after this delay
  connection-timeout: 20000 # 20 s — throw if no connection is available
  max-lifetime: 1800000    # 30 min — recycle connections to avoid stale handles
```

---

### PostgreSQL 17

The relational database that persists the friends data.

#### Role in the stack

```
React SPA  ──►  Spring Controller  ──►  Spring Data JPA  ──►  HikariCP  ──►  PostgreSQL
(browser)        (HTTP layer)           (ORM / JPQL)         (pool)         (SQL engine)
```

Spring Data JPA translates entity operations into SQL; PostgreSQL executes them
and enforces constraints:

```sql
-- Generated by Hibernate from Friend.java @Column(unique=true)
ALTER TABLE friends ADD CONSTRAINT friends_email_key UNIQUE (email);

-- A duplicate INSERT raises:
-- ERROR: duplicate key value violates unique constraint "friends_email_key"
```

The **Alpine** variant is used in Docker (`postgres:17-alpine`) to keep the image
small (~70 MB vs ~400 MB for the Debian-based default).

---

### React 18

A JavaScript **UI library** developed by Meta for building component-based single-page applications (SPAs).

#### What does "18" mean?

React follows **semantic versioning**: `MAJOR.MINOR.PATCH`.
**18** is the major version number, released in March 2022.
Each major version introduces breaking changes alongside new capabilities.

Key additions in React 18 used in this project:

| Feature | What it does |
|---|---|
| **Concurrent rendering** | React can interrupt, pause, and resume rendering work. Enables `useTransition` and `Suspense` (not used here, but available). |
| **Automatic batching** | Multiple `setState` calls inside async functions (e.g. inside `fetch().then()`) are now batched into a single re-render instead of triggering one per call. |
| **`createRoot` API** | Replaces the old `ReactDOM.render`. Required to opt into concurrent features. |

```js
// React 18 — new root API (used at the bottom of app.js)
ReactDOM.createRoot(document.getElementById("root")).render(<App />);

// React 17 and below — deprecated
// ReactDOM.render(<App />, document.getElementById("root"));
```

#### What is a SPA?

A **Single-Page Application** loads one HTML page once; all subsequent navigation
is handled by JavaScript without a full page reload.
The server serves `index.html` + JS assets once; React then updates the DOM in response to user actions and API responses.

```
Traditional multi-page app          Single-Page Application (React)
──────────────────────────          ──────────────────────────────
User clicks link                    User clicks button
  → Browser requests new page         → React updates the DOM in memory
  → Server renders HTML               → fetch() calls the REST API
  → Full page reload                  → React re-renders only changed components
  (slow, loses scroll position)       (fast, no flicker)
```

#### Components

A **component** is a JavaScript function that returns JSX — an HTML-like syntax
that Babel compiles to `React.createElement(...)` calls:

```jsx
// JSX source (what you write)
function FriendForm({ draft, onChange, onSubmit, editing }) {
  return (
    <form onSubmit={onSubmit}>
      <input value={draft.name} onChange={(e) => onChange({ ...draft, name: e.target.value })} />
      <button type="submit">{editing ? "Save" : "Create"}</button>
    </form>
  );
}

// What Babel compiles it to (what the browser executes)
function FriendForm({ draft, onChange, onSubmit, editing }) {
  return React.createElement("form", { onSubmit },
    React.createElement("input", { value: draft.name, onChange: … }),
    React.createElement("button", { type: "submit" }, editing ? "Save" : "Create")
  );
}
```

#### State and re-rendering

```jsx
// useState returns [currentValue, setterFunction].
// Calling the setter triggers a re-render of this component and its children.
const [friends, setFriends] = useState([]);   // initially empty array
const [error,   setError]   = useState("");   // initially empty string

// When setFriends is called, React diffs the virtual DOM and updates only
// the <ul> list in the real DOM — not the entire page.
setFriends(data.content);
```

#### Effects and the session check

```jsx
// useEffect(fn, []) runs fn once after the first render (like componentDidMount).
// Used here to check whether a session already exists when the page loads.
useEffect(() => {
  fetch("/api/auth/me", { credentials: "same-origin" })
    .then((res) => (res.ok ? res.json() : null))
    .then((data) => { if (data?.username) setCurrentUser(data.username); })
    .finally(() => setCheckingSession(false));
}, []);   // ← empty array = "run once on mount, never again"
```

#### Virtual DOM

React never writes directly to the browser DOM on every state change.
Instead it maintains a lightweight **virtual DOM** (a plain JS object tree),
diffs it against the previous version, and applies only the minimal necessary
real DOM mutations:

```
State changes
  → React builds new Virtual DOM tree
  → React diffs new tree vs old tree  (reconciliation)
  → React patches only the changed real DOM nodes
  (e.g. updates one <li> text node instead of re-rendering the whole <ul>)
```

---

### Babel (in-browser)

Babel is a **JavaScript transpiler** — it converts modern or non-standard JavaScript
syntax into plain ES5 that every browser can execute without native support.

#### What does "transpiler" mean?

```
Source code (JSX / ES2022)          Output code (ES5 / plain JS)
──────────────────────────          ────────────────────────────
const x = a ?? b;          ──►      var x = a !== null && a !== void 0 ? a : b;
<MyComp prop={val} />      ──►      React.createElement(MyComp, { prop: val })
```

#### How it is loaded in this project

```html
<!-- index.html -->
<script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>

<!-- type="text/babel" tells Babel to intercept and compile this script
     before the browser executes it -->
<script type="text/babel" src="/app.js"></script>
```

When the browser encounters `<script type="text/babel">`, it does **not** execute
the script directly. Babel's runtime intercepts it, parses the JSX, emits plain JS,
and then executes the result.

#### Why not use a build tool (Vite / webpack)?

| | In-browser Babel (this project) | Build tool (Vite / webpack) |
|---|---|---|
| **Setup** | Zero — just a `<script>` tag | `npm install`, config files |
| **Compilation** | At runtime in the browser | At build time on the developer's machine |
| **Initial load** | Slower (compile + ~1 MB Babel bundle) | Fast (pre-compiled, tree-shaken bundle) |
| **Best for** | Teaching / demos | Production applications |

---

### CDN (Content Delivery Network)

A CDN is a globally distributed network of servers that **cache and serve static files**
(JavaScript, CSS, fonts, images) from the **edge node geographically closest to the user**,
reducing latency compared to fetching from a single origin server.

#### How it works

```
Without CDN                         With CDN
───────────────                     ────────────────────────────────
Browser (Rome)                      Browser (Rome)
  → request → Single server (US)      → request → Edge node (Milan)
  ← 150 ms round-trip latency         ← 5 ms round-trip latency
                                       (edge caches the file after first request)
```

#### Libraries loaded from unpkg CDN in this project

```html
<!-- React core: the library itself (component model, hooks, Virtual DOM diffing) -->
<script src="https://unpkg.com/react@18/umd/react.development.js"></script>

<!-- React DOM: the renderer that bridges React's Virtual DOM to the real browser DOM.
     Separated from React core so React can target other environments
     (React Native, React Three Fiber, etc.) using the same core. -->
<script src="https://unpkg.com/react-dom@18/umd/react-dom.development.js"></script>

<!-- Babel standalone: in-browser JSX → JS compiler (~1 MB, development only) -->
<script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
```

> **Note:** `react.development.js` includes extra warnings and error messages useful
> during development. In production you would switch to `react.production.min.js`
> (minified, no debug output, ~40 KB gzipped).

#### Pros and cons

| | CDN | Self-hosted / build tool |
|---|---|---|
| **Setup** | One `<script>` tag | `npm install` + bundler config |
| **Availability** | Depends on CDN uptime | Under your control |
| **Caching** | Shared browser cache across sites | Per-site cache |
| **Version pinning** | `@18` resolves to latest 18.x — may change | Exact version locked in `package.json` |
| **Best for** | Demos, prototypes | Production |

---

### Docker & Docker Compose

**Docker** packages the application and all its dependencies into a portable,
isolated **container image** that runs identically on any machine with Docker installed.

#### Multi-stage Dockerfile

```dockerfile
# ── Stage 1: build ───────────────────────────────────────────────────────────
# Uses a full Maven + JDK 21 image to compile the source and package the JAR.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline          # cache dependencies in a separate layer
COPY src ./src
RUN mvn package -DskipTests

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
# Copies only the JAR into a minimal JRE image (~100 MB vs ~500 MB with JDK).
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Docker Compose services

```yaml
# docker-compose.yml (simplified)
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB:       friendsdb
      POSTGRES_USER:     friends_user
      POSTGRES_PASSWORD: friends_password
    volumes:
      - pgdata:/var/lib/postgresql/data   # named volume — data survives restarts
      - ./docker/init.sql:/docker-entrypoint-initdb.d/init.sql

  app:
    build: .                              # builds from the Dockerfile above
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy        # wait for postgres health check
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/friendsdb
      APP_SECURITY_PASSWORD: admin

volumes:
  pgdata:   # named volume persists DB data across "docker compose down" restarts
```

---

### Testcontainers

A Java library that starts **real Docker containers** (PostgreSQL, Redis, Kafka, …)
during the test run and tears them down automatically afterwards.

#### Why not use an H2 in-memory database for tests?

| | H2 in-memory | Testcontainers (real PostgreSQL) |
|---|---|---|
| **Speed** | Very fast | Fast (container reused per class) |
| **Fidelity** | Different SQL dialect — some queries may behave differently | Identical to production |
| **Constraints** | H2 may not enforce all PostgreSQL-specific constraints | Full PostgreSQL constraint enforcement |
| **Recommendation** | Acceptable for pure unit tests | Preferred for integration tests |

#### Usage in this project

```java
@SpringBootTest
@Testcontainers
class FriendRepositoryTest {

    // Testcontainers starts a real postgres:17-alpine Docker container.
    // @ServiceConnection reads its JDBC URL/username/password and injects
    // them into Spring's DataSource — no manual application.yaml override needed.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired FriendRepository friendRepository;

    @Test
    void save_duplicateEmail_throwsException() {
        // This test relies on the real PostgreSQL UNIQUE constraint.
        // It would behave differently (or pass silently) with H2.
        assertThrows(Exception.class,
            () -> friendRepository.saveAndFlush(new Friend("X", "alice@example.com", null)));
    }
}
```

---

### MockMvc

A Spring Test utility that dispatches HTTP requests directly to the `DispatcherServlet`
**in-process**, without opening a real TCP socket or starting a full HTTP server.

#### What runs and what is bypassed

```
Real HTTP request (production)
  Browser → TCP socket → OS network stack → Tomcat → DispatcherServlet → Controller

MockMvc request (test)
  Test code → MockMvc → DispatcherServlet → Controller
              ↑ everything to the left of this arrow is bypassed
              ↓ everything to the right runs for real (Security filters, JPA, DB)
```

#### Usage pattern

```java
@Test
@DisplayName("POST /friends - creates friend → 201 Created")
void create_success() throws Exception {
    mockMvc.perform(
               post("/api/v1/friends")                      // build the request
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("""
                       {"name":"Alice","email":"alice@x.it","phone":"123"}
                       """)
           )
           .andExpect(status().isCreated())                 // assert HTTP status 201
           .andExpect(header().string("Location",           // assert Location header
               containsString("/api/v1/friends/")))
           .andExpect(jsonPath("$.name", is("Alice")));     // assert JSON body field
}
```

#### `@WithMockUser`

```java
// Applied at class level — affects every @Test method.
// Injects a synthetic UserDetails into the SecurityContext,
// bypassing the real login flow while keeping Security filters active.
@WithMockUser   // default: username="user", roles=["USER"]
class FriendControllerTest { … }

// Can be overridden per method:
@Test
@WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
void adminOnlyEndpoint() { … }
```

## Quick Start with Docker

```powershell
# 1. Start everything (builds app image + pulls postgres)
docker compose up --build

# 2. Open in browser
#    SPA:  http://localhost:8080
#    API:  http://localhost:8080/api/v1/friends
```

### Useful Docker commands

```powershell
# Stop all containers
docker compose down

# Stop and remove database volume (fresh start)
docker compose down -v

# View logs
docker compose logs -f app
docker compose logs -f postgres

# Rebuild only the app after code changes
docker compose up --build app
```

## Run without Docker

1. Start a local PostgreSQL instance and create the database:

```sql
CREATE USER friends_user WITH PASSWORD 'friends_password';
CREATE DATABASE friendsdb OWNER friends_user;
```

2. Start the application:

```powershell
.\mvnw.cmd spring-boot:run
```

Or set custom credentials via environment variables:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/friendsdb"
$env:DB_USERNAME="friends_user"
$env:DB_PASSWORD="friends_password"
.\mvnw.cmd spring-boot:run
```

## REST API Reference

### Authentication endpoints (public — no session required)

| Method | Endpoint           | Description                                        | Request Body           |
|--------|--------------------|----------------------------------------------------|------------------------|
| `POST` | `/api/auth/login`  | Authenticates the user and creates an HTTP session | `{username, password}` |
| `POST` | `/api/auth/logout` | Invalidates the current session                    | —                      |
| `GET`  | `/api/auth/me`     | Returns the user bound to the active session       | —                      |

### Friends endpoints (🔒 require a valid session)

| Method   | Endpoint               | Description         | Request Body           |
|----------|------------------------|---------------------|------------------------|
| `GET`    | `/api/v1/friends`      | List all friends    | —                      |
| `GET`    | `/api/v1/friends/{id}` | Get friend by ID    | —                      |
| `POST`   | `/api/v1/friends`      | Create a new friend | `{name, email, phone}` |
| `PUT`    | `/api/v1/friends/{id}` | Update a friend     | `{name, email, phone}` |
| `DELETE` | `/api/v1/friends/{id}` | Delete a friend     | —                      |

### Quick API examples

Each operation is shown with:
1. The **raw HTTP payload** (request + response headers and body)
2. The **PowerShell** command (`Invoke-RestMethod` — built-in on Windows PowerShell 5.1+ / PowerShell 7+)
3. The **curl** command (pre-installed on Linux/macOS; available on Windows 10 1803+ or via `winget install curl.curl`)

> ⚠️ **All `/api/v1/**` endpoints require a valid session.**
> Run the **Login** example first to obtain the `JSESSIONID` cookie.

---

#### 0 — Login (POST /api/auth/login)

Authenticates the user. The server sets the `JSESSIONID` cookie in the response.
All subsequent requests must carry this cookie.

**HTTP raw**
```
→ REQUEST
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{"username":"admin","password":"admin"}

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: JSESSIONID=ABC123; Path=/; HttpOnly

{"username":"admin"}
```

**PowerShell** *(stores the cookie in a WebRequestSession for reuse)*
```powershell
# Creates a session object that automatically stores received cookies.
# $session is passed to all subsequent calls via -WebSession.
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin"}' `
  -SessionVariable session
```

**curl** *(saves the cookie to a file for reuse)*
```bash
# -c cookie.txt  → writes received cookies (JSESSIONID) to cookie.txt
# The file is read back by curl on all subsequent calls via -b cookie.txt
curl -v -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' \
  -c cookie.txt
```

---

#### 0b — Check session (GET /api/auth/me)

Verifies that the session cookie is still valid and returns the current user.
Useful for diagnosing authentication issues.

**HTTP raw**
```
→ REQUEST
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=ABC123

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

{"username":"admin"}
```

**PowerShell**
```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/auth/me" `
  -WebSession $session
```

**curl**
```bash
# -b cookie.txt  → sends the previously saved cookies
curl -v http://localhost:8080/api/auth/me \
  -b cookie.txt
```

---

#### 0c — Logout (POST /api/auth/logout)

Invalidates the session on the server. The `JSESSIONID` cookie will no longer be accepted.

**HTTP raw**
```
→ REQUEST
POST /api/auth/logout HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=ABC123

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

{"message":"Logged out successfully"}
```

**PowerShell**
```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/logout" `
  -WebSession $session
```

**curl**
```bash
curl -v -X POST http://localhost:8080/api/auth/logout \
  -b cookie.txt
```

---

#### 1 — Create (POST)

Creates a new friend. The server responds with `201 Created` and the newly inserted record (including the `id` assigned by the database).

**HTTP raw**
```
→ REQUEST
POST /api/v1/friends HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
Cookie: JSESSIONID=ABC123

{"name":"Alice","email":"alice@example.com","phone":"123"}

← RESPONSE
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/friends/1

{"id":1,"name":"Alice","email":"alice@example.com","phone":"123"}
```

**PowerShell** *(Windows PowerShell 5.1+ / PowerShell 7+)*
```powershell
# -WebSession $session → sends the JSESSIONID cookie saved at login
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/friends" `
  -ContentType "application/json" `
  -Body '{"name":"Alice","email":"alice@example.com","phone":"123"}' `
  -WebSession $session
```

**curl** *(Linux/macOS/Windows with curl installed)*
```bash
# -b cookie.txt → sends the JSESSIONID cookie saved at login
curl -v -X POST http://localhost:8080/api/v1/friends \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","phone":"123"}' \
  -b cookie.txt
```

---

#### 2 — List all (GET)

Returns a JSON array of all friends. Response `200 OK`.

**HTTP raw**
```
→ REQUEST
GET /api/v1/friends HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=ABC123

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

[{"id":1,"name":"Alice","email":"alice@example.com","phone":"123"}]
```

**PowerShell**
```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/friends" `
  -WebSession $session
```

**curl**
```bash
curl -v http://localhost:8080/api/v1/friends \
  -H "Accept: application/json" \
  -b cookie.txt
```

---

#### 3 — Get by ID (GET)

Returns a single friend by `id`. Response `200 OK` or `404 Not Found` if the record does not exist.

**HTTP raw**
```
→ REQUEST
GET /api/v1/friends/1 HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=ABC123

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"name":"Alice","email":"alice@example.com","phone":"123"}
```

**PowerShell**
```powershell
# Replace 1 with the actual record id
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/friends/1" `
  -WebSession $session
```

**curl**
```bash
# Replace /1 with the actual record id
curl -v http://localhost:8080/api/v1/friends/1 \
  -H "Accept: application/json" \
  -b cookie.txt
```

---

#### 4 — Update (PUT)

Fully replaces an existing friend's fields. Response `200 OK` with the updated record.

**HTTP raw**
```
→ REQUEST
PUT /api/v1/friends/1 HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
Cookie: JSESSIONID=ABC123

{"name":"Alice Updated","email":"alice@example.com","phone":"456"}

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"name":"Alice Updated","email":"alice@example.com","phone":"456"}
```

**PowerShell**
```powershell
# -Method Put → HTTP verb for a full resource replacement
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/v1/friends/1" `
  -ContentType "application/json" `
  -Body '{"name":"Alice Updated","email":"alice@example.com","phone":"456"}' `
  -WebSession $session
```

**curl**
```bash
# -X PUT → fully overwrites the resource identified by the URI
curl -v -X PUT http://localhost:8080/api/v1/friends/1 \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"name":"Alice Updated","email":"alice@example.com","phone":"456"}' \
  -b cookie.txt
```

---

#### 5 — Delete (DELETE)

Deletes a friend by `id`. Response `204 No Content` (empty body).

**HTTP raw**
```
→ REQUEST
DELETE /api/v1/friends/1 HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=ABC123

← RESPONSE
HTTP/1.1 204 No Content
```

**PowerShell**
```powershell
# 204 response has no body, so nothing is printed
Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/api/v1/friends/1" `
  -WebSession $session
```

**curl**
```bash
# -v shows that the response is 204 No Content with no body
curl -v -X DELETE http://localhost:8080/api/v1/friends/1 \
  -b cookie.txt
```

## Environment Variables

| Variable                | Default                                         | Used by    |
|-------------------------|-------------------------------------------------|------------|
| `POSTGRES_DB`           | `friendsdb`                                     | PostgreSQL |
| `POSTGRES_USER`         | `friends_user`                                  | PostgreSQL |
| `POSTGRES_PASSWORD`     | `friends_password`                              | PostgreSQL |
| `DB_URL`                | `jdbc:postgresql://localhost:5432/friendsdb`    | Spring     |
| `DB_USERNAME`           | `friends_user`                                  | Spring     |
| `DB_PASSWORD`           | `friends_password`                              | Spring     |
| `SERVER_PORT`           | `8080`                                          | Spring     |
| `APP_SECURITY_USERNAME` | `admin`                                         | Spring Security |
| `APP_SECURITY_PASSWORD` | `admin`                                         | Spring Security |
