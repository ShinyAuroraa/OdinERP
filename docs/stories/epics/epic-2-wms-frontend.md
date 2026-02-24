---
epicNum: 2
epicId: EPIC-FE-001
title: "WMS Frontend — Interface Web Completa (React 19 + Next.js 15)"
status: Complete
module: WMS — Frontend
projeto: Odin ERP
createdBy: "@pm (Morgan)"
createdAt: "2026-02-24"
completedAt: "2026-02-24"
version: "1.1.0"
storiesTotal: 15
storiesCompleted: 15
wavesTotal: 7
wavesCompleted: [1, 2, 3, 4, 5, 6, 7]
wavesInProgress: []
wavesNext: []
---

# Epic 2 — WMS Frontend: Interface Web Completa

**ID:** EPIC-FE-001
**Módulo:** WMS — Frontend
**Projeto:** Odin ERP
**Status:** Complete ✅
**Criado por:** @pm (Morgan)
**Data:** 2026-02-24

---

## Epic Goal

Implementar a interface web completa do WMS Odin ERP usando React 19, Next.js 15 App Router, Tailwind CSS e shadcn/ui — cobrindo 100% das operações do backend WMS (EPIC-WMS-001): autenticação Keycloak PKCE, cadastros, inbound, gestão de estoque, outbound, integração MRP e relatórios regulatórios — com experiência de usuário responsiva e acessível para operadores de armazém, supervisores e administradores.

---

## Existing System Context

- **Projeto:** Brownfield — WMS backend (EPIC-WMS-001) 100% completo e em produção
- **Backend API:** Spring Boot 3.2.3 REST APIs em `/api/v1/` com JWT (Keycloak)
- **Autenticação:** Keycloak realm `odin`, RBAC com 4 roles: `WMS_ADMIN`, `WMS_SUPERVISOR`, `WMS_OPERATOR`, `WMS_VIEWER`
- **Multi-tenant:** claim `tenant_id` no JWT, todos os endpoints filtrados por tenant
- **Stack Frontend:**
  - React 19 + Next.js 15 (App Router, Server Components, Streaming)
  - TypeScript 5.x
  - Tailwind CSS 3.x + shadcn/ui (Radix UI primitives)
  - TanStack Query v5 (React Query — cache, sync, optimistic updates)
  - TanStack Table v8 (tabelas com sort/filter/pagination)
  - Keycloak JS Adapter (PKCE auth flow para SPA/Next.js)
  - next-intl (internacionalização PT-BR + EN)
  - Recharts (gráficos e dashboards)
  - React Hook Form + Zod (formulários e validação)
  - Lucide React (ícones)
- **Containerização:** Docker + Kubernetes (mesmo cluster do wms-service)
- **CI/CD:** GitHub Actions + ArgoCD

**APIs backend disponíveis (EPIC-WMS-001):**
- Warehouses, Zones, Aisles, Shelves, Locations (CRUD completo)
- Products WMS (CRUD + controles lote/serial/validade)
- Receiving Notes (recebimento de mercadorias)
- Putaway (alocação de localizações)
- Quarantine (gestão de quarentena)
- Stock Balance (saldo em tempo real por SKU/lote/localização)
- Lot & Serial Traceability (rastreabilidade completa)
- Physical Inventory (sessões de contagem)
- Audit Log (log imutável)
- Internal Transfers (transferências internas)
- Picking Orders (separação + CRM integration)
- Packing (estação de embalagem)
- Shipping (expedição)
- MRP Integration (requisições de material de produção)
- Reports (Ficha Estoque, ANVISA, Rastreabilidade, Movimentações — PDF/Excel/XML/JSON)
- Report Schedules (agendamento de relatórios)

---

## Story Summary

| # | Story | Wave | Complexidade | Status |
|---|-------|------|-------------|--------|
| 1.1 | Bootstrap Next.js 15 + Keycloak PKCE + Layout Shell | 1 | M | ✅ Done |
| 1.2 | Design System & Componentes Base (shadcn/ui + React Query) | 1 | M | ✅ Done |
| 2.1 | Gestão de Armazéns (CRUD + Zonas + Corredores + Prateleiras + Localizações) | 2 | L | ✅ Done |
| 2.2 | Gestão de Produtos WMS (CRUD + Controles Lote/Serial) | 2 | M | ✅ Done |
| 3.1 | Recebimento de Mercadorias (Notas + Conferência + Lotes) | 3 | L | ✅ Done |
| 3.2 | Putaway & Quarentena (Alocação + Gestão Quarentena) | 3 | M | ✅ Done |
| 4.1 | Controle de Estoque em Tempo Real (Saldo + Filtros + Drill-down) | 4 | L | ✅ Done |
| 4.2 | Rastreabilidade (Timeline Lote/Serial + Histórico Movimentações) | 4 | M | ✅ Done |
| 4.3 | Inventário Físico (Sessões + Workflow Contagem + Ajustes) | 4 | M | ✅ Done |
| 4.4 | Auditoria & Transferências (Log Imutável + Transferências Internas) | 4 | M | ✅ Done |
| 5.1 | Picking (Ordens + Workflow Separação + Status) | 5 | L | ✅ Done |
| 5.2 | Packing & Shipping (Estação Embalagem + Expedição + Despacho) | 5 | M | ✅ Done |
| 6.1 | Integração MRP (Dashboard Requisições Material + Status Saga) | 6 | M | ✅ Done |
| 7.1 | Relatórios Regulatórios (Ficha/ANVISA/Rastreabilidade/Movimentações + Export + Agendamento) | 6 | L | ✅ Done |
| 8.1 | Dashboard Principal & Analytics (KPIs + Gráficos + Visão Operacional) → Story 15.1 | 7 | L | ✅ Done |

