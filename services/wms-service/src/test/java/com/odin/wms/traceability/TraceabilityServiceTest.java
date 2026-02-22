package com.odin.wms.traceability;

import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.SerialStatus;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.response.ExpiryResponse;
import com.odin.wms.dto.response.LotTraceabilityResponse;
import com.odin.wms.dto.response.SerialTraceabilityResponse;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityDocument;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityRepository;
import com.odin.wms.service.LotTraceabilityService;
import com.odin.wms.service.SerialTraceabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários dos services de rastreabilidade.
 * AC1, AC2, AC4 — estratégia ES first / PostgreSQL fallback.
 */
@ExtendWith(MockitoExtension.class)
class TraceabilityServiceTest {

    @Mock private LotRepository lotRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private TraceabilityRepository traceabilityRepository;
    @Mock private SerialNumberRepository serialNumberRepository;

    @InjectMocks
    private LotTraceabilityService lotService;

    @InjectMocks
    private SerialTraceabilityService serialService;

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID LOT_ID     = UUID.randomUUID();
    private static final UUID SERIAL_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(lotService, "maxMovements", 500);
    }

    // -------------------------------------------------------------------------
    // AC1 — getLotHistory: ES first → PostgreSQL fallback
    // -------------------------------------------------------------------------

    @Test
    void getLotHistory_esHasResults_returnsMovementsFromEs() {
        Lot lot = buildLot();
        TraceabilityDocument doc = buildLotDoc("LOT-001");

        when(lotRepository.findByTenantIdAndLotNumber(TENANT_ID, "LOT-001"))
                .thenReturn(Optional.of(lot));
        when(traceabilityRepository.findByTenantIdAndLotNumberOrderByCreatedAtAsc(
                TENANT_ID.toString(), "LOT-001"))
                .thenReturn(List.of(doc));

        LotTraceabilityResponse resp = lotService.getLotHistory(TENANT_ID, "LOT-001");

        assertThat(resp.lotNumber()).isEqualTo("LOT-001");
        assertThat(resp.movements()).hasSize(1);
        assertThat(resp.movements().getFirst().movementType()).isEqualTo("INBOUND");
        assertThat(resp.totalMovements()).isEqualTo(1);
        // PostgreSQL NÃO deve ter sido consultado
        verify(stockMovementRepository, never())
                .findByTenantIdAndLotIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void getLotHistory_esEmpty_fallsBackToPostgres() {
        Lot lot = buildLot();
        StockMovement movement = buildMovement();

        when(lotRepository.findByTenantIdAndLotNumber(TENANT_ID, "LOT-001"))
                .thenReturn(Optional.of(lot));
        when(traceabilityRepository.findByTenantIdAndLotNumberOrderByCreatedAtAsc(
                TENANT_ID.toString(), "LOT-001"))
                .thenReturn(List.of());
        when(stockMovementRepository.findByTenantIdAndLotIdOrderByCreatedAtAsc(TENANT_ID, LOT_ID))
                .thenReturn(List.of(movement));

        LotTraceabilityResponse resp = lotService.getLotHistory(TENANT_ID, "LOT-001");

        assertThat(resp.movements()).hasSize(1);
        verify(stockMovementRepository).findByTenantIdAndLotIdOrderByCreatedAtAsc(TENANT_ID, LOT_ID);
    }

    @Test
    void getLotHistory_unknownLot_throwsResourceNotFoundException() {
        when(lotRepository.findByTenantIdAndLotNumber(TENANT_ID, "INEXISTENTE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> lotService.getLotHistory(TENANT_ID, "INEXISTENTE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("INEXISTENTE");
    }

    // -------------------------------------------------------------------------
    // AC4 — getExpiryByProduct: FEFO com item disponível
    // -------------------------------------------------------------------------

    @Test
    void getExpiryByProduct_withAvailableLotAndItem_returnsExpiryResponse() {
        Lot lot = buildLot();

        // Hierarquia de localização mockada para navegação warehouseId
        Location location = mock(Location.class);
        Shelf shelf = mock(Shelf.class);
        Aisle aisle = mock(Aisle.class);
        Zone zone = mock(Zone.class);
        Warehouse warehouse = mock(Warehouse.class);
        UUID warehouseId = UUID.randomUUID();

        when(warehouse.getId()).thenReturn(warehouseId);
        when(zone.getWarehouse()).thenReturn(warehouse);
        when(aisle.getZone()).thenReturn(zone);
        when(shelf.getAisle()).thenReturn(aisle);
        when(location.getShelf()).thenReturn(shelf);
        when(location.getCode()).thenReturn("LOC-001");

        StockItem stockItem = mock(StockItem.class);
        when(stockItem.getLocation()).thenReturn(location);
        when(stockItem.getQuantityAvailable()).thenReturn(10);
        when(stockItem.getLot()).thenReturn(lot);

        when(lotRepository.findAvailableByProductFefo(TENANT_ID, PRODUCT_ID, null))
                .thenReturn(List.of(lot));
        when(stockItemRepository.findAvailableByTenantIdAndLotIdIn(TENANT_ID, List.of(LOT_ID)))
                .thenReturn(List.of(stockItem));

        List<ExpiryResponse> result = lotService.getExpiryByProduct(TENANT_ID, PRODUCT_ID, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().lotNumber()).isEqualTo("LOT-001");
        assertThat(result.getFirst().quantityAvailable()).isEqualTo(10);
        assertThat(result.getFirst().locationCode()).isEqualTo("LOC-001");
        assertThat(result.getFirst().warehouseId()).isEqualTo(warehouseId);
        assertThat(result.getFirst().daysUntilExpiry()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // AC2 — getSerialHistory: ES first → PostgreSQL fallback
    // -------------------------------------------------------------------------

    @Test
    void getSerialHistory_esHasResults_returnsMovementsFromEs() {
        SerialNumber serial = buildSerial();
        TraceabilityDocument doc = buildSerialDoc("SER-001");

        when(serialNumberRepository.findByTenantIdAndSerialNumber(TENANT_ID, "SER-001"))
                .thenReturn(Optional.of(serial));
        when(traceabilityRepository.findByTenantIdAndSerialNumberOrderByCreatedAtAsc(
                TENANT_ID.toString(), "SER-001"))
                .thenReturn(List.of(doc));

        SerialTraceabilityResponse resp = serialService.getSerialHistory(TENANT_ID, "SER-001");

        assertThat(resp.serialNumber()).isEqualTo("SER-001");
        assertThat(resp.currentStatus()).isEqualTo("IN_STOCK");
        assertThat(resp.movements()).hasSize(1);
        assertThat(resp.movements().getFirst().movementType()).isEqualTo("INBOUND");
        verify(stockMovementRepository, never())
                .findByTenantIdAndSerialNumberIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void getSerialHistory_esThrows_fallsBackToPostgres() {
        SerialNumber serial = buildSerial();
        StockMovement movement = buildMovement();

        when(serialNumberRepository.findByTenantIdAndSerialNumber(TENANT_ID, "SER-001"))
                .thenReturn(Optional.of(serial));
        when(traceabilityRepository.findByTenantIdAndSerialNumberOrderByCreatedAtAsc(
                TENANT_ID.toString(), "SER-001"))
                .thenThrow(new RuntimeException("ES indisponível"));
        when(stockMovementRepository.findByTenantIdAndSerialNumberIdOrderByCreatedAtAsc(TENANT_ID, SERIAL_ID))
                .thenReturn(List.of(movement));

        SerialTraceabilityResponse resp = serialService.getSerialHistory(TENANT_ID, "SER-001");

        assertThat(resp.movements()).hasSize(1);
        verify(stockMovementRepository)
                .findByTenantIdAndSerialNumberIdOrderByCreatedAtAsc(TENANT_ID, SERIAL_ID);
    }

    @Test
    void getSerialHistory_unknownSerial_throwsResourceNotFoundException() {
        when(serialNumberRepository.findByTenantIdAndSerialNumber(TENANT_ID, "INEXISTENTE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> serialService.getSerialHistory(TENANT_ID, "INEXISTENTE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("INEXISTENTE");
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private ProductWms buildProduct() {
        ProductWms p = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .sku("SKU-RASTR")
                .name("Produto Rastreável")
                .storageType(StorageType.DRY)
                .controlsLot(true).controlsSerial(true).controlsExpiry(true).active(true)
                .build();
        setId(p, PRODUCT_ID, "com.odin.wms.domain.entity.base.BaseEntity");
        return p;
    }

    private Lot buildLot() {
        Lot lot = Lot.builder()
                .tenantId(TENANT_ID)
                .product(buildProduct())
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2027, 6, 30))
                .active(true)
                .build();
        setId(lot, LOT_ID, "com.odin.wms.domain.entity.base.BaseAppendOnlyEntity");
        ReflectionTestUtils.setField(lot, "createdAt", Instant.now());
        return lot;
    }

    private SerialNumber buildSerial() {
        SerialNumber serial = SerialNumber.builder()
                .tenantId(TENANT_ID)
                .product(buildProduct())
                .serialNumber("SER-001")
                .status(SerialStatus.IN_STOCK)
                .build();
        setId(serial, SERIAL_ID, "com.odin.wms.domain.entity.base.BaseEntity");
        return serial;
    }

    private StockMovement buildMovement() {
        StockMovement m = StockMovement.builder()
                .tenantId(TENANT_ID)
                .type(MovementType.INBOUND)
                .product(buildProduct())
                .quantity(10)
                .build();
        setId(m, UUID.randomUUID(), "com.odin.wms.domain.entity.base.BaseAppendOnlyEntity");
        ReflectionTestUtils.setField(m, "createdAt", Instant.now());
        return m;
    }

    private TraceabilityDocument buildLotDoc(String lotNumber) {
        TraceabilityDocument doc = new TraceabilityDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setTenantId(TENANT_ID.toString());
        doc.setLotId(LOT_ID.toString());
        doc.setLotNumber(lotNumber);
        doc.setMovementType("INBOUND");
        doc.setQuantity(10);
        doc.setCreatedAt(Instant.now());
        return doc;
    }

    private TraceabilityDocument buildSerialDoc(String serialNumber) {
        TraceabilityDocument doc = new TraceabilityDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setTenantId(TENANT_ID.toString());
        doc.setSerialNumber(serialNumber);
        doc.setMovementType("INBOUND");
        doc.setQuantity(1);
        doc.setCreatedAt(Instant.now());
        return doc;
    }

    private void setId(Object entity, UUID id, String baseClassName) {
        try {
            Class<?> base = Class.forName(baseClassName);
            var field = base.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao definir UUID via reflexão", e);
        }
    }
}
