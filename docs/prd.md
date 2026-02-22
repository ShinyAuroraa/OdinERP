# ODIN CRM — Product Requirements Document (PRD)

_Criado por: Morgan (PM Agent) + AIOS_
_Data: 2026-02-22 | Versão: 1.0_

---

## Goals and Background Context

### Goals

- Entregar um CRM completo e nativo ao ecossistema ODIN ERP, integrado em tempo real ao WMS já existente
- Digitalizar 100% do processo comercial de indústrias B2B, eliminando o "caderno do vendedor" como sistema de registro
- Permitir que vendedores consultem estoque disponível e criem pedidos sem sair do CRM, com propagação automática ao WMS
- Fornecer visibilidade total do pipeline comercial para gestores, sem dependência de relatórios manuais
- Migrar dados do ERP legado com 100% de integridade, garantindo continuidade operacional no go-live
- Estabelecer a fundação arquitetural para integração futura com os módulos SCM, MRP, MRPII e Finance do ODIN ERP

### Background Context

Indústrias de médio e grande porte no mercado B2B brasileiro gerenciam seu relacionamento comercial de forma predominantemente manual. O "caderno do vendedor" — combinado com um ERP legado desconectado da operação comercial — é a realidade atual. O resultado é previsível: promessas de entrega sem base em estoque real, pedidos perdidos ou duplicados, incapacidade do SAC de responder ao cliente sem acionar almoxarifado ou time de vendas, e ausência de dados confiáveis para tomada de decisão comercial.

O ODIN CRM nasce como segundo módulo do ODIN ERP — plataforma integrada construída para substituir esse cenário de fragmentação. Com o WMS já em produção gerenciando armazém e estoque, o CRM fecha o ciclo: integração real-time via gRPC para consulta de estoque e Kafka para criação automática de pedidos no WMS. O diferencial não é ser mais um CRM — é ser o único CRM nativo a um ecossistema ERP industrial completo, acessível a indústrias de médio porte sem o custo e a complexidade de SAP ou TOTVS.

### Change Log

| Date | Version | Description | Author |
|---|---|---|---|
| 2026-02-22 | 1.0 | Versão inicial — baseada no Project Brief (docs/brief.md) | Morgan (PM Agent) |

---

## Requirements

### Functional Requirements

- **FR1:** O sistema deve permitir cadastro, edição e inativação de contas (empresas clientes) com os campos: razão social, CNPJ, endereço completo, segmento industrial, limite de crédito e status (ativo/inativo)
- **FR2:** O sistema deve suportar múltiplos contatos por conta, com campos: nome, cargo, telefone, e-mail e vínculo com a conta pai
- **FR3:** O sistema deve oferecer pipeline de oportunidades com estágios configuráveis, valor estimado, probabilidade de fechamento, data prevista e vendedor responsável
- **FR4:** O sistema deve permitir registro de atividades e interações (ligações, reuniões, e-mails, anotações) vinculadas a uma conta ou oportunidade, com timestamp e usuário responsável
- **FR5:** O sistema deve consultar disponibilidade de estoque do WMS em tempo real via gRPC durante a criação ou edição de uma oportunidade, sem sair do CRM
- **FR6:** O sistema deve permitir criação de pedido de venda a partir de uma oportunidade fechada, com disparo automático ao WMS via Kafka em menos de 5 segundos
- **FR7:** O sistema deve exibir o status atualizado do pedido (separação, expedição, entregue) recebido do WMS via Kafka no histórico da oportunidade e da conta
- **FR8:** O sistema deve oferecer visão 360° do cliente: oportunidades, pedidos, interações, pendências e status financeiro consolidados em uma única tela de conta
- **FR9:** O sistema deve oferecer dashboard comercial com: pipeline por vendedor, taxa de conversão, forecast mensal e ranking de contas por volume de pedidos
- **FR10:** O sistema deve gerenciar usuários e perfis de acesso (vendedor, gerente comercial, SAC, financeiro, comprador) com permissões distintas via Keycloak RBAC
- **FR11:** O sistema deve fornecer ferramenta de importação de dados do ERP legado (contas, contatos, histórico de pedidos) com validação, relatório de erros e confirmação antes da aplicação
- **FR12:** O sistema deve permitir busca e filtragem de contas, contatos e oportunidades por múltiplos critérios via Elasticsearch

### Non-Functional Requirements

- **NFR1:** A API gRPC de consulta de estoque ao WMS deve responder em menos de 200ms no percentil 95
- **NFR2:** O evento Kafka de criação de pedido deve ser processado pelo WMS em menos de 5 segundos em condições normais de carga
- **NFR3:** O sistema deve implementar o Outbox Pattern para garantia de entrega de eventos Kafka — nenhum pedido deve ser perdido por falha temporária do broker
- **NFR4:** O sistema deve estar disponível com 99,5% de uptime mensal
- **NFR5:** Todos os dados de PII de clientes devem ser armazenados em conformidade com a LGPD — criptografia AES-256 em repouso e TLS em trânsito
- **NFR6:** O sistema deve suportar controle de acesso baseado em papéis (RBAC) via Keycloak, com isolamento completo de dados entre perfis
- **NFR7:** O frontend deve carregar a tela inicial em menos de 3 segundos em conexão de 10Mbps
- **NFR8:** O sistema deve implementar circuit breaker para chamadas ao WMS — falha do WMS não deve derrubar o CRM, apenas degradar funcionalidades dependentes
- **NFR9:** Toda a comunicação inter-serviços deve usar mTLS via Istio Service Mesh
- **NFR10:** O sistema deve registrar logs estruturados de todas as operações críticas no ELK Stack com retenção mínima de 90 dias

