# =============================================================================
# Multi-stage Dockerfile for the Friends Manager Spring Boot application.
#
# WHY MULTI-STAGE?
# A single-stage build would ship the full Maven + JDK toolchain (≈500 MB)
# inside the final image even though none of it is needed at runtime.
# Multi-stage builds solve this by splitting the process into two images:
#   Stage 1 (builder) — has Maven + JDK; compiles and packages the JAR.
#   Stage 2 (runtime) — has only a JRE; copies the JAR from stage 1.
# The builder image is discarded after the build; only stage 2 is shipped.
# Result: ~120 MB final image instead of ~500 MB.
# =============================================================================

# -----------------------------------------------------------------------------
# STAGE 1 — BUILD
# Base image: official Maven image bundling Eclipse Temurin JDK 21.
# Eclipse Temurin is the community build of OpenJDK by the Eclipse Adoptium
# project — free, production-grade, and regularly patched.
# Pinning "3.9.9" ensures reproducible builds across environments.
# -----------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

# Set the working directory inside the container.
# All subsequent COPY and RUN instructions are relative to this path.
WORKDIR /workspace

# -----------------------------------------------------------------------------
# DEPENDENCY CACHING — copy pom.xml before source code.
#
# Docker builds images in layers. Each instruction creates a cached layer:
# if neither the instruction nor its inputs changed, Docker reuses the cache.
#
# Maven dependencies (declared in pom.xml) change far less often than source.
# By copying pom.xml first and resolving dependencies before copying src/, we
# guarantee:
#   - Source-only change  → dependency layer is reused  (fast rebuild)
#   - pom.xml change      → dependencies are re-downloaded (necessary, slower)
#
# "dependency:go-offline" downloads and caches every declared dependency
# (compile, test, plugin) into ~/.m2 so the later "package" step works
# without any network access.
# "-q" suppresses verbose Maven output (quiet mode).
# -----------------------------------------------------------------------------
COPY pom.xml ./
RUN mvn dependency:go-offline -q

# Copy the full source tree AFTER the dependency step so source changes
# do not bust the expensive dependency-download cache layer above.
COPY src ./src

# -----------------------------------------------------------------------------
# PACKAGE — compile and produce the executable JAR.
#
# "mvn package" runs the Maven lifecycle up to the "package" phase:
#   validate → compile → test → package
#
# "-DskipTests" skips test execution for two reasons:
#   1. Integration tests use Testcontainers, which needs a Docker daemon
#      inside the builder — not available in most CI environments.
#   2. Tests belong in a dedicated CI pipeline step, not the image build.
#
# Spring Boot's Maven plugin wraps the standard JAR into an executable
# "fat JAR" (uber JAR) that bundles all dependencies plus an embedded
# Tomcat, so the app starts with "java -jar app.jar" — no external server.
# -----------------------------------------------------------------------------
RUN mvn -q -DskipTests package

# =============================================================================
# STAGE 2 — RUNTIME
# Base image: Eclipse Temurin JRE 21 on Alpine Linux.
#
# JRE vs JDK: the JRE contains only what is needed to *run* Java programs
# (JVM + standard libraries). The JDK adds compiler, debugger, and dev tools —
# none of which are needed at runtime. Using the JRE saves ~100 MB.
#
# Alpine Linux: a minimal Linux distro (~5 MB) using musl libc and BusyBox
# instead of glibc. Standard choice for small, secure production images.
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# Working directory for the running application.
WORKDIR /app

# -----------------------------------------------------------------------------
# LEAST-PRIVILEGE PRINCIPLE — run as a non-root user.
#
# Docker containers run as root (UID 0) by default. If an attacker exploits
# a vulnerability, root access gives full control over the filesystem and
# potentially the host machine.
#
# We create a dedicated system group and user (no login shell, no home dir)
# and switch to that user before launching the app:
#   addgroup -S appgroup  → -S = system group (no password)
#   adduser  -S appuser   → -S = system user; -G assigns the group
#   USER appuser          → all subsequent RUN/CMD/ENTRYPOINT run as appuser
# -----------------------------------------------------------------------------
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# -----------------------------------------------------------------------------
# Copy only the fat JAR from stage 1 into this image.
# "--from=builder" references the named stage above.
# The JDK, Maven, source code, and entire ~/.m2 cache are NOT copied —
# they exist only in the builder image, which is discarded after this step.
# This is the key benefit of multi-stage builds.
# -----------------------------------------------------------------------------
COPY --from=builder /workspace/target/RestReactApplication-0.0.1-SNAPSHOT.jar app.jar

# -----------------------------------------------------------------------------
# Declare the port the application listens on.
# EXPOSE is documentation only — it does not publish the port to the host.
# Actual port mapping is configured in docker-compose.yml under "ports:" or
# via "docker run -p 8080:8080".
# -----------------------------------------------------------------------------
EXPOSE 8080

# -----------------------------------------------------------------------------
# HEALTH CHECK
#
# Docker runs this command periodically inside the container.
# A non-zero exit code marks the container "unhealthy"; Docker Compose (and
# Kubernetes liveness probes) can then restart it automatically.
#
# /actuator/health (Spring Boot Actuator) aggregates multiple indicators:
#   application status, DataSource connectivity, disk space, …
# It returns {"status":"UP"} when healthy (exit 0) or {"status":"DOWN"} (exit 1).
#
# Parameters:
#   --interval=30s     run a check every 30 s after the previous one finishes
#   --timeout=5s       mark unhealthy if the check takes longer than 5 s
#   --start-period=60s ignore failures during the first 60 s (Spring Boot
#                      needs time to start up and connect to PostgreSQL)
#   --retries=3        mark unhealthy only after 3 consecutive failures
#
# wget flags:
#   -q    quiet (suppresses progress output)
#   -O-   write the response body to stdout
# grep -q '"status":"UP"' exits 0 if the string is found, 1 otherwise.
# "|| exit 1" ensures Docker always receives a non-zero code on failure.
# -----------------------------------------------------------------------------
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# -----------------------------------------------------------------------------
# ENTRYPOINT — command executed when the container starts.
#
# Exec form (JSON array) vs shell form ("java -jar app.jar"):
#   - The JVM becomes PID 1 directly — no intermediate shell process.
#   - Docker's SIGTERM reaches the JVM immediately, enabling Spring Boot's
#     graceful shutdown: in-flight requests are drained before the process
#     exits (requires server.shutdown=graceful in application.yaml).
#
# JVM flags:
#   -XX:+UseContainerSupport
#       Reads CPU and memory limits from Linux cgroups (set by Docker)
#       instead of querying the host's total RAM. Without this flag, the
#       JVM sizes the heap against the host memory and the container is
#       killed by the OOM killer.
#
#   -XX:MaxRAMPercentage=75.0
#       Caps the heap at 75 % of the container's memory limit.
#       The remaining 25 % covers thread stacks, Metaspace, native caches,
#       and the OS. Example: 512 MB limit → max heap ≈ 384 MB.
#
#   -Djava.security.egd=file:/dev/./urandom
#       The JVM's SecureRandom defaults to /dev/random, which blocks when
#       the kernel entropy pool is low (common in I/O-idle containers).
#       "/dev/./urandom" (note the extra dot — it bypasses a JDK
#       canonical-path check that would remap it back to /dev/random)
#       avoids startup hangs during cryptographic init (HTTPS, Spring
#       Security session-ID generation, etc.).
# -----------------------------------------------------------------------------
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
