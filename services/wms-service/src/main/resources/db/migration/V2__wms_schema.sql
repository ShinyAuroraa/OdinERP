-- =============================================================================
-- WMS Service — Schema Base
-- Story 1.2: Schema & Domain Model
-- Version: 2.0.0
-- Author: @data-engineer (Dara)
-- Date: 2026-02-21
--
-- Tabelas criadas nesta migration:
--   Hierarquia: warehouses → zones → aisles → shelves → locations
--   Estoque:    products_wms, lots, serial_numbers, stock_items
--   Auditoria:  stock_movements (append-only), audit_log (append-only)
--
-- Design principles:
--   - tenant_id em todas as tabelas (multi-tenant SaaS)
--   - UUIDs como PKs via gen_random_uuid()
--   - FKs com ON DELETE RESTRICT (nunca destruição em cascata silenciosa)
--   - stock_movements e audit_log são imutáveis (sem updated_at)
--   - stock_items com CHECK constraints de quantidade não-negativa
--   - FIFO: received_at em stock_items
--   - FEFO: expiry_date em lots
-- =============================================================================

-- ─── Extensions ──────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- HIERARQUIA DE ARMAZÉM
-- =============================================================================

-- ─── warehouses ──────────────────────────────────────────────────────────────
CREATE TABLE warehouses (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    address     TEXT,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),

    CONSTRAINT pk_warehouses PRIMARY KEY (id),
    CONSTRAINT uq_warehouses_tenant_code UNIQUE (tenant_id, code)
);

COMMENT ON TABLE warehouses IS 'Armazéns físicos do WMS. Um tenant pode ter múltiplos armazéns.';
COMMENT ON COLUMN warehouses.tenant_id IS 'Identificador do cliente/tenant (multi-tenant SaaS).';
COMMENT ON COLUMN warehouses.code IS 'Código único do armazém dentro do tenant (ex: WH-001).';

-- ─── zones ───────────────────────────────────────────────────────────────────
CREATE TABLE zones (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL,
    warehouse_id UUID        NOT NULL,
    code         VARCHAR(50) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    type         VARCHAR(50) NOT NULL DEFAULT 'STORAGE',
    active       BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),

    CONSTRAINT pk_zones PRIMARY KEY (id),
    CONSTRAINT fk_zones_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT,
    CONSTRAINT uq_zones_tenant_warehouse_code UNIQUE (tenant_id, warehouse_id, code),
    CONSTRAINT chk_zones_type CHECK (type IN ('STORAGE', 'PICKING', 'RECEIVING_DOCK', 'SHIPPING_DOCK', 'QUARANTINE', 'DAMAGED'))
);

COMMENT ON TABLE zones IS 'Zonas funcionais dentro de um armazém (Armazenagem, Picking, Doca, Quarentena, etc).';
COMMENT ON COLUMN zones.type IS 'Tipo funcional: STORAGE, PICKING, RECEIVING_DOCK, SHIPPING_DOCK, QUARANTINE, DAMAGED.';

-- ─── aisles ──────────────────────────────────────────────────────────────────
CREATE TABLE aisles (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL,
    zone_id    UUID        NOT NULL,
    code       VARCHAR(50) NOT NULL,
    name       VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_aisles PRIMARY KEY (id),
    CONSTRAINT fk_aisles_zone FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE RESTRICT,
    CONSTRAINT uq_aisles_tenant_zone_code UNIQUE (tenant_id, zone_id, code)
);

COMMENT ON TABLE aisles IS 'Corredores dentro de uma zona de armazém.';

-- ─── shelves ─────────────────────────────────────────────────────────────────
CREATE TABLE shelves (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL,
    aisle_id   UUID        NOT NULL,
    code       VARCHAR(50) NOT NULL,
    level      INTEGER     NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_shelves PRIMARY KEY (id),
    CONSTRAINT fk_shelves_aisle FOREIGN KEY (aisle_id) REFERENCES aisles(id) ON DELETE RESTRICT,
    CONSTRAINT uq_shelves_tenant_aisle_code UNIQUE (tenant_id, aisle_id, code),
    CONSTRAINT chk_shelves_level_positive CHECK (level > 0)
);

