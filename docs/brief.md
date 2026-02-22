# Project Brief: ODIN CRM

_Criado por: Morgan (PM Agent) + AIOS_
_Data: 2026-02-22_

---

## Executive Summary

**ODIN CRM** é o módulo de gestão de relacionamento com clientes do **ODIN ERP** — plataforma integrada de gestão empresarial voltada para indústrias de médio e grande porte no mercado B2B. É o segundo módulo do ecossistema, desenvolvido sobre a fundação do WMS (Warehouse Management System) já existente, com o qual opera em tempo real como um único sistema unificado.

O ODIN CRM resolve a fragmentação da gestão comercial em indústrias que hoje utilizam CRMs genéricos desconectados de sua operação de estoque, logística e produção. Ao integrar pipeline de vendas, gestão de contas e oportunidades diretamente ao WMS em tempo real, o ODIN CRM elimina a lacuna entre o time comercial e a operação — permitindo que vendedores consultem estoque disponível, criem pedidos e acompanhem entregas dentro do mesmo sistema que gerencia armazém, supply chain, MRP e finanças.

O MVP entrega o CRM completo com todas as suas funcionalidades core, integrado em tempo real ao WMS, com arquitetura preparada para a incorporação futura dos módulos SCM, MRP, MRPII e Finance.

---

## Problem Statement

**Estado atual:** As indústrias que serão atendidas pelo ODIN ERP gerenciam seu relacionamento comercial de forma predominantemente manual — o "caderno do vendedor" é o principal registro de clientes, negociações e pedidos. Não existe integração entre a operação comercial e o ERP legado em uso, cujos dados permanecem isolados e de difícil acesso para o time de vendas.

**Impacto do problema:**
- Vendedores não têm visibilidade de estoque disponível durante a negociação, resultando em promessas não cumpridas e perda de vendas
- Pedidos são duplicados ou perdidos por ausência de registro centralizado e rastreável
- Clientes não conseguem obter status de pedidos ou entregas em tempo hábil, gerando insatisfação e retrabalho para o time interno
- A ausência de histórico estruturado impede análise de carteira, identificação de oportunidades e previsão de demanda
- O ERP legado atual não resolve esses problemas — será substituído pelo ODIN ERP

**Por que agora:** A implantação do WMS ODIN já estabeleceu a fundação tecnológica e operacional. O CRM é o próximo passo natural para fechar o ciclo entre operação e comercial, aproveitando a infraestrutura existente e os dados de estoque já gerenciados pelo WMS.

**Por que soluções existentes falham:** CRMs genéricos (Pipedrive, HubSpot) não se integram ao WMS/ERP industrial; o ERP legado não possui módulo CRM funcional; soluções como SAP ou TOTVS são proibitivas em custo e complexidade para o perfil de indústria alvo.

---

## Proposed Solution

**Conceito central:** O ODIN CRM é um módulo CRM completo, nativo ao ecossistema ODIN ERP, que unifica a gestão comercial com a operação de armazém em tempo real. Ao contrário de CRMs genéricos que exigem integrações frágeis e custosas, o ODIN CRM compartilha a mesma infraestrutura do WMS — tornando estoque, pedidos e clientes faces diferentes do mesmo sistema.

**Como funciona:**
- Vendedor acessa pipeline de oportunidades e consulta estoque disponível em tempo real (WMS via gRPC) sem sair do CRM
- Pedido fechado no CRM dispara automaticamente o fluxo de separação e expedição no WMS via Kafka
- Histórico completo do cliente consolida interações comerciais, pedidos, entregas e pendências em uma única visão
- Gestão de contas e contatos B2B com suporte a múltiplos contatos por empresa e hierarquia de relacionamento
- Migração assistida dos dados do ERP legado para garantir continuidade operacional

**Por que vai funcionar:**
- Integração nativa elimina o problema fundamental dos CRMs genéricos: o descolamento da operação
- WMS já entregue e em produção fornece base de dados confiável e APIs estáveis
- Substituição do ERP legado cria oportunidade de migração estruturada, sem legado a manter
- Usuário atual (caderno do vendedor) tem baixa resistência técnica — qualquer interface digital representa ganho imediato

