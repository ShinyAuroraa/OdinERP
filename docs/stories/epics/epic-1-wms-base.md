# Epic 1 — WMS Base Module: Implementação Completa

**ID:** EPIC-WMS-001
**Módulo:** WMS — Warehouse Management System
**Projeto:** Odin ERP
**Status:** Ready
**Criado por:** @pm (Morgan)
**Data:** 2026-02-21

---

## Epic Goal

Implementar o microserviço WMS completo do Odin ERP, cobrindo toda a cadeia operacional de armazém: infraestrutura, recebimento, armazenagem, separação, expedição, inventário, rastreabilidade, compliance e app mobile Android — integrado ao MRP, SCM, CRM e Keycloak, com observabilidade e infraestrutura production-ready.

---

## Existing System Context

- **Projeto:** Greenfield — Odin ERP
- **Arquitetura:** Microserviços (database-per-service)
- **Stack Backend:** Java 21 + Spring Boot 3 + Spring Cloud
- **Stack Frontend:** React 19 + TypeScript + Next.js 15 (App Router) + Tailwind CSS + shadcn/ui
- **App Mobile:** Kotlin + Jetpack Compose (Android)
- **Banco de Dados:** PostgreSQL 17 (instância dedicada por módulo)
- **Cache:** Redis
- **Busca/Indexação:** Elasticsearch
- **Mensageria:** Apache Kafka
- **Autenticação:** Keycloak (OAuth2 / OIDC / RBAC / multi-tenant)
- **Containerização:** Docker + Kubernetes
- **CI/CD:** GitHub Actions + ArgoCD
- **Observabilidade:** Prometheus + Grafana + ELK Stack + Jaeger
- **Service Mesh:** Istio

**Integrações neste epic:**
- MRP/MRP II → Ordens de produção afetam estoque WMS
- SCM → Pedidos de compra geram recebimentos no WMS
- CRM → Pedidos de venda disparam picking/shipping no WMS
- Keycloak → Autenticação/RBAC em todas as operações

---

## Enhancement Details

**O que está sendo construído:**
Microserviço WMS completo como parte do Odin ERP, operando de forma independente com seu próprio banco de dados, mas integrado ao ecossistema via Kafka e API Gateway.

**Como integra ao sistema:**
- Expõe APIs REST (CRUD) + gRPC (comunicação interna de alta performance)
- Consome eventos Kafka de SCM (pedidos de compra), CRM (pedidos de venda) e MRP (ordens de produção)
- Publica eventos Kafka para atualização de estoque em tempo real
- Autenticação centralizada via Keycloak com RBAC multi-tenant

**Critérios de sucesso:**
- WMS operacional com todos os fluxos inbound/outbound funcionando
- App Android permitindo operações via scanner
- Rastreabilidade completa por lote, série e validade
- Compliance com GS1, auditoria imutável e relatórios regulatórios
- Multi-tenant habilitado
- Observabilidade completa (métricas, logs, tracing)
- Cobertura de testes ≥ 80%
- Pipeline CI/CD funcionando com deploy no K8s

---

## Waves de Desenvolvimento

### Wave 1 — Fundação & Infraestrutura

> Pré-requisito de todas as outras waves. Nenhuma story operacional pode começar sem esta.

---

#### Story 1.1 — WMS Service Bootstrap

```yaml
executor: "@devops"
quality_gate: "@architect"
quality_gate_tools: [docker_validation, k8s_manifest_review, cicd_pipeline_check]
```

- **Descrição:** Criar o projeto Spring Boot 3 do WMS, configurar Dockerfile, manifests Kubernetes, pipeline CI/CD (GitHub Actions + ArgoCD), configurar conexão PostgreSQL dedicada, Redis e Elasticsearch.
- **Executor:** @devops
- **Quality Gate:** @architect (valida padrões de containerização e infra)
- **Quality Gates:**
  - Pre-Commit: Dockerfile lint, K8s manifest validation
  - Pre-PR: Security scan de imagem, pipeline smoke test
- **Foco:** Estrutura base do microserviço, health checks, graceful shutdown

---

#### Story 1.2 — Schema & Domain Model (WMS Core)

```yaml
executor: "@data-engineer"
quality_gate: "@dev"
quality_gate_tools: [schema_validation, migration_review, index_strategy]
```

