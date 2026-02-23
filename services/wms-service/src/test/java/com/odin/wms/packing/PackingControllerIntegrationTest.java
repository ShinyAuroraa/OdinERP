package com.odin.wms.packing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PackingOrder.PackingStatus;
import com.odin.wms.domain.entity.PickingItem.PickingItemStatus;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.PackingCompletedEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para PackingController — I1–I12.
 */
@AutoConfigureMockMvc
class PackingControllerIntegrationTest extends AbstractIntegrationTest {

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

    @MockBean private PackingCompletedEventPublisher packingCompletedEventPublisher;
    @MockBean private AuditLogIndexer auditLogIndexer;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID locationId;
    private ProductWms product;

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

        product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-PKG-" + tenantId).name("Produto Packing Test")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());
    }

    // -------------------------------------------------------------------------
    // I1 — openPacking_asSupervisor_returns200AndStatusInProgressInDB
    // -------------------------------------------------------------------------

    @Test
    void openPacking_asSupervisor_returns200AndStatusInProgressInDB() throws Exception {
        UUID orderId = createPendingPackingOrderWithItem();
        UUID operatorId = UUID.randomUUID();

        String body = objectMapper.writeValueAsString(Map.of("operatorId", operatorId.toString()));

        mockMvc.perform(withSupervisor(put("/api/v1/packing-orders/{id}/open", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.operatorId").value(operatorId.toString()));

        PackingOrder updated = packingOrderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PackingStatus.IN_PROGRESS);
    }

    // -------------------------------------------------------------------------
    // I2 — openPacking_asOperator_returns403
    // -------------------------------------------------------------------------

    @Test
    void openPacking_asOperator_returns403() throws Exception {
        UUID orderId = createPendingPackingOrderWithItem();

        String body = objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()));

        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/open", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I3 — scanItem_validSku_returns200AndMarksScannedInDB
    // -------------------------------------------------------------------------

    @Test
    void scanItem_validSku_returns200AndMarksScannedInDB() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();
        UUID itemId = packingItemRepository
                .findByTenantIdAndPackingOrderId(tenantId, orderId).get(0).getId();

        String body = objectMapper.writeValueAsString(Map.of(
                "barcode", product.getSku(),
                "scannedBy", UUID.randomUUID().toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/scan-item/{itemId}", orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(true));

        PackingItem updated = packingItemRepository.findByTenantIdAndId(tenantId, itemId).orElseThrow();
        assertThat(updated.isScanned()).isTrue();
        assertThat(updated.getScannedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // I4 — scanItem_wrongBarcode_returns400
    // -------------------------------------------------------------------------

    @Test
    void scanItem_wrongBarcode_returns400() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();
        UUID itemId = packingItemRepository
                .findByTenantIdAndPackingOrderId(tenantId, orderId).get(0).getId();

        String body = objectMapper.writeValueAsString(Map.of(
                "barcode", "BARCODE-QUE-NAO-EXISTE",
                "scannedBy", UUID.randomUUID().toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/scan-item/{itemId}", orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // I5 — setPackageDetails_validData_returns200AndUpdatesInDB
    // -------------------------------------------------------------------------

    @Test
    void setPackageDetails_validData_returns200AndUpdatesInDB() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();

        String body = objectMapper.writeValueAsString(Map.of(
                "weightKg", 3.5,
                "packageType", "BOX",
                "lengthCm", 30.0,
                "widthCm", 20.0,
                "heightCm", 15.0
        ));

        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/set-details", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(3.5))
                .andExpect(jsonPath("$.packageType").value("BOX"));

        PackingOrder updated = packingOrderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow();
        assertThat(updated.getWeightKg()).isNotNull();
        assertThat(updated.getPackageType().name()).isEqualTo("BOX");
    }

    // -------------------------------------------------------------------------
    // I6 — generateLabel_returns200WithSSCC18DigitsAndBarcodeBase64
    // -------------------------------------------------------------------------

    @Test
    void generateLabel_returns200WithSSCC18DigitsAndBarcodeBase64() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();

        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/generate-label", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sscc").isNotEmpty())
                .andExpect(jsonPath("$.barcodeBase64").isNotEmpty())
                .andExpect(jsonPath("$.barcodeFormat").value("GS1_128"));

        PackingOrder updated = packingOrderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow();
        assertThat(updated.getSscc()).isNotNull();
        assertThat(updated.getSscc()).hasSize(18);
    }

    // -------------------------------------------------------------------------
    // I7 — completePacking_allScanned_withLabel_returns200AndPublishesKafka
    // -------------------------------------------------------------------------

    @Test
    void completePacking_allScanned_withLabel_returns200AndPublishesKafka() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();
        UUID itemId = packingItemRepository
                .findByTenantIdAndPackingOrderId(tenantId, orderId).get(0).getId();

        // Scan item
        String scanBody = objectMapper.writeValueAsString(Map.of(
                "barcode", product.getSku(),
                "scannedBy", UUID.randomUUID().toString()
        ));
        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/scan-item/{itemId}", orderId, itemId))
                .contentType(MediaType.APPLICATION_JSON).content(scanBody))
                .andExpect(status().isOk());

        // Generate label
        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/generate-label", orderId)))
                .andExpect(status().isOk());

        // Complete
        mockMvc.perform(withSupervisor(put("/api/v1/packing-orders/{id}/complete", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        PackingOrder completed = packingOrderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PackingStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();

        // Publisher chamado 1 vez
        org.mockito.Mockito.verify(packingCompletedEventPublisher, org.mockito.Mockito.times(1))
                .publishPackingCompleted(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // I8 — completePacking_itemNotScanned_returns422
    // -------------------------------------------------------------------------

    @Test
    void completePacking_itemNotScanned_returns422() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();

        // Generate label without scanning
        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/generate-label", orderId)))
                .andExpect(status().isOk());

        // Try to complete without scanning → 422
        mockMvc.perform(withSupervisor(put("/api/v1/packing-orders/{id}/complete", orderId)))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // I9 — completePacking_noLabel_returns422
    // -------------------------------------------------------------------------

    @Test
    void completePacking_noLabel_returns422() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();
        UUID itemId = packingItemRepository
                .findByTenantIdAndPackingOrderId(tenantId, orderId).get(0).getId();

        // Scan item
        String scanBody = objectMapper.writeValueAsString(Map.of(
                "barcode", product.getSku(),
                "scannedBy", UUID.randomUUID().toString()
        ));
        mockMvc.perform(withOperator(put("/api/v1/packing-orders/{id}/scan-item/{itemId}", orderId, itemId))
                .contentType(MediaType.APPLICATION_JSON).content(scanBody))
                .andExpect(status().isOk());

        // Try to complete without label → 422
        mockMvc.perform(withSupervisor(put("/api/v1/packing-orders/{id}/complete", orderId)))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // I10 — cancelPacking_inProgress_returns200AndStatusCancelledInDB
    // -------------------------------------------------------------------------

    @Test
    void cancelPacking_inProgress_returns200AndStatusCancelledInDB() throws Exception {
        UUID orderId = createInProgressPackingOrderWithItem();

        String body = objectMapper.writeValueAsString(Map.of("reason", "Teste de cancelamento"));

        mockMvc.perform(withSupervisor(put("/api/v1/packing-orders/{id}/cancel", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Teste de cancelamento"));

        PackingOrder cancelled = packingOrderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(PackingStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // I11 — listPackingOrders_filterByStatus_returnsPaged
    // -------------------------------------------------------------------------

    @Test
    void listPackingOrders_filterByStatus_returnsPaged() throws Exception {
        createPendingPackingOrderWithItem();
        createPendingPackingOrderWithItem();

        mockMvc.perform(withOperator(get("/api/v1/packing-orders").param("status", "PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                // No listing, items should be null
                .andExpect(jsonPath("$.content[0].items").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // I12 — getPackingOrder_wrongTenant_returns403
    // -------------------------------------------------------------------------

    @Test
    void getPackingOrder_wrongTenant_returns403() throws Exception {
        UUID orderId = createPendingPackingOrderWithItem();

        // Usar tenantId diferente no JWT
        UUID otherTenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/packing-orders/{id}", orderId)
                        .with(jwt()
                                .jwt(j -> j.claim("tenant_id", otherTenantId.toString())
                                           .claim("sub", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Cria PickingOrder + PickingItem + PackingOrder (PENDING) + PackingItem no DB.
     */
    private UUID createPendingPackingOrderWithItem() {
        // PickingOrder (FK para packing_orders.picking_order_id)
        PickingOrder pickingOrder = pickingOrderRepository.save(PickingOrder.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .createdBy(UUID.randomUUID())
                .status(PickingStatus.COMPLETED)
                .build());

        // PickingItem (FK para packing_items.picking_item_id)
        PickingItem pickingItem = pickingItemRepository.save(PickingItem.builder()
                .tenantId(tenantId)
                .pickingOrderId(pickingOrder.getId())
                .productId(product.getId())
                .locationId(locationId)
                .quantityRequested(3)
                .quantityPicked(3)
                .status(PickingItemStatus.PICKED)
                .build());

        // PackingOrder
        PackingOrder packingOrder = packingOrderRepository.save(PackingOrder.builder()
                .tenantId(tenantId)
                .pickingOrderId(pickingOrder.getId())
                .warehouseId(warehouseId)
                .status(PackingStatus.PENDING)
                .build());

        // PackingItem
        packingItemRepository.save(PackingItem.builder()
                .tenantId(tenantId)
                .packingOrderId(packingOrder.getId())
                .pickingItemId(pickingItem.getId())
                .productId(product.getId())
                .quantityPacked(3)
                .scanned(false)
                .build());

        return packingOrder.getId();
    }

    /**
     * Cria PackingOrder em status IN_PROGRESS diretamente no DB.
     */
    private UUID createInProgressPackingOrderWithItem() {
        PickingOrder pickingOrder = pickingOrderRepository.save(PickingOrder.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .createdBy(UUID.randomUUID())
                .status(PickingStatus.COMPLETED)
                .build());

        PickingItem pickingItem = pickingItemRepository.save(PickingItem.builder()
                .tenantId(tenantId)
                .pickingOrderId(pickingOrder.getId())
                .productId(product.getId())
                .locationId(locationId)
                .quantityRequested(3)
                .quantityPicked(3)
                .status(PickingItemStatus.PICKED)
                .build());

        PackingOrder packingOrder = packingOrderRepository.save(PackingOrder.builder()
                .tenantId(tenantId)
                .pickingOrderId(pickingOrder.getId())
                .warehouseId(warehouseId)
                .status(PackingStatus.IN_PROGRESS)
                .operatorId(UUID.randomUUID())
                .build());

        packingItemRepository.save(PackingItem.builder()
                .tenantId(tenantId)
                .packingOrderId(packingOrder.getId())
                .pickingItemId(pickingItem.getId())
                .productId(product.getId())
                .quantityPacked(3)
                .scanned(false)
                .build());

        return packingOrder.getId();
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