**Diferencial estratégico:** Nenhum CRM de médio porte no mercado brasileiro oferece integração real-time nativa com WMS industrial sem custo de implementação SAP/TOTVS. O ODIN CRM preenche esse gap para indústrias de médio porte.

---

## Target Users

### Segmento Primário: Vendedor Interno / Representante Comercial

**Perfil:** Profissional responsável pela gestão da carteira de clientes industriais, prospecção de novos negócios e fechamento de pedidos.

**Comportamento atual:**
- Registra clientes, negociações e pedidos em caderno, planilha ou memória
- Liga para o almoxarifado ou acessa o ERP legado para verificar estoque — processo lento e impreciso
- Perde tempo repassando status de pedido para clientes por telefone/WhatsApp

**Necessidades:**
- Saber em tempo real se o produto está disponível durante a negociação
- Registrar e acompanhar oportunidades sem depender de papel
- Criar pedidos rapidamente sem retrabalho de digitação em múltiplos sistemas

**Objetivo:** Fechar mais vendas com menos esforço operacional e zero perda de informação

---

### Segmento Secundário: Gerente Comercial / Diretor de Vendas

**Perfil:** Responsável pela equipe de vendas, metas, pipeline e relacionamento com grandes contas.

**Necessidades:**
- Visibilidade do pipeline da equipe sem depender de relatórios manuais
- Métricas de conversão, carteira ativa e forecast de vendas
- Identificar gargalos no ciclo de venda e contas em risco

**Objetivo:** Gestão estratégica da operação comercial com dados confiáveis em tempo real

---

### Segmento Terciário: Atendimento ao Cliente (SAC)

**Perfil:** Responsável por receber e resolver demandas de clientes pós-venda — status de pedidos, reclamações, devoluções, prazo de entrega.

**Necessidades:**
- Acessar histórico completo do cliente (pedidos, interações, pendências) em uma única tela
- Consultar status de pedidos em tempo real via integração com WMS
- Registrar ocorrências e interações para rastreabilidade

**Objetivo:** Resolver demandas do cliente com agilidade, sem precisar consultar vendedor ou almoxarifado separadamente

---

### Segmento Quaternário: Comprador (Procurement — lado do cliente)

**Perfil:** Profissional do lado do cliente que emite pedidos de compra e acompanha fornecedores.

**Necessidades:**
- Ser cadastrado como contato vinculado à conta da empresa cliente
- Ter seu histórico de pedidos e interações acessível pelo time comercial
- Receber propostas e orçamentos de forma organizada

**Objetivo:** Relacionamento fluido com o fornecedor sem retrabalho de comunicação

---

### Segmento Quinário: Analista Financeiro / Controladoria

**Perfil:** Acessa o CRM pontualmente para consultar histórico de relacionamento comercial de um cliente para subsidiar decisões de limite de crédito ou cobrança.

**Necessidades:**
- Consulta (somente leitura) ao histórico de pedidos e faturamento por cliente
- Visibilidade de clientes com pedidos em aberto ou inadimplentes
- Integração futura com módulo Finance do ODIN ERP

**Objetivo:** Subsidiar decisões de crédito e cobrança com dados comerciais consolidados

---

## Goals & Success Metrics

### Business Objectives

- **Digitalizar 100% do processo comercial** — eliminar o caderno do vendedor como sistema de registro, com todas as interações, oportunidades e pedidos registrados no ODIN CRM dentro dos primeiros 60 dias de uso
- **Migrar dados do ERP legado** com integridade total — zero perda de histórico de clientes e pedidos durante a transição para o ODIN ERP
- **Reduzir tempo médio de criação de pedido em 70%** — do fechamento da venda até a entrada do pedido no WMS, eliminando a re-digitação manual entre sistemas
- **Garantir visibilidade de pipeline** para gestão comercial — gerente acessa forecast e status da equipe em tempo real, sem depender de relatórios manuais

