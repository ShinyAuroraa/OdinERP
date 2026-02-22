package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.Aisle;
import com.odin.wms.domain.entity.Warehouse;
import com.odin.wms.domain.entity.Zone;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.repository.AisleRepository;
import com.odin.wms.domain.repository.ZoneRepository;
import com.odin.wms.dto.request.CreateAisleRequest;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AisleServiceTest {

    @Mock
    private AisleRepository aisleRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @InjectMocks
    private AisleService aisleService;

    private MockedStatic<TenantContextHolder> tenantContextHolderMock;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ZONE_ID = UUID.randomUUID();
    private static final UUID AISLE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantContextHolderMock = mockStatic(TenantContextHolder.class);
        tenantContextHolderMock.when(TenantContextHolder::getTenantId).thenReturn(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        tenantContextHolderMock.close();
    }

    @Test
    void createAisle_success() {
        Zone zone = buildZone(ZONE_ID, TENANT_ID, true);
        when(zoneRepository.findById(ZONE_ID)).thenReturn(Optional.of(zone));
        when(aisleRepository.existsByTenantIdAndZoneIdAndCode(TENANT_ID, ZONE_ID, "A01")).thenReturn(false);

        Aisle saved = Aisle.builder().tenantId(TENANT_ID).zone(zone).code("A01").name("Corredor 1").build();
        setId(saved, AISLE_ID);
        when(aisleRepository.save(any(Aisle.class))).thenReturn(saved);

        var response = aisleService.create(ZONE_ID, new CreateAisleRequest("A01", "Corredor 1"));
        assertThat(response.code()).isEqualTo("A01");
        assertThat(response.zoneId()).isEqualTo(ZONE_ID);
    }

    @Test
    void createAisle_inactiveZone_throwsBusinessException() {
        Zone zone = buildZone(ZONE_ID, TENANT_ID, false);
        when(zoneRepository.findById(ZONE_ID)).thenReturn(Optional.of(zone));

        assertThatThrownBy(() -> aisleService.create(ZONE_ID, new CreateAisleRequest("A01", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactive zone");
    }

    @Test
    void createAisle_duplicateCode_throwsBusinessException() {
        Zone zone = buildZone(ZONE_ID, TENANT_ID, true);
        when(zoneRepository.findById(ZONE_ID)).thenReturn(Optional.of(zone));
        when(aisleRepository.existsByTenantIdAndZoneIdAndCode(TENANT_ID, ZONE_ID, "A01")).thenReturn(true);

        assertThatThrownBy(() -> aisleService.create(ZONE_ID, new CreateAisleRequest("A01", null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findById_otherTenant_throwsResourceNotFoundException() {
        Aisle aisle = Aisle.builder().tenantId(UUID.randomUUID()).code("A01").build();
        setId(aisle, AISLE_ID);
        when(aisleRepository.findById(AISLE_ID)).thenReturn(Optional.of(aisle));

        assertThatThrownBy(() -> aisleService.findById(AISLE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Zone buildZone(UUID id, UUID tenantId, boolean active) {
        Warehouse wh = Warehouse.builder().tenantId(tenantId).code("WH").name("WH").active(true).build();
        setId(wh, UUID.randomUUID());
        Zone z = Zone.builder()
                .tenantId(tenantId)
                .warehouse(wh)
                .code("ZN-01")
                .name("Zone 1")
                .type(LocationType.STORAGE)
                .active(active)
                .build();
        setId(z, id);
        return z;
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
