# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Full JDK + Maven image; this stage never ships to production.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Resolve dependencies first — this layer is cached as long as pom.xml doesn't change.
COPY pom.xml .
RUN mvn dependency:resolve --no-transfer-progress -q


# Copy source and frontend, then build everything in one shot.
COPY src ./src
COPY frontend ./frontend
RUN mvn package -DskipTests --no-transfer-progress

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# JRE-only image 
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Run as a non-root user to reduce attack surface.
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
COPY --from=builder /build/target/api-monitor-*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

# Default to prod; docker-compose can override via SPRING_PROFILES_ACTIVE env var.
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
