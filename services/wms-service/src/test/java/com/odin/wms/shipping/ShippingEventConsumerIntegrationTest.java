package com.odin.wms.shipping;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PackingOrder.PackingStatus;
import com.odin.wms.domain.entity.PickingItem.PickingItemStatus;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.ShippingCompletedEventPublisher;
import com.odin.wms.messaging.event.PackingCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para ShippingEventConsumer — I13–I14.
 * Usa Embedded Kafka para simular o tópico wms.packing.completed.
 */
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"wms.packing.completed"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=wms-shipping-consumer-test",
        "wms.kafka.topics.packing-completed=wms.packing.completed"
})
@DirtiesContext
class ShippingEventConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, PackingCompletedEvent> kafkaTemplate;

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
    @Autowired private ShippingOrderRepository shippingOrderRepository;
    @Autowired private ShippingItemRepository shippingItemRepository;

    @MockBean private ShippingCompletedEventPublisher shippingCompletedEventPublisher;
    @MockBean private AuditLogIndexer auditLogIndexer;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;
    private UUID locationId;
    private UUID pickingOrderId;
    private UUID pickingItemId;
    private UUID packingOrderId;
    private UUID packingItemId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-SC-" + tenantId).name("WH Shipping Consumer Test").build());
        warehouseId = wh.getId();

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("Z-SC-" + tenantId)
                .name("Zona Shipping Consumer").type(LocationType.STORAGE).build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-01").build());

        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-01").level(1).build());

        Location location = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-SC-" + tenantId)
                .fullAddress("WH-SC/Z/A-01/S-01/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());
        locationId = location.getId();

        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-SC-" + tenantId).name("Produto Shipping Consumer Test")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());
        productId = product.getId();

        PickingOrder pickingOrder = pickingOrderRepository.save(PickingOrder.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .crmOrderId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
                .status(PickingStatus.COMPLETED)
                .build());
        pickingOrderId = pickingOrder.getId();

        PickingItem pickingItem = pickingItemRepository.save(PickingItem.builder()
                .tenantId(tenantId)
                .pickingOrderId(pickingOrderId)
                .productId(productId)
                .locationId(locationId)
                .quantityRequested(2)
                .quantityPicked(2)
                .status(PickingItemStatus.PICKED)
                .build());
        pickingItemId = pickingItem.getId();

        PackingOrder packingOrder = packingOrderRepository.save(PackingOrder.builder()
                .tenantId(tenantId)
                .pickingOrderId(pickingOrderId)
                .warehouseId(warehouseId)
                .status(PackingStatus.COMPLETED)
                .operatorId(UUID.randomUUID())
                .build());
        packingOrderId = packingOrder.getId();

        PackingItem packingItem = packingItemRepository.save(PackingItem.builder()
                .tenantId(tenantId)
                .packingOrderId(packingOrderId)
                .pickingItemId(pickingItemId)
                .productId(productId)
                .quantityPacked(2)
                .scanned(true)
                .build());
        packingItemId = packingItem.getId();
    }

    // -------------------------------------------------------------------------
    // I13 — shippingConsumer_packingCompleted_createsPendingShippingOrder
    // -------------------------------------------------------------------------

    @Test
    void shippingConsumer_packingCompleted_createsPendingShippingOrder() throws Exception {
        PackingCompletedEvent event = buildPackingCompletedEvent();

        kafkaTemplate.send("wms.packing.completed", tenantId.toString(), event);

        // Aguarda o consumer processar (até 8s)
        Thread.sleep(8000);

        var orderOpt = shippingOrderRepository.findByTenantIdAndPackingOrderId(tenantId, packingOrderId);
        assertThat(orderOpt).isPresent();
        assertThat(orderOpt.get().getStatus().name()).isEqualTo("PENDING");
        assertThat(orderOpt.get().getWarehouseId()).isEqualTo(warehouseId);

        // Verifica que os ShippingItems foram criados com o packingItemId correto
        UUID shippingOrderId = orderOpt.get().getId();
        var shippingItems = shippingItemRepository.findByTenantIdAndShippingOrderId(tenantId, shippingOrderId);
        assertThat(shippingItems).hasSize(1);
        assertThat(shippingItems.get(0).getPackingItemId()).isEqualTo(packingItemId);
    }

    // -------------------------------------------------------------------------
    // I14 — shippingConsumer_duplicatePackingOrderId_isIdempotent
    // -------------------------------------------------------------------------

    @Test
    void shippingConsumer_duplicatePackingOrderId_isIdempotent() throws Exception {
        PackingCompletedEvent event = buildPackingCompletedEvent();

        // Envia o mesmo evento duas vezes (simula duplicata por retry Kafka)
        kafkaTemplate.send("wms.packing.completed", tenantId.toString(), event);
        kafkaTemplate.send("wms.packing.completed", tenantId.toString(), event);

        Thread.sleep(8000);

        // Deve existir apenas 1 ShippingOrder com esse packingOrderId (idempotência)
        var orderOpt = shippingOrderRepository.findByTenantIdAndPackingOrderId(tenantId, packingOrderId);
        assertThat(orderOpt).isPresent();

        long count = shippingOrderRepository.findByTenantId(
                        tenantId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(o -> packingOrderId.equals(o.getPackingOrderId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PackingCompletedEvent buildPackingCompletedEvent() {
        List<PackingCompletedEvent.PackingCompletedItem> items = List.of(
                new PackingCompletedEvent.PackingCompletedItem(productId, null, 2)
        );
        return new PackingCompletedEvent(
                "PACKING_ORDER_COMPLETED",
                tenantId,
                packingOrderId,
                pickingOrderId,
                UUID.randomUUID(),       // crmOrderId
                warehouseId,
                UUID.randomUUID(),       // operatorId
                "SSCC-SC-TEST-001",
                BigDecimal.valueOf(5.0),
                "BOX",
                "COMPLETED",
                items,
                Instant.now()
        );
    }
}