### User Success Metrics

- **Vendedor:** consegue verificar disponibilidade de estoque e criar pedido sem sair do CRM — em menos de 2 minutos
- **Gerente Comercial:** acessa pipeline completo da equipe, taxa de conversão e forecast em um único dashboard — sem planilhas auxiliares
- **SAC:** resolve consulta de status de pedido em menos de 1 minuto, com acesso ao histórico completo do cliente na mesma tela
- **Financeiro:** consulta histórico de compras e pedidos em aberto de um cliente em menos de 30 segundos

### Key Performance Indicators (KPIs)

- **Taxa de adoção:** % de vendedores registrando oportunidades no CRM vs. canais informais — meta: 100% em 30 dias
- **Tempo de criação de pedido:** tempo médio do fechamento da venda até pedido no WMS — meta: < 5 minutos
- **Taxa de conversão do pipeline:** % de oportunidades convertidas em pedidos — baseline a estabelecer no primeiro mês
- **Tempo de resposta do SAC:** tempo médio para responder consulta de status — meta: < 1 minuto com CRM
- **Integridade da migração:** % de registros migrados do ERP legado sem erro — meta: 100%
- **Disponibilidade do sistema:** uptime do CRM integrado ao WMS — meta: 99,5%

---

## MVP Scope

### Core Features (Must Have)

- **Gestão de Contas:** cadastro completo de empresas clientes com CNPJ, endereços, segmento industrial, limite de crédito, status ativo/inativo
- **Gestão de Contatos:** múltiplos contatos por conta (comprador, financeiro, diretoria), com cargo, telefone, e-mail e histórico de interações
- **Pipeline de Oportunidades:** funil de vendas configurável com estágios, valor estimado, probabilidade de fechamento e data prevista
- **Atividades e Interações:** registro de ligações, reuniões, e-mails e anotações vinculados à conta ou oportunidade
- **Gestão de Pedidos:** criação de pedido no CRM com disparo automático para o WMS via Kafka em tempo real; acompanhamento de status de separação e expedição
- **Consulta de Estoque em Tempo Real:** vendedor verifica disponibilidade de produtos do WMS via gRPC durante negociação, sem sair do CRM
- **Histórico 360° do Cliente:** visão unificada de oportunidades, pedidos, interações, pendências e status financeiro por conta
- **Dashboard Comercial:** visão do pipeline por vendedor, taxa de conversão, forecast mensal, ranking de contas por volume
- **Gestão de Usuários e Perfis:** controle de acesso por perfil (vendedor, gerente, SAC, financeiro, comprador) com permissões distintas via Keycloak RBAC
- **Migração de Dados do ERP Legado:** importação estruturada de contas, contatos e histórico de pedidos do sistema atual

### Out of Scope para MVP

- Integração com WhatsApp Business API
- App mobile Android (campo) — Phase 2
- Integração com NF-e / faturamento automático
- Portal do cliente (self-service)
- Integração com módulos SCM, MRP, MRPII, Finance *(futuras integrações do ODIN ERP)*
- IA/ML para previsão de churn ou demanda
- E-mail marketing e automações de marketing
- GPS/GIS para geolocalização de clientes

### MVP Success Criteria

1. 100% dos vendedores registram oportunidades e pedidos no ODIN CRM
2. Pedidos criados no CRM chegam ao WMS em menos de 5 segundos via Kafka
3. SAC resolve consultas de status sem acionar time de vendas ou almoxarifado
4. Dados do ERP legado migrados com 100% de integridade
5. Sistema disponível com 99,5% de uptime

---

## Post-MVP Vision

### Phase 2 Features

- **App Mobile Android para Vendedor Externo:** acesso ao pipeline, consulta de estoque e criação de pedidos em campo via Kotlin + Jetpack Compose
- **Integração com WhatsApp Business API:** registro automático de interações no histórico do cliente; envio de orçamentos e notificações de pedido
- **Integração com NF-e e Faturamento:** pedido fechado no CRM → separação no WMS → emissão automática de NF-e → baixa financeira
- **Automações de Pipeline:** alertas para oportunidades paradas, lembretes de follow-up e gatilhos de ação por estágio do funil

