package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.Warehouse;
import com.odin.wms.domain.repository.WarehouseRepository;
import com.odin.wms.dto.request.CreateWarehouseRequest;
import com.odin.wms.dto.request.UpdateWarehouseRequest;
import com.odin.wms.dto.response.WarehouseResponse;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    private MockedStatic<TenantContextHolder> tenantContextHolderMock;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

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
    void createWarehouse_success() {
        var request = new CreateWarehouseRequest("WH-001", "Armazém Central", "Rua A, 100");
        when(warehouseRepository.existsByTenantIdAndCode(TENANT_ID, "WH-001")).thenReturn(false);

        Warehouse saved = Warehouse.builder()
                .tenantId(TENANT_ID)
                .code("WH-001")
                .name("Armazém Central")
                .address("Rua A, 100")
                .active(true)
                .createdBy("test-user")
                .build();
        setId(saved, WAREHOUSE_ID);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(saved);

        WarehouseResponse response = warehouseService.create(request);

        assertThat(response.code()).isEqualTo("WH-001");
        assertThat(response.name()).isEqualTo("Armazém Central");
        assertThat(response.active()).isTrue();
        assertThat(response.createdBy()).isEqualTo("test-user");
        verify(warehouseRepository).save(any(Warehouse.class));
    }

    @Test
    void createWarehouse_duplicateCode_throwsBusinessException() {
        var request = new CreateWarehouseRequest("WH-001", "Armazém Central", null);
        when(warehouseRepository.existsByTenantIdAndCode(TENANT_ID, "WH-001")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WH-001");
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> warehouseService.findById(WAREHOUSE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_belongsToOtherTenant_throwsResourceNotFoundException() {
        Warehouse otherTenant = Warehouse.builder()
                .tenantId(UUID.randomUUID())
                .code("WH-001")
                .name("Other")
                .build();
        setId(otherTenant, WAREHOUSE_ID);
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> warehouseService.findById(WAREHOUSE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivate_setsActiveFalse() {
        Warehouse warehouse = Warehouse.builder()
                .tenantId(TENANT_ID)
                .code("WH-001")
                .name("Armazém Central")
                .active(true)
                .build();
        setId(warehouse, WAREHOUSE_ID);
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any())).thenReturn(warehouse);

        warehouseService.deactivate(WAREHOUSE_ID);

        assertThat(warehouse.getActive()).isFalse();
        verify(warehouseRepository).save(warehouse);
    }

    // Helper: set id via reflection since @SuperBuilder doesn't expose id setter
    private void setId(Warehouse w, UUID id) {
        try {
            var field = com.odin.wms.domain.entity.base.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(w, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
