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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final WarehouseRepository warehouseRepository;

    public ZoneResponse create(UUID warehouseId, CreateZoneRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .filter(w -> w.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + warehouseId));

        if (!warehouse.getActive()) {
            throw new BusinessException("Cannot create zone in inactive warehouse: " + warehouseId);
        }

        if (zoneRepository.existsByTenantIdAndWarehouseIdAndCode(tenantId, warehouseId, request.code())) {
            throw new BusinessException("Zone code already exists in warehouse: " + request.code());
        }

        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        Zone zone = Zone.builder()
                .tenantId(tenantId)
                .warehouse(warehouse)
                .code(request.code())
                .name(request.name())
                .type(request.type() != null ? request.type() : LocationType.STORAGE)
                .active(true)
                .createdBy(actor)
                .build();
        return toResponse(zoneRepository.save(zone));
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> findAllByWarehouse(UUID warehouseId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return zoneRepository.findByTenantIdAndWarehouseId(tenantId, warehouseId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ZoneResponse findById(UUID id) {
        return toResponse(getByTenant(id));
    }

    public ZoneResponse update(UUID id, UpdateZoneRequest request) {
        Zone zone = getByTenant(id);
        zone.setName(request.name());
        zone.setType(request.type());
        return toResponse(zoneRepository.save(zone));
    }

    public void deactivate(UUID id) {
        Zone zone = getByTenant(id);
        zone.setActive(false);
        zoneRepository.save(zone);
    }

    Zone getByTenant(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return zoneRepository.findById(id)
                .filter(z -> z.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + id));
    }

    private ZoneResponse toResponse(Zone z) {
        return new ZoneResponse(
                z.getId(),
                z.getWarehouse().getId(),
                z.getCode(),
                z.getName(),
                z.getType(),
                z.getActive(),
                z.getCreatedBy(),
                z.getCreatedAt(),
                z.getUpdatedAt()
        );
    }
}