- **Descrição:** Projetar e criar o schema PostgreSQL completo do WMS: `warehouses`, `zones`, `aisles`, `shelves`, `locations`, `products_wms`, `stock_items`, `lots`, `serial_numbers`, `movements`, `audit_log`.
- **Executor:** @data-engineer
- **Quality Gate:** @dev (valida integridade do schema)
- **Quality Gates:**
  - Pre-Commit: Schema validation, FK constraints, índices
  - Pre-PR: Migration safety check, rollback plan

---

#### Story 1.3 — Autenticação & RBAC Multi-tenant (Keycloak)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [security_review, rbac_validation, multi_tenant_test]
```

- **Descrição:** Integrar Keycloak ao WMS com OAuth2/OIDC. Definir roles: `wms-admin`, `wms-operator`, `wms-viewer`, `wms-supervisor`. Implementar multi-tenant (isolamento por `tenant_id`). Configurar filtros de segurança Spring Security.
- **Executor:** @dev
- **Quality Gate:** @architect (valida modelo de segurança)
- **Quality Gates:**
  - Pre-Commit: Security scan, OWASP validation
  - Pre-PR: Penetration test básico, role isolation test

---

#### Story 1.4 — Observabilidade WMS (Prometheus + ELK + Jaeger + Istio)

```yaml
executor: "@devops"
quality_gate: "@architect"
quality_gate_tools: [metrics_validation, tracing_coverage, istio_config_review]
```

- **Descrição:** Configurar Prometheus metrics customizadas para WMS (movimentações/seg, erros, latência). Configurar ELK para logs estruturados. Integrar Jaeger para distributed tracing. Configurar Istio sidecar e policies de service mesh.
- **Executor:** @devops
- **Quality Gate:** @architect
- **Quality Gates:**
  - Pre-Commit: Metrics naming conventions, log format validation
  - Pre-PR: Dashboard funcional, trace propagation test

---

### Wave 2 — Gestão de Infraestrutura de Armazém

> Depende de: Wave 1 completa.

---

#### Story 2.1 — Cadastro de Armazéns e Estrutura Física

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [api_contract_review, code_review, test_coverage]
```

- **Descrição:** CRUD completo para `warehouses`, `zones`, `aisles`, `shelves` e `locations`. Endereçamento hierárquico (Armazém > Zona > Corredor > Prateleira > Posição). Tipos de localização: picking, bulk, doca de recebimento, doca de expedição, quarentena, avaria. APIs REST + validações de negócio.
- **Executor:** @dev
- **Quality Gate:** @architect
- **Foco:** Modelo de endereçamento flexível, suporte a múltiplos layouts de armazém

---

#### Story 2.2 — Cadastro de Produtos no WMS (SKU + Atributos de Armazenagem)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [api_contract_review, integration_test]
```

- **Descrição:** Cadastro de produtos no contexto WMS com atributos específicos: dimensões, peso, tipo de armazenagem (seco/refrigerado/congelado), controla lote/série/validade, código GS1 (EAN-13, GS1-128, QR Code), capacidade por localização. Sincronização com cadastro master de produtos (via Kafka).
- **Executor:** @dev
- **Quality Gate:** @architect

---

### Wave 3 — Operações Inbound (Recebimento & Putaway)

> Depende de: Waves 1 e 2 completas.

---

#### Story 3.1 — Recebimento de Mercadorias (Inbound — Integração SCM)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [api_contract_review, kafka_integration_test, business_rules_validation]
```

- **Descrição:** Fluxo completo de recebimento: consumir evento Kafka de SCM (Pedido de Compra aprovado), criar Nota de Recebimento, conferência de itens (quantidade, qualidade), leitura de código de barras GS1, registro de lote/série/validade, divergências (falta, excesso, avaria). Interface web e integração com app Android para conferência.
- **Executor:** @dev
- **Quality Gate:** @architect
- **Quality Gates:**
  - Pre-Commit: Kafka consumer validation, business rules test
  - Pre-PR: Integration test com SCM mock, GS1 parsing test

---

#### Story 3.2 — Putaway (Alocação Inteligente de Estoque)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [algorithm_review, performance_test, rules_engine_validation]
```

- **Descrição:** Motor de regras de putaway: FIFO por padrão, FEFO para perecíveis, sugestão automática de posição (baseada em tipo de produto, zona, capacidade disponível, histórico), confirmação de posição via scanner (app Android), validação de capacidade e compatibilidade da localização.
- **Executor:** @dev
- **Quality Gate:** @architect
- **Foco:** Performance do motor de regras, capacidade de customização por tenant

---

#### Story 3.3 — Controle de Qualidade e Quarentena

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [code_review, test_coverage, workflow_validation, quarantine_flow_review]
```

