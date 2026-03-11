FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Run as a non-root user to reduce attack surface
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
COPY target/api-monitor-*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

# Default to prod; docker-compose overrides this via SPRING_PROFILES_ACTIVE env var.
# Never exposes H2 console or dev defaults in a plain "docker run".
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]