### Long-term Vision (1-2 anos)

- **Integração completa com módulo Finance (ODIN ERP):** limite de crédito dinâmico, histórico de inadimplência e bloqueio automático de pedidos
- **Integração com SCM:** visibilidade da cadeia de suprimentos na negociação — prazo de reposição em tempo real
- **Integração com MRP/MRPII:** pipeline de oportunidades do CRM alimenta o planejamento de produção
- **Portal do Cliente:** acompanhamento de pedidos, histórico de compras e documentos self-service

### Expansion Opportunities

- **ODIN CRM SaaS Multi-tenant:** produto standalone como porta de entrada para o ecossistema ODIN ERP
- **IA para Forecast de Vendas:** modelo preditivo combinando histórico CRM + dados WMS + sazonalidade
- **Previsão de Churn:** alertas para contas com queda de volume de compras
- **Marketplace de Integrações:** conectores com ERPs legados (TOTVS, SAP B1, Sankhya)

---

## Technical Considerations

### Platform Requirements

- **Target Platforms:** Web Responsive (desktop prioritário) + App Android para campo (Phase 2)
- **Browser Support:** Chrome, Edge, Firefox — últimas 2 versões
- **Performance:** APIs REST/gRPC < 200ms p95; eventos Kafka CRM → WMS < 500ms; consultas Elasticsearch < 1s

### Technology Preferences

| Camada | Tecnologia |
|---|---|
| **Frontend Web** | React 19 + TypeScript + Next.js (App Router) + Tailwind CSS + shadcn/ui |
| **Backend (CRM)** | Java 21 + Spring Boot 3 + Spring Cloud |
| **App Android** | Kotlin + Jetpack Compose (Phase 2) |
| **Database** | PostgreSQL 17 — database-per-service |
| **Cache** | Redis |
| **Busca/Indexação** | Elasticsearch + ELK Stack |
| **Mensageria** | Apache Kafka |
| **API Gateway** | Kong ou Spring Cloud Gateway |
| **APIs** | REST (CRUD) + GraphQL (painel cliente) + gRPC (inter-serviços) |
| **Auth** | Keycloak (OAuth2 / OIDC / RBAC / multi-tenant) |
| **Containerização** | Docker + Kubernetes (EKS/GKE) |
| **CI/CD** | GitHub Actions + ArgoCD |
| **Observabilidade** | Prometheus + Grafana + ELK + Jaeger + Istio |
| **IA (futuro)** | Python + FastAPI + LangChain/LangGraph |
| **Armazenamento** | MinIO (S3-compatible) ou AWS S3 |
| **Infra/Cloud** | **AWS** ✅ — EKS (Kubernetes), MSK (Kafka), S3 (storage), ECR (registry) |

### Architecture Considerations

- **Arquitetura:** Microserviços — cada módulo ODIN ERP é um serviço independente deployável
- **Repository:** **Polyrepo** ✅ — um repositório por módulo; contratos Protobuf em repositório dedicado `odin-proto`
- **Integração CRM ↔ WMS:**
  - **Síncrona:** gRPC para consultas de estoque em tempo real durante negociação
  - **Assíncrona:** Kafka para pedido criado no CRM → dispara fluxo WMS
- **Auth/SSO:** Keycloak compartilhado — SSO entre todos os módulos com RBAC por perfil
- **Service Mesh:** Istio para mTLS, observabilidade e traffic management
- **Security/Compliance:** LGPD para PII de clientes; RBAC granular por perfil
- **Multi-tenancy:** Keycloak multi-tenant para modelo SaaS futuro

---

## Constraints & Assumptions

### Constraints