**Total: 15 stories, 7 waves**

---

## Wave Plan

### Wave 1 — Foundation (Stories 1.1–1.2)
**Objetivo:** Estrutura base do projeto Next.js com autenticação funcional e design system pronto para uso.

- **1.1:** Bootstrap Next.js 15 App Router, Keycloak PKCE auth, multi-tenant middleware, layout shell (sidebar + header + breadcrumbs), rotas protegidas por role, i18n PT-BR
- **1.2:** Configuração shadcn/ui completa, componentes base reutilizáveis (DataTable, FormField, PageHeader, StatusBadge, ConfirmDialog, LoadingState, ErrorBoundary), React Query provider, API client com interceptor JWT

**Desbloqueado por:** EPIC-WMS-001 concluído

---

### Wave 2 — Cadastros Base (Stories 2.1–2.2)
**Objetivo:** Interface de cadastro e configuração do armazém.

- **2.1:** CRUD Warehouses → Zones → Aisles → Shelves → Locations (hierarquia em árvore), mapa visual do armazém (grid de localizações), filtros por tipo/status/capacidade
- **2.2:** CRUD Produtos WMS, SKU search, configuração de controles (lote, serial, validade, vigilância sanitária), bulk import CSV

**Desbloqueado por:** Wave 1

---

### Wave 3 — Inbound (Stories 3.1–3.2)
**Objetivo:** Fluxo completo de entrada de mercadorias.

- **3.1:** Criar/listar notas de recebimento, wizard de conferência item por item, atribuição de lotes, geração de etiquetas, integração com ordens de compra (SCM placeholder)
- **3.2:** Fila de putaway com sugestão de localização, confirmação de alocação, gestão de quarentena (visualizar/liberar/rejeitar itens)

**Desbloqueado por:** Wave 2

---

### Wave 4 — Gestão de Estoque (Stories 4.1–4.4)
**Objetivo:** Visibilidade e controle completo do estoque.

- **4.1:** Dashboard de saldo por SKU/lote/localização, filtros avançados, export, alertas de estoque mínimo
- **4.2:** Timeline de rastreabilidade de lote/serial, histórico de movimentações por produto/lote/localização
- **4.3:** Criar sessões de inventário, workflow de contagem (parcial/completo), tela de ajuste de divergências
- **4.4:** Viewer do audit log com filtros tenant/operador/período/operação, gestão de transferências internas (criar/confirmar/cancelar)

**Desbloqueado por:** Wave 3

---

### Wave 5 — Outbound (Stories 5.1–5.2)
**Objetivo:** Fluxo completo de saída de mercadorias.

- **5.1:** Dashboard de ordens de picking, workflow de separação (item por item com confirmação), status em tempo real, integração com ordens CRM
- **5.2:** Estação de embalagem (pesagem, geração de etiquetas/QR code), gestão de expedição (criar romaneio, confirmar despacho, rastrear carrier)

**Desbloqueado por:** Wave 4

---

### Wave 6 — Integrações & Relatórios (Stories 6.1–7.1)
**Objetivo:** Visibilidade de integrações e compliance regulatório.

- **6.1:** Dashboard de integração MRP (requisições de material, status da saga PENDING→PICKING, alertas de falta de estoque)
- **7.1:** Geração de todos os 4 relatórios (Ficha de Estoque, ANVISA, Rastreabilidade Lote, Movimentações), seletor de formato (JSON/PDF/Excel/XML), download direto, agendamento de relatórios com cron expression

**Desbloqueado por:** Wave 5

---

### Wave 7 — Dashboard & Analytics (Story 8.1)
**Objetivo:** Visão executiva e operacional do armazém.

- **8.1:** Dashboard principal com KPIs em tempo real (movimentações/dia, taxa de picking, utilização de capacidade, top SKUs), gráficos Recharts (linha/barra/pizza), widgets configuráveis por role, modo escuro

**Desbloqueado por:** Wave 6

---

## Technical Architecture

### Estrutura de Pastas