---

## User Interface Design Goals

### Overall UX Vision

Interface enterprise profissional, densa em informação mas sem sobrecarga cognitiva. Prioridade de UX: **velocidade de operação** — vendedor consulta conta, vê pipeline, cria pedido e fecha a tela em menos de 3 cliques.

### Key Interaction Paradigms

- **Dashboard-first:** tela inicial mostra pipeline do dia, tarefas pendentes e alertas
- **Sidebar navigation:** menu lateral fixo com módulos principais
- **Kanban de pipeline:** oportunidades arrastáveis entre estágios
- **Quick actions:** botões de ação rápida em cada card sem abrir nova tela
- **Modais contextuais:** detalhes abrem em drawer lateral preservando contexto
- **Real-time feedback:** indicadores visuais de status do WMS (verde/vermelho/cinza)

### Core Screens and Views

1. Dashboard Comercial — pipeline geral, KPIs, follow-ups pendentes, ranking de contas
2. Lista de Contas — grid filtrável com busca, segmento, status, vendedor
3. Detalhe da Conta (360°) — dados cadastrais, contatos, oportunidades, pedidos, interações
4. Pipeline de Oportunidades — Kanban com estágios configuráveis + consulta de estoque inline
5. Criação/Edição de Oportunidade — formulário com seleção de produtos + disponibilidade WMS real-time
6. Criação de Pedido — confirmação de itens, quantidade e disparo ao WMS
7. Acompanhamento de Pedidos — status sincronizado do WMS (separação, expedição, entregue)
8. Gestão de Atividades — calendário/lista de interações e follow-ups pendentes
9. Relatórios e Forecast — gráficos de conversão, forecast mensal, comparativo por período
10. Gestão de Usuários — admin de perfis e permissões via Keycloak RBAC
11. Importação de Dados — wizard de migração do ERP legado com preview e validação

### Accessibility

**WCAG AA** — contraste adequado, navegação por teclado, labels descritivos, feedbacks não dependentes apenas de cor.

### Branding

⚠️ **Open Question:** definir paleta de cores e design system antes de iniciar Epic 2. Se o WMS tem identidade visual estabelecida, o CRM deve seguir o mesmo padrão.

### Target Device and Platforms

**Web Responsive — Desktop prioritário.** App Android (Kotlin + Jetpack Compose) planejado para Phase 2.

---

## Technical Assumptions

### Repository Structure

**Polyrepo** ✅ — um repositório por módulo do ODIN ERP. Repositórios do CRM: `odin-crm-backend` (Java/Spring Boot) e `odin-crm-frontend` (Next.js). Contratos Protobuf compartilhados via repositório dedicado `odin-proto` publicado como pacote Maven/npm.

### Service Architecture

**Microserviços — database-per-service — event-driven via Kafka**

| Camada | Tecnologia |
|---|---|
| **Frontend Web** | React 19 + TypeScript + Next.js (App Router) + Tailwind CSS + shadcn/ui |
| **Backend (CRM)** | Java 21 + Spring Boot 3 + Spring Cloud |
| **Database** | PostgreSQL 17 — database-per-service |
| **Cache** | Redis |
| **Busca/Indexação** | Elasticsearch + ELK Stack |
| **Mensageria** | Apache Kafka |
| **API Gateway** | Kong ou Spring Cloud Gateway |
| **APIs** | REST (CRUD) + GraphQL (painel cliente) + gRPC (inter-serviços) |
| **Auth** | Keycloak (OAuth2 / OIDC / RBAC / multi-tenant) — já em produção no WMS |
| **Containerização** | Docker + Kubernetes (EKS/GKE) — já em produção no WMS |
| **CI/CD** | GitHub Actions + ArgoCD |
| **Observabilidade** | Prometheus + Grafana + ELK + Jaeger + Istio |
| **Armazenamento** | MinIO (S3-compatible) ou AWS S3 |
| **Infra/Cloud** | **AWS** ✅ — EKS (Kubernetes), MSK (Kafka gerenciado), S3 (armazenamento), ECR (registry de imagens) |

**Padrões arquiteturais obrigatórios:**
- **Outbox Pattern** para garantia de entrega de eventos Kafka (pedidos CRM → WMS)
- **Circuit Breaker** (Resilience4j) para chamadas gRPC ao WMS
- **Kafka Consumer Idempotente** para atualizações de status do WMS
- **gRPC + Protobuf versionado** para contrato CRM ↔ WMS

### Testing Requirements

**Pirâmide completa — qualidade sobre velocidade:**
- **Unitários:** JUnit 5 + Mockito (backend) | Jest + React Testing Library (frontend) — cobertura mínima 80%
- **Integração:** Spring Boot Test + Testcontainers (PostgreSQL, Redis, Kafka reais em containers)
- **Contrato:** Pact (Consumer-Driven Contract Testing) para gRPC entre CRM e WMS
- **E2E:** Playwright para fluxos críticos
- **Performance:** k6 para validação de NFR1, NFR2, NFR7
- **Segurança:** OWASP ZAP integrado ao CI/CD
- **Code Review:** CodeRabbit — CRITICAL e HIGH bloqueiam merge

### Additional Technical Assumptions

- Keycloak, Kafka e Kubernetes já em produção no WMS — CRM reutilizará a mesma infraestrutura
- CRM criará seus próprios Kafka topics sem modificar topics existentes do WMS
- Novo namespace `odin-crm` criado no cluster Kubernetes existente
- Preset AIOS `odin-erp-fullstack` a ser criado documentando a stack completa