COMMENT ON TABLE shelves IS 'Prateleiras/níveis dentro de um corredor. Level 1 = piso.';

-- ─── locations ───────────────────────────────────────────────────────────────
CREATE TABLE locations (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    shelf_id          UUID         NOT NULL,
    code              VARCHAR(100) NOT NULL,
    full_address      VARCHAR(255) NOT NULL,
    type              VARCHAR(50)  NOT NULL DEFAULT 'STORAGE',
    capacity_units    INTEGER,
    capacity_weight_kg DECIMAL(10,3),
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_locations PRIMARY KEY (id),
    CONSTRAINT fk_locations_shelf FOREIGN KEY (shelf_id) REFERENCES shelves(id) ON DELETE RESTRICT,
    CONSTRAINT uq_locations_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_locations_type CHECK (type IN ('STORAGE', 'PICKING', 'RECEIVING_DOCK', 'SHIPPING_DOCK', 'QUARANTINE', 'DAMAGED'))
);

COMMENT ON TABLE locations IS 'Posições individuais de armazenagem (leaf nodes da hierarquia). Ex: WH001-ZN01-A-01-001.';
COMMENT ON COLUMN locations.code IS 'Código único da posição dentro do tenant. Normalmente derivado da hierarquia.';
COMMENT ON COLUMN locations.full_address IS 'Endereço legível: ArmazémCódigo/Zona/Corredor/Prateleira/Posição.';

-- =============================================================================
-- PRODUTOS E RASTREABILIDADE
-- =============================================================================

-- ─── products_wms ────────────────────────────────────────────────────────────
CREATE TABLE products_wms (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    product_id         UUID         NOT NULL,
    sku                VARCHAR(100) NOT NULL,
    name               VARCHAR(255) NOT NULL,
    ean13              VARCHAR(13),
    gs1_128            VARCHAR(255),
    qr_code            TEXT,
    storage_type       VARCHAR(50)  NOT NULL DEFAULT 'DRY',
    controls_lot       BOOLEAN      NOT NULL DEFAULT false,
    controls_serial    BOOLEAN      NOT NULL DEFAULT false,
    controls_expiry    BOOLEAN      NOT NULL DEFAULT false,
    unit_width_cm      DECIMAL(10,2),
    unit_height_cm     DECIMAL(10,2),
    unit_depth_cm      DECIMAL(10,2),
    unit_weight_kg     DECIMAL(10,3),
    units_per_location INTEGER,
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_products_wms PRIMARY KEY (id),
    CONSTRAINT uq_products_wms_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT chk_products_wms_storage_type CHECK (storage_type IN ('DRY', 'REFRIGERATED', 'FROZEN', 'HAZARDOUS'))
);

COMMENT ON TABLE products_wms IS 'Produtos no contexto WMS com atributos de armazenagem, códigos GS1 e flags de rastreabilidade.';
COMMENT ON COLUMN products_wms.product_id IS 'Referência UUID ao cadastro master de produtos (microserviço de produtos — sem FK cross-service).';
COMMENT ON COLUMN products_wms.ean13 IS 'Código de barras EAN-13 (13 dígitos).';
COMMENT ON COLUMN products_wms.gs1_128 IS 'Código GS1-128 completo para etiquetas de logística.';
COMMENT ON COLUMN products_wms.controls_lot IS 'Se true, movimentações exigem informar lote.';
COMMENT ON COLUMN products_wms.controls_serial IS 'Se true, movimentações exigem informar número de série.';
COMMENT ON COLUMN products_wms.controls_expiry IS 'Se true, lotes devem ter data de validade.';

