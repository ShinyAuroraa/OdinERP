-- V12__shipping_orders.sql
-- Story 5.3: Shipping — Expedição & Despacho de Volumes

-- ─── Adicionar SHIPPING_ORDER ao constraint de reference_type ──────────────
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_reference_type
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE',
        'PUTAWAY_TASK', 'QUARANTINE_TASK', 'INTERNAL_TRANSFER', 'PICKING_ORDER',
        'PACKING_ORDER', 'SHIPPING_ORDER'
    ));

-- ─── Tabela shipping_orders ─────────────────────────────────────────────────
CREATE TABLE shipping_orders (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    packing_order_id     UUID NOT NULL REFERENCES packing_orders(id) ON DELETE RESTRICT,
    picking_order_id     UUID REFERENCES picking_orders(id),
    warehouse_id         UUID NOT NULL REFERENCES warehouses(id),
    crm_order_id         UUID,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DISPATCHED', 'DELIVERED', 'CANCELLED')),
    carrier_name         VARCHAR(200),
    vehicle_plate        VARCHAR(20),
    driver_name          VARCHAR(200),
    tracking_number      VARCHAR(100),
    estimated_delivery   DATE,
    manifest_json        TEXT,
    manifest_generated_at TIMESTAMPTZ,
    operator_id          UUID,
    dispatched_at        TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    cancellation_reason  VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_shipping_orders_packing_order UNIQUE (tenant_id, packing_order_id)
);

-- ─── Tabela shipping_items ──────────────────────────────────────────────────
CREATE TABLE shipping_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    shipping_order_id UUID NOT NULL REFERENCES shipping_orders(id) ON DELETE RESTRICT,
    packing_item_id   UUID NOT NULL REFERENCES packing_items(id) ON DELETE RESTRICT,
    product_id        UUID NOT NULL REFERENCES products_wms(id),
    lot_id            UUID REFERENCES lots(id),
    quantity_shipped  INT NOT NULL CHECK (quantity_shipped > 0),
    loaded            BOOLEAN NOT NULL DEFAULT FALSE,
    loaded_at         TIMESTAMPTZ,
    loaded_by         UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_shipping_items_packing_item UNIQUE (shipping_order_id, packing_item_id)
);

-- ─── Índices ─────────────────────────────────────────────────────────────────
CREATE INDEX idx_shipping_orders_tenant_status
    ON shipping_orders (tenant_id, status, created_at DESC);

CREATE INDEX idx_shipping_orders_packing_order
    ON shipping_orders (packing_order_id);

CREATE INDEX idx_shipping_orders_tenant_crm
    ON shipping_orders (tenant_id, crm_order_id) WHERE crm_order_id IS NOT NULL;

CREATE INDEX idx_shipping_orders_tracking
    ON shipping_orders (tracking_number) WHERE tracking_number IS NOT NULL;

CREATE INDEX idx_shipping_items_order
    ON shipping_items (shipping_order_id);