---

## Epic List

| Epic | Título | Goal |
|---|---|---|
| **Epic 1** | Foundation & Core Infrastructure | Estabelecer infraestrutura completa com auth funcional e deploy automatizado |
| **Epic 2** | Gestão de Clientes | Digitalizar carteira de clientes eliminando o caderno do vendedor |
| **Epic 3** | Pipeline Comercial & Visibilidade | Entregar funil de vendas completo com dashboard e forecast |
| **Epic 4** | Integração WMS — O Diferencial | Integrar CRM ao WMS em tempo real via gRPC e Kafka |
| **Epic 5** | Migração & Go-Live | Garantir transição segura do ERP legado com dados migrados e sistema production-ready |

---

## Epic 1: Foundation & Core Infrastructure

**Goal:** Estabelecer toda a infraestrutura técnica necessária para o desenvolvimento do ODIN CRM — microserviço Spring Boot com banco de dados, autenticação Keycloak, frontend Next.js e pipeline CI/CD com deploy automatizado no Kubernetes. Ao final deste epic, o sistema estará deployado em ambiente real com login funcional e gestão de perfis de acesso, pronto para receber funcionalidades de negócio nos epics seguintes.

### Story 1.1: Spring Boot Microservice Skeleton

*Como desenvolvedor, quero um microserviço Spring Boot estruturado e funcional, para que eu tenha a base técnica do backend do ODIN CRM pronta para receber código de negócio.*

**Acceptance Criteria:**
1. Projeto Spring Boot 3 + Java 21 criado com estrutura DDD (`domain`, `application`, `infrastructure`, `interfaces`)
2. Dependências configuradas: Spring Web, Spring Data JPA, Spring Security, Spring Actuator, Micrometer, Lombok, MapStruct
3. Endpoint `GET /actuator/health` retorna `{"status": "UP"}` com HTTP 200
4. Dockerfile multi-stage criado — imagem final < 200MB
5. `docker-compose.yml` local com PostgreSQL 17 para desenvolvimento
6. Logging estruturado em JSON configurado via Logback
7. Testes unitários de smoke passando com JUnit 5

### Story 1.2: Database Setup & Migration Pipeline

*Como desenvolvedor, quero banco de dados configurado com pipeline de migrations, para que o schema evolua de forma controlada e reproduzível em todos os ambientes.*

**Acceptance Criteria:**
1. PostgreSQL 17 conectado via Spring Data JPA com HikariCP configurado
2. Flyway configurado para migrations versionadas em `resources/db/migration/`
3. Migration inicial `V1__create_schema.sql` com schema `crm` criado
4. Testcontainers configurado — testes de repositório usam PostgreSQL real, não H2
5. Variáveis de ambiente externalizadas para credenciais (sem hardcode)
6. Migration executada automaticamente no startup em todos os ambientes

### Story 1.3: Keycloak Backend Integration

*Como sistema, quero validar tokens JWT do Keycloak e aplicar RBAC por endpoint, para que apenas usuários autenticados com perfil correto acessem os recursos do CRM.*

**Acceptance Criteria:**
1. Spring Security configurado como OAuth2 Resource Server validando JWT do Keycloak existente
2. Roles mapeadas: `crm-vendedor`, `crm-gerente`, `crm-sac`, `crm-financeiro`, `crm-admin`
3. Endpoint sem token retorna HTTP 401; role incorreta retorna HTTP 403
4. `GET /api/v1/me` retorna dados do usuário autenticado (sub, roles, name)
5. Realm e client `odin-crm` configurados no Keycloak existente do WMS
6. Testes de integração validando cenários 401, 403 e 200 com tokens mockados

### Story 1.4: Next.js Frontend Setup

*Como desenvolvedor, quero um projeto Next.js estruturado com design system configurado, para que o desenvolvimento do frontend parta de uma base consistente com os padrões visuais do ODIN ERP.*

**Acceptance Criteria:**
1. Next.js 15 (App Router) + React 19 + TypeScript com estrutura de pastas (`app`, `components`, `lib`, `hooks`, `types`)
2. Tailwind CSS + shadcn/ui instalados e configurados com tema base
3. Layout principal criado: sidebar + header + área de conteúdo
4. Páginas de loading e 404 customizadas
5. ESLint + Prettier configurados
6. Dockerfile para produção (Next.js standalone output)
7. Variáveis de ambiente tipadas via `env.ts` com validação Zod

### Story 1.5: Frontend Authentication (Keycloak)

*Como usuário, quero fazer login com minhas credenciais corporativas via Keycloak, para que eu acesse o ODIN CRM com meu perfil de acesso correto sem criar nova senha.*

**Acceptance Criteria:**
1. Fluxo de login via Keycloak OIDC implementado (redirect → callback → sessão)
2. Rotas protegidas redirecionam para login se sessão não existir
3. Dados do usuário (nome, email, roles) disponíveis via hook `useCurrentUser()`
4. Logout encerra sessão no Next.js e no Keycloak (global logout)
5. Token renovado automaticamente antes de expirar (silent refresh)
6. Tela de login exibe mensagem de erro para credenciais inválidas

### Story 1.6: CI/CD Pipeline & Kubernetes Deploy

*Como desenvolvedor, quero pipeline automatizado que valide, construa e faça deploy do CRM no Kubernetes, para que cada merge na branch principal resulte em deploy confiável sem intervenção manual.*

