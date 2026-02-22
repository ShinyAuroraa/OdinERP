# ─────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy wrapper + build scripts first — Docker layer cache for Gradle download
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# Copy each module
COPY crm-domain/       crm-domain/
COPY crm-application/  crm-application/
COPY crm-infrastructure/ crm-infrastructure/
COPY crm-web/          crm-web/

RUN chmod +x ./gradlew && \
    ./gradlew :crm-web:bootJar -x test --no-daemon

# ─────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S odin && adduser -S odin -G odin

WORKDIR /app
COPY --from=builder /build/crm-web/build/libs/*.jar app.jar

USER odin

# HTTP + gRPC (future)
EXPOSE 8080 9090

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
