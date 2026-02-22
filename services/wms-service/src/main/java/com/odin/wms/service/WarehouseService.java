package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.Warehouse;
import com.odin.wms.domain.repository.WarehouseRepository;
import com.odin.wms.dto.request.CreateWarehouseRequest;
import com.odin.wms.dto.request.UpdateWarehouseRequest;
import com.odin.wms.dto.response.WarehouseResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseResponse create(CreateWarehouseRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (warehouseRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new BusinessException("Warehouse code already exists: " + request.code());
        }
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        Warehouse warehouse = Warehouse.builder()
                .tenantId(tenantId)
                .code(request.code())
                .name(request.name())
                .address(request.address())
                .active(true)
                .createdBy(actor)
                .build();
        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return warehouseRepository.findByTenantId(tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(UUID id) {
        return toResponse(getByTenant(id));
    }

    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = getByTenant(id);
        warehouse.setName(request.name());
        warehouse.setAddress(request.address());
        return toResponse(warehouseRepository.save(warehouse));
    }

    public void deactivate(UUID id) {
        Warehouse warehouse = getByTenant(id);
        warehouse.setActive(false);
        warehouseRepository.save(warehouse);
    }

    private Warehouse getByTenant(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return warehouseRepository.findById(id)
                .filter(w -> w.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));
    }

    private WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(
                w.getId(),
                w.getCode(),
                w.getName(),
                w.getAddress(),
                w.getActive(),
                w.getCreatedBy(),
                w.getCreatedAt(),
                w.getUpdatedAt()
        );
    }
}
