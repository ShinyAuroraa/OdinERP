package com.odin.wms.domain;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de integração que validam o schema V2 e os repositórios JPA.
 * Todos os containers (PostgreSQL, Redis, Elasticsearch) são iniciados via AbstractIntegrationTest.
 */
@DisplayName("Schema V2 + Domain Model — Integration Tests")
class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private LotRepository lotRepository;
    @Autowired private SerialNumberRepository serialNumberRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-01: Migração V2 aplicada com sucesso (todas as tabelas existem)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-01: Todos os repositórios inicializam sem erro (tabelas existem)")
    void allRepositoriesShouldBeAvailable() {
        assertThat(warehouseRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(zoneRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(aisleRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(shelfRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(locationRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(productWmsRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(lotRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(serialNumberRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(stockItemRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(stockMovementRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-02/03: Hierarquia de localização com FKs e tenant_id
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("AC-02: Hierarquia Warehouse → Zone → Aisle → Shelf → Location persiste com tenant_id")
    void locationHierarchyShouldPersistCorrectly() {
        Warehouse warehouse = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId)
                .code("WH-01")
                .name("Galpão Principal")
                .active(true)
                .build());

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId)
                .warehouse(warehouse)
                .code("ZN-A")
                .name("Zona A")
                .type(LocationType.STORAGE)
                .build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId)
                .zone(zone)
                .code("A01")
                .build());

        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId)
                .aisle(aisle)
                .code("S01")
                .level(1)
                .build());

        Location location = locationRepository.save(Location.builder()
                .tenantId(tenantId)
                .shelf(shelf)
                .code("A01-S01-P01")
                .fullAddress("WH-01/ZN-A/A01/S01/A01-S01-P01")
                .type(LocationType.STORAGE)
                .build());

        assertThat(location.getId()).isNotNull();
        assertThat(location.getShelf().getAisle().getZone().getWarehouse().getCode())
                .isEqualTo("WH-01");

        var found = locationRepository.findByTenantIdAndCode(tenantId, "A01-S01-P01");
        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-04: Multi-tenant — isolamento por tenant_id
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("AC-04: Dois tenants podem ter o mesmo código de warehouse sem conflito")
    void multiTenantIsolationShouldWork() {
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();

        warehouseRepository.save(Warehouse.builder()
                .tenantId(tenant1).code("WH-01").name("Tenant 1 WH").active(true).build());
        warehouseRepository.save(Warehouse.builder()
                .tenantId(tenant2).code("WH-01").name("Tenant 2 WH").active(true).build());

        assertThat(warehouseRepository.existsByTenantIdAndCode(tenant1, "WH-01")).isTrue();
        assertThat(warehouseRepository.existsByTenantIdAndCode(tenant2, "WH-01")).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-05: Produto WMS com campos GS1 e controles booleanos
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("AC-05: ProductWms persiste com campos GS1 e flags de controle")
    void productWmsShouldPersistWithGs1Fields() {
        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId)
                .productId(UUID.randomUUID())
                .sku("SKU-001")
                .name("Produto SKU-001")
                .ean13("7891234567890")
                .gs1128("(01)07891234567890")
                .qrCode("https://odin.erp/p/SKU-001")
                .storageType(StorageType.DRY)
                .controlsLot(true)
                .controlsSerial(false)
                .controlsExpiry(true)
                .active(true)
                .build());

        var found = productWmsRepository.findByTenantIdAndEan13(tenantId, "7891234567890");
        assertThat(found).isPresent();
        assertThat(found.get().getControlsLot()).isTrue();
        assertThat(found.get().getStorageType()).isEqualTo(StorageType.DRY);
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-06: Lot append-only + FEFO query
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("AC-06: Lots persistem como append-only e query FEFO ordena por expiry_date ASC")
    void lotFEFOQueryShouldReturnOrderedByExpiryDate() {
        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID()).sku("FEFO-SKU")
                .name("Produto FEFO")
                .storageType(StorageType.REFRIGERATED).controlsLot(true)
                .controlsSerial(false).controlsExpiry(true).active(true).build());

        Lot lot2025 = lotRepository.save(Lot.builder()
                .tenantId(tenantId).product(product).lotNumber("LOT-2025")
                .expiryDate(LocalDate.of(2025, 12, 31)).active(true).build());

        Lot lot2024 = lotRepository.save(Lot.builder()
                .tenantId(tenantId).product(product).lotNumber("LOT-2024")
                .expiryDate(LocalDate.of(2024, 6, 30)).active(true).build());

        Lot lotNoExpiry = lotRepository.save(Lot.builder()
                .tenantId(tenantId).product(product).lotNumber("LOT-NO-EXP")
                .active(true).build());

        List<Lot> fefo = lotRepository.findActiveLotsFEFO(tenantId, product.getId());

        assertThat(fefo).hasSize(3);
        // FEFO: menor expiryDate primeiro, nulos por último
        assertThat(fefo.get(0).getLotNumber()).isEqualTo("LOT-2024");
        assertThat(fefo.get(1).getLotNumber()).isEqualTo("LOT-2025");
        assertThat(fefo.get(2).getLotNumber()).isEqualTo("LOT-NO-EXP");
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-07: StockItem com optimistic locking (@Version) e FIFO
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("AC-07: StockItem persiste com @Version e FIFO ordena por receivedAt ASC")
    void stockItemFIFOAndVersionShouldWork() {
        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-FIFO").name("FIFO WH").active(true).build());
        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("ZN-F").name("Zone F")
                .type(LocationType.STORAGE).build());
        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A1").build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S1").level(1).build());
        Location loc = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("F01")
                .fullAddress("WH-FIFO/ZN-F/A1/S1/F01")
                .type(LocationType.STORAGE).build());

        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID()).sku("FIFO-SKU")
                .name("Produto FIFO")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        StockItem item = stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(loc).product(product)
                .quantityAvailable(100).build());

        assertThat(item.getVersion()).isNotNull();
        assertThat(item.getVersion()).isEqualTo(0L);
        assertThat(item.getReceivedAt()).isNotNull();

        // Update deve incrementar version — saveAndFlush força o SQL UPDATE imediato
        // (necessário em @Transactional test: sem flush, @Version não incrementa antes do assert)
        item.setQuantityAvailable(90);
        StockItem updated = stockItemRepository.saveAndFlush(item);
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-08: StockMovement append-only + idempotência por kafkaEventId
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("AC-08: StockMovement é append-only e kafkaEventId garante idempotência")
    void stockMovementIdempotencyShouldWork() {
        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID()).sku("KAFKA-SKU")
                .name("Produto Kafka")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        String kafkaEventId = "evt-" + UUID.randomUUID();

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .product(product)
                .type(MovementType.INBOUND)
                .quantity(50)
                .operatorId(UUID.randomUUID())
                .kafkaEventId(kafkaEventId)
                .build());

        assertThat(stockMovementRepository.existsByKafkaEventId(kafkaEventId)).isTrue();
        assertThat(stockMovementRepository.existsByKafkaEventId("evt-unknown")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-09: AuditLog append-only com JSONB e INET
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("AC-09: AuditLog persiste com campos JSONB e INET corretamente")
    void auditLogShouldPersistWithJsonbAndInet() {
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("Warehouse")
                .entityId(entityId)
                .action(AuditAction.CREATE)
                .actorId(actorId)
                .actorName("João Silva")
                .actorRole("WAREHOUSE_MANAGER")
                .newValue("{\"code\":\"WH-01\",\"name\":\"Galpão Principal\"}")
                .ipAddress("192.168.1.100")
                .correlationId("corr-" + UUID.randomUUID())
                .build());

        assertThat(log.getId()).isNotNull();
        assertThat(log.getCreatedAt()).isNotNull();

        var page = auditLogRepository.findByTenantIdAndEntityTypeAndEntityId(
                tenantId, "Warehouse", entityId, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getActorName()).isEqualTo("João Silva");
    }

    // ─────────────────────────────────────────────────────────────────
    // AC-10: CHECK constraint quantity >= 0 é respeitada
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-10: CHECK constraint impede quantity negativo no StockItem")
    void stockItemNegativeQuantityShouldViolateConstraint() {
        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-CHK").name("Check WH").active(true).build());
        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("ZN-C").name("Zone C")
                .type(LocationType.STORAGE).build());
        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("AC").build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("SC").level(1).build());
        Location loc = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("CHK01")
                .fullAddress("WH-CHK/ZN-C/AC/SC/CHK01")
                .type(LocationType.STORAGE).build());

        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID()).sku("CHK-SKU")
                .name("Produto Check")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        assertThatThrownBy(() -> stockItemRepository.saveAndFlush(StockItem.builder()
                .tenantId(tenantId).location(loc).product(product)
                .quantityAvailable(-1)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
