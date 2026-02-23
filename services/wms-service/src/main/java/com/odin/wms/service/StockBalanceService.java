package com.odin.wms.service;

import com.odin.wms.domain.entity.StockItem;
import com.odin.wms.domain.entity.Warehouse;
import com.odin.wms.domain.entity.Zone;
import com.odin.wms.domain.repository.LocationRepository;
import com.odin.wms.domain.repository.StockItemRepository;
import com.odin.wms.domain.repository.WarehouseRepository;
import com.odin.wms.domain.repository.ZoneRepository;
import com.odin.wms.dto.response.StockBalanceResponse;
import com.odin.wms.dto.response.WarehouseOccupationResponse;
import com.odin.wms.dto.response.ZoneOccupationResponse;
import com.odin.wms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service de consulta de saldo de estoque em tempo real.
 * Usa Redis cache para baixa latência em leituras frequentes.
 * Cache evictado automaticamente em operações de escrita (PutawayService, QuarantineService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockBalanceService {

    private final StockItemRepository stockItemRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ZoneRepository zoneRepository;

    /**
     * Retorna saldo de estoque com filtros opcionais cumulativos (AND).
     * Resultado cacheado no Redis com chave por tenant + parâmetros.
     *
     * @param tenantId   tenant do JWT (obrigatório)
     * @param productId  filtro por produto (opcional)
     * @param locationId filtro por localização (opcional)
     * @param lotId      filtro por lote (opcional)
     * @param warehouseId filtro por armazém (opcional)
     */
    @Cacheable(
            value = "stockBalance",
            key = "#tenantId.toString() + ':bal:' + #productId + ':' + #locationId + ':' + #lotId + ':' + #warehouseId",
            unless = "#result == null"
    )
    public List<StockBalanceResponse> getBalance(UUID tenantId, UUID productId,
                                                  UUID locationId, UUID lotId,
                                                  UUID warehouseId) {
        log.debug("Cache miss — consultando PostgreSQL: tenant={} product={} loc={} lot={} wh={}",
                tenantId, productId, locationId, lotId, warehouseId);

        List<StockItem> items = stockItemRepository.findByFilters(
                tenantId, productId, locationId, lotId, warehouseId);

        return items.stream().map(this::toBalanceResponse).toList();
    }

    /**
     * Retorna saldo de estoque de uma localização específica.
     * Retorna 404 se a localização não existe ou pertence a outro tenant.
     */
    @Cacheable(
            value = "stockBalance",
            key = "#tenantId.toString() + ':loc:' + #locationId.toString()",
            unless = "#result == null"
    )
    public List<StockBalanceResponse> getLocationBalance(UUID tenantId, UUID locationId) {
        // Valida que a localização existe e pertence ao tenant
        locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Localização não encontrada: " + locationId));

        List<StockItem> items = stockItemRepository.findByFilters(
                tenantId, null, locationId, null, null);

        return items.stream().map(this::toBalanceResponse).toList();
    }

    /**
     * Retorna taxa de ocupação de um armazém, detalhada por zona.
     * Retorna 404 se o warehouse não existe ou pertence a outro tenant.
     */
    @Cacheable(
            value = "stockBalance",
            key = "#tenantId.toString() + ':occ:' + #warehouseId.toString()",
            unless = "#result == null"
    )
    public WarehouseOccupationResponse getOccupation(UUID tenantId, UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .filter(w -> tenantId.equals(w.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse não encontrado: " + warehouseId));

        List<Zone> zones = zoneRepository.findByTenantIdAndWarehouseId(tenantId, warehouseId);

        // Batch queries para evitar N+1
        Map<UUID, Long> totalLocPerZone = toMap(
                locationRepository.countLocationsByZone(tenantId, warehouseId));
        Map<UUID, Long> occupiedLocPerZone = toMap(
                stockItemRepository.countOccupiedLocationsByZone(tenantId, warehouseId));
        Map<UUID, Long> totalCapPerZone = toMap(
                locationRepository.sumCapacityUnitsByZone(tenantId, warehouseId));
        Map<UUID, Long> usedCapPerZone = toMap(
                stockItemRepository.sumQuantitiesByZone(tenantId, warehouseId));

        List<ZoneOccupationResponse> zoneResponses = zones.stream()
                .map(zone -> buildZoneResponse(zone, totalLocPerZone, occupiedLocPerZone,
                        totalCapPerZone, usedCapPerZone))
                .toList();

        long totalLoc = zoneResponses.stream()
                .mapToLong(ZoneOccupationResponse::totalLocations).sum();
        long occupiedLoc = zoneResponses.stream()
                .mapToLong(ZoneOccupationResponse::occupiedLocations).sum();

        return new WarehouseOccupationResponse(
                warehouse.getId(),
                warehouse.getName(),
                totalLoc,
                occupiedLoc,
                occupancyRate(occupiedLoc, totalLoc),
                zoneResponses
        );
    }

    /**
     * Evicta todo o cache de saldo para o tenant (chamado via @CacheEvict nos services de escrita).
     * Método utilitário exposto para uso programático se necessário.
     */
    @CacheEvict(cacheNames = "stockBalance", allEntries = true)
    public void evictAll() {
        log.debug("Cache stockBalance evictado");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private StockBalanceResponse toBalanceResponse(StockItem s) {
        return new StockBalanceResponse(
                s.getId(),
                s.getProduct().getId(),
                s.getProduct().getSku(),
                s.getProduct().getName(),
                s.getLocation().getId(),
                s.getLocation().getCode(),
                s.getLocation().getType(),
                s.getLot() != null ? s.getLot().getId() : null,
                s.getLot() != null ? s.getLot().getLotNumber() : null,
                s.getQuantityAvailable(),
                s.getQuantityReserved(),
                s.getQuantityQuarantine(),
                s.getQuantityDamaged(),
                s.getReceivedAt()
        );
    }

    private ZoneOccupationResponse buildZoneResponse(Zone zone,
                                                      Map<UUID, Long> totalLocPerZone,
                                                      Map<UUID, Long> occupiedLocPerZone,
                                                      Map<UUID, Long> totalCapPerZone,
                                                      Map<UUID, Long> usedCapPerZone) {
        long total = totalLocPerZone.getOrDefault(zone.getId(), 0L);
        long occupied = occupiedLocPerZone.getOrDefault(zone.getId(), 0L);

        return new ZoneOccupationResponse(
                zone.getId(),
                zone.getName(),
                total,
                occupied,
                occupancyRate(occupied, total),
                totalCapPerZone.getOrDefault(zone.getId(), 0L),
                usedCapPerZone.getOrDefault(zone.getId(), 0L)
        );
    }

    /**
     * Taxa de ocupação: (occupied / total) × 100, arredondada em 1 casa decimal.
     * Retorna 0.0 se total == 0 (evita divisão por zero).
     */
    static double occupancyRate(long occupied, long total) {
        if (total == 0) return 0.0;
        return Math.round(occupied * 1000.0 / total) / 10.0;
    }

    /**
     * Converte List<Object[]> de query JPQL em Map<UUID, Long>.
     * Esperado: row[0] = UUID (zone/location id), row[1] = Number.
     */
    private Map<UUID, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(
                Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                )
        );
    }
}