**Acceptance Criteria:**
1. GitHub Actions com stages: `lint` → `test` → `build` → `docker-push` → `deploy`
2. Build backend (Maven) e frontend (Next.js) executados em paralelo
3. Imagem Docker publicada no registry com tag do commit
4. Manifests Kubernetes: `Deployment`, `Service`, `Ingress`, `ConfigMap`, `Secret`
5. ArgoCD configurado para sync automático do namespace `odin-crm`
6. Pod em `Running` e `Ready` após deploy
7. URL pública acessível com certificado TLS válido

### Story 1.7: User Profile Management

*Como administrador, quero gerenciar usuários e seus perfis de acesso no CRM, para que cada colaborador tenha apenas as permissões necessárias para sua função.*

**Acceptance Criteria:**
1. Tela lista todos os usuários do realm Keycloak `odin-crm`
2. Admin atribui/revoga roles por usuário
3. Admin ativa/inativa usuários (soft — preserva histórico)
4. Ações chamam Keycloak Admin REST API via backend Spring Boot
5. Apenas `crm-admin` acessa esta tela
6. Log de auditoria registra cada alteração de permissão

---

## Epic 2: Gestão de Clientes

**Goal:** Permitir que o time comercial gerencie digitalmente toda a carteira de clientes industriais — cadastro completo de contas e contatos, registro estruturado de interações e busca avançada. Ao final deste epic, vendedores, SAC e gestores terão substituído completamente o "caderno do vendedor" por um sistema centralizado, rastreável e acessível por perfil de acesso.

### Story 2.1: Account Domain Model & REST API

*Como sistema, quero um domínio de contas bem modelado com API REST completa, para que os dados de clientes sejam armazenados, validados e acessados de forma segura e consistente.*

**Acceptance Criteria:**
1. Entidade `Account`: `id`, `razaoSocial`, `nomeFantasia`, `cnpj` (único), `email`, `telefone`, `endereco`, `segmentoIndustrial`, `limiteCredito`, `status` (ATIVO/INATIVO), `vendedorResponsavel`, `createdAt`, `updatedAt`
2. API REST: `POST`, `GET` (paginado + filtros), `GET /{id}`, `PUT /{id}`, `PATCH /{id}/status`
3. Validação de CNPJ retorna HTTP 422 com mensagem descritiva
4. `crm-sac` e `crm-financeiro` têm acesso somente leitura
5. Migration `V2__create_accounts_table.sql`
6. Testes de integração com Testcontainers cobrindo CRUD e validações

### Story 2.2: Account Management UI

*Como vendedor ou gerente, quero gerenciar contas de clientes pela interface web, para que eu possa cadastrar novos clientes e manter dados atualizados sem depender de planilhas.*

**Acceptance Criteria:**
1. Lista paginada com colunas: razão social, CNPJ, segmento, vendedor, status
2. Filtros por status, segmento e vendedor responsável
3. Busca por nome ou CNPJ com debounce 300ms
4. Formulário com validação client-side e feedback por campo
5. Inativar conta exige confirmação em modal
6. `crm-sac` e `crm-financeiro` visualizam lista sem ações de edição
7. Estados de loading, erro e lista vazia tratados com UI adequada

### Story 2.3: Contact Domain Model & REST API

*Como sistema, quero um domínio de contatos vinculados a contas, para que múltiplos interlocutores de uma mesma empresa cliente sejam registrados individualmente.*

**Acceptance Criteria:**
1. Entidade `Contact`: `id`, `accountId` (FK), `nome`, `cargo`, `email`, `telefone`, `departamento`, `isPrincipal`, `createdAt`, `updatedAt`
2. API REST: CRUD completo com escopo por `accountId`
3. Conta inexistente retorna HTTP 404
4. Máximo um `isPrincipal=true` por conta — constraint garantida pelo sistema
5. Migration `V3__create_contacts_table.sql`
6. Testes de integração cobrindo relacionamento e validações

### Story 2.4: Contact Management UI

*Como vendedor, quero gerenciar os contatos de cada empresa cliente, para que eu saiba com quem falar e como contatá-lo sem consultar o vendedor anterior.*

**Acceptance Criteria:**
1. Aba "Contatos" na tela da conta lista todos os contatos vinculados
2. Card exibe: nome, cargo, email, telefone e badge "Principal"
3. Formulário inline/modal para criação e edição sem sair da tela da conta
4. Definir contato como principal atualiza automaticamente o anterior
5. Soft delete com confirmação
6. Clique em email/telefone copia para clipboard com toast
7. Roles `crm-sac` e `crm-financeiro` visualizam sem ações de edição

### Story 2.5: Activity & Interaction Domain Model & API

*Como sistema, quero um domínio de atividades vinculadas a contas, para que todo o histórico de relacionamento com o cliente seja registrado e rastreável.*

**Acceptance Criteria:**
1. Entidade `Activity`: `id`, `accountId` (FK), `tipo` (LIGACAO/REUNIAO/EMAIL/ANOTACAO), `titulo`, `descricao`, `dataHora`, `usuarioResponsavel`, `createdAt`
2. API REST: CRUD com filtros por tipo e período
3. Qualquer perfil pode registrar; apenas criador ou `crm-admin` pode editar/deletar
4. Migration `V4__create_activities_table.sql`
5. Testes de integração cobrindo CRUD e filtros

### Story 2.6: Activity UI & Customer Interaction Timeline

*Como vendedor ou SAC, quero visualizar todas as interações com um cliente em ordem cronológica, para que eu entenda o histórico completo do relacionamento antes de qualquer contato.*

