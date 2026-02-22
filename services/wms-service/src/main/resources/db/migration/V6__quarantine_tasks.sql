-- =============================================================================
-- WMS Service — Controle de Qualidade e Quarentena
-- Story 3.3: Quarantine — Motor de QC com decisão RELEASE/RETURN/SCRAP
-- Version: 1.0.0
-- Author: @dev (Dex)
-- Date: 2026-02-22
--
-- Tabelas criadas:
--   quarantine_tasks  — tarefas de QC para itens FLAGGED de notas COMPLETED_WITH_DIVERGENCE
--
-- Alterações:
--   stock_movements.chk_stock_movements_reference_type — adiciona QUARANTINE_TASK
-- =============================================================================

-- ─── Adicionar QUARANTINE_TASK ao constraint de reference_type ───────────────
-- O constraint existente (V5) inclui PUTAWAY_TASK; V6 adiciona QUARANTINE_TASK
-- necessário para StockMovements criados em QuarantineService (QUARANTINE_IN, PUTAWAY, QUARANTINE_OUT)
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_reference_type
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE',
        'PUTAWAY_TASK', 'QUARANTINE_TASK'
    ));

-- ─── quarantine_tasks ─────────────────────────────────────────────────────────
CREATE TABLE quarantine_tasks (
    id                          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID         NOT NULL,
    receiving_note_id           UUID         NOT NULL,
    receiving_note_item_id      UUID         NOT NULL,
    stock_item_id               UUID         NOT NULL,
    source_location_id          UUID         NOT NULL,
    quarantine_location_id      UUID         NOT NULL,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    decision                    VARCHAR(20),
    quality_notes               TEXT,
    supervisor_id               UUID,
    started_at                  TIMESTAMPTZ,
    decided_at                  TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_quarantine_tasks PRIMARY KEY (id),
    CONSTRAINT uq_quarantine_tasks_stock_item UNIQUE (stock_item_id),
    CONSTRAINT fk_quarantine_tasks_receiving_note
        FOREIGN KEY (receiving_note_id) REFERENCES receiving_notes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_quarantine_tasks_receiving_note_item
        FOREIGN KEY (receiving_note_item_id) REFERENCES receiving_note_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_quarantine_tasks_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_quarantine_tasks_source_location
        FOREIGN KEY (source_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_quarantine_tasks_quarantine_location
        FOREIGN KEY (quarantine_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT chk_quarantine_tasks_status CHECK (status IN (
        'PENDING', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED'
    )),
    CONSTRAINT chk_quarantine_tasks_decision CHECK (decision IS NULL OR decision IN (
        'RELEASE_TO_STOCK', 'RETURN_TO_SUPPLIER', 'SCRAP'
    ))
);

COMMENT ON TABLE quarantine_tasks IS 'Tarefas de QC para itens FLAGGED de notas COMPLETED_WITH_DIVERGENCE. Uma tarefa por stock_item. Geradas via POST /receiving-notes/{id}/quarantine-tasks.';
COMMENT ON COLUMN quarantine_tasks.source_location_id IS 'Dock de recebimento de origem (RECEIVING_DOCK). Preenchida no momento da geração.';
COMMENT ON COLUMN quarantine_tasks.quarantine_location_id IS 'Localização de quarentena onde o item foi movido (QUARANTINE). Sempre preenchida — geração falha com 422 se não existe QUARANTINE no warehouse.';
COMMENT ON COLUMN quarantine_tasks.status IS 'PENDING: aguardando início da inspeção | IN_REVIEW: supervisor iniciou análise | APPROVED: liberado para estoque (RELEASE_TO_STOCK) | REJECTED: devolvido ou descartado (RETURN_TO_SUPPLIER ou SCRAP) | CANCELLED: cancelado sem decisão.';
COMMENT ON COLUMN quarantine_tasks.decision IS 'RELEASE_TO_STOCK: liberado para armazenagem | RETURN_TO_SUPPLIER: devolvido (Kafka event publicado) | SCRAP: descartado (quantityAvailable=0). NULL até decide().';
COMMENT ON COLUMN quarantine_tasks.quality_notes IS 'Observações do supervisor sobre a decisão de qualidade. Nullable.';
COMMENT ON COLUMN quarantine_tasks.supervisor_id IS 'UUID do supervisor que executou start() e/ou decide(). Cross-service (sem FK).';
COMMENT ON COLUMN quarantine_tasks.version IS 'Versão para optimistic locking (JPA @Version). Previne dois supervisores decidindo a mesma task simultaneamente.';

-- ─── Índices ─────────────────────────────────────────────────────────────────
CREATE INDEX idx_quarantine_tasks_tenant_status
    ON quarantine_tasks (tenant_id, status);

CREATE INDEX idx_quarantine_tasks_receiving_note
    ON quarantine_tasks (tenant_id, receiving_note_id);

CREATE INDEX idx_quarantine_tasks_stock_item
    ON quarantine_tasks (stock_item_id);

-- =============================================================================
-- ROLLBACK (para referência)
-- =============================================================================
-- DROP TABLE IF EXISTS quarantine_tasks CASCADE;
-- ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;
-- ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_reference_type
--     CHECK (reference_type IS NULL OR reference_type IN (
--         'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
--         'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE',
--         'PUTAWAY_TASK'
--     ));
