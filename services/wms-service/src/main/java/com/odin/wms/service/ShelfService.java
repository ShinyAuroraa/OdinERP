package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.Aisle;
import com.odin.wms.domain.entity.Shelf;
import com.odin.wms.domain.repository.AisleRepository;
import com.odin.wms.domain.repository.ShelfRepository;
import com.odin.wms.dto.request.CreateShelfRequest;
import com.odin.wms.dto.request.UpdateShelfRequest;
import com.odin.wms.dto.response.ShelfResponse;
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
public class ShelfService {

    private final ShelfRepository shelfRepository;
    private final AisleRepository aisleRepository;

    public ShelfResponse create(UUID aisleId, CreateShelfRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Aisle aisle = aisleRepository.findById(aisleId)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Aisle not found: " + aisleId));

        if (shelfRepository.existsByTenantIdAndAisleIdAndCode(tenantId, aisleId, request.code())) {
            throw new BusinessException("Shelf code already exists in aisle: " + request.code());
        }

        Shelf shelf = Shelf.builder()
                .tenantId(tenantId)
                .aisle(aisle)
                .code(request.code())
                .level(request.level() != null ? request.level() : 1)
                .build();
        return toResponse(shelfRepository.save(shelf));
    }

    @Transactional(readOnly = true)
    public List<ShelfResponse> findAllByAisle(UUID aisleId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return shelfRepository.findByTenantIdAndAisleId(tenantId, aisleId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ShelfResponse findById(UUID id) {
        return toResponse(getByTenant(id));
    }

    public ShelfResponse update(UUID id, UpdateShelfRequest request) {
        Shelf shelf = getByTenant(id);
        shelf.setLevel(request.level());
        return toResponse(shelfRepository.save(shelf));
    }

    Shelf getByTenant(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return shelfRepository.findById(id)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Shelf not found: " + id));
    }

    private ShelfResponse toResponse(Shelf s) {
        return new ShelfResponse(
                s.getId(),
                s.getAisle().getId(),
                s.getCode(),
                s.getLevel(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