**Acceptance Criteria:**
1. Aba "Interações" exibe timeline (mais recente primeiro): ícone, título, descrição, data/hora, usuário
2. Formulário de nova atividade via modal com tipo, título, descrição e data/hora
3. Filtros por tipo e período
4. Botões editar/deletar apenas nas atividades do usuário logado
5. Paginação infinita para timelines longas
6. Estado vazio com mensagem incentivando o primeiro registro

### Story 2.7: Account 360° View & Advanced Search

*Como vendedor, gerente ou SAC, quero visão consolidada de cada cliente e busca avançada na carteira, para que eu acesse qualquer informação do cliente em segundos.*

**Acceptance Criteria:**
1. Tela da conta exibe em abas: dados cadastrais, contatos, interações, oportunidades (placeholder), pedidos (placeholder)
2. Header: razão social, CNPJ, segmento, vendedor, status, limite de crédito
3. Card de resumo: oportunidades abertas, total de pedidos, última interação
4. Elasticsearch: `GET /api/v1/accounts/search?q={termo}` em razão social, CNPJ, nomeFantasia, contatos
5. Busca retorna em < 500ms para base de até 10.000 contas
6. Sincronização automática PostgreSQL → Elasticsearch ao criar/editar
7. Busca global no header com resultados em dropdown

---

## Epic 3: Pipeline Comercial & Visibilidade

**Goal:** Entregar o funil de vendas completo com pipeline de oportunidades em Kanban, follow-ups vinculados a oportunidades, configuração de estágios customizáveis e dashboard comercial com forecast em tempo real. Ao final deste epic, vendedores e gestores substituirão planilhas e reuniões de status por visibilidade instantânea e dados confiáveis do funil.

### Story 3.1: Opportunity Domain Model & REST API

*Como sistema, quero um domínio de oportunidades vinculadas a contas, para que o ciclo de venda completo seja registrado desde a prospecção até o fechamento.*

**Acceptance Criteria:**
1. Entidade `Opportunity`: `id`, `accountId` (FK), `contactId` (FK opcional), `titulo`, `valor`, `probabilidade`, `estagio` (FK PipelineStage), `dataPrevisaoFechamento`, `vendedorResponsavel`, `status` (ABERTA/GANHA/PERDIDA/CANCELADA), `motivoPerda`, timestamps
2. API REST: CRUD completo com filtros por status/estágio/vendedor/período + `PATCH /{id}/stage` + `PATCH /{id}/status`
3. PERDIDA sem `motivoPerda` retorna HTTP 422
4. Permissões: vendedor (próprias), gerente/admin (todas), SAC/financeiro (leitura)
5. Migration `V5__create_opportunities_table.sql`
6. Testes cobrindo fluxo completo criação → estágio → fechamento

### Story 3.2: Kanban Pipeline Board UI

*Como vendedor ou gerente, quero visualizar o pipeline em Kanban, para que eu entenda o estado do funil e mova oportunidades entre estágios com um clique.*

**Acceptance Criteria:**
1. Board Kanban com colunas por estágio configurado
2. Card exibe: conta, título, valor (R$), data prevista, vendedor
3. Drag & drop persiste via `PATCH /stage`
4. Filtros: vendedor, conta, período de fechamento previsto
5. Badge de alerta em cards com data de fechamento vencida
6. Drawer lateral ao clicar no card com resumo + "Ver detalhes"
7. Header de coluna: contador de oportunidades + valor total acumulado
8. Gerente vê todos; vendedor vê apenas as próprias

### Story 3.3: Opportunity Detail View & Editing

*Como vendedor, quero tela de detalhe completa para cada oportunidade, para que eu gerencie todas as informações e histórico em um único lugar.*

**Acceptance Criteria:**
1. Todos os campos com edição inline
2. Header: título, conta (link), valor, probabilidade, estágio, data prevista
3. Ações rápidas: "Marcar como Ganha", "Marcar como Perdida" (solicita motivo), "Cancelar"
4. Contato vinculado com opção de alterar
5. Aba de interações reutiliza componente da Story 2.6
6. Breadcrumb: Dashboard → Pipeline → [Oportunidade]
7. Autosave com debounce 1s + indicador visual
8. Histórico de mudanças de estágio na timeline

### Story 3.4: Opportunity Activities & Follow-ups

*Como vendedor, quero registrar atividades e follow-ups diretamente em uma oportunidade, para que o histórico de cada negociação seja rastreável.*

**Acceptance Criteria:**
1. `Activity` estendida com `opportunityId` (FK opcional) — migration `V6__add_opportunity_id_to_activities.sql`
2. `GET /api/v1/opportunities/{id}/activities` retorna atividades da oportunidade
3. Atividade vinculada aparece na timeline da oportunidade E da conta
4. Tipo `FOLLOWUP` com `dataLembrete` — alerta no dashboard quando vencido
5. Dashboard exibe seção "Follow-ups pendentes"
6. Formulário pré-seleciona `opportunityId` automaticamente
7. Testes validando atividade aparece em ambas as timelines

### Story 3.5: Pipeline Stage Configuration

*Como administrador, quero configurar os estágios do pipeline, para que o funil reflita o processo comercial real da empresa.*

**Acceptance Criteria:**
1. Entidade `PipelineStage`: `id`, `nome`, `ordem`, `probabilidadePadrao`, `cor` (hex), `isDefault`, `isFinal`, `ativo`
2. API REST apenas para `crm-admin`
3. Seed: Prospecção (10%), Qualificação (30%), Proposta (50%), Negociação (70%), Fechamento (90%)
4. Tela `/admin/pipeline-stages` com lista reordenável e edição inline
5. Deletar estágio com oportunidades retorna HTTP 409
6. Reordenação reflete imediatamente no Kanban
7. Apenas `crm-admin` visualiza o menu de configuração

