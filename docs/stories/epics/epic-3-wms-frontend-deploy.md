---
epicNum: 3
epicId: EPIC-FE-002
title: "WMS Frontend Deploy — Docker + GitHub Actions CI/CD + Kubernetes"
status: Ready
module: WMS — Frontend Infrastructure
projeto: Odin ERP
createdBy: "@pm (Morgan)"
createdAt: "2026-02-24"
version: "1.0.0"
storiesTotal: 5
storiesCompleted: 4
wavesTotal: 3
wavesCompleted: [1, 2]
wavesInProgress: []
wavesNext: [3]
---

# Epic 3 — WMS Frontend Deploy: Docker + CI/CD + Kubernetes

**ID:** EPIC-FE-002
**Módulo:** WMS — Frontend Infrastructure
**Projeto:** Odin ERP
**Status:** Ready
**Criado por:** @pm (Morgan) via @aios-master
**Data:** 2026-02-24

---

## Epic Goal

Containerizar e fazer deploy do WMS Frontend (`wms-web`) em ambiente de produção: Dockerfile multi-stage otimizado, pipeline CI/CD completo via GitHub Actions (lint + typecheck + test + build + security scan + deploy), manifestos Kubernetes production-ready (Deployment, Service, Ingress, ConfigMap, Secret), e observabilidade básica com health endpoints e métricas Prometheus — integrando com o cluster existente do `wms-service` (EPIC-WMS-001).

---

## Existing System Context

- **Projeto:** Brownfield — WMS Frontend (EPIC-FE-001) 100% implementado, 169/169 testes passando
- **App:** Next.js 15 App Router, TypeScript, Tailwind CSS, shadcn/ui — em `apps/wms-web/`
- **Monorepo:** Turborepo (`apps/wms-web` + `services/wms-service`)
- **Autenticação:** Keycloak realm `odin`, PKCE flow — precisa de variáveis de ambiente em runtime
- **Backend:** WMS Service rodando em K8s (`wms-service` deployment)
- **Registry:** GitHub Container Registry (ghcr.io) ou Docker Hub
- **Infraestrutura alvo:** Kubernetes local (`kind` ou `minikube`) para desenvolvimento/testes; cluster remoto em produção
- **CI/CD existente:** GitHub Actions com branch protection em `main`

**Variáveis de ambiente necessárias (wms-web):**
```
NEXT_PUBLIC_API_BASE_URL=https://api.odin.internal/wms/api/v1
NEXT_PUBLIC_KEYCLOAK_URL=https://keycloak.odin.internal
NEXT_PUBLIC_KEYCLOAK_REALM=odin
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=wms-web
NEXTAUTH_SECRET=<secret>
NEXTAUTH_URL=https://wms.odin.internal
```

---

## Story Summary

| # | Story | Wave | Complexidade | Status |
|---|-------|------|-------------|--------|
| 16.1 | Dockerfile Multi-Stage + Docker Compose (dev + prod) | 1 | M | ✅ Done |
| 16.2 | GitHub Actions CI Pipeline (lint + typecheck + test + build + CodeRabbit) | 1 | M | ✅ Done |
| 16.3 | GitHub Actions CD Pipeline (Docker build + push ghcr.io + deploy K8s) | 2 | L | ✅ Done |
| 16.4 | Kubernetes Manifests (Deployment + Service + Ingress + ConfigMap + Secret) | 2 | M | ✅ Done |
| 16.5 | Health Endpoints + Observabilidade (Prometheus metrics + readiness/liveness) | 3 | S | Pending |

**Total: 5 stories, 3 waves**

---

## Wave Plan

### Wave 1 — Foundation (Stories 16.1–16.2)
**Objetivo:** Containerização e pipeline de qualidade contínua.

- **16.1:** Dockerfile multi-stage (builder → runner), imagem Alpine/slim, `.dockerignore`, `docker-compose.yml` (dev com hot-reload + prod estático), variáveis via `.env.local` em dev
- **16.2:** CI workflow (`ci.yml`) disparado em push/PR → `main`: checkout → setup-node → install → lint → typecheck → test (Vitest) → build → upload artifacts; badge no README; falha bloqueia merge

**Desbloqueado por:** EPIC-FE-001 concluído

---

### Wave 2 — Deploy (Stories 16.3–16.4)
**Objetivo:** Pipeline de deploy automático e manifestos K8s.

- **16.3:** CD workflow (`cd.yml`) disparado em push → `main` após CI verde: build Docker image → tag com SHA + `latest` → push `ghcr.io/shinuauroraa/odin-wms-web` → kubectl rollout restart; secrets via GitHub Actions Secrets + Environments (staging/production)
- **16.4:** Manifestos K8s em `infra/k8s/wms-web/`: `deployment.yaml` (2 réplicas, resource limits, rolling update), `service.yaml` (ClusterIP), `ingress.yaml` (nginx, TLS via cert-manager), `configmap.yaml` (variáveis não-sensíveis), `secret.yaml` (template — valores via CI/CD, não commitados)