- **Budget:** Não definido — qualidade como prioridade sobre custo/prazo
- **Timeline:** Sem prazo fixo — entrega orientada por qualidade e completude
- **Resources:** 1 desenvolvedor + assistência integral de IA (AIOS/Claude) — AI-Assisted Development
- **Expertise:** Stack enterprise sem experiência prévia — toda implementação guiada pelo AIOS
- **Technical:**
  - Stack enterprise complexa requer aprendizado progressivo — mitigado por suporte contínuo de IA
  - Solo developer = sem revisão de par humano; qualidade gates via `@qa` e CodeRabbit são críticos
  - Infraestrutura WMS (Kafka, K8s, Keycloak) já em produção — reduz risco de setup, mas exige cuidado na integração

### Key Assumptions

- A infraestrutura Kafka, Kubernetes e Keycloak do WMS está estável e disponível para o CRM reutilizar
- O WMS expõe contratos de API (gRPC/REST) documentados e estáveis para integração
- O ritmo de desenvolvimento será determinado pelo aprendizado e pela qualidade — sem pressão de prazo
- O AIOS (agentes @dev, @architect, @qa, @data-engineer) fornecerá assistência técnica completa em cada story
- Os dados do ERP legado são exportáveis para migração estruturada
- LGPD compliance é requisito não negociável

---

## Risks & Open Questions

### Key Risks

| Severidade | Risco | Mitigação |
|---|---|---|
| **CRÍTICO** | Solo developer + stack enterprise complexa sem experiência prévia | Desenvolvimento guiado story a story pelo AIOS; qualidade sobre velocidade |
| **ALTO** | Integração real-time CRM ↔ WMS via gRPC — mudança no WMS quebra CRM | Versionar APIs com Protobuf; testes de contrato automatizados |
| **ALTO** | Falha do Kafka durante criação de pedido crítico | Outbox Pattern no CRM; circuit breaker via Istio |
| **ALTO** | Migração do ERP legado com perda/corrupção de dados | Pipeline de migração com validação, dry-run, rollback plan |
| **MÉDIO** | Curva de aprendizado Java/Spring Boot atrasa implementação | AIOS fornece exemplos completos e padrões reutilizáveis por story |
| **MÉDIO** | Complexidade operacional K8s + Istio + Kafka | Usar serviços gerenciados (EKS/GKE, MSK/Confluent Cloud) |
| **BAIXO** | Resistência à adoção pelos vendedores (vínculo com caderno) | UX simples, treinamento in-loco, ganho imediato visível |

### Open Questions

- Qual sistema ERP legado está sendo substituído? *(impacta estratégia de migração)*
- O contrato de API do WMS (Protobuf/gRPC + Kafka topics) está documentado?
- ~~Decisão final sobre cloud provider~~ → **AWS (EKS + MSK + S3 + ECR)** ✅
- ~~Estratégia de repositório~~ → **Polyrepo** + `odin-proto` para contratos gRPC ✅
- O CRM terá ambiente de homologação separado do WMS em produção?
- Quais dados do ERP legado precisam ser migrados?

### Areas Needing Further Research

- Padrões Java 21 + Spring Boot 3 para arquitetura CRM (DDD aplicado)
- Outbox Pattern com Spring Boot + Kafka para garantia de entrega
- Protobuf/gRPC contract design para integração CRM ↔ WMS
- LGPD compliance em Spring Boot — criptografia de PII, direito ao esquecimento
- Keycloak RBAC configuration para os 5 perfis do ODIN CRM
- Estratégias de ETL para migração do ERP legado

---

## Next Steps

1. Responder as open questions críticas (cloud provider, polyrepo vs monorepo, ERP legado)
2. Iniciar `*create-prd` usando este brief como fundação
3. Engajar `@architect` para criar documento de arquitetura baseado no PRD
4. Criar preset `odin-erp-fullstack` no AIOS documentando a stack completa

---

**PM Handoff:**

Este Project Brief fornece o contexto completo do **ODIN CRM**. Próximo passo: iniciar modo de criação do PRD, revisando este brief para desenvolver cada seção com clareza e precisão, garantindo rastreabilidade completa dos requisitos ao brief.

---

_— Morgan, planejando o futuro 📊_
