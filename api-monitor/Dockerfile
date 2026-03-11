# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Full JDK + Maven image; this stage never ships to production.
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Resolve dependencies first — this layer is cached as long as pom.xml doesn't change.
COPY pom.xml .
RUN mvn dependency:resolve --no-transfer-progress -q

# VITE_API_KEY is baked into the React bundle at build time.
# Passed via --build-arg (docker build) / build.args (Compose).
# The value is NOT printed in docker build output (--progress=auto never shows
# individual RUN commands; --progress=plain shows the Dockerfile text literally,
# not the resolved ARG value). It IS stored in the intermediate builder-stage
# layer cache (local only, never pushed to a registry).
ARG VITE_API_KEY
# Fail fast — refuse to build a bundle with a blank key.
RUN test -n "${VITE_API_KEY}" || { echo "ERROR: VITE_API_KEY build-arg is empty or missing"; exit 1; }
# Promote ARG → ENV so Maven → npm → Vite child processes all inherit the value.
# Without this, ARG is only a Dockerfile-level substitution and isn't visible
# in the shell environment that the mvn/node/vite subprocesses actually read.
ENV VITE_API_KEY="${VITE_API_KEY}"

# Copy source and frontend, then build everything in one shot.
COPY src ./src
COPY frontend ./frontend
RUN mvn package -DskipTests --no-transfer-progress

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# JRE-only image — ~200 MB smaller than the JDK builder image.
FROM eclipse-temurin:17-jre-jammy

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
