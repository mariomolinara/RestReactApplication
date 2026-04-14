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

```powershell
# Create
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/friends" `
  -ContentType "application/json" `
  -Body '{"name":"Alice","email":"alice@example.com","phone":"123"}'

# List all
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/friends"

# Get by ID
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/friends/1"

# Update
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/v1/friends/1" `
  -ContentType "application/json" `
  -Body '{"name":"Alice Updated","email":"alice@example.com","phone":"456"}'

# Delete
Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/api/v1/friends/1"
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