### Story 3.6: Sales Dashboard & KPIs

*Como vendedor e gerente, quero dashboard comercial com métricas em tempo real, para que eu tome decisões baseadas em dados sem relatórios manuais.*

**Acceptance Criteria:**
1. Cards de KPIs: oportunidades abertas, valor total do pipeline, conversão do mês, follow-ups pendentes, oportunidades vencidas
2. Gráfico de pipeline por estágio com valor acumulado
3. Ranking top 5 contas por valor de oportunidades abertas
4. Tabela de performance: conversão, ticket médio e pipeline por vendedor
5. Filtros: hoje, semana, mês, trimestre, personalizado
6. Gerente vê equipe; vendedor vê apenas próprios dados
7. Dashboard carrega em < 2 segundos
8. Atualização automática a cada 5 minutos (React Query stale-while-revalidate)

### Story 3.7: Monthly Forecast & Reports

*Como gerente, quero relatórios de forecast e conversão exportáveis, para que eu apresente resultados à diretoria e planeje metas com dados reais.*

**Acceptance Criteria:**
1. 3 relatórios: "Forecast Mensal", "Conversão por Estágio", "Performance por Vendedor"
2. Forecast Mensal: oportunidades agrupadas por semana com valor ponderado (valor × probabilidade)
3. Conversão por Estágio: taxa de avanço/abandono entre estágios consecutivos
4. Performance por Vendedor: criadas, ganhas, perdidas, valor ganho, ticket médio
5. Exportação CSV com um clique
6. Filtros de período e vendedor
7. Apenas `crm-gerente` e `crm-admin` acessam

---

## Epic 4: Integração WMS — O Diferencial

**Goal:** Integrar o ODIN CRM ao WMS em tempo real — consulta de estoque via gRPC durante a negociação e criação automática de pedidos via Kafka com Outbox Pattern. Com circuit breaker garantindo resiliência, o CRM funciona mesmo com o WMS degradado. Ao final deste epic, um vendedor consulta estoque, fecha a oportunidade e cria o pedido em menos de 3 cliques.

### Story 4.1: gRPC Contract & WMS Stock Query Client

*Como sistema, quero cliente gRPC configurado com contrato Protobuf versionado para consultar estoque do WMS, para que o CRM obtenha disponibilidade de produtos em tempo real com resiliência.*

**Acceptance Criteria:**
1. Contrato `StockService.proto`: `CheckStock(StockRequest) returns (StockResponse)` — request: `productId`, `quantity`; response: `available`, `quantityAvailable`, `unit`, `warehouseLocation`
2. Cliente gRPC Spring Boot configurado via `WMS_GRPC_HOST`
3. Circuit Breaker Resilience4j: timeout 2s, fallback `{"available": false, "reason": "WMS_UNAVAILABLE"}`
4. `GET /api/v1/products/{productId}/stock?quantity={n}` expõe consulta via REST
5. Cache Redis TTL 30s para respostas de estoque
6. Mock gRPC server para desenvolvimento e testes sem WMS real
7. Testes com Testcontainers: resposta normal, timeout → fallback, cache hit

### Story 4.2: Stock Availability Display in Opportunity UI

*Como vendedor, quero ver disponibilidade de estoque diretamente na oportunidade, para que eu saiba se posso comprometer o prazo antes de apresentar proposta ao cliente.*

**Acceptance Criteria:**
1. Componente de seleção de produto com busca por nome/código
2. Consulta automática ao selecionar produto e informar quantidade
3. Indicador visual: 🟢 Disponível | 🟡 Estoque baixo | 🔴 Indisponível | ⚪ Consulta indisponível
4. Loading spinner máximo 2s — fallback visual imediato se WMS não responder
5. Múltiplos produtos com consultas independentes
6. ⚪ nunca bloqueia o vendedor — negociação continua sem confirmação de estoque
7. Resultado em tooltip ao passar o mouse sobre o badge

### Story 4.3: Order Domain Model & REST API

*Como sistema, quero domínio de pedidos completo com rastreamento de status, para que cada pedido criado no CRM seja persistido, rastreável e associado à oportunidade e conta.*

**Acceptance Criteria:**
1. Entidade `Order`: `id`, `opportunityId` (FK), `accountId` (FK), `status` (CRIADO/ENVIADO_WMS/EM_SEPARACAO/EXPEDIDO/ENTREGUE/CANCELADO), `valorTotal`, `observacoes`, timestamps
2. Entidade `OrderItem`: `id`, `orderId` (FK), `productId`, `productName` (snapshot), `quantidade`, `precoUnitario` (snapshot)
3. Tabela `OutboxEvent`: `id`, `aggregateId`, `eventType`, `payload` (JSON), `status` (PENDENTE/PUBLICADO/ERRO), timestamps — migration `V8__create_orders_and_outbox.sql`
4. API REST: `GET /api/v1/orders`, `GET /api/v1/orders/{id}`, `GET /api/v1/accounts/{id}/orders`, `GET /api/v1/opportunities/{id}/orders`
5. `crm-financeiro` tem leitura completa
6. Preenche placeholder de pedidos da Story 2.7

### Story 4.4: Kafka Producer — Order Creation with Outbox Pattern

