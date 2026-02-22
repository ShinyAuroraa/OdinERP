package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.Aisle;
import com.odin.wms.domain.entity.Zone;
import com.odin.wms.domain.repository.AisleRepository;
import com.odin.wms.domain.repository.ZoneRepository;
import com.odin.wms.dto.request.CreateAisleRequest;
import com.odin.wms.dto.request.UpdateAisleRequest;
import com.odin.wms.dto.response.AisleResponse;
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
public class AisleService {

    private final AisleRepository aisleRepository;
    private final ZoneRepository zoneRepository;

    public AisleResponse create(UUID zoneId, CreateAisleRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Zone zone = zoneRepository.findById(zoneId)
                .filter(z -> z.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + zoneId));

        if (!zone.getActive()) {
            throw new BusinessException("Cannot create aisle in inactive zone: " + zoneId);
        }

        if (aisleRepository.existsByTenantIdAndZoneIdAndCode(tenantId, zoneId, request.code())) {
            throw new BusinessException("Aisle code already exists in zone: " + request.code());
        }

        Aisle aisle = Aisle.builder()
                .tenantId(tenantId)
                .zone(zone)
                .code(request.code())
                .name(request.name())
                .build();
        return toResponse(aisleRepository.save(aisle));
    }

    @Transactional(readOnly = true)
    public List<AisleResponse> findAllByZone(UUID zoneId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return aisleRepository.findByTenantIdAndZoneId(tenantId, zoneId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AisleResponse findById(UUID id) {
        return toResponse(getByTenant(id));
    }

    public AisleResponse update(UUID id, UpdateAisleRequest request) {
        Aisle aisle = getByTenant(id);
        aisle.setName(request.name());
        return toResponse(aisleRepository.save(aisle));
    }

    Aisle getByTenant(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return aisleRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Aisle not found: " + id));
    }

    private AisleResponse toResponse(Aisle a) {
        return new AisleResponse(
                a.getId(),
                a.getZone().getId(),
                a.getCode(),
                a.getName(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