```
apps/wms-web/
├── app/                          # Next.js 15 App Router
│   ├── (auth)/                   # Grupo de rotas autenticadas
│   │   ├── layout.tsx            # Layout com sidebar + header
│   │   ├── dashboard/            # Wave 7 — Dashboard principal
│   │   ├── warehouses/           # Wave 2 — Gestão armazéns
│   │   ├── products/             # Wave 2 — Produtos WMS
│   │   ├── receiving/            # Wave 3 — Recebimento
│   │   ├── putaway/              # Wave 3 — Putaway
│   │   ├── stock/                # Wave 4 — Estoque
│   │   ├── inventory/            # Wave 4 — Inventário
│   │   ├── picking/              # Wave 5 — Picking
│   │   ├── shipping/             # Wave 5 — Shipping
│   │   ├── mrp/                  # Wave 6 — Integração MRP
│   │   ├── reports/              # Wave 6 — Relatórios
│   │   └── audit/                # Wave 4 — Auditoria
│   ├── api/                      # Next.js Route Handlers (BFF)
│   │   └── auth/[...nextauth]/   # Keycloak PKCE callback
│   ├── login/                    # Página de login
│   └── layout.tsx                # Root layout
├── components/
│   ├── ui/                       # shadcn/ui (auto-gerado)
│   ├── wms/                      # Componentes de domínio WMS
│   └── shared/                   # Componentes compartilhados
├── lib/
│   ├── api/                      # API client + hooks React Query
│   ├── auth/                     # Keycloak adapter + middleware
│   └── utils/                    # Utilitários
├── hooks/                        # Custom hooks
├── types/                        # TypeScript types (DTOs do backend)
└── middleware.ts                  # Auth middleware (protege rotas)
```

### Auth Flow (Keycloak PKCE)

```
Browser → Next.js middleware → Keycloak (PKCE) → JWT
JWT contém: sub, tenant_id, realm_access.roles
Middleware: verifica token, extrai tenant_id → injeta em header X-Tenant-ID
API client: adiciona Authorization: Bearer {token} em toda requisição
```

### React Query Strategy

```typescript
// Cache keys por domínio
const warehouseKeys = {
  all: ['warehouses'] as const,
  byTenant: (tenantId: string) => [...warehouseKeys.all, tenantId] as const,
}

// Stale time: 30s para dados operacionais, 5min para cadastros
// Optimistic updates para confirmações de picking/packing
// Polling: 10s para stock balance e picking status
```

### RBAC Frontend

```typescript
type WmsRole = 'WMS_ADMIN' | 'WMS_SUPERVISOR' | 'WMS_OPERATOR' | 'WMS_VIEWER'

// Proteção por rota: middleware.ts verifica role antes de renderizar
// Proteção por componente: <RequireRole role="WMS_SUPERVISOR"> wrapper
// Ocultação de botões/ações por role (não apenas rotas)
```

---

## Non-Functional Requirements

| Requisito | Meta |
|-----------|------|
| Lighthouse Performance | ≥ 90 (mobile + desktop) |
| LCP (Largest Contentful Paint) | ≤ 2.5s |
| Acessibilidade (WCAG 2.1 AA) | ≥ 85 score |
| Responsividade | Mobile (320px) → Desktop (1920px) |
| Cobertura de testes | ≥ 70% (unit + integration) |
| Bundle inicial (JS) | ≤ 200KB gzipped |
| TypeScript strict mode | 100% — sem `any` explícito |

---

## Definition of Done (Epic-level)

- [x] Todas as 15 stories com status Done
- [x] Testes passando: unit (Vitest) + integration (Testing Library) — 169/169 ✅
- [ ] Lighthouse ≥ 90 em produção (pendente deploy K8s)
- [x] Keycloak PKCE funcionando com os 4 roles WMS
- [x] Multi-tenant validado (dados isolados por tenant_id)
- [ ] Docker image buildada e deployada no cluster K8s (pendente deploy)
- [x] CI/CD pipeline configurado (lint + typecheck + test + build)
- [ ] Documentação de componentes (Storybook opcional — out of scope MVP)

---

## Risks

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| Next.js 15 + Keycloak PKCE — incompatibilidade | Média | Alto | Usar `keycloak-js` diretamente com PKCE; middleware Next.js gerencia sessão |
| Server Components vs Client Components — overfetch | Alta | Médio | Regra: Server Components para leitura, Client Components para interatividade |
| Tailwind CSS purge remove classes dinâmicas | Baixa | Médio | Safelist para classes geradas dinamicamente (ex: cores de status) |
| React Query v5 breaking changes vs v4 | Baixa | Baixo | Usar v5 desde o início; não migrar v4 |
| Bundle size com shadcn/ui | Média | Médio | Tree-shaking + code splitting por rota |
| CORS em desenvolvimento local | Alta | Baixo | Next.js rewrites proxiam API — sem CORS em dev |

---

## Change Log

| Data | Versão | Descrição | Autor |
|------|--------|-----------|-------|
| 2026-02-24 | 1.0.0 | Epic criado — WMS Frontend MVP Completo, 15 stories, 7 waves | @pm (Morgan) |
| 2026-02-24 | 1.1.0 | EPIC-FE-001 concluído — 15/15 stories Done, 7/7 waves completas. 169/169 testes passando. 25 rotas implementadas. PR #21 (Story 15.1 Dashboard). | @po (Pax) |
