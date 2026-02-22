-- =============================================================================
-- WMS Service — Alocação Inteligente de Estoque (Putaway)
-- Story 3.2: Putaway — Motor FIFO/FEFO
-- Version: 1.0.0
-- Author: @dev (Dex)
-- Date: 2026-02-22
--
-- Tabelas criadas:
--   putaway_tasks  — tarefas de alocação de itens recebidos
--
-- Alterações:
--   stock_movements.chk_stock_movements_reference_type — adiciona PUTAWAY_TASK
-- =============================================================================

-- ─── Adicionar PUTAWAY_TASK ao constraint de reference_type ──────────────────
-- O constraint existente (V4) inclui RECEIVING_NOTE; V5 adiciona PUTAWAY_TASK
-- necessário para StockMovement criado em PutawayService.confirm()
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_reference_type
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE',
        'PUTAWAY_TASK'
    ));

-- ─── putaway_tasks ────────────────────────────────────────────────────────────
CREATE TABLE putaway_tasks (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    receiving_note_id       UUID         NOT NULL,
    stock_item_id           UUID         NOT NULL,
    source_location_id      UUID         NOT NULL,
    suggested_location_id   UUID         NOT NULL,
    confirmed_location_id   UUID,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    strategy_used           VARCHAR(10)  NOT NULL,
    started_at              TIMESTAMPTZ,
    confirmed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    operator_id             UUID,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_putaway_tasks PRIMARY KEY (id),
    CONSTRAINT uq_putaway_tasks_stock_item UNIQUE (stock_item_id),
    CONSTRAINT fk_putaway_tasks_receiving_note
        FOREIGN KEY (receiving_note_id) REFERENCES receiving_notes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_putaway_tasks_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_putaway_tasks_source_location
        FOREIGN KEY (source_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_putaway_tasks_suggested_location
        FOREIGN KEY (suggested_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_putaway_tasks_confirmed_location
        FOREIGN KEY (confirmed_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT chk_putaway_tasks_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'CONFIRMED', 'CANCELLED'
    )),
    CONSTRAINT chk_putaway_tasks_strategy CHECK (strategy_used IN (
        'FIFO', 'FEFO'
    ))
);

COMMENT ON TABLE putaway_tasks IS 'Tarefas de alocação de itens recebidos para localizações de armazenagem. Uma tarefa por stock_item. Geradas após recebimento via POST /receiving-notes/{id}/putaway-tasks.';
COMMENT ON COLUMN putaway_tasks.source_location_id IS 'Localização de origem (dock de recebimento). Preenchida no momento da geração.';
COMMENT ON COLUMN putaway_tasks.suggested_location_id IS 'Localização sugerida pelo motor FIFO/FEFO. Sempre preenchida — se nenhuma localização disponível, geração falha com 422.';
COMMENT ON COLUMN putaway_tasks.confirmed_location_id IS 'Localização efetivamente confirmada pelo operador. NULL até confirm(). Pode diferir da sugestão.';
COMMENT ON COLUMN putaway_tasks.status IS 'PENDING: aguardando início | IN_PROGRESS: operador iniciou movimentação | CONFIRMED: item alocado e StockMovement criado | CANCELLED: tarefa cancelada (item permanece no dock).';
COMMENT ON COLUMN putaway_tasks.strategy_used IS 'Estratégia usada na sugestão: FIFO (controlsExpiry=false) ou FEFO (controlsExpiry=true).';
COMMENT ON COLUMN putaway_tasks.version IS 'Versão para optimistic locking (JPA @Version). Previne conflito entre dois operadores confirmando a mesma tarefa simultaneamente.';

-- ─── Índices ─────────────────────────────────────────────────────────────────
CREATE INDEX idx_putaway_tasks_tenant_status
    ON putaway_tasks (tenant_id, status);

CREATE INDEX idx_putaway_tasks_receiving_note
    ON putaway_tasks (tenant_id, receiving_note_id);

CREATE INDEX idx_putaway_tasks_stock_item
    ON putaway_tasks (stock_item_id);

-- =============================================================================
-- ROLLBACK (para referência)
-- =============================================================================
-- DROP TABLE IF EXISTS putaway_tasks CASCADE;
-- ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;
-- ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_reference_type
--     CHECK (reference_type IS NULL OR reference_type IN (
--         'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
--         'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE'
--     ));
