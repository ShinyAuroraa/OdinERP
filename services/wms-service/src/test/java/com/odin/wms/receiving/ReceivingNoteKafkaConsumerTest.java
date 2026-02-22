package com.odin.wms.receiving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.ReceivingStatus;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.ReceivingNoteRepository;
import com.odin.wms.dto.request.*;
import com.odin.wms.infrastructure.kafka.event.PurchaseOrderConfirmedEvent;
import com.odin.wms.infrastructure.kafka.event.PurchaseOrderItem;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"scm.purchase-orders.confirmed", "wms.receiving.dlq"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=wms-receiving-test"
})
@DirtiesContext
class ReceivingNoteKafkaConsumerTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, PurchaseOrderConfirmedEvent> kafkaTemplate;

    @Autowired
    private ReceivingNoteRepository receivingNoteRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private DlqCapture dlqCapture;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    private MockHttpServletRequestBuilder withAdminJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", TEST_TENANT_ID.toString()).claim("sub", "admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }

    private UUID createDockAndReturnWarehouseId(String suffix) throws Exception {
        String whJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest("WH-KF-" + suffix, "KF WH " + suffix, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID warehouseId = UUID.fromString(objectMapper.readTree(whJson).get("id").asText());

        String znJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateZoneRequest("ZN-KF-" + suffix, "KF ZN", LocationType.STORAGE)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID zoneId = UUID.fromString(objectMapper.readTree(znJson).get("id").asText());

        String aiJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/zones/{id}/aisles", zoneId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAisleRequest("A-KF-" + suffix, "KF Aisle")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID aisleId = UUID.fromString(objectMapper.readTree(aiJson).get("id").asText());

        String shJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aisleId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShelfRequest("S-KF-" + suffix, 1)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID shelfId = UUID.fromString(objectMapper.readTree(shJson).get("id").asText());

        mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("DOCK-KF-" + suffix, LocationType.RECEIVING_DOCK, null, null)))))
                .andExpect(status().isCreated());

        return warehouseId;
    }

    private UUID createProduct(String sku) throws Exception {
        String json = mockMvc.perform(withAdminJwt(
                post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProductWmsRequest(
                                        UUID.randomUUID(), sku, "KF " + sku, StorageType.DRY,
                                        false, false, false,
                                        null, null, null, null, null, null, null, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    @Test
    void consumeEvent_validEvent_createsReceivingNote() throws Exception {
        UUID warehouseId = createDockAndReturnWarehouseId("EV01");
        UUID productId = createProduct("SKU-KF-EV01");
        String poRef = "PO-KF-EV01-" + UUID.randomUUID();

        PurchaseOrderConfirmedEvent event = new PurchaseOrderConfirmedEvent(
                poRef, TEST_TENANT_ID, warehouseId, null,
                List.of(new PurchaseOrderItem(productId, 5)));

        kafkaTemplate.send("scm.purchase-orders.confirmed", poRef, event);

        // Aguarda o consumer processar (até 10s)
        Thread.sleep(5000);

        long count = receivingNoteRepository
                .existsByTenantIdAndPurchaseOrderRef(TEST_TENANT_ID, poRef) ? 1 : 0;
        assertThat(count).isEqualTo(1);

        var notes = receivingNoteRepository.findByTenantIdAndStatus(TEST_TENANT_ID, ReceivingStatus.PENDING);
        assertThat(notes.stream().anyMatch(n -> poRef.equals(n.getPurchaseOrderRef()))).isTrue();
    }

    @Test
    void consumeEvent_duplicatePurchaseOrderRef_ignoresEvent() throws Exception {
        UUID warehouseId = createDockAndReturnWarehouseId("EV02");
        UUID productId = createProduct("SKU-KF-EV02");
        String poRef = "PO-KF-EV02-" + UUID.randomUUID();

        PurchaseOrderConfirmedEvent event = new PurchaseOrderConfirmedEvent(
                poRef, TEST_TENANT_ID, warehouseId, null,
                List.of(new PurchaseOrderItem(productId, 3)));

        // Envia duas vezes o mesmo PO
        kafkaTemplate.send("scm.purchase-orders.confirmed", poRef, event);
        kafkaTemplate.send("scm.purchase-orders.confirmed", poRef, event);

        Thread.sleep(6000);

        // Deve existir apenas 1 nota (idempotência)
        long count = receivingNoteRepository.findByTenantId(TEST_TENANT_ID).stream()
                .filter(n -> poRef.equals(n.getPurchaseOrderRef()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void consumeEvent_unknownWarehouseId_sendsToDeadLetterTopic() throws Exception {
        UUID unknownWarehouse = UUID.randomUUID();
        UUID productId = createProduct("SKU-KF-DLQ01");
        String poRef = "PO-KF-DLQ01-" + UUID.randomUUID();

        PurchaseOrderConfirmedEvent event = new PurchaseOrderConfirmedEvent(
                poRef, TEST_TENANT_ID, unknownWarehouse, null,
                List.of(new PurchaseOrderItem(productId, 2)));

        kafkaTemplate.send("scm.purchase-orders.confirmed", poRef, event);

        Thread.sleep(6000);

        // Nota NÃO deve ter sido criada (sem dock disponível)
        assertThat(receivingNoteRepository.existsByTenantIdAndPurchaseOrderRef(TEST_TENANT_ID, poRef)).isFalse();
    }

    /**
     * Bean para capturar mensagens do DLQ nos testes.
     * Opcional — usado apenas para assertions de DLQ se necessário.
     */
    @Component
    static class DlqCapture {
        final BlockingQueue<ConsumerRecord<String, String>> queue = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "wms.receiving.dlq", groupId = "wms-receiving-test-dlq")
        public void capture(ConsumerRecord<String, String> record) {
            queue.offer(record);
        }

        public ConsumerRecord<String, String> poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }
}
