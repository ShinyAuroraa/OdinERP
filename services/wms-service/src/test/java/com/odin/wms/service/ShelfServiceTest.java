package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.Aisle;
import com.odin.wms.domain.entity.Shelf;
import com.odin.wms.domain.entity.Warehouse;
import com.odin.wms.domain.entity.Zone;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.repository.AisleRepository;
import com.odin.wms.domain.repository.ShelfRepository;
import com.odin.wms.dto.request.CreateShelfRequest;
import com.odin.wms.dto.request.UpdateShelfRequest;
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
class ShelfServiceTest {

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private AisleRepository aisleRepository;

    @InjectMocks
    private ShelfService shelfService;

    private MockedStatic<TenantContextHolder> tenantContextHolderMock;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AISLE_ID = UUID.randomUUID();
    private static final UUID SHELF_ID = UUID.randomUUID();

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
    void createShelf_success() {
        Aisle aisle = buildAisle(AISLE_ID, TENANT_ID);
        when(aisleRepository.findById(AISLE_ID)).thenReturn(Optional.of(aisle));
        when(shelfRepository.existsByTenantIdAndAisleIdAndCode(TENANT_ID, AISLE_ID, "S01")).thenReturn(false);

        Shelf saved = Shelf.builder().tenantId(TENANT_ID).aisle(aisle).code("S01").level(2).build();
        setId(saved, SHELF_ID);
        when(shelfRepository.save(any(Shelf.class))).thenReturn(saved);

        var response = shelfService.create(AISLE_ID, new CreateShelfRequest("S01", 2));
        assertThat(response.code()).isEqualTo("S01");
        assertThat(response.level()).isEqualTo(2);
        assertThat(response.aisleId()).isEqualTo(AISLE_ID);
    }

    @Test
    void createShelf_duplicateCode_throwsBusinessException() {
        Aisle aisle = buildAisle(AISLE_ID, TENANT_ID);
        when(aisleRepository.findById(AISLE_ID)).thenReturn(Optional.of(aisle));
        when(shelfRepository.existsByTenantIdAndAisleIdAndCode(TENANT_ID, AISLE_ID, "S01")).thenReturn(true);

        assertThatThrownBy(() -> shelfService.create(AISLE_ID, new CreateShelfRequest("S01", 1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findById_otherTenant_throwsResourceNotFoundException() {
        Shelf shelf = Shelf.builder().tenantId(UUID.randomUUID()).code("S01").build();
        setId(shelf, SHELF_ID);
        when(shelfRepository.findById(SHELF_ID)).thenReturn(Optional.of(shelf));

        assertThatThrownBy(() -> shelfService.findById(SHELF_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_changesLevel() {
        Aisle aisle = buildAisle(AISLE_ID, TENANT_ID);
        Shelf shelf = Shelf.builder().tenantId(TENANT_ID).aisle(aisle).code("S01").level(1).build();
        setId(shelf, SHELF_ID);
        when(shelfRepository.findById(SHELF_ID)).thenReturn(Optional.of(shelf));
        when(shelfRepository.save(any())).thenReturn(shelf);

        var response = shelfService.update(SHELF_ID, new UpdateShelfRequest(3));
        assertThat(shelf.getLevel()).isEqualTo(3);
    }

    private Aisle buildAisle(UUID id, UUID tenantId) {
        Warehouse wh = Warehouse.builder().tenantId(tenantId).code("WH").name("WH").active(true).build();
        setId(wh, UUID.randomUUID());
        Zone z = Zone.builder().tenantId(tenantId).warehouse(wh).code("ZN").name("ZN")
                .type(LocationType.STORAGE).active(true).build();
        setId(z, UUID.randomUUID());
        Aisle a = Aisle.builder().tenantId(tenantId).zone(z).code("A01").build();
        setId(a, id);
        return a;
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
