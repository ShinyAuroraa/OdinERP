package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.ProductWms;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.ProductWmsRepository;
import com.odin.wms.dto.request.CreateProductWmsRequest;
import com.odin.wms.dto.response.ProductWmsResponse;
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
class ProductWmsServiceTest {

    @Mock
    private ProductWmsRepository productWmsRepository;

    @InjectMocks
    private ProductWmsService productWmsService;

    private MockedStatic<TenantContextHolder> tenantContextHolderMock;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

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
    void createProduct_success() {
        when(productWmsRepository.existsByTenantIdAndSku(TENANT_ID, "SKU-001")).thenReturn(false);
        UUID savedId = UUID.randomUUID();
        ProductWms saved = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .sku("SKU-001")
                .name("Produto Teste")
                .storageType(StorageType.DRY)
                .controlsLot(true)
                .controlsSerial(false)
                .controlsExpiry(false)
                .active(true)
                .build();
        setId(saved, savedId);
        when(productWmsRepository.save(any(ProductWms.class))).thenReturn(saved);

        var request = new CreateProductWmsRequest(
                PRODUCT_ID, "SKU-001", "Produto Teste", StorageType.DRY,
                true, false, false, null, null, null, null, null, null, null, null);
        ProductWmsResponse response = productWmsService.create(request);

        assertThat(response.sku()).isEqualTo("SKU-001");
        assertThat(response.storageType()).isEqualTo(StorageType.DRY);
        assertThat(response.active()).isTrue();
        assertThat(response.controlsLot()).isTrue();
    }

    @Test
    void createProduct_duplicateSku_throwsBusinessException() {
        when(productWmsRepository.existsByTenantIdAndSku(TENANT_ID, "SKU-DUP")).thenReturn(true);

        var request = new CreateProductWmsRequest(
                PRODUCT_ID, "SKU-DUP", "Produto Dup", StorageType.DRY,
                null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> productWmsService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SKU-DUP");
    }

    @Test
    void createProduct_duplicateEan13_throwsBusinessException() {
        when(productWmsRepository.existsByTenantIdAndSku(TENANT_ID, "SKU-002")).thenReturn(false);
        when(productWmsRepository.existsByTenantIdAndEan13(TENANT_ID, "7891234567890")).thenReturn(true);

        var request = new CreateProductWmsRequest(
                PRODUCT_ID, "SKU-002", "Produto EAN", StorageType.REFRIGERATED,
                null, null, null, "7891234567890", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> productWmsService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("7891234567890");
    }

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(productWmsRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productWmsService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_wrongTenant_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        ProductWms product = ProductWms.builder()
                .tenantId(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .sku("SKU-WRONG")
                .name("Wrong Tenant")
                .storageType(StorageType.DRY)
                .active(true)
                .build();
        setId(product, id);
        when(productWmsRepository.findById(id)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productWmsService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivate_setsActiveFalse() {
        UUID id = UUID.randomUUID();
        ProductWms product = buildProduct(id, "SKU-DEA");
        when(productWmsRepository.findById(id)).thenReturn(Optional.of(product));
        when(productWmsRepository.save(any())).thenReturn(product);

        productWmsService.deactivate(id);

        assertThat(product.getActive()).isFalse();
    }

    private ProductWms buildProduct(UUID id, String sku) {
        ProductWms p = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .sku(sku)
                .name("Produto " + sku)
                .storageType(StorageType.DRY)
                .active(true)
                .build();
        setId(p, id);
        return p;
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
