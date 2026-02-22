package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.CreateLocationRequest;
import com.odin.wms.dto.request.UpdateLocationRequest;
import com.odin.wms.dto.response.LocationResponse;
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
public class LocationService {

    private final LocationRepository locationRepository;
    private final ShelfRepository shelfRepository;
    private final AisleRepository aisleRepository;
    private final ZoneRepository zoneRepository;
    private final WarehouseRepository warehouseRepository;

    public LocationResponse create(UUID shelfId, CreateLocationRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Shelf shelf = shelfRepository.findById(shelfId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Shelf not found: " + shelfId));

        if (locationRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new BusinessException("Location code already exists: " + request.code());
        }

        String fullAddress = computeFullAddress(shelf, request.code(), tenantId);

        Location location = Location.builder()
                .tenantId(tenantId)
                .shelf(shelf)
                .code(request.code())
                .fullAddress(fullAddress)
                .type(request.type() != null ? request.type() : LocationType.STORAGE)
                .capacityUnits(request.capacityUnits())
                .capacityWeightKg(request.capacityWeightKg())
                .active(true)
                .build();

        return toResponse(locationRepository.save(location));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> findAllByShelf(UUID shelfId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return locationRepository.findByTenantIdAndShelfId(tenantId, shelfId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse findById(UUID id) {
        return toResponse(getByTenant(id));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> findByType(LocationType type) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return locationRepository.findByTenantIdAndType(tenantId, type)
                .stream().map(this::toResponse).toList();
    }

    public LocationResponse update(UUID id, UpdateLocationRequest request) {
        Location location = getByTenant(id);
        location.setType(request.type());
        location.setCapacityUnits(request.capacityUnits());
        location.setCapacityWeightKg(request.capacityWeightKg());
        return toResponse(locationRepository.save(location));
    }

    public void deactivate(UUID id) {
        Location location = getByTenant(id);
        location.setActive(false);
        locationRepository.save(location);
    }

    private String computeFullAddress(Shelf shelf, String locationCode, UUID tenantId) {
        Aisle aisle = aisleRepository.findById(shelf.getAisle().getId())
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Aisle not found for shelf: " + shelf.getId()));

        Zone zone = zoneRepository.findById(aisle.getZone().getId())
                .filter(z -> z.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found for aisle: " + aisle.getId()));

        Warehouse warehouse = warehouseRepository.findById(zone.getWarehouse().getId())
                .filter(w -> w.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found for zone: " + zone.getId()));

        return String.format("%s/%s/%s/%s/%s",
                warehouse.getCode(), zone.getCode(),
                aisle.getCode(), shelf.getCode(), locationCode);
    }

    private Location getByTenant(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return locationRepository.findById(id)
                .filter(l -> l.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
    }

    private LocationResponse toResponse(Location l) {
        return new LocationResponse(
                l.getId(),
                l.getShelf().getId(),
                l.getCode(),
                l.getFullAddress(),
                l.getType(),
                l.getCapacityUnits(),
                l.getCapacityWeightKg(),
                l.getActive(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }
}
