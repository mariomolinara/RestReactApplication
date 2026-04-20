# ---- Stage 1: build ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cache Maven dependencies separately from source code.
# pom.xml changes rarely; source changes often.
COPY pom.xml ./
RUN mvn dependency:go-offline -q

# Copy source and package (skip tests – they need H2 and run in CI)
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/target/RestReactApplication-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Graceful shutdown + container health
# /actuator/health is provided by spring-boot-starter-actuator.
# It checks both the app itself and its DB connection (DataSource health indicator).
# start-period is set to 60 s to give Spring Boot time to start before the first check.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
