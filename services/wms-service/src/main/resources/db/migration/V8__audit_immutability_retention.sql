-- =============================================================================
-- WMS Service — Auditoria Imutável & Retenção LGPD
-- Story 4.4: Auditoria Imutável & Log de Movimentações
-- Version: 1.0.0
-- Author: @data-engineer (Dara)
-- Date: 2026-02-23
--
-- O que esta migration faz:
--   1. Cria tabela tenant_retention_config (retenção LGPD por tenant)
--   2. Adiciona trigger de imutabilidade em audit_log (previne UPDATE/DELETE)
--   3. Cria índice adicional para queries de auditoria por action
--
-- NOTA: audit_log já existe desde V2. NÃO recriar a tabela.
-- =============================================================================

-- =============================================================================
-- 1. TABELA DE RETENÇÃO POR TENANT (LGPD)
-- =============================================================================

CREATE TABLE IF NOT EXISTS tenant_retention_config (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,
    retention_months INT         NOT NULL DEFAULT 84,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenant_retention_config PRIMARY KEY (id),
    CONSTRAINT uq_tenant_retention_config_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_tenant_retention_config_months CHECK (retention_months >= 12)
);

COMMENT ON TABLE tenant_retention_config IS 'Configuração de retenção LGPD por tenant. Default: 84 meses (7 anos). Mínimo: 12 meses.';
COMMENT ON COLUMN tenant_retention_config.tenant_id IS 'Identificador único do tenant. Cada tenant tem no máximo uma configuração de retenção.';
COMMENT ON COLUMN tenant_retention_config.retention_months IS 'Período de retenção dos registros de auditoria em meses. Mínimo: 12 (compliance LGPD). Default: 84 (7 anos).';

-- =============================================================================
-- 2. TRIGGER DE IMUTABILIDADE EM audit_log
-- =============================================================================
-- Garante que registros de auditoria não podem ser alterados ou excluídos
-- após a inserção. O trigger BEFORE UPDATE OR DELETE lança exceção em qualquer
-- tentativa de modificação, garantindo a integridade do log para auditoria
-- regulatória.

CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log é imutável — UPDATE/DELETE proibidos. Registro: %', OLD.id
        USING ERRCODE = 'restrict_violation';
    RETURN NULL;
END;
$$;

COMMENT ON FUNCTION prevent_audit_log_modification() IS 'Função de trigger que previne UPDATE e DELETE em audit_log. Garante imutabilidade do log de auditoria para compliance regulatório.';

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_modification();

COMMENT ON TRIGGER trg_audit_log_immutable ON audit_log IS 'Trigger de imutabilidade — impede UPDATE e DELETE em qualquer registro de audit_log após inserção.';

-- =============================================================================
-- 3. ÍNDICE ADICIONAL PARA QUERIES DE AUDITORIA
-- =============================================================================
-- idx_audit_log_tenant_created já existe desde V2 — NÃO recriar.
-- Adicionando índice para queries filtradas por action (usado em GET /audit/log?action=X).

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_action
    ON audit_log (tenant_id, action, created_at DESC);

COMMENT ON INDEX idx_audit_log_tenant_action IS 'Índice para queries filtradas por tipo de ação (ex: MOVEMENT, CREATE) dentro de um tenant, ordenadas por data decrescente.';

-- =============================================================================
-- ROLLBACK (para referência — nunca executar em produção sem validação)
-- =============================================================================
-- DROP TRIGGER IF EXISTS trg_audit_log_immutable ON audit_log;
-- DROP FUNCTION IF EXISTS prevent_audit_log_modification();
-- DROP TABLE IF EXISTS tenant_retention_config CASCADE;
-- DROP INDEX IF EXISTS idx_audit_log_tenant_action;
