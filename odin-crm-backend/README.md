# ODIN CRM — Backend

Spring Boot 3 microservice skeleton for the ODIN CRM platform.
Multi-module Gradle project following DDD layered architecture.

## Prerequisites

| Tool        | Version  | Notes                       |
|-------------|----------|-----------------------------|
| Java        | 21 LTS   | eclipse-temurin recommended |
| Docker      | 24+      | Docker Desktop or Rancher   |
| Docker Compose | v2+   | Bundled with Docker Desktop |
| Gradle      | 8.8      | Use `./gradlew` wrapper     |

> **First-time setup:** Generate the Gradle wrapper JAR (requires Gradle installed globally):
> ```bash
> gradle wrapper --gradle-version 8.8
> ```
> Alternatively, download `gradle-wrapper.jar` from Spring Initializr and place it in `gradle/wrapper/`.

---

## Quick Start

### 1. Start local infrastructure

```bash
cd docker
docker compose up -d
```

Wait for all services to be healthy:
```bash
docker compose ps
```

| Service    | URL                               | Credentials               |
|------------|-----------------------------------|---------------------------|
| PostgreSQL | `localhost:5432`                  | odin_crm_app / local_password |
| Redis      | `localhost:6379`                  | —                         |
| Kafka      | `localhost:9092`                  | —                         |
| Keycloak   | http://localhost:8180             | admin / admin             |
| OpenSearch | http://localhost:9200             | security disabled         |

### 2. Run the application

```bash
./gradlew :crm-web:bootRun --args='--spring.profiles.active=local'
```

### 3. Verify health

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

curl http://localhost:8080/actuator/health/liveness
# → {"status":"UP"}

curl http://localhost:8080/actuator/health/readiness
# → {"status":"UP"} (requires all infra services running)
```

---

## Run Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :crm-domain:test
./gradlew :crm-web:test

# With report
./gradlew test --info
```

### Integration Tests (Testcontainers)

The `crm-infrastructure` module includes integration tests that spin up a real PostgreSQL container via Testcontainers. These require Docker Desktop to be running before executing `./gradlew test`.

**Prerequisites (Windows — Docker Desktop):**

1. Docker Desktop must be running.
2. Enable the TCP daemon:
   `Settings → General → "Expose daemon on tcp://localhost:2375 without TLS"` → ON → Apply & Restart.

**First run:** Testcontainers downloads `postgres:17-alpine` (~85 MB) on first execution. Subsequent runs use the cached image.

**CI/CD:** Set `DOCKER_API_VERSION=1.46` environment variable to pin the Docker API version (required for Docker Engine 29.x).

```bash
# Run only infrastructure integration tests
./gradlew :crm-infrastructure:test
```

---

## Build Docker Image

```bash
docker build -t odin-crm-backend:local .

# Verify image size (must be < 200MB)
docker images odin-crm-backend

# Run container
docker run -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e KEYCLOAK_ISSUER_URI=http://host.docker.internal:8180/realms/odin \
  --spring.profiles.active=local \
  odin-crm-backend:local
```

---

## Project Structure

```
odin-crm-backend/
├── crm-domain/          # Pure Java — aggregates, value objects, domain services
│   └── src/main/java/br/com/odin/crm/domain/
│       ├── account/     # Account aggregate
│       ├── activity/    # Activity aggregate
│       ├── opportunity/ # Opportunity aggregate
│       ├── order/       # Order aggregate
│       └── shared/      # Cross-cutting domain primitives
│
├── crm-application/     # Use cases — ports (interfaces) + DTOs
│   └── src/main/java/br/com/odin/crm/application/
│       └── {domain}/
│           ├── dto/     # Request/response data objects
│           └── port/    # Repository and service interfaces
│
├── crm-infrastructure/  # Adapters — JPA, Kafka, gRPC, Redis, OpenSearch
│   └── src/main/java/br/com/odin/crm/infrastructure/
│       ├── persistence/ # Spring Data JPA + Flyway
│       ├── messaging/   # Kafka producers/consumers
│       ├── grpc/        # gRPC service implementations
│       ├── cache/       # Redis
│       ├── search/      # OpenSearch
│       └── security/    # Keycloak JWT adapter
│
└── crm-web/             # Spring Boot app — controllers, security config, Actuator
    └── src/main/java/br/com/odin/crm/web/
        ├── CrmApplication.java
        ├── rest/         # REST controllers
        ├── exception/    # Global error handling
        └── security/     # Spring Security configuration
```

**Dependency rule (no reverse dependencies):**
```
crm-web → crm-infrastructure → crm-application → crm-domain
```

---

## Environment Variables

| Variable                  | Default                                  | Description              |
|---------------------------|------------------------------------------|--------------------------|
| `DB_HOST`                 | `localhost`                              | PostgreSQL host          |
| `DB_USER`                 | `odin_crm_app`                           | PostgreSQL username      |
| `DB_PASSWORD`             | `local_password`                         | PostgreSQL password      |
| `REDIS_HOST`              | `localhost`                              | Redis host               |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                         | Kafka bootstrap          |
| `KEYCLOAK_ISSUER_URI`     | `http://localhost:8180/realms/odin`      | Keycloak realm URL       |

---

## Stopping Services

```bash
cd docker
docker compose down          # stop and remove containers
docker compose down -v       # also remove volumes (resets data)
```
