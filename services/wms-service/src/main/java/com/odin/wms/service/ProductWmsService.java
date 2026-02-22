package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.ProductWms;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.ProductWmsRepository;
import com.odin.wms.dto.request.CreateProductWmsRequest;
import com.odin.wms.dto.request.UpdateProductWmsRequest;
import com.odin.wms.dto.response.ProductWmsResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductWmsService {

    private final ProductWmsRepository productWmsRepository;

    public ProductWmsResponse create(CreateProductWmsRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (productWmsRepository.existsByTenantIdAndSku(tenantId, request.sku())) {
            throw new BusinessException("SKU '" + request.sku() + "' already exists for this tenant");
        }
        if (request.ean13() != null
                && productWmsRepository.existsByTenantIdAndEan13(tenantId, request.ean13())) {
            throw new BusinessException("EAN-13 '" + request.ean13() + "' already registered");
        }
        ProductWms product = ProductWms.builder()
                .tenantId(tenantId)
                .productId(request.productId())
                .sku(request.sku())
                .name(request.name())
                .storageType(request.storageType())
                .controlsLot(Boolean.TRUE.equals(request.controlsLot()))
                .controlsSerial(Boolean.TRUE.equals(request.controlsSerial()))
                .controlsExpiry(Boolean.TRUE.equals(request.controlsExpiry()))
                .ean13(request.ean13())
                .gs1128(request.gs1128())
                .qrCode(request.qrCode())
                .unitWidthCm(request.unitWidthCm())
                .unitHeightCm(request.unitHeightCm())
                .unitDepthCm(request.unitDepthCm())
                .unitWeightKg(request.unitWeightKg())
                .unitsPerLocation(request.unitsPerLocation())
                .active(true)
                .build();
        return toResponse(productWmsRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductWmsResponse findById(UUID id) {
        return toResponse(getByTenant(id));
    }

    @Transactional(readOnly = true)
    public List<ProductWmsResponse> findAll(StorageType storageType, Boolean active) {
        UUID tenantId = TenantContextHolder.getTenantId();
        List<ProductWms> products;
        if (storageType != null) {
            products = productWmsRepository.findByTenantIdAndStorageType(tenantId, storageType);
        } else if (active != null) {
            products = productWmsRepository.findByTenantIdAndActive(tenantId, active);
        } else {
            products = productWmsRepository.findByTenantId(tenantId);
        }
        return products.stream().map(this::toResponse).toList();
    }

    public ProductWmsResponse update(UUID id, UpdateProductWmsRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ProductWms product = getByTenant(id);
        if (request.ean13() != null
                && !request.ean13().equals(product.getEan13())
                && productWmsRepository.existsByTenantIdAndEan13(tenantId, request.ean13())) {
            throw new BusinessException("EAN-13 '" + request.ean13() + "' already registered");
        }
        product.setName(request.name());
        product.setStorageType(request.storageType());
        if (request.controlsLot() != null) product.setControlsLot(request.controlsLot());
        if (request.controlsSerial() != null) product.setControlsSerial(request.controlsSerial());
        if (request.controlsExpiry() != null) product.setControlsExpiry(request.controlsExpiry());
        product.setEan13(request.ean13());
        product.setGs1128(request.gs1128());
        product.setQrCode(request.qrCode());
        product.setUnitWidthCm(request.unitWidthCm());
        product.setUnitHeightCm(request.unitHeightCm());
        product.setUnitDepthCm(request.unitDepthCm());
        product.setUnitWeightKg(request.unitWeightKg());
        product.setUnitsPerLocation(request.unitsPerLocation());
        return toResponse(productWmsRepository.save(product));
    }

    public void deactivate(UUID id) {
        ProductWms product = getByTenant(id);
        product.setActive(false);
        productWmsRepository.save(product);
    }

    private ProductWms getByTenant(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return productWmsRepository.findById(id)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private ProductWmsResponse toResponse(ProductWms p) {
        return new ProductWmsResponse(
                p.getId(),
                p.getTenantId(),
                p.getProductId(),
                p.getSku(),
                p.getName(),
                p.getStorageType(),
                p.getControlsLot(),
                p.getControlsSerial(),
                p.getControlsExpiry(),
                p.getEan13(),
                p.getGs1128(),
                p.getQrCode(),
                p.getUnitWidthCm(),
                p.getUnitHeightCm(),
                p.getUnitDepthCm(),
                p.getUnitWeightKg(),
                p.getUnitsPerLocation(),
                p.getActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
