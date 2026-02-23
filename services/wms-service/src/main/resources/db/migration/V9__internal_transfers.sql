-- V9__internal_transfers.sql
-- Story 4.5: Transferências Internas entre Posições

-- ─── Adicionar INTERNAL_TRANSFER ao constraint de reference_type ──────────────
-- O constraint existente (V6) inclui até QUARANTINE_TASK; V9 adiciona INTERNAL_TRANSFER
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_reference_type
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE',
        'PUTAWAY_TASK', 'QUARANTINE_TASK', 'INTERNAL_TRANSFER'
    ));

CREATE TABLE internal_transfers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    transfer_type           VARCHAR(20) NOT NULL CHECK (transfer_type IN ('MANUAL', 'REPLENISHMENT')),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    source_location_id      UUID NOT NULL REFERENCES locations(id),
    destination_location_id UUID NOT NULL REFERENCES locations(id),
    product_id              UUID NOT NULL REFERENCES products_wms(id),
    lot_id                  UUID REFERENCES lots(id),
    quantity                INT NOT NULL CHECK (quantity > 0),
    requested_by            UUID NOT NULL,
    confirmed_by            UUID,
    cancelled_by            UUID,
    reason                  VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_diff_locations CHECK (source_location_id != destination_location_id)
);

CREATE INDEX idx_internal_transfers_tenant_status
    ON internal_transfers (tenant_id, status, created_at DESC);

CREATE INDEX idx_internal_transfers_tenant_product
    ON internal_transfers (tenant_id, product_id, created_at DESC);