*Como sistema, quero criar pedidos no CRM e publicá-los no Kafka com garantia de entrega via Outbox Pattern, para que nenhum pedido seja perdido mesmo em falha temporária do broker.*

**Acceptance Criteria:**
1. `POST /api/v1/orders` cria `Order` + `OrderItem`(s) + `OutboxEvent` em única transação PostgreSQL atômica
2. Outbox Publisher `@Scheduled` a cada 5s: lê PENDENTE, publica em `crm.orders.created`, marca como PUBLICADO
3. Payload Kafka: `{ orderId, accountId, opportunityId, items: [{productId, quantidade}], timestamp }`
4. Falha na publicação: conta tentativas → após 3, encaminha para `crm.orders.created.dlq`
5. Kafka Message Key = `accountId` (ordenação por conta)
6. Idempotency key no header previne processamento duplicado pelo WMS
7. Testes Testcontainers: atomicidade, publicação, retry, DLQ

### Story 4.5: Kafka Consumer — WMS Order Status Updates

*Como sistema, quero consumir eventos de atualização de status do WMS via Kafka, para que o acompanhamento do pedido no CRM reflita automaticamente o progresso no armazém.*

**Acceptance Criteria:**
1. Consumer Kafka para `wms.orders.status` com consumer group `crm-order-status`
2. Payload: `{ orderId, newStatus, timestamp, warehouseOperator }`
3. Consumer idempotente — processar mesmo evento duas vezes não altera estado incorretamente
4. Cada mudança de status cria `Activity` automática na conta e na oportunidade
5. Falhas após 3 tentativas encaminham para `wms.orders.status.dlq`
6. Métricas Prometheus: `crm_kafka_messages_consumed_total`, `crm_kafka_messages_failed_total`, `crm_order_status_lag`
7. Testes Testcontainers: atualização de status, idempotência, DLQ

### Story 4.6: Order Management UI & Order Tracking

*Como vendedor e SAC, quero criar pedidos diretamente do CRM e acompanhar seu progresso até a entrega, para que o ciclo completo venda → separação → expedição aconteça sem sair do sistema.*

**Acceptance Criteria:**
1. Botão "Criar Pedido" na oportunidade (status ABERTA + estágio Negociação/Fechamento)
2. Modal: itens com produto, quantidade e disponibilidade de estoque; observações; valor total calculado
3. Confirmação antes de criar com aviso de envio automático ao WMS
4. Após criação: oportunidade marcada como GANHA; pedido aparece nas abas da oportunidade e da conta
5. Stepper visual: CRIADO → EM_SEPARACAO → EXPEDIDO → ENTREGUE com timestamp
6. Status atualiza via polling a cada 30 segundos
7. Tela `/orders` com filtros por status, conta, período e vendedor
8. Badge de status do pedido no card Kanban da oportunidade

---

## Epic 5: Migração & Go-Live

**Goal:** Garantir a transição completa e segura do ERP legado para o ODIN CRM — ferramenta de importação estruturada, migração do histórico de pedidos, hardening LGPD, testes de carga validando todos os NFRs e sistema production-ready com alertas e runbooks.

### Story 5.1: Legacy ERP Data Migration — Accounts & Contacts

*Como administrador, quero ferramenta de importação de contas e contatos do ERP legado, para que o histórico de clientes seja migrado com integridade sem retrabalho manual.*

**Acceptance Criteria:**
1. Wizard `/admin/migration` em 4 etapas: Upload → Validação → Preview → Confirmação
2. CSV com mapeamento de colunas configurável via interface visual
3. Relatório de validação: total, válidos, erros por linha
4. Dry-run obrigatório antes da importação real
5. Importação idempotente — CNPJ como chave de deduplicação
6. Progress bar em tempo real para volumes > 1.000 registros (job assíncrono)
7. Relatório final exportável: importados, ignorados (duplicatas), erros
8. Apenas `crm-admin` acessa

### Story 5.2: Order History Migration

*Como administrador, quero importar histórico de pedidos do ERP legado vinculados às contas migradas, para que o histórico 360° do cliente seja completo desde o primeiro dia.*

**Acceptance Criteria:**
1. CSV com: `cnpjCliente`, `numeroPedidoLegado`, `dataPedido`, `status`, `itens`, `valorTotal`
2. Vincula à conta pelo CNPJ; CNPJ sem conta → erro ignorado com relatório
3. Status do ERP legado mapeado via configuração visual no wizard
4. Flag `importedFromLegacy=true` e `externalId` em todos os pedidos importados
5. Pedidos importados NÃO disparam evento Kafka ao WMS
6. Dry-run e idempotência (`externalId` como chave)
7. Histórico aparece nas abas "Pedidos" da conta e oportunidade

### Story 5.3: LGPD Compliance & Security Hardening

*Como sistema, quero dados pessoais protegidos por criptografia e direito ao esquecimento implementado, para que o ODIN CRM esteja em conformidade com a LGPD antes do go-live.*

**Acceptance Criteria:**
1. Campos PII criptografados AES-256: `Contact.email`, `Contact.telefone`, `Account.email`
2. TLS/mTLS em toda comunicação (Istio para inter-serviços, certificado válido para frontend)
3. `DELETE /api/v1/accounts/{id}/personal-data` anonimiza PII sem deletar registro
4. Log de auditoria de acesso a dados PII retido por mínimo 12 meses no ELK
5. OWASP ZAP scan: zero vulnerabilidades CRITICAL ou HIGH
6. Job mensal anonimiza contas inativas há > 5 anos
7. Documentação de privacidade: mapa de dados PII (campos, armazenamento, finalidade, retenção)