-- ─── lots ─────────────────────────────────────────────────────────────────────
CREATE TABLE lots (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    product_id          UUID         NOT NULL,
    lot_number          VARCHAR(100) NOT NULL,
    manufacturing_date  DATE,
    expiry_date         DATE,
    supplier_id         UUID,
    supplier_lot_number VARCHAR(100),
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_lots PRIMARY KEY (id),
    CONSTRAINT fk_lots_product FOREIGN KEY (product_id) REFERENCES products_wms(id) ON DELETE RESTRICT,
    CONSTRAINT uq_lots_tenant_product_lot UNIQUE (tenant_id, product_id, lot_number)
);

COMMENT ON TABLE lots IS 'Lotes de produtos. Suporta FEFO via expiry_date. Imutável após criação (sem updated_at por design operacional).';
COMMENT ON COLUMN lots.expiry_date IS 'Data de validade para algoritmo FEFO (First Expired, First Out).';
COMMENT ON COLUMN lots.supplier_id IS 'UUID do fornecedor — referência cross-service sem FK.';

-- ─── serial_numbers ───────────────────────────────────────────────────────────
CREATE TABLE serial_numbers (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    product_id    UUID         NOT NULL,
    lot_id        UUID,
    serial_number VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'IN_STOCK',
    location_id   UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_serial_numbers PRIMARY KEY (id),
    CONSTRAINT fk_serial_numbers_product FOREIGN KEY (product_id) REFERENCES products_wms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_serial_numbers_lot FOREIGN KEY (lot_id) REFERENCES lots(id) ON DELETE RESTRICT,
    CONSTRAINT fk_serial_numbers_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT uq_serial_numbers_tenant_product_serial UNIQUE (tenant_id, product_id, serial_number),
    CONSTRAINT chk_serial_numbers_status CHECK (status IN ('IN_STOCK', 'RESERVED', 'SHIPPED', 'RETURNED', 'DAMAGED'))
);

COMMENT ON TABLE serial_numbers IS 'Números de série individuais por produto. lot_id opcional (apenas para produtos que controlam lote E série).';

-- =============================================================================
-- CONTROLE DE ESTOQUE
-- =============================================================================

