-- V11__packing_orders.sql
-- Story 5.2: Packing — Estação de Embalagem

-- ─── Adicionar PACKING_ORDER ao constraint de reference_type ──────────────
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_reference_type
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE',
        'PUTAWAY_TASK', 'QUARANTINE_TASK', 'INTERNAL_TRANSFER', 'PICKING_ORDER',
        'PACKING_ORDER'
    ));

-- ─── Tabela packing_orders ─────────────────────────────────────────────────
CREATE TABLE packing_orders (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    picking_order_id     UUID NOT NULL REFERENCES picking_orders(id),
    warehouse_id         UUID NOT NULL REFERENCES warehouses(id),
    crm_order_id         UUID,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    operator_id          UUID,
    weight_kg            DECIMAL(10,3),
    package_type         VARCHAR(20),
    length_cm            DECIMAL(8,2),
    width_cm             DECIMAL(8,2),
    height_cm            DECIMAL(8,2),
    sscc                 VARCHAR(20) UNIQUE,
    notes                VARCHAR(500),
    completed_at         TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    cancellation_reason  VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_packing_orders_picking_order UNIQUE (tenant_id, picking_order_id)
);

-- ─── Tabela packing_items ──────────────────────────────────────────────────
CREATE TABLE packing_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    packing_order_id  UUID NOT NULL REFERENCES packing_orders(id) ON DELETE RESTRICT,
    picking_item_id   UUID NOT NULL REFERENCES picking_items(id),
    product_id        UUID NOT NULL REFERENCES products_wms(id),
    lot_id            UUID REFERENCES lots(id),
    quantity_packed   INT NOT NULL,
    scanned           BOOLEAN NOT NULL DEFAULT FALSE,
    scanned_at        TIMESTAMPTZ,
    scanned_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_packing_items_picking_item UNIQUE (picking_item_id)
);

-- ─── Índices ──────────────────────────────────────────────────────────────
CREATE INDEX idx_packing_orders_tenant_id
    ON packing_orders (tenant_id);

CREATE INDEX idx_packing_orders_picking_order_id
    ON packing_orders (picking_order_id);

CREATE INDEX idx_packing_orders_tenant_status
    ON packing_orders (tenant_id, status);

CREATE INDEX idx_packing_items_packing_order_id
    ON packing_items (packing_order_id);

CREATE INDEX idx_packing_items_tenant_id
    ON packing_items (tenant_id);
