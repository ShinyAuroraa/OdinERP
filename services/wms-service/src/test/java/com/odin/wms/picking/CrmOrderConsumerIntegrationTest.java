package com.odin.wms.picking;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.messaging.event.CrmOrderConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para CrmOrderEventConsumer — I15–I16.
 * Usa Embedded Kafka para simular o tópico crm.orders.confirmed.
 */
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"crm.orders.confirmed"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=wms-crm-consumer-test",
        "wms.kafka.topics.crm-orders-confirmed=crm.orders.confirmed"
})
@DirtiesContext
class CrmOrderConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, CrmOrderConfirmedEvent> kafkaTemplate;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private PickingOrderRepository pickingOrderRepository;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-CRM-" + tenantId).name("WH CRM Test").build());
        warehouseId = wh.getId();

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("Z-CRM-" + tenantId)
                .name("Zona CRM").type(LocationType.STORAGE).build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-01").build());

        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-01").level(1).build());

        Location location = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-CRM-" + tenantId)
                .fullAddress("WH-CRM/Z/A-01/S-01/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());

        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-CRM-" + tenantId).name("Produto CRM Test")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());
        productId = product.getId();

        stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(location).product(product)
                .quantityAvailable(100).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0)
                .receivedAt(Instant.now()).build());
    }

    // -------------------------------------------------------------------------
    // I15 — crmConsumer_newSalesOrder_createsPendingPickingOrder
    // -------------------------------------------------------------------------

    @Test
    void crmConsumer_newSalesOrder_createsPendingPickingOrder() throws Exception {
        UUID crmOrderId = UUID.randomUUID();

        CrmOrderConfirmedEvent event = new CrmOrderConfirmedEvent(
                "SALES_ORDER_CONFIRMED",
                tenantId,
                crmOrderId,
                warehouseId,
                1,
                List.of(new CrmOrderConfirmedEvent.CrmOrderItem(productId, 5))
        );

        kafkaTemplate.send("crm.orders.confirmed", tenantId.toString(), event);

        // Aguarda o consumer processar (até 10s)
        Thread.sleep(8000);

        var orderOpt = pickingOrderRepository.findByTenantIdAndCrmOrderId(tenantId, crmOrderId);
        assertThat(orderOpt).isPresent();
        assertThat(orderOpt.get().getWarehouseId()).isEqualTo(warehouseId);
        assertThat(orderOpt.get().getStatus().name()).isEqualTo("PENDING");
    }

    // -------------------------------------------------------------------------
    // I16 — crmConsumer_duplicateCrmOrderId_isIdempotent
    // -------------------------------------------------------------------------

    @Test
    void crmConsumer_duplicateCrmOrderId_isIdempotent() throws Exception {
        UUID crmOrderId = UUID.randomUUID();

        CrmOrderConfirmedEvent event = new CrmOrderConfirmedEvent(
                "SALES_ORDER_CONFIRMED",
                tenantId,
                crmOrderId,
                warehouseId,
                1,
                List.of(new CrmOrderConfirmedEvent.CrmOrderItem(productId, 3))
        );

        // Envia o mesmo evento duas vezes (simula duplicata por retry Kafka)
        kafkaTemplate.send("crm.orders.confirmed", tenantId.toString(), event);
        kafkaTemplate.send("crm.orders.confirmed", tenantId.toString(), event);

        Thread.sleep(8000);

        // Deve existir apenas 1 PickingOrder com esse crmOrderId (idempotência)
        long count = pickingOrderRepository.findByTenantId(tenantId, Pageable.unpaged()).stream()
                .filter(o -> crmOrderId.equals(o.getCrmOrderId()))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
