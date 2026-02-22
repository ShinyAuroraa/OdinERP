# WMS Service — Odin ERP

Microserviço de Warehouse Management System do Odin ERP.

## Pré-requisitos

- Java 21 (JDK)
- Maven 3.9+
- Docker & Docker Compose
- kubectl (para deploy)

## Variáveis de Ambiente

| Variável | Descrição | Padrão (local) |
|----------|-----------|----------------|
| `DB_URL` | JDBC URL do PostgreSQL | `jdbc:postgresql://localhost:5432/wms_db` |
| `DB_USER` | Usuário do banco | `wms_user` |
| `DB_PASSWORD` | Senha do banco | `wms_pass` |
| `REDIS_URL` | URL do Redis | `redis://localhost:6379` |
| `ES_URL` | URL do Elasticsearch | `http://localhost:9200` |
| `SERVER_PORT` | Porta da API | `8080` |
| `MANAGEMENT_PORT` | Porta do Actuator | `8081` |

## Rodando Localmente

```bash
# 1. Subir dependências com Docker
docker run -d --name wms-postgres -e POSTGRES_DB=wms_db \
  -e POSTGRES_USER=wms_user -e POSTGRES_PASSWORD=wms_pass \
  -p 5432:5432 postgres:17-alpine

docker run -d --name wms-redis -p 6379:6379 redis:7-alpine

docker run -d --name wms-elasticsearch \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -p 9200:9200 \
  docker.elastic.co/elasticsearch/elasticsearch:8.11.3

# 2. Rodar a aplicação
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Executando Testes

```bash
# Todos os testes (requer Docker para Testcontainers)
mvn verify

# Apenas testes unitários
mvn test -Dtest="**/*Test.java" -DexcludedGroups="integration"
```

## Health Checks

```bash
# Saúde geral
curl http://localhost:8081/actuator/health

# Liveness (K8s)
curl http://localhost:8081/actuator/health/liveness

# Readiness (K8s)
curl http://localhost:8081/actuator/health/readiness

# Métricas Prometheus
curl http://localhost:8081/actuator/prometheus
```

## Build Docker

```bash
docker build -t odin/wms-service:local .
docker images odin/wms-service:local  # verificar tamanho < 200MB
```

## Deploy Kubernetes

```bash
kubectl apply -f k8s/wms/ -n odin
kubectl rollout status deployment/wms-service -n odin
```

## Arquitetura

Ver [EPIC-WMS-001](../../docs/stories/epics/epic-1-wms-base.md) para detalhes completos.

**Stack:** Java 21 · Spring Boot 3.2 · PostgreSQL 17 · Redis 7 · Elasticsearch 8 · Docker · Kubernetes