- **Descrição:** Fluxo de QC no recebimento: itens suspeitos vão para zona de quarentena, workflow de aprovação/rejeição pelo supervisor, integração com laudos de qualidade, liberação para estoque ou devolução ao fornecedor (evento Kafka para SCM), rastreabilidade do fluxo de quarentena.
- **Executor:** @dev
- **Quality Gate:** @architect

---

### Wave 4 — Gestão de Estoque & Rastreabilidade

> Depende de: Wave 3 completa (itens precisam estar em estoque).

---

#### Story 4.1 — Controle de Estoque em Tempo Real

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [consistency_test, concurrency_review, redis_cache_validation]
```

- **Descrição:** Saldo de estoque por localização em tempo real (Redis cache + PostgreSQL). Estoque disponível, reservado, em quarentena, avariado. Bloqueio otimista para movimentações concorrentes. Dashboard de ocupação por zona/armazém. APIs para consulta de saldo por produto/lote/série/localização.
- **Executor:** @dev
- **Quality Gate:** @architect
- **Foco:** Consistência eventual, concorrência, cache invalidation

---

#### Story 4.2 — Rastreabilidade: Lote, Série, Validade & GS1

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [traceability_test, gs1_standard_validation, elasticsearch_index_test]
```

- **Descrição:** Rastreabilidade completa de movimentações por lote, número de série e data de validade. Parsing e geração de códigos GS1 (EAN-13, GS1-128, SSCC, QR Code). Histórico completo de cada item desde o recebimento até a expedição. Indexação no Elasticsearch para consultas rápidas de rastreabilidade. Relatório de árvore de rastreabilidade.
- **Executor:** @dev
- **Quality Gate:** @architect

---

#### Story 4.3 — Inventário Físico (Contagem de Estoque)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [workflow_validation, reconciliation_logic_review, audit_test]
```

- **Descrição:** Processo de inventário: cíclico (por zona/corredor) e geral. Geração de listas de contagem, conferência via app Android (scanner), dupla contagem opcional, reconciliação automática de divergências, ajuste de estoque com motivo, aprovação de supervisor para ajustes acima de threshold, publicação de evento Kafka de ajuste para MRP/SCM.
- **Executor:** @dev
- **Quality Gate:** @architect

---

#### Story 4.4 — Auditoria Imutável & Log de Movimentações

```yaml
executor: "@data-engineer"
quality_gate: "@dev"
quality_gate_tools: [immutability_test, audit_completeness_review, compliance_validation]
```

- **Descrição:** Log imutável (append-only) de toda movimentação de estoque: recebimento, putaway, picking, packing, shipping, transferência, ajuste, inventário. Campos: timestamp, usuário, tenant, origem, destino, quantidade, lote, série, motivo, evento Kafka correlacionado. Indexação no Elasticsearch. Exportação para auditoria regulatória.
- **Executor:** @data-engineer
- **Quality Gate:** @dev
- **Foco:** Imutabilidade, compliance LGPD, retenção configurável por tenant

---

#### Story 4.5 — Transferências Internas entre Posições

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [code_review, concurrency_test, audit_trail_validation]
```

- **Descrição:** Movimentação interna: transferência entre posições, zonas e armazéns. Transferências manuais (operador) e automáticas (reabastecimento de zona de picking). Confirmação via scanner no app Android. Registro no audit log. Publicação de evento Kafka de movimentação.
- **Executor:** @dev
- **Quality Gate:** @architect

---

### Wave 5 — Operações Outbound (Picking, Packing & Shipping)

> Depende de: Wave 4 completa.

---

#### Story 5.1 — Picking (Separação de Pedidos — Integração CRM)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [kafka_integration_test, picking_algorithm_review, performance_test]
```

- **Descrição:** Consumir evento Kafka de CRM (Pedido de Venda confirmado). Gerar ordem de picking otimizada (algoritmo de rota: S-shape, Z-shape, ou largest gap). Respeitar FIFO/FEFO automaticamente. Atribuição de ordens por operador/zona. Guia de picking no app Android (localização, quantidade, confirmação por scanner). Picking parcial e substituição de localização.
- **Executor:** @dev
- **Quality Gate:** @architect
- **Foco:** Eficiência do algoritmo de rota, suporte a wave picking e batch picking

---

#### Story 5.2 — Packing (Embalagem)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [code_review, barcode_generation_test, packing_rules_validation]
```

