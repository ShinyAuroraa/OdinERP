-- V7__inventory_count.sql
-- Story 4.3: Inventário Físico (Contagem de Estoque)

-- Adiciona INVENTORY_ADJUSTMENT ao CHECK constraint de MovementType
ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_type CHECK (type IN (
    'INBOUND', 'PUTAWAY', 'TRANSFER', 'PICKING', 'PACKING',
    'SHIPPING', 'ADJUSTMENT', 'INVENTORY', 'RETURN',
    'QUARANTINE_IN', 'QUARANTINE_OUT', 'INVENTORY_ADJUSTMENT'
));

CREATE TABLE inventory_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    zone_id UUID REFERENCES zones(id),
    count_type VARCHAR(20) NOT NULL CHECK (count_type IN ('CYCLIC', 'FULL')),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    adjustment_threshold INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    approved_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT chk_cyclic_zone CHECK (count_type != 'CYCLIC' OR zone_id IS NOT NULL)
);

CREATE TABLE inventory_count_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_count_id UUID NOT NULL REFERENCES inventory_counts(id),
    tenant_id UUID NOT NULL,
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products_wms(id),
    lot_id UUID REFERENCES lots(id),
    expected_qty NUMERIC(15,3) NOT NULL,
    counted_qty NUMERIC(15,3),
    second_counted_qty NUMERIC(15,3),
    adjusted_qty NUMERIC(15,3),
    divergence_qty NUMERIC(15,3),
    divergence_pct NUMERIC(8,4),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_count_location_product_lot
        UNIQUE (inventory_count_id, location_id, product_id, lot_id)
);

CREATE INDEX idx_inv_counts_tenant_status ON inventory_counts(tenant_id, status);
CREATE INDEX idx_inv_items_count_status ON inventory_count_items(inventory_count_id, status);
CREATE INDEX idx_inv_items_count_location ON inventory_count_items(inventory_count_id, location_id);
