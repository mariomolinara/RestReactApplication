# Friends Manager — Spring Boot + React + PostgreSQL

Single-page application to manage friends with full CRUD, open access (no authentication), REST API, and Docker deployment.

## Project Structure

```
RestReactApplication/
├── docker/
│   └── init.sql                  # PostgreSQL init script (runs once)
├── src/main/
│   ├── java/.../
│   │   ├── Friend.java           # JPA entity
│   │   ├── FriendRepository.java # Spring Data repository
│   │   ├── FriendController.java # REST controller (/api/v1/friends)
│   │   ├── WebConfig.java        # CORS configuration
│   │   └── RestReactApplication.java
│   └── resources/
│       ├── application.yaml      # Spring config (PostgreSQL + HikariCP)
│       └── static/               # React SPA (index.html, app.js, styles.css)
├── .env                          # Docker environment variables
├── Dockerfile                    # Multi-stage build (Maven → JRE Alpine)
├── docker-compose.yml            # App + PostgreSQL orchestration
└── pom.xml
```

## Features
- REST API at `/api/v1/friends` with separate endpoints for each CRUD operation
- PostgreSQL persistence with HikariCP connection pool
- React SPA served as Spring Boot static resources
- Docker Compose: app + database with dedicated network, health checks, and persistent volume
- No authentication required — all pages and endpoints are open

## Stack
| Layer     | Technology                             |
|-----------|----------------------------------------|
| Language  | Java 21                                |
| Framework | Spring Boot 4, Spring Web, Spring Data JPA |
| Database  | PostgreSQL 17 (Alpine)                 |
| Frontend  | React 18 (CDN, Babel in-browser)       |
| Deploy    | Docker Compose                         |

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

| Method   | Endpoint               | Description         | Request Body         |
|----------|------------------------|---------------------|----------------------|
| `GET`    | `/api/v1/friends`      | List all friends    | —                    |
| `GET`    | `/api/v1/friends/{id}` | Get friend by ID    | —                    |
| `POST`   | `/api/v1/friends`      | Create a new friend | `{name, email, phone}` |
| `PUT`    | `/api/v1/friends/{id}` | Update a friend     | `{name, email, phone}` |
| `DELETE` | `/api/v1/friends/{id}` | Delete a friend     | —                    |

### Quick API examples

Per ogni operazione vengono mostrati:
1. Il **payload HTTP raw** (request + response headers e body)
2. Il comando **PowerShell** (`Invoke-RestMethod` — built-in su Windows PowerShell 5.1+ / PowerShell 7+)
3. Il comando **curl** (pre-installato su Linux/macOS; su Windows disponibile da Windows 10 1803+ o installabile via `winget install curl.curl`)

---

#### 1 — Create (POST)

Crea un nuovo amico. Il server risponde `201 Created` con il record appena inserito (incluso l'`id` assegnato dal DB).

**HTTP raw**
```
→ REQUEST
POST /api/v1/friends HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{"name":"Alice","email":"alice@example.com","phone":"123"}

← RESPONSE
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/friends/1

{"id":1,"name":"Alice","email":"alice@example.com","phone":"123"}
```

**PowerShell** *(Windows PowerShell 5.1+ / PowerShell 7+)*
```powershell
# -Method Post      → verbo HTTP
# -ContentType      → imposta l'header Content-Type
# -Body             → payload JSON da inviare
# L'output è già deserializzato come oggetto PowerShell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/friends" `
  -ContentType "application/json" `
  -Body '{"name":"Alice","email":"alice@example.com","phone":"123"}'
```

**curl** *(Linux/macOS/Windows con curl installato)*
```bash
# -v              → verbose: mostra tutti gli header di request e response
# -X POST         → specifica il metodo HTTP
# -H              → aggiunge un header alla request
# -d              → corpo della request (JSON)
curl -v -X POST http://localhost:8080/api/v1/friends \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","phone":"123"}'
```

---

#### 2 — List all (GET)

Restituisce l'array JSON di tutti gli amici. Risposta `200 OK`.

**HTTP raw**
```
→ REQUEST
GET /api/v1/friends HTTP/1.1
Host: localhost:8080
Accept: application/json

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

[{"id":1,"name":"Alice","email":"alice@example.com","phone":"123"}]
```

**PowerShell**
```powershell
# GET è il metodo di default, quindi -Method Get è opzionale
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/friends"
```

**curl**
```bash
# Senza -X il metodo è GET per default
# -v mostra gli header completi di request e response
curl -v http://localhost:8080/api/v1/friends \
  -H "Accept: application/json"
```

---

#### 3 — Get by ID (GET)

Restituisce un singolo amico per `id`. Risposta `200 OK` oppure `404 Not Found` se non esiste.

**HTTP raw**
```
→ REQUEST
GET /api/v1/friends/1 HTTP/1.1
Host: localhost:8080
Accept: application/json

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"name":"Alice","email":"alice@example.com","phone":"123"}
```

**PowerShell**
```powershell
# Sostituire 1 con l'id effettivo del record
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/friends/1"
```

**curl**
```bash
# Sostituire /1 con l'id effettivo del record
curl -v http://localhost:8080/api/v1/friends/1 \
  -H "Accept: application/json"
```

---

#### 4 — Update (PUT)

Aggiorna completamente un amico esistente. Risposta `200 OK` con il record aggiornato.

**HTTP raw**
```
→ REQUEST
PUT /api/v1/friends/1 HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{"name":"Alice Updated","email":"alice@example.com","phone":"456"}

← RESPONSE
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"name":"Alice Updated","email":"alice@example.com","phone":"456"}
```

**PowerShell**
```powershell
# -Method Put → verbo HTTP per aggiornamento completo della risorsa
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/v1/friends/1" `
  -ContentType "application/json" `
  -Body '{"name":"Alice Updated","email":"alice@example.com","phone":"456"}'
```

**curl**
```bash
# -X PUT → sovrascrive completamente la risorsa identificata dall'URI
curl -v -X PUT http://localhost:8080/api/v1/friends/1 \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"name":"Alice Updated","email":"alice@example.com","phone":"456"}'
```

---

#### 5 — Delete (DELETE)

Elimina un amico per `id`. Risposta `204 No Content` (corpo vuoto).

**HTTP raw**
```
→ REQUEST
DELETE /api/v1/friends/1 HTTP/1.1
Host: localhost:8080

← RESPONSE
HTTP/1.1 204 No Content
```

**PowerShell**
```powershell
# La risposta è vuota (204), quindi non viene stampato nulla
Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/api/v1/friends/1"
```

**curl**
```bash
# -v mostra che la risposta è 204 No Content senza body
curl -v -X DELETE http://localhost:8080/api/v1/friends/1
```

## Environment Variables

| Variable          | Default                                         | Used by    |
|-------------------|-------------------------------------------------|------------|
| `POSTGRES_DB`     | `friendsdb`                                     | PostgreSQL |
| `POSTGRES_USER`   | `friends_user`                                  | PostgreSQL |
| `POSTGRES_PASSWORD` | `friends_password`                            | PostgreSQL |
| `DB_URL`          | `jdbc:postgresql://localhost:5432/friendsdb`    | Spring     |
| `DB_USERNAME`     | `friends_user`                                  | Spring     |
| `DB_PASSWORD`     | `friends_password`                              | Spring     |
| `SERVER_PORT`     | `8080`                                          | Spring     |
