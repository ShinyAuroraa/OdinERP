-- V13__production_material_requests.sql
-- Story 6.1: Integração WMS↔MRP/MRP II — Matéria-Prima & Produto Acabado

-- ─── Adicionar OUTBOUND ao constraint de type em stock_movements ────────────
-- (necessário para movimentações de saída de MP para a linha de produção)
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_type CHECK (type IN (
    'INBOUND', 'OUTBOUND', 'PUTAWAY', 'TRANSFER', 'PICKING', 'PACKING',
    'SHIPPING', 'ADJUSTMENT', 'INVENTORY', 'RETURN',
    'QUARANTINE_IN', 'QUARANTINE_OUT', 'INVENTORY_ADJUSTMENT'
));

-- ─── Extensão de picking_orders (order_type + production_order_id) ──────────
ALTER TABLE picking_orders
    ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER_ORDER'
        CHECK (order_type IN ('CUSTOMER_ORDER', 'PRODUCTION_ORDER')),
    ADD COLUMN production_order_id UUID;

CREATE INDEX idx_picking_orders_production_order
    ON picking_orders(production_order_id) WHERE production_order_id IS NOT NULL;

-- ─── Tabela production_material_requests ────────────────────────────────────
CREATE TABLE production_material_requests (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  UUID NOT NULL,
    production_order_id        UUID NOT NULL,
    mrp_order_number           VARCHAR(100),
    warehouse_id               UUID NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    status                     VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN (
                                       'PENDING', 'RESERVING', 'STOCK_SHORTAGE', 'PICKING_PENDING',
                                       'PICKING_IN_PROGRESS', 'DELIVERED', 'FINISHED_GOODS_RECEIVED',
                                       'CANCELLED', 'ERROR'
                                   )),
    picking_order_id           UUID REFERENCES picking_orders(id),
    total_components           INT NOT NULL DEFAULT 0,
    shortage_components        INT NOT NULL DEFAULT 0,
    confirmed_delivery_at      TIMESTAMPTZ,
    confirmed_by               UUID,
    finished_goods_received_at TIMESTAMPTZ,
    cancellation_reason        VARCHAR(500),
    cancelled_at               TIMESTAMPTZ,
    error_message              TEXT,
    version                    BIGINT NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pmr_tenant_production_order UNIQUE (tenant_id, production_order_id)
);

-- ─── Tabela production_material_request_items ───────────────────────────────
CREATE TABLE production_material_request_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    request_id         UUID NOT NULL REFERENCES production_material_requests(id) ON DELETE RESTRICT,
    product_id         UUID NOT NULL REFERENCES products_wms(id) ON DELETE RESTRICT,
    lot_id             UUID REFERENCES lots(id),
    location_id        UUID REFERENCES locations(id),
    quantity_requested INT NOT NULL CHECK (quantity_requested > 0),
    quantity_reserved  INT NOT NULL DEFAULT 0,
    quantity_delivered INT NOT NULL DEFAULT 0,
    shortage           BOOLEAN NOT NULL DEFAULT false,
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─── Índices ─────────────────────────────────────────────────────────────────
CREATE INDEX idx_pmr_tenant_id
    ON production_material_requests(tenant_id);
CREATE INDEX idx_pmr_tenant_status
    ON production_material_requests(tenant_id, status, created_at DESC);
CREATE INDEX idx_pmr_production_order
    ON production_material_requests(production_order_id);
CREATE INDEX idx_pmri_request_id
    ON production_material_request_items(request_id);
CREATE INDEX idx_pmri_tenant_request
    ON production_material_request_items(tenant_id, request_id);