- **Descrição:** Estação de packing: verificação do conteúdo da ordem de picking, seleção de embalagem, pesagem, geração de etiqueta de expedição (GS1-128 / SSCC), geração de romaneio de embarque, confirmação de fechamento da caixa via scanner. Suporte a kitting (montagem de kits). Integração com balança eletrônica via API.
- **Executor:** @dev
- **Quality Gate:** @architect

---

#### Story 5.3 — Shipping (Expedição & Integração Transportadora)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [api_contract_review, integration_test, shipping_rules_validation]
```

- **Descrição:** Processo de expedição: confirmação de carregamento no caminhão, geração de manifesto de carga, integração com APIs de transportadoras (via módulo dedicado Spring Boot), rastreamento de entrega, confirmação de entrega (evento Kafka para CRM e SCM), tratamento de devoluções (logística reversa inicia novo recebimento no WMS).
- **Executor:** @dev
- **Quality Gate:** @architect

---

### Wave 6 — Integração MRP/MRP II

> Depende de: Waves 1-5 completas.

---

#### Story 6.1 — Integração WMS ↔ MRP/MRP II

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [kafka_integration_test, consistency_validation, saga_pattern_review]
```

- **Descrição:** Bidirecional: WMS consome eventos MRP (Ordem de Produção liberada → reserva materiais no WMS, gera ordem de picking de matéria-prima para linha de produção). WMS publica eventos para MRP (materiais entregues na produção, produtos acabados recebidos no WMS). Saga pattern para consistência distribuída. Tratamento de cancelamentos e alterações de OP.
- **Executor:** @dev
- **Quality Gate:** @architect
- **Foco:** Consistência eventual, idempotência, Dead Letter Queue para falhas

---

### Wave 7 — Compliance & Relatórios Regulatórios

> Depende de: Wave 4 completa (dados de rastreabilidade disponíveis).

---

#### Story 7.1 — Relatórios Regulatórios (ANVISA, Receita Federal)

```yaml
executor: "@dev"
quality_gate: "@pm"
quality_gate_tools: [regulatory_compliance_review, report_accuracy_test, export_format_validation]
```

- **Descrição:** Geração de relatórios regulatórios: Ficha de Estoque (Receita Federal), Controle de Produtos Sujeitos à Vigilância Sanitária (ANVISA — para clientes do setor), rastreabilidade por lote para recalls, relatório de movimentações por período. Exportação em PDF, Excel e XML. Agendamento automático de relatórios.
- **Executor:** @dev
- **Quality Gate:** @pm (valida conformidade com requisitos regulatórios)

---

### Wave 8 — App Android (Operações Mobile)

> Depende de: Waves 3, 4 e 5 (APIs prontas para consumo).

---

#### Story 8.1 — App Android: Fundação (Kotlin + Jetpack Compose)

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [architecture_review, security_review, api_integration_test]
```

- **Descrição:** Setup do projeto Android: Kotlin + Jetpack Compose, arquitetura MVVM + Clean Architecture, integração com APIs WMS (Retrofit + OkHttp), autenticação Keycloak (OAuth2 PKCE flow), suporte offline com sincronização, leitor de câmera para QR/barcode (ML Kit), navegação Compose Navigation. Telas: Login, Dashboard Operador, Menu de Operações.
- **Executor:** @dev
- **Quality Gate:** @architect

---

#### Story 8.2 — App Android: Recebimento & Conferência

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [mobile_ux_review, scanner_integration_test, offline_sync_test]
```

- **Descrição:** Fluxo de recebimento no app: lista de pedidos de compra esperados, leitura de código de barras/QR (GS1), confirmação de itens recebidos, registro de divergências, fotos de avaria, assinatura digital do conferente. Suporte offline com sincronização quando reconectar.
- **Executor:** @dev
- **Quality Gate:** @architect

---

