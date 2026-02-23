-- V14 — Relatórios Regulatórios
-- Adiciona campo vigilancia_sanitaria em products_wms e cria tabela report_schedules

-- Story 7.1: campo de controle ANVISA
ALTER TABLE products_wms
    ADD COLUMN IF NOT EXISTS vigilancia_sanitaria BOOLEAN NOT NULL DEFAULT FALSE;

-- Story 7.1: agendamentos de relatórios por tenant
CREATE TABLE report_schedules (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL,
    report_type       VARCHAR(50) NOT NULL,
    export_format     VARCHAR(10) NOT NULL,
    cron_expression   VARCHAR(100) NOT NULL,
    warehouse_id      UUID        NOT NULL,
    filters           JSONB,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    last_executed_at  TIMESTAMPTZ,
    next_execution_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_schedules_tenant_active
    ON report_schedules (tenant_id, active);

CREATE INDEX idx_report_schedules_next_execution
    ON report_schedules (next_execution_at)
    WHERE active = TRUE;
