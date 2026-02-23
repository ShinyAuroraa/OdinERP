-- V10__picking_orders.sql
-- Story 5.1: Picking — Separação de Pedidos (Integração CRM)

-- ─── Adicionar PICKING_ORDER ao constraint de reference_type ──────────────
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS chk_stock_movements_reference_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_reference_type
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT', 'RECEIVING_NOTE',
        'PUTAWAY_TASK', 'QUARANTINE_TASK', 'INTERNAL_TRANSFER', 'PICKING_ORDER'
    ));

-- ─── Tabela picking_orders ────────────────────────────────────────────────
CREATE TABLE picking_orders (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    crm_order_id         UUID,
    warehouse_id         UUID NOT NULL REFERENCES warehouses(id),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'PARTIAL', 'CANCELLED')),
    picking_type         VARCHAR(20) NOT NULL DEFAULT 'SINGLE'
                             CHECK (picking_type IN ('SINGLE', 'WAVE', 'BATCH')),
    routing_algorithm    VARCHAR(20) NOT NULL DEFAULT 'S_SHAPE'
                             CHECK (routing_algorithm IN ('S_SHAPE', 'Z_SHAPE', 'LARGEST_GAP')),
    priority             INT NOT NULL DEFAULT 0,
    operator_id          UUID,
    zone_id              UUID REFERENCES zones(id),
    created_by           UUID NOT NULL,
    completed_at         TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    cancellation_reason  VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_picking_orders_crm_order UNIQUE (tenant_id, crm_order_id)
);

-- ─── Tabela picking_items ─────────────────────────────────────────────────
CREATE TABLE picking_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    picking_order_id    UUID NOT NULL REFERENCES picking_orders(id) ON DELETE RESTRICT,
    product_id          UUID NOT NULL REFERENCES products_wms(id),
    lot_id              UUID REFERENCES lots(id),
    location_id         UUID NOT NULL REFERENCES locations(id),
    quantity_requested  INT NOT NULL CHECK (quantity_requested > 0),
    quantity_picked     INT NOT NULL DEFAULT 0 CHECK (quantity_picked >= 0),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'PICKED', 'PARTIAL', 'SKIPPED')),
    sort_order          INT NOT NULL DEFAULT 0,
    picked_by           UUID,
    picked_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

-- ─── Índices ──────────────────────────────────────────────────────────────
CREATE INDEX idx_picking_orders_tenant_status
    ON picking_orders (tenant_id, status, created_at DESC);

CREATE INDEX idx_picking_orders_tenant_operator
    ON picking_orders (tenant_id, operator_id, status);

CREATE INDEX idx_picking_orders_crm_order
    ON picking_orders (crm_order_id) WHERE crm_order_id IS NOT NULL;

CREATE INDEX idx_picking_items_order
    ON picking_items (picking_order_id, sort_order);

CREATE INDEX idx_picking_items_tenant_status
    ON picking_items (tenant_id, status);
