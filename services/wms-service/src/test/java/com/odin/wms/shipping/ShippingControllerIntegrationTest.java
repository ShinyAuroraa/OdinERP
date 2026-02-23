package com.odin.wms.shipping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PackingOrder.PackingStatus;
import com.odin.wms.domain.entity.PickingItem.PickingItemStatus;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.entity.ShippingOrder.ShippingStatus;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.ShippingCompletedEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para ShippingController — I1–I12.
 */
@AutoConfigureMockMvc
class ShippingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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
    private UUID locationId;
    private ProductWms product;
    private UUID pickingOrderId;
    private UUID pickingItemId;
    private UUID packingOrderId;
    private UUID packingItemId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-SHIP-" + tenantId).name("WH Shipping Test").build());
        warehouseId = wh.getId();

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("Z-SHIP-" + tenantId)
                .name("Zona Shipping").type(LocationType.STORAGE).build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-01").build());

        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-01").level(1).build());

        Location location = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-SHIP-" + tenantId)
                .fullAddress("WH-SHIP/Z/A-01/S-01/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());
        locationId = location.getId();

        product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-SHIP-" + tenantId).name("Produto Shipping Test")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        // PickingOrder + PickingItem (chain FKs)
        PickingOrder pickingOrder = pickingOrderRepository.save(PickingOrder.builder()
                .tenantId(tenantId).warehouseId(warehouseId)
                .createdBy(UUID.randomUUID()).status(PickingStatus.COMPLETED).build());
        pickingOrderId = pickingOrder.getId();

        PickingItem pickingItem = pickingItemRepository.save(PickingItem.builder()
                .tenantId(tenantId).pickingOrderId(pickingOrderId)
                .productId(product.getId()).locationId(locationId)
                .quantityRequested(3).quantityPicked(3).status(PickingItemStatus.PICKED).build());
        pickingItemId = pickingItem.getId();

        // PackingOrder + PackingItem
        PackingOrder packingOrder = packingOrderRepository.save(PackingOrder.builder()
                .tenantId(tenantId).pickingOrderId(pickingOrderId)
                .warehouseId(warehouseId).status(PackingStatus.COMPLETED)
                .operatorId(UUID.randomUUID()).build());
        packingOrderId = packingOrder.getId();

        PackingItem packingItem = packingItemRepository.save(PackingItem.builder()
                .tenantId(tenantId).packingOrderId(packingOrderId)
                .pickingItemId(pickingItemId).productId(product.getId())
                .quantityPacked(3).scanned(true).build());
        packingItemId = packingItem.getId();
    }

    // -------------------------------------------------------------------------
    // I1 — startLoading_asSupervisor_returns200AndStatusInProgressInDB
    // -------------------------------------------------------------------------

    @Test
    void startLoading_asSupervisor_returns200AndStatusInProgressInDB() throws Exception {
        UUID orderId = createPendingShippingOrder();
        UUID operatorId = UUID.randomUUID();

        String body = objectMapper.writeValueAsString(Map.of("operatorId", operatorId.toString()));

        mockMvc.perform(withSupervisor(put("/api/v1/shipping-orders/{id}/start-loading", orderId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.operatorId").value(operatorId.toString()));

        ShippingOrder updated = shippingOrderRepository.findByIdAndTenantId(orderId, tenantId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ShippingStatus.IN_PROGRESS);
    }

    // -------------------------------------------------------------------------
    // I2 — startLoading_asOperator_returns403
    // -------------------------------------------------------------------------

    @Test
    void startLoading_asOperator_returns403() throws Exception {
        UUID orderId = createPendingShippingOrder();
        String body = objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()));

        mockMvc.perform(withOperator(put("/api/v1/shipping-orders/{id}/start-loading", orderId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I3 — loadItem_validSku_returns200AndMarksLoadedInDB
    // -------------------------------------------------------------------------

    @Test
    void loadItem_validSku_returns200AndMarksLoadedInDB() throws Exception {
        UUID orderId = createInProgressShippingOrder();
        UUID itemId = createUnloadedShippingItem(orderId);

        String body = objectMapper.writeValueAsString(Map.of("barcode", "SKU-SHIP-" + tenantId));

        mockMvc.perform(withOperator(put("/api/v1/shipping-orders/{id}/load-item/{itemId}", orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true));

        ShippingItem updated = shippingItemRepository.findByIdAndTenantId(itemId, tenantId).orElseThrow();
        assertThat(updated.isLoaded()).isTrue();
    }

    // -------------------------------------------------------------------------
    // I4 — loadItem_wrongBarcode_returns422
    // -------------------------------------------------------------------------

    @Test
    void loadItem_wrongBarcode_returns422() throws Exception {
        UUID orderId = createInProgressShippingOrder();
        UUID itemId = createUnloadedShippingItem(orderId);

        String body = objectMapper.writeValueAsString(Map.of("barcode", "WRONG-BARCODE"));

        mockMvc.perform(withOperator(put("/api/v1/shipping-orders/{id}/load-item/{itemId}", orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // I5 — setCarrierDetails_validData_returns200AndUpdatesInDB
    // -------------------------------------------------------------------------

    @Test
    void setCarrierDetails_validData_returns200AndUpdatesInDB() throws Exception {
        UUID orderId = createPendingShippingOrder();

        String body = objectMapper.writeValueAsString(Map.of(
                "carrierName", "Transportadora XYZ",
                "vehiclePlate", "ABC-1234"
        ));

        mockMvc.perform(withSupervisor(put("/api/v1/shipping-orders/{id}/set-carrier-details", orderId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrierName").value("Transportadora XYZ"))
                .andExpect(jsonPath("$.vehiclePlate").value("ABC-1234"));

        ShippingOrder updated = shippingOrderRepository.findByIdAndTenantId(orderId, tenantId).orElseThrow();
        assertThat(updated.getCarrierName()).isEqualTo("Transportadora XYZ");
    }

    // -------------------------------------------------------------------------
    // I6 — generateManifest_returns200WithManifestJson
    // -------------------------------------------------------------------------

    @Test
    void generateManifest_returns200WithManifestJson() throws Exception {
        UUID orderId = createInProgressShippingOrder();
        createLoadedShippingItem(orderId);

        mockMvc.perform(withSupervisor(put("/api/v1/shipping-orders/{id}/generate-manifest", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestJson").isNotEmpty())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.manifestGeneratedAt").isNotEmpty());

        ShippingOrder updated = shippingOrderRepository.findByIdAndTenantId(orderId, tenantId).orElseThrow();
        assertThat(updated.getManifestJson()).isNotNull();
        assertThat(updated.getManifestGeneratedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // I7 — dispatch_allLoaded_withCarrier_returns200AndPublishesKafka
    // -------------------------------------------------------------------------

    @Test
    void dispatch_allLoaded_withCarrier_returns200AndPublishesKafka() throws Exception {
        UUID orderId = createInProgressShippingOrderWithCarrier();
        createLoadedShippingItem(orderId);

        mockMvc.perform(withSupervisor(put("/api/v1/shipping-orders/{id}/dispatch", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.dispatchedAt").isNotEmpty());

        ShippingOrder updated = shippingOrderRepository.findByIdAndTenantId(orderId, tenantId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ShippingStatus.DISPATCHED);
        verify(shippingCompletedEventPublisher).publishShippingCompleted(any());
    }

    // -------------------------------------------------------------------------
    // I8 — dispatch_itemNotLoaded_returns422
    // -------------------------------------------------------------------------

    @Test
    void dispatch_itemNotLoaded_returns422() throws Exception {
        UUID orderId = createInProgressShippingOrderWithCarrier();
        createUnloadedShippingItem(orderId);

        mockMvc.perform(withSupervisor(put("/api/v1/shipping-orders/{id}/dispatch", orderId)))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // I9 — dispatch_noCarrierName_returns422
    // -------------------------------------------------------------------------

    @Test
    void dispatch_noCarrierName_returns422() throws Exception {
        UUID orderId = createInProgressShippingOrder();
        createLoadedShippingItem(orderId);

        mockMvc.perform(withSupervisor(put("/api/v1/shipping-orders/{id}/dispatch", orderId)))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // I10 — cancelShipping_inProgress_returns200AndStatusCancelledInDB
    // -------------------------------------------------------------------------

    @Test
    void cancelShipping_inProgress_returns200AndStatusCancelledInDB() throws Exception {
        UUID orderId = createInProgressShippingOrder();
        String body = objectMapper.writeValueAsString(
                Map.of("cancellationReason", "Pedido cancelado pelo cliente"));

        mockMvc.perform(withSupervisor(put("/api/v1/shipping-orders/{id}/cancel", orderId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        ShippingOrder updated = shippingOrderRepository.findByIdAndTenantId(orderId, tenantId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ShippingStatus.CANCELLED);
    }

    // -------------------------------------------------------------------------
    // I11 — listShippingOrders_filterByStatus_returnsPaged
    // -------------------------------------------------------------------------

    @Test
    void listShippingOrders_filterByStatus_returnsPaged() throws Exception {
        // Cria PENDING e promove para IN_PROGRESS diretamente (evita duplicata no unique (tenant, packingOrder))
        UUID orderId = createPendingShippingOrder();
        shippingOrderRepository.findByIdAndTenantId(orderId, tenantId).ifPresent(o -> {
            o.setStatus(ShippingStatus.IN_PROGRESS);
            o.setOperatorId(UUID.randomUUID());
            shippingOrderRepository.save(o);
        });

        mockMvc.perform(withOperator(get("/api/v1/shipping-orders")
                        .param("status", "IN_PROGRESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));
    }

    // -------------------------------------------------------------------------
    // I12 — getShippingOrder_wrongTenant_returns403
    // -------------------------------------------------------------------------

    @Test
    void getShippingOrder_wrongTenant_returns403() throws Exception {
        UUID orderId = createPendingShippingOrder();
        UUID otherTenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/shipping-orders/{id}", orderId)
                        .with(jwt()
                                .jwt(j -> j.claim("tenant_id", otherTenantId.toString())
                                           .claim("sub", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID createPendingShippingOrder() {
        ShippingOrder order = shippingOrderRepository.save(ShippingOrder.builder()
                .tenantId(tenantId)
                .packingOrderId(packingOrderId)
                .pickingOrderId(pickingOrderId)
                .warehouseId(warehouseId)
                .status(ShippingStatus.PENDING)
                .build());
        return order.getId();
    }

    private UUID createInProgressShippingOrder() {
        ShippingOrder order = shippingOrderRepository.save(ShippingOrder.builder()
                .tenantId(tenantId)
                .packingOrderId(packingOrderId)
                .pickingOrderId(pickingOrderId)
                .warehouseId(warehouseId)
                .status(ShippingStatus.IN_PROGRESS)
                .operatorId(UUID.randomUUID())
                .build());
        return order.getId();
    }

    private UUID createInProgressShippingOrderWithCarrier() {
        ShippingOrder order = shippingOrderRepository.save(ShippingOrder.builder()
                .tenantId(tenantId)
                .packingOrderId(packingOrderId)
                .pickingOrderId(pickingOrderId)
                .warehouseId(warehouseId)
                .status(ShippingStatus.IN_PROGRESS)
                .operatorId(UUID.randomUUID())
                .carrierName("Transportadora XYZ")
                .build());
        return order.getId();
    }

    private UUID createUnloadedShippingItem(UUID shippingOrderId) {
        ShippingItem item = shippingItemRepository.save(ShippingItem.builder()
                .tenantId(tenantId)
                .shippingOrderId(shippingOrderId)
                .packingItemId(packingItemId)
                .productId(product.getId())
                .quantityShipped(3)
                .loaded(false)
                .build());
        return item.getId();
    }

    private void createLoadedShippingItem(UUID shippingOrderId) {
        shippingItemRepository.save(ShippingItem.builder()
                .tenantId(tenantId)
                .shippingOrderId(shippingOrderId)
                .packingItemId(packingItemId)
                .productId(product.getId())
                .quantityShipped(3)
                .loaded(true)
                .loadedAt(Instant.now().minusSeconds(30))
                .loadedBy(UUID.randomUUID())
                .build());
    }

    private MockHttpServletRequestBuilder withOperator(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private MockHttpServletRequestBuilder withSupervisor(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_SUPERVISOR")));
    }
}
