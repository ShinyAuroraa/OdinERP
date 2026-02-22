package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.Warehouse;
import com.odin.wms.domain.entity.Zone;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.repository.WarehouseRepository;
import com.odin.wms.domain.repository.ZoneRepository;
import com.odin.wms.dto.request.CreateZoneRequest;
import com.odin.wms.dto.request.UpdateZoneRequest;
import com.odin.wms.dto.response.ZoneResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private ZoneService zoneService;

    private MockedStatic<TenantContextHolder> tenantContextHolderMock;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID ZONE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantContextHolderMock = mockStatic(TenantContextHolder.class);
        tenantContextHolderMock.when(TenantContextHolder::getTenantId).thenReturn(TENANT_ID);

        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("test-user");
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        tenantContextHolderMock.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createZone_success() {
        Warehouse warehouse = buildWarehouse(WAREHOUSE_ID, TENANT_ID, true);
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));
        when(zoneRepository.existsByTenantIdAndWarehouseIdAndCode(TENANT_ID, WAREHOUSE_ID, "ZN-01")).thenReturn(false);

        Zone saved = Zone.builder()
                .tenantId(TENANT_ID)
                .warehouse(warehouse)
                .code("ZN-01")
                .name("Zona 1")
                .type(LocationType.STORAGE)
                .active(true)
                .build();
        setId(saved, ZONE_ID);
        when(zoneRepository.save(any(Zone.class))).thenReturn(saved);

        var request = new CreateZoneRequest("ZN-01", "Zona 1", LocationType.STORAGE);
        ZoneResponse response = zoneService.create(WAREHOUSE_ID, request);

        assertThat(response.code()).isEqualTo("ZN-01");
        assertThat(response.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(response.active()).isTrue();
    }

    @Test
    void createZone_inactiveWarehouse_throwsBusinessException() {
        Warehouse warehouse = buildWarehouse(WAREHOUSE_ID, TENANT_ID, false);
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));

        var request = new CreateZoneRequest("ZN-01", "Zona 1", null);
        assertThatThrownBy(() -> zoneService.create(WAREHOUSE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactive warehouse");
    }

    @Test
    void createZone_duplicateCode_throwsBusinessException() {
        Warehouse warehouse = buildWarehouse(WAREHOUSE_ID, TENANT_ID, true);
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));
        when(zoneRepository.existsByTenantIdAndWarehouseIdAndCode(TENANT_ID, WAREHOUSE_ID, "ZN-01")).thenReturn(true);

        var request = new CreateZoneRequest("ZN-01", "Zona 1", null);
        assertThatThrownBy(() -> zoneService.create(WAREHOUSE_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ZN-01");
    }

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(zoneRepository.findById(ZONE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> zoneService.findById(ZONE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivate_setsActiveFalse() {
        Warehouse warehouse = buildWarehouse(WAREHOUSE_ID, TENANT_ID, true);
        Zone zone = Zone.builder()
                .tenantId(TENANT_ID)
                .warehouse(warehouse)
                .code("ZN-01")
                .name("Zona 1")
                .active(true)
                .build();
        setId(zone, ZONE_ID);
        when(zoneRepository.findById(ZONE_ID)).thenReturn(Optional.of(zone));
        when(zoneRepository.save(any())).thenReturn(zone);

        zoneService.deactivate(ZONE_ID);

        assertThat(zone.getActive()).isFalse();
    }

    private Warehouse buildWarehouse(UUID id, UUID tenantId, boolean active) {
        Warehouse w = Warehouse.builder()
                .tenantId(tenantId)
                .code("WH-001")
                .name("Armazém")
                .active(active)
                .build();
        setId(w, id);
        return w;
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = com.odin.wms.domain.entity.base.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
