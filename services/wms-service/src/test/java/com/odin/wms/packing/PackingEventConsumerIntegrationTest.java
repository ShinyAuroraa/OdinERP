package com.odin.wms.packing;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.PackingCompletedEventPublisher;
import com.odin.wms.messaging.event.PickingCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para PackingEventConsumer — I13–I14.
 * Usa Embedded Kafka para simular o tópico wms.picking.completed.
 */
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"wms.picking.completed"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=wms-packing-consumer-test",
        "wms.kafka.topics.picking-completed=wms.picking.completed"
})
@DirtiesContext
class PackingEventConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, PickingCompletedEvent> kafkaTemplate;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private PickingOrderRepository pickingOrderRepository;
    @Autowired private PickingItemRepository pickingItemRepository;
    @Autowired private PackingOrderRepository packingOrderRepository;
    @Autowired private PackingItemRepository packingItemRepository;

    @MockBean private PackingCompletedEventPublisher packingCompletedEventPublisher;
    @MockBean private AuditLogIndexer auditLogIndexer;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;
    private UUID locationId;
    private UUID pickingOrderId;
    private UUID pickingItemId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-PKG-" + tenantId).name("WH Packing Test").build());
        warehouseId = wh.getId();

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("Z-PKG-" + tenantId)
                .name("Zona Packing").type(LocationType.STORAGE).build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-01").build());

        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-01").level(1).build());

        Location location = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-PKG-" + tenantId)
                .fullAddress("WH-PKG/Z/A-01/S-01/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());
        locationId = location.getId();

        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-PKG-" + tenantId).name("Produto Packing Test")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());
        productId = product.getId();

        // PickingOrder — FK: packing_orders.picking_order_id → picking_orders.id
        PickingOrder pickingOrder = pickingOrderRepository.save(PickingOrder.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .crmOrderId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
                .status(PickingOrder.PickingStatus.COMPLETED)
                .build());
        pickingOrderId = pickingOrder.getId();

        // PickingItem — FK: packing_items.picking_item_id → picking_items.id
        PickingItem pickingItem = pickingItemRepository.save(PickingItem.builder()
                .tenantId(tenantId)
                .pickingOrderId(pickingOrderId)
                .productId(productId)
                .locationId(locationId)
                .quantityRequested(2)
                .quantityPicked(2)
                .status(PickingItem.PickingItemStatus.PICKED)
                .build());
        pickingItemId = pickingItem.getId();
    }

    // -------------------------------------------------------------------------
    // I13 — packingConsumer_pickingCompleted_createsPendingPackingOrder
    // -------------------------------------------------------------------------

    @Test
    void packingConsumer_pickingCompleted_createsPendingPackingOrder() throws Exception {
        PickingCompletedEvent event = buildPickingCompletedEvent();

        kafkaTemplate.send("wms.picking.completed", tenantId.toString(), event);

        // Aguarda o consumer processar (até 8s)
        Thread.sleep(8000);

        var orderOpt = packingOrderRepository.findByTenantIdAndPickingOrderId(tenantId, pickingOrderId);
        assertThat(orderOpt).isPresent();
        assertThat(orderOpt.get().getStatus().name()).isEqualTo("PENDING");
        assertThat(orderOpt.get().getWarehouseId()).isEqualTo(warehouseId);

        // Verifica que os PackingItems foram criados com o pickingItemId correto
        UUID packingOrderDbId = orderOpt.get().getId();
        var packingItems = packingItemRepository.findByTenantIdAndPackingOrderId(tenantId, packingOrderDbId);
        assertThat(packingItems).hasSize(1);
        assertThat(packingItems.get(0).getPickingItemId()).isEqualTo(pickingItemId);
    }

    // -------------------------------------------------------------------------
    // I14 — packingConsumer_duplicatePickingOrderId_isIdempotent
    // -------------------------------------------------------------------------

    @Test
    void packingConsumer_duplicatePickingOrderId_isIdempotent() throws Exception {
        PickingCompletedEvent event = buildPickingCompletedEvent();

        // Envia o mesmo evento duas vezes (simula duplicata por retry Kafka)
        kafkaTemplate.send("wms.picking.completed", tenantId.toString(), event);
        kafkaTemplate.send("wms.picking.completed", tenantId.toString(), event);

        Thread.sleep(8000);

        // Deve existir apenas 1 PackingOrder com esse pickingOrderId (idempotência)
        var orderOpt = packingOrderRepository.findByTenantIdAndPickingOrderId(tenantId, pickingOrderId);
        assertThat(orderOpt).isPresent();

        long count = packingOrderRepository.findByTenantId(tenantId,
                org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(o -> pickingOrderId.equals(o.getPickingOrderId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PickingCompletedEvent buildPickingCompletedEvent() {
        List<PickingCompletedEvent.PickingCompletedItem> items = List.of(
                new PickingCompletedEvent.PickingCompletedItem(
                        pickingItemId, productId, null, locationId, 2, 2
                )
        );
        return new PickingCompletedEvent(
                "PICKING_ORDER_COMPLETED",
                tenantId,
                pickingOrderId,
                UUID.randomUUID(), // crmOrderId
                "COMPLETED",
                warehouseId,
                UUID.randomUUID(), // operatorId
                items,
                Instant.now()
        );
    }
}