-- ─── stock_items ─────────────────────────────────────────────────────────────
CREATE TABLE stock_items (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL,
    location_id          UUID        NOT NULL,
    product_id           UUID        NOT NULL,
    lot_id               UUID,
    quantity_available   INTEGER     NOT NULL DEFAULT 0,
    quantity_reserved    INTEGER     NOT NULL DEFAULT 0,
    quantity_quarantine  INTEGER     NOT NULL DEFAULT 0,
    quantity_damaged     INTEGER     NOT NULL DEFAULT 0,
    received_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_stock_items PRIMARY KEY (id),
    CONSTRAINT fk_stock_items_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_items_product FOREIGN KEY (product_id) REFERENCES products_wms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_items_lot FOREIGN KEY (lot_id) REFERENCES lots(id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_items_location_product_lot UNIQUE (tenant_id, location_id, product_id, lot_id),
    CONSTRAINT chk_stock_items_quantities CHECK (
        quantity_available  >= 0 AND
        quantity_reserved   >= 0 AND
        quantity_quarantine >= 0 AND
        quantity_damaged    >= 0
    )
);

COMMENT ON TABLE stock_items IS 'Saldo de estoque por localização + produto + lote. received_at suporta FIFO. version suporta optimistic locking.';
COMMENT ON COLUMN stock_items.received_at IS 'Data/hora de entrada do item na posição — chave para algoritmo FIFO.';
COMMENT ON COLUMN stock_items.version IS 'Versão para optimistic locking (JPA @Version). Incrementado automaticamente em cada UPDATE.';
COMMENT ON COLUMN stock_items.lot_id IS 'NULL permitido para produtos que não controlam lote.';

-- ─── stock_movements ─────────────────────────────────────────────────────────
-- APPEND-ONLY: sem updated_at por design — registros imutáveis
CREATE TABLE stock_movements (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    type                    VARCHAR(50)  NOT NULL,
    product_id              UUID         NOT NULL,
    lot_id                  UUID,
    serial_number_id        UUID,
    source_location_id      UUID,
    destination_location_id UUID,
    quantity                INTEGER      NOT NULL,
    reference_type          VARCHAR(50),
    reference_id            UUID,
    reference_number        VARCHAR(100),
    operator_id             UUID         NOT NULL,
    operator_name           VARCHAR(255),
    reason                  TEXT,
    kafka_event_id          VARCHAR(255),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products_wms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_lot FOREIGN KEY (lot_id) REFERENCES lots(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_serial FOREIGN KEY (serial_number_id) REFERENCES serial_numbers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_source FOREIGN KEY (source_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_destination FOREIGN KEY (destination_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT chk_stock_movements_type CHECK (type IN (
        'INBOUND', 'PUTAWAY', 'TRANSFER', 'PICKING', 'PACKING',
        'SHIPPING', 'ADJUSTMENT', 'INVENTORY', 'RETURN',
        'QUARANTINE_IN', 'QUARANTINE_OUT'
    )),
    CONSTRAINT chk_stock_movements_reference_type CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER', 'SALES_ORDER', 'PRODUCTION_ORDER',
        'TRANSFER_ORDER', 'MANUAL', 'INVENTORY_COUNT'
    )),
    CONSTRAINT chk_stock_movements_quantity_nonzero CHECK (quantity != 0)
);

COMMENT ON TABLE stock_movements IS 'Registro imutável (append-only) de toda movimentação de estoque. SEM updated_at por design.';
COMMENT ON COLUMN stock_movements.kafka_event_id IS 'ID do evento Kafka que originou este movimento (idempotência).';
COMMENT ON COLUMN stock_movements.operator_id IS 'UUID do usuário Keycloak que executou a operação — sem FK cross-service.';

-- ─── audit_log ───────────────────────────────────────────────────────────────
-- APPEND-ONLY: log imutável de todas as ações do sistema — sem FK de outras tabelas
CREATE TABLE audit_log (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    entity_type    VARCHAR(100) NOT NULL,
    entity_id      UUID         NOT NULL,
    action         VARCHAR(50)  NOT NULL,
    actor_id       UUID         NOT NULL,
    actor_name     VARCHAR(255),
    actor_role     VARCHAR(100),
    old_value      JSONB,
    new_value      JSONB,
    ip_address     INET,
    user_agent     TEXT,
    correlation_id VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT chk_audit_log_action CHECK (action IN (
        'CREATE', 'UPDATE', 'DELETE', 'MOVEMENT', 'ACCESS', 'LOGIN', 'LOGOUT'
    ))
);

COMMENT ON TABLE audit_log IS 'Log imutável (append-only) de todas as ações do sistema. SEM FKs de outras tabelas e SEM updated_at.';
COMMENT ON COLUMN audit_log.entity_type IS 'Nome da entidade afetada (ex: Warehouse, StockItem, Location).';
COMMENT ON COLUMN audit_log.old_value IS 'Estado anterior em JSON (null para CREATE).';
COMMENT ON COLUMN audit_log.new_value IS 'Estado novo em JSON (null para DELETE).';
COMMENT ON COLUMN audit_log.ip_address IS 'Endereço IP do cliente (tipo INET do PostgreSQL).';

-- =============================================================================
-- ÍNDICES DE PERFORMANCE
-- =============================================================================

-- warehouses
CREATE INDEX idx_warehouses_tenant_id ON warehouses (tenant_id);

-- zones
CREATE INDEX idx_zones_tenant_id ON zones (tenant_id);
CREATE INDEX idx_zones_warehouse_id ON zones (warehouse_id);

-- aisles
CREATE INDEX idx_aisles_tenant_id ON aisles (tenant_id);
CREATE INDEX idx_aisles_zone_id ON aisles (zone_id);

-- shelves
CREATE INDEX idx_shelves_tenant_id ON shelves (tenant_id);
CREATE INDEX idx_shelves_aisle_id ON shelves (aisle_id);

-- locations
CREATE INDEX idx_locations_tenant_id ON locations (tenant_id);
CREATE INDEX idx_locations_shelf_id ON locations (shelf_id);
CREATE INDEX idx_locations_tenant_code ON locations (tenant_id, code);
CREATE INDEX idx_locations_tenant_type ON locations (tenant_id, type);

-- products_wms
CREATE INDEX idx_products_wms_tenant_id ON products_wms (tenant_id);
CREATE INDEX idx_products_wms_tenant_sku ON products_wms (tenant_id, sku);
CREATE INDEX idx_products_wms_ean13 ON products_wms (tenant_id, ean13) WHERE ean13 IS NOT NULL;

-- lots — expiry_date index for FEFO queries
CREATE INDEX idx_lots_tenant_product ON lots (tenant_id, product_id);
CREATE INDEX idx_lots_expiry_date ON lots (expiry_date) WHERE expiry_date IS NOT NULL;

-- serial_numbers
CREATE INDEX idx_serial_numbers_tenant_product ON serial_numbers (tenant_id, product_id);
CREATE INDEX idx_serial_numbers_location_id ON serial_numbers (location_id) WHERE location_id IS NOT NULL;
CREATE INDEX idx_serial_numbers_status ON serial_numbers (tenant_id, status);

-- stock_items — received_at index for FIFO queries
CREATE INDEX idx_stock_items_tenant_product ON stock_items (tenant_id, product_id);
CREATE INDEX idx_stock_items_location_id ON stock_items (location_id);
CREATE INDEX idx_stock_items_received_at ON stock_items (tenant_id, product_id, received_at ASC);

-- stock_movements — append-only, heavy on reads by tenant/product/reference
CREATE INDEX idx_stock_movements_tenant_id ON stock_movements (tenant_id);
CREATE INDEX idx_stock_movements_tenant_product ON stock_movements (tenant_id, product_id);
CREATE INDEX idx_stock_movements_reference ON stock_movements (tenant_id, reference_id) WHERE reference_id IS NOT NULL;
CREATE INDEX idx_stock_movements_kafka_event ON stock_movements (kafka_event_id) WHERE kafka_event_id IS NOT NULL;
CREATE INDEX idx_stock_movements_created_at ON stock_movements (tenant_id, created_at DESC);

-- audit_log — append-only, heavy on reads by tenant/entity/actor
CREATE INDEX idx_audit_log_tenant_entity ON audit_log (tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_log_actor ON audit_log (tenant_id, actor_id);
CREATE INDEX idx_audit_log_correlation ON audit_log (correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_audit_log_created_at ON audit_log (tenant_id, created_at DESC);

-- =============================================================================
-- ROLLBACK (para referência — nunca executar em produção sem validação)
-- =============================================================================
-- DROP TABLE IF EXISTS audit_log CASCADE;
-- DROP TABLE IF EXISTS stock_movements CASCADE;
-- DROP TABLE IF EXISTS stock_items CASCADE;
-- DROP TABLE IF EXISTS serial_numbers CASCADE;
-- DROP TABLE IF EXISTS lots CASCADE;
-- DROP TABLE IF EXISTS products_wms CASCADE;
-- DROP TABLE IF EXISTS locations CASCADE;
-- DROP TABLE IF EXISTS shelves CASCADE;
-- DROP TABLE IF EXISTS aisles CASCADE;
-- DROP TABLE IF EXISTS zones CASCADE;
-- DROP TABLE IF EXISTS warehouses CASCADE;
-- DROP EXTENSION IF EXISTS pgcrypto;