**Desbloqueado por:** Wave 1

---

### Wave 3 — Observabilidade (Story 16.5)
**Objetivo:** Health checks e métricas para o cluster K8s.

- **16.5:** `app/api/health/route.ts` (readiness + liveness endpoints), `app/api/metrics/route.ts` (exposição Prometheus — next-prometheus ou prom-client), anotações K8s para Prometheus scrape, `readinessProbe` + `livenessProbe` no Deployment

**Desbloqueado por:** Wave 2

---

## Technical Architecture

### Estrutura de Arquivos

```
apps/wms-web/
├── Dockerfile              # Multi-stage build
├── .dockerignore           # Excluir node_modules, .next, etc.
├── docker-compose.yml      # Dev + prod services
└── app/api/
    ├── health/route.ts     # Health check endpoint
    └── metrics/route.ts    # Prometheus metrics

infra/k8s/wms-web/
├── deployment.yaml         # K8s Deployment
├── service.yaml            # K8s Service (ClusterIP)
├── ingress.yaml            # Nginx Ingress + TLS
├── configmap.yaml          # Variáveis não-sensíveis
└── secret.yaml             # Template (valores via CI/CD)

.github/workflows/
├── ci.yml                  # CI: lint + typecheck + test + build
└── cd.yml                  # CD: Docker build + push + K8s deploy
```

### Dockerfile Multi-Stage

```dockerfile
# Stage 1: Dependencies
FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

# Stage 2: Builder
FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# Stage 3: Runner
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=deps /app/node_modules ./node_modules
COPY package.json .
EXPOSE 3000
CMD ["npm", "start"]
```

### CI/CD Flow

```
Push → main
  └── CI (ci.yml)
       ├── npm run lint ✅
       ├── npm run typecheck ✅
       ├── npm test ✅
       └── npm run build ✅
            └── CD (cd.yml) [only on success]
                 ├── docker build -t ghcr.io/...wms-web:$SHA
                 ├── docker push ghcr.io/...wms-web:$SHA
                 └── kubectl rollout restart deployment/wms-web
```

### K8s Resource Limits

```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

---

## Non-Functional Requirements

| Requisito | Meta |
|-----------|------|
| Docker image size | ≤ 300MB (alpine base) |
| CI pipeline duration | ≤ 5 minutos |
| CD pipeline duration | ≤ 10 minutos (build + push + deploy) |
| K8s rolling update | Zero-downtime (maxSurge: 1, maxUnavailable: 0) |
| Health check response | ≤ 200ms |
| Réplicas mínimas | 2 (alta disponibilidade) |

---

## Definition of Done (Epic-level)

- [ ] Dockerfile multi-stage buildando sem erros (`docker build` OK)
- [ ] `docker-compose up` inicia wms-web em dev com hot-reload
- [ ] CI pipeline verde em todos os PRs para `main`
- [ ] CD pipeline deployando automaticamente para cluster K8s
- [ ] Imagem publicada em `ghcr.io` com tag SHA + `latest`
- [ ] Manifestos K8s aplicados no cluster (kubectl apply)
- [ ] `https://wms.odin.internal` acessível via Ingress + TLS
- [ ] Health endpoints respondendo (`/api/health` → 200 OK)
- [ ] Prometheus scraping métricas do wms-web
- [ ] Zero secrets commitados no repositório

---

## Risks

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| Next.js 15 standalone build — configuração específica | Média | Alto | Usar `output: 'standalone'` em `next.config.ts` |
| Variáveis de ambiente NEXT_PUBLIC em build-time | Alta | Alto | Usar `ARG` no Dockerfile; env vars runtime via ConfigMap |
| Keycloak CORS em produção | Média | Alto | Configurar `Valid Redirect URIs` no realm Keycloak |
| K8s cluster não disponível para testes | Alta | Médio | Usar `kind` ou `minikube` para testes locais |
| Docker image size excessiva | Baixa | Baixo | `.dockerignore` agressivo + multi-stage |

---

## Agent Assignments

| Story | Dev | DevOps | Notes |
|-------|-----|--------|-------|
| 16.1 Dockerfile | @dev | @devops | @dev cria, @devops valida e push |
| 16.2 CI Pipeline | @devops | — | @devops é authority em CI/CD |
| 16.3 CD Pipeline | @devops | — | @devops exclusivo para deploy |
| 16.4 K8s Manifests | @dev + @devops | — | @dev cria templates, @devops aplica |
| 16.5 Health/Metrics | @dev | @devops | @dev implementa endpoints, @devops configura scrape |

---

## Change Log

| Data | Versão | Descrição | Autor |
|------|--------|-----------|-------|
| 2026-02-24 | 1.0.0 | Epic criado — WMS Frontend Deploy: Docker + CI/CD + K8s, 5 stories, 3 waves | @pm (Morgan) via @aios-master |