#### Story 8.3 — App Android: Picking Guiado & Expedição

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [mobile_ux_review, picking_flow_test, barcode_validation_test]
```

- **Descrição:** Picking guiado no app: receber tarefa de picking, navegação por corredor (indicação de rota), confirmação de posição e quantidade por scanner, alertas de FEFO/FIFO, confirmação de separação completa. Fluxo de expedição: confirmar carregamento no caminhão, scan de volumes, confirmação de manifesto.
- **Executor:** @dev
- **Quality Gate:** @architect

---

#### Story 8.4 — App Android: Inventário & Transferências

```yaml
executor: "@dev"
quality_gate: "@architect"
quality_gate_tools: [mobile_ux_review, inventory_accuracy_test, sync_conflict_resolution]
```

- **Descrição:** Inventário via app: lista de contagem por corredor/posição, scan de produto e confirmação de quantidade, alertas de divergência vs. sistema, dupla contagem. Transferências internas: scan de origem, scan de destino, confirmação. Todas as operações com suporte offline.
- **Executor:** @dev
- **Quality Gate:** @architect

---

## Out of Scope (Este Epic)

> Itens explicitamente **excluídos** deste epic — serão endereçados em epics futuros.

- **Agentes de IA/ML** → Epic futuro (sugestão de putaway por IA, previsão de demanda)
- **App iOS** → Fora do roadmap atual (apenas Android)
- **Módulos MRP, SCM, CRM** → Apenas as integrações WMS↔módulo (não o módulo em si)
- **Portal do Cliente** → Interface pública de rastreamento de pedidos
- **Integração com ERPs externos** → SAP, TOTVS, etc. (epic de integrações futuro)
- **WMS Multi-armazém avançado** → Balanceamento automático entre armazéns
- **Robótica/Automação** → Integração com AGVs, esteiras automatizadas
- **API Gateway (Kong/Spring Cloud Gateway)** → Configuração centralizada em epic de Infra Base
- **Kafka Schema Registry Setup** → Epic de Infra Base / DevOps

---

## Business Value

**Benefícios Operacionais Esperados:**
- Eliminação de planilhas manuais de controle de estoque → redução de erros humanos
- Rastreabilidade em tempo real → resposta a recalls em horas, não dias
- Picking otimizado por rota → aumento de produtividade do operador estimado em 20-35%
- App Android para operadores → elimina papeladas, confirmações em tempo real

**KPIs de Sucesso:**
- Acuracidade de inventário ≥ 99,5% (vs. média de mercado ~97%)
- Tempo médio de recebimento por nota reduzido em ≥ 30%
- Zero divergência não rastreável (100% dos movimentos auditáveis)
- Uptime do serviço WMS ≥ 99,9% (SLA production)

---

## Compatibility Requirements

- [ ] APIs REST seguem OpenAPI 3.0 specification
- [ ] Comunicação gRPC com contrato `.proto` versionado
- [ ] Eventos Kafka com schema Avro registrado no Schema Registry
- [ ] Banco de dados com migrations Flyway (versionadas e reversíveis)
- [ ] Multi-tenant via `tenant_id` em todas as tabelas (Row-Level Security no PostgreSQL)
- [ ] LGPD: dados pessoais identificados e com política de retenção

---

## Risk Mitigation

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| Complexidade do motor de putaway | Média | Alto | Iniciar com regras simples, adicionar complexidade incrementalmente |
| Concorrência em movimentações de estoque | Alta | Alto | Bloqueio otimista + Redis distributed lock |
| Integração Kafka entre módulos | Média | Alto | Saga pattern + Dead Letter Queue + idempotência |
| Performance do app Android offline | Média | Médio | Cache local (Room DB) + sync seletivo |
| Compliance regulatório variável por tenant | Baixa | Alto | Configuração por tenant, não hardcoded |

**Rollback Plan:** Cada wave é deployável independentemente via ArgoCD. Em caso de falha, rollback para versão anterior com `argocd app rollback wms-service`. Migrations Flyway suportam rollback.

---

## Quality Assurance Strategy

- **CodeRabbit:** Ativo em todos os PRs (CRITICAL/HIGH auto-fix, MEDIUM como debt)
- **Cobertura de Testes:** ≥ 80% para lógica de negócio crítica (picking, putaway, inventory)
- **Integration Tests:** Testcontainers para PostgreSQL, Redis, Kafka em CI
- **Load Testing:** k6 para APIs de picking e inventory antes de cada wave deploy
- **Security:** OWASP ZAP scan em cada PR de API
- **Mobile:** Espresso + Robolectric para Android UI tests

---

## Definition of Done (Epic)

- [ ] Todas as stories completas com acceptance criteria met
- [ ] Cobertura de testes ≥ 80% no backend Java
- [ ] App Android funcionando em device real com scanner
- [ ] Pipeline CI/CD passando em todas as waves
- [ ] Deploy no Kubernetes (staging) validado
- [ ] Observabilidade: dashboards Grafana + alerts configurados
- [ ] Documentação de API (OpenAPI + gRPC .proto) publicada
- [ ] Audit log imutável validado
- [ ] Multi-tenant isolamento validado (teste cross-tenant)
- [ ] Integrações MRP, SCM, CRM e Keycloak testadas end-to-end
- [ ] Relatórios regulatórios validados por revisor de negócio

---

## Story Summary

| Wave | Story | Executor | Quality Gate | Complexidade |
|------|-------|----------|--------------|-------------|
| Wave 1 | 1.1 WMS Service Bootstrap | @devops | @architect | M | ✅ Done |
| Wave 1 | 1.2 Schema & Domain Model | @data-engineer | @dev | L | ✅ Done |
| Wave 1 | 1.3 Autenticação & RBAC Keycloak | @dev | @architect | M | ✅ Done |
| Wave 1 | 1.4 Observabilidade (Prometheus+ELK+Jaeger+Istio) | @devops | @architect | M | ✅ Done |
| Wave 2 | 2.1 Cadastro Armazéns & Estrutura Física | @dev | @architect | M | ✅ Done |
| Wave 2 | 2.2 Cadastro Produtos WMS (SKU+GS1) | @dev | @architect | S | ⬜ Próxima |
| Wave 3 | 3.1 Recebimento de Mercadorias (Inbound+SCM) | @dev | @architect | L |
| Wave 3 | 3.2 Putaway (Motor de Regras) | @dev | @architect | L |
| Wave 3 | 3.3 Controle de Qualidade & Quarentena | @dev | @architect | M |
| Wave 4 | 4.1 Controle de Estoque em Tempo Real | @dev | @architect | L |
| Wave 4 | 4.2 Rastreabilidade Lote/Série/Validade/GS1 | @dev | @architect | L |
| Wave 4 | 4.3 Inventário Físico | @dev | @architect | M |
| Wave 4 | 4.4 Auditoria Imutável | @data-engineer | @dev | M |
| Wave 4 | 4.5 Transferências Internas | @dev | @architect | S |
| Wave 5 | 5.1 Picking (Outbound+CRM) | @dev | @architect | L |
| Wave 5 | 5.2 Packing (Embalagem) | @dev | @architect | M |
| Wave 5 | 5.3 Shipping (Expedição+Transportadora) | @dev | @architect | M |
| Wave 6 | 6.1 Integração WMS↔MRP/MRP II | @dev | @architect | XL |
| Wave 7 | 7.1 Relatórios Regulatórios | @dev | @pm | M |
| Wave 8 | 8.1 Android: Fundação | @dev | @architect | L |
| Wave 8 | 8.2 Android: Recebimento & Conferência | @dev | @architect | M |
| Wave 8 | 8.3 Android: Picking Guiado & Expedição | @dev | @architect | M |
| Wave 8 | 8.4 Android: Inventário & Transferências | @dev | @architect | M |

**Total: 23 stories** · **8 waves**

---

## Handoff ao Story Manager (@sm)

> "Desenvolva stories detalhadas para o Epic EPIC-WMS-001. Considerações-chave:
>
> - **Stack:** Java 21 + Spring Boot 3 (backend), React 19 + Next.js (frontend web), Kotlin + Jetpack Compose (Android)
> - **Padrões existentes:** Microserviços com database-per-service, eventos Kafka, autenticação Keycloak
> - **Integrações:** MRP/MRP II (Kafka), SCM (Kafka), CRM (Kafka), Keycloak (OAuth2)
> - **Requisitos críticos:** Multi-tenant (tenant_id em todas as tabelas), FIFO/FEFO, GS1, auditoria imutável
> - **Executor assignments:** Conforme tabela de stories acima (executor ≠ quality_gate sempre)
> - **Cada story deve verificar:** integridade do estoque, isolamento multi-tenant, rastreabilidade do audit log
>
> O epic deve manter a integridade do sistema Odin enquanto entrega um WMS production-ready."

---

## Metadata

```yaml
epicId: EPIC-WMS-001
epicNum: 1
module: WMS
project: Odin ERP
status: Ready
validatedBy: "@po (Pax)"
validatedAt: "2026-02-21"
validationScore: "8.3/10"
version: 1.0.0
createdBy: "@pm (Morgan)"
createdAt: "2026-02-21"
totalStories: 23
totalWaves: 8
complexity: XL
estimatedSprints: 12-16
```