### Story 5.4: Load Testing & Performance Validation

*Como sistema, quero testes de carga validando todos os NFRs de performance, para que o go-live aconteça com garantia de que o sistema suporta a carga real sem degradação.*

**Acceptance Criteria:**
1. Suite k6 com cenários: consulta de estoque, criação de pedido, carregamento de dashboard, busca Elasticsearch
2. Cenário de carga: 50 usuários simultâneos por 10 minutos
3. NFR1 validado: gRPC stock query < 200ms p95
4. NFR2 validado: evento Kafka processado < 5 segundos
5. NFR7 validado: frontend carrega < 3s (Playwright no CI)
6. NFR4 validado: 99,5% disponibilidade em 24h de teste de estabilidade
7. Relatório HTML publicado como artefato do GitHub Actions
8. NFR violado **bloqueia o go-live** e gera issue automática no repositório

### Story 5.5: Production Readiness & Go-Live

*Como administrador e operador, quero sistema monitorado com runbooks documentados e checklist de go-live executado, para que a transição do ERP legado aconteça de forma controlada e reversível.*

**Acceptance Criteria:**
1. Alertas Prometheus/Grafana: CPU > 80%, latência gRPC > 500ms, lag Kafka > 100, erro HTTP > 1%, Outbox PENDENTE > 5min
2. Dashboard Grafana: saúde do CRM, métricas de integração WMS, taxa de pedidos, erros do consumer
3. Runbook: restart do serviço, rollback de deploy (ArgoCD), falha do Kafka, recuperação de eventos DLQ
4. Checklist de go-live executado: ✅ dados migrados, ✅ LGPD aprovado, ✅ NFRs validados, ✅ alertas ativos, ✅ runbook testado, ✅ backup do ERP legado realizado
5. Plano de rollback: restaurar ERP legado em < 2 horas se go-live falhar
6. Variáveis de ambiente de produção auditadas — zero secrets em código ou logs
7. Guia de uso por perfil (vendedor, gerente, SAC, financeiro) com screenshots dos fluxos principais

---

## Checklist Results Report

### Executive Summary

| Dimensão | Resultado |
|---|---|
| Completude geral | **83%** |
| Adequação do escopo MVP | Just Right |
| Prontidão para arquitetura | ✅ **READY** |
| Cloud provider | AWS (EKS + MSK + S3 + ECR) ✅ |
| Repository strategy | Polyrepo + `odin-proto` para contratos gRPC ✅ |

### Category Analysis

| Categoria | Status | Issues Críticos |
|---|---|---|
| 1. Problem Definition & Context | ⚠️ PARTIAL | Sem baseline quantitativo; sem pesquisa formal |
| 2. MVP Scope Definition | ⚠️ PARTIAL | Feedback pós-go-live não planejado |
| 3. User Experience Requirements | ⚠️ PARTIAL | Sem diagramas de fluxo; branding indefinido |
| 4. Functional Requirements | ✅ PASS | FRs sem ranking P1/P2/P3 |
| 5. Non-Functional Requirements | ⚠️ PARTIAL | Escalabilidade e backup/RTO/RPO ausentes |
| 6. Epic & Story Structure | ✅ PASS | Dev environment sem story dedicada |
| 7. Technical Guidance | ✅ PASS | Tech debt approach não documentada |
| 8. Cross-Functional Requirements | ⚠️ PARTIAL | Data quality e suporte não documentados |
| 9. Clarity & Communication | ⚠️ PARTIAL | Sem diagramas visuais |

### Final Decision

> ✅ **READY FOR ARCHITECT** — Blockers resolvidos (AWS + Polyrepo). O PRD está completo e pronto para o `@architect` iniciar o documento de arquitetura.

---

## Next Steps

### UX Expert Prompt

> @ux-design-expert: O ODIN CRM é um módulo CRM B2B para indústrias, integrado ao WMS em tempo real. Temos 5 perfis de usuário (vendedor, gerente, SAC, financeiro, comprador), 11 telas core e a UX vision de "interface enterprise, dashboard-first, Kanban de pipeline, operação em menos de 3 cliques". Stack: React 19 + Next.js + Tailwind + shadcn/ui. Acessibilidade WCAG AA. Desktop prioritário. **Inicie o modo de criação de especificação UX/UI** usando este PRD como input, priorizando os fluxos críticos: vendedor → oportunidade → consulta de estoque → criação de pedido; e SAC → conta → histórico 360°. Resolva também a open question de branding antes de iniciar.

### Architect Prompt

> @architect: O ODIN CRM é o segundo módulo do ODIN ERP — sistema enterprise para indústrias B2B. Stack definida: Java 21 + Spring Boot 3 (backend), React 19 + Next.js (frontend), PostgreSQL 17 database-per-service, Apache Kafka para mensageria inter-módulos, gRPC para integração real-time com WMS existente, Keycloak para SSO, Kubernetes no AWS/GCP. **Inicie o modo de criação de arquitetura** usando este PRD como input. Prioridades arquiteturais: (1) Outbox Pattern para garantia de entrega de pedidos CRM → WMS, (2) Circuit Breaker para chamadas gRPC ao WMS, (3) Contrato Protobuf versionado CRM ↔ WMS, (4) Estratégia de sincronização PostgreSQL → Elasticsearch. Resolva antes de iniciar: cloud provider (AWS vs GCP) e estratégia de repositório (polyrepo vs monorepo).

---

_— Morgan, planejando o futuro 📊_
_Documento baseado em: docs/brief.md_
