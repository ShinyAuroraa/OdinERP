-- =============================================================================
-- WMS Service — Recebimento de Mercadorias
-- Story 3.1: Recebimento de Mercadorias (Inbound — Integração SCM)
-- Version: 1.0.0
-- Author: @dev (Dex)
-- Date: 2026-02-22
--
-- Tabelas criadas:
--   receiving_notes       — cabeçalho da nota de recebimento
--   receiving_note_items  — itens da conferência
--
-- Alterações:
--   stock_movements.chk_stock_movements_reference_type — adiciona RECEIVING_NOTE
-- =============================================================================

-- ─── Adicionar RECEIVING_NOTE ao constraint de reference_type ────────────────
-- O constraint existente (V2) não inclui RECEIVING_NOTE, necessário para
-- StockMovement criado em ReceivingNoteService.complete()
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_reference_type
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE'
    ));

-- ─── receiving_notes ─────────────────────────────────────────────────────────
CREATE TABLE receiving_notes (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    warehouse_id        UUID         NOT NULL,
    dock_location_id    UUID         NOT NULL,
    purchase_order_ref  VARCHAR(100),
    supplier_id         UUID,
    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_receiving_notes PRIMARY KEY (id),
    CONSTRAINT fk_receiving_notes_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_notes_dock_location
        FOREIGN KEY (dock_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT chk_receiving_notes_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_DIVERGENCE', 'CANCELLED'
    ))
);

COMMENT ON TABLE receiving_notes IS 'Notas de recebimento de mercadorias. Criadas manualmente ou via Kafka consumer (SCM → WMS).';
COMMENT ON COLUMN receiving_notes.purchase_order_ref IS 'Referência ao pedido de compra no SCM (cross-service — sem FK). Usado para idempotência no consumer Kafka.';
COMMENT ON COLUMN receiving_notes.status IS 'PENDING: aguardando conferência | IN_PROGRESS: em conferência | COMPLETED: finalizado | COMPLETED_WITH_DIVERGENCE: finalizado com itens divergentes | CANCELLED: cancelado.';

-- ─── receiving_note_items ────────────────────────────────────────────────────
CREATE TABLE receiving_note_items (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    receiving_note_id   UUID         NOT NULL,
    product_id          UUID         NOT NULL,
    expected_quantity   INTEGER      NOT NULL,
    received_quantity   INTEGER,
    divergence_type     VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    lot_number          VARCHAR(100),
    manufacturing_date  DATE,
    expiry_date         DATE,
    gs1_code            VARCHAR(200),
    item_status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    lot_id              UUID,
    stock_item_id       UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_receiving_note_items PRIMARY KEY (id),
    CONSTRAINT fk_receiving_note_items_note
        FOREIGN KEY (receiving_note_id) REFERENCES receiving_notes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_note_items_product
        FOREIGN KEY (product_id) REFERENCES products_wms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_note_items_lot
        FOREIGN KEY (lot_id) REFERENCES lots(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_note_items_stock_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_items(id) ON DELETE RESTRICT,
    CONSTRAINT chk_receiving_note_items_expected_qty CHECK (expected_quantity > 0),
    CONSTRAINT chk_receiving_note_items_received_qty CHECK (received_quantity IS NULL OR received_quantity >= 0),
    CONSTRAINT chk_receiving_note_items_divergence_type CHECK (divergence_type IN (
        'NONE', 'SHORT', 'EXCESS', 'DAMAGED', 'WRONG_PRODUCT'
    )),
    CONSTRAINT chk_receiving_note_items_item_status CHECK (item_status IN (
        'PENDING', 'CONFIRMED', 'FLAGGED'
    ))
);

COMMENT ON TABLE receiving_note_items IS 'Itens de conferência da nota de recebimento. Um item por produto/linha do pedido.';
COMMENT ON COLUMN receiving_note_items.divergence_type IS 'NONE: sem divergência | SHORT: qty recebida < esperada | EXCESS: qty recebida > esperada | DAMAGED: qty=0, produto danificado | WRONG_PRODUCT: produto errado.';
COMMENT ON COLUMN receiving_note_items.item_status IS 'PENDING: aguardando confirmação | CONFIRMED: confirmado | FLAGGED: sinalizado (qty=0 ou dano grave).';
COMMENT ON COLUMN receiving_note_items.version IS 'Versão para optimistic locking (JPA @Version). Previne conflito entre dois operadores confirmando o mesmo item simultaneamente.';

-- ─── Índices ─────────────────────────────────────────────────────────────────
CREATE INDEX idx_receiving_notes_tenant_status
    ON receiving_notes (tenant_id, status);

CREATE INDEX idx_receiving_notes_tenant_poref
    ON receiving_notes (tenant_id, purchase_order_ref)
    WHERE purchase_order_ref IS NOT NULL;

CREATE INDEX idx_receiving_notes_warehouse
    ON receiving_notes (tenant_id, warehouse_id);

CREATE INDEX idx_receiving_note_items_note
    ON receiving_note_items (receiving_note_id);

CREATE INDEX idx_receiving_note_items_tenant_status
    ON receiving_note_items (tenant_id, item_status);

-- =============================================================================
-- ROLLBACK (para referência)
-- =============================================================================
-- DROP TABLE IF EXISTS receiving_note_items CASCADE;
-- DROP TABLE IF EXISTS receiving_notes CASCADE;
-- ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;
-- ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_reference_type
--     CHECK (reference_type IS NULL OR reference_type IN (
--         'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
--         'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT'
--     ));
