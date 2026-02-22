package com.odin.wms.service;

import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.ConfirmPutawayRequest;
import com.odin.wms.dto.response.PutawayTaskResponse;
import com.odin.wms.dto.response.PutawayTaskSummaryResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PutawayService {

    /** UUID de sistema para ações sem contexto de autenticação. */
    static final UUID SYSTEM_OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PutawayTaskRepository putawayTaskRepository;
    private final ReceivingNoteRepository receivingNoteRepository;
    private final LocationRepository locationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Gera tarefas de putaway para todos os StockItems de uma nota de recebimento.
     * Idempotente: retorna 409 se já existem tarefas para a nota.
     * Motor FIFO (controlsExpiry=false) ou FEFO (controlsExpiry=true) por produto.
     */
    public List<PutawayTaskResponse> generateTasks(UUID noteId, UUID tenantId) {
        ReceivingNote note = getReceivingNoteByTenant(noteId, tenantId);

        if (note.getStatus() != ReceivingStatus.COMPLETED
                && note.getStatus() != ReceivingStatus.COMPLETED_WITH_DIVERGENCE) {
            throw new BusinessException(
                    "Nota deve estar COMPLETED ou COMPLETED_WITH_DIVERGENCE. Status atual: " + note.getStatus());
        }

        if (putawayTaskRepository.existsByTenantIdAndReceivingNoteId(tenantId, noteId)) {
            throw new ConflictException("Tarefas de putaway já geradas para a nota: " + noteId);
        }

        UUID warehouseId = note.getDockLocation().getShelf().getAisle().getZone().getWarehouse().getId();

        // Uma query para ocupância de todas as localizações — evita N+1
        Map<UUID, Long> occupancyMap = buildOccupancyMap(tenantId);

        // Localizações de armazenagem do warehouse (STORAGE + PICKING), ativas
        List<Location> storageLocations = locationRepository.findStorageOrPickingByWarehouse(warehouseId, tenantId);

        List<PutawayTask> tasks = new ArrayList<>();

        for (ReceivingNoteItem item : note.getItems()) {
            if (item.getStockItem() == null) {
                // Item FLAGGED sem StockItem criado — ignorar
                continue;
            }

            StockItem stockItem = item.getStockItem();
            ProductWms product = stockItem.getProduct();

            PutawayStrategy strategy = Boolean.TRUE.equals(product.getControlsExpiry())
                    ? PutawayStrategy.FEFO
                    : PutawayStrategy.FIFO;

            Location suggested = suggestLocation(stockItem, tenantId, strategy, storageLocations, occupancyMap);

            // Atualiza ocupância in-memory para refletir a sugestão desta tarefa
            occupancyMap.merge(suggested.getId(), 1L, Long::sum);

            PutawayTask task = PutawayTask.builder()
                    .tenantId(tenantId)
                    .receivingNote(note)
                    .stockItem(stockItem)
                    .sourceLocation(note.getDockLocation())
                    .suggestedLocation(suggested)
                    .strategyUsed(strategy)
                    .build();

            tasks.add(putawayTaskRepository.save(task));
        }

        if (tasks.isEmpty()) {
            throw new BusinessException(
                    "Nenhum item com StockItem encontrado na nota para gerar tarefas de putaway");
        }

        log.info("Geradas {} tarefas de putaway para nota {} (tenant {})", tasks.size(), noteId, tenantId);
        return tasks.stream().map(this::toResponse).toList();
    }

    /**
     * Inicia movimentação física: PENDING → IN_PROGRESS.
     */
    public PutawayTaskResponse start(UUID taskId, UUID tenantId) {
        PutawayTask task = getByTenant(taskId, tenantId);
        if (task.getStatus() != PutawayStatus.PENDING) {
            throw new ConflictException("Tarefa não está PENDING. Status atual: " + task.getStatus());
        }
        task.setStatus(PutawayStatus.IN_PROGRESS);
        task.setStartedAt(Instant.now());
        task.setOperatorId(extractOperatorId());
        return toResponse(putawayTaskRepository.save(task));
    }

    /**
     * Confirma alocação: IN_PROGRESS → CONFIRMED.
     * Se {@code request.confirmedLocationId} for null, usa a localização sugerida.
     * Cria StockMovement(PUTAWAY) e AuditLog(MOVEMENT). Atualiza StockItem.location.
     */
    public PutawayTaskResponse confirm(UUID taskId, ConfirmPutawayRequest request, UUID tenantId) {
        PutawayTask task = getByTenant(taskId, tenantId);
        if (task.getStatus() != PutawayStatus.IN_PROGRESS) {
            throw new ConflictException("Tarefa não está IN_PROGRESS. Status atual: " + task.getStatus());
        }

        Location confirmedLocation = resolveConfirmedLocation(task, request, tenantId);

        validateCapacity(confirmedLocation, tenantId);

        UUID operatorId = extractOperatorId();

        // Atualiza localização do StockItem para a localização confirmada
        StockItem stockItem = task.getStockItem();
        stockItem.setLocation(confirmedLocation);
        stockItemRepository.save(stockItem);

        // Rastreio de movimentação
        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .type(MovementType.PUTAWAY)
                .product(stockItem.getProduct())
                .lot(stockItem.getLot())
                .sourceLocation(task.getSourceLocation())
                .destinationLocation(confirmedLocation)
                .quantity(stockItem.getQuantityAvailable())
                .referenceType(ReferenceType.PUTAWAY_TASK)
                .referenceId(task.getId())
                .operatorId(operatorId)
                .build());

        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PUTAWAY_TASK")
                .entityId(task.getId())
                .action(AuditAction.MOVEMENT)
                .actorId(operatorId)
                .newValue("{\"sourceLocationId\":\"" + task.getSourceLocation().getId()
                        + "\",\"confirmedLocationId\":\"" + confirmedLocation.getId() + "\"}")
                .build());

        task.setStatus(PutawayStatus.CONFIRMED);
        task.setConfirmedLocation(confirmedLocation);
        task.setConfirmedAt(Instant.now());
        task.setOperatorId(operatorId);

        log.info("Tarefa {} confirmada: {} → {} (tenant {})",
                taskId, task.getSourceLocation().getId(), confirmedLocation.getId(), tenantId);
        return toResponse(putawayTaskRepository.save(task));
    }

    /**
     * Cancela tarefa: PENDING ou IN_PROGRESS → CANCELLED.
     * StockItem permanece na dock; nenhum StockMovement é criado.
     */
    public void cancel(UUID taskId, UUID tenantId) {
        PutawayTask task = getByTenant(taskId, tenantId);
        if (task.getStatus() == PutawayStatus.CONFIRMED || task.getStatus() == PutawayStatus.CANCELLED) {
            throw new ConflictException("Tarefa não pode ser cancelada. Status atual: " + task.getStatus());
        }
        task.setStatus(PutawayStatus.CANCELLED);
        task.setCancelledAt(Instant.now());
        task.setOperatorId(extractOperatorId());
        putawayTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public PutawayTaskResponse findById(UUID taskId, UUID tenantId) {
        return toResponse(getByTenant(taskId, tenantId));
    }

    @Transactional(readOnly = true)
    public List<PutawayTaskSummaryResponse> findAll(UUID tenantId, UUID warehouseId, PutawayStatus status) {
        List<PutawayTask> tasks = status != null
                ? putawayTaskRepository.findByTenantIdAndStatus(tenantId, status)
                : putawayTaskRepository.findByTenantId(tenantId);

        if (warehouseId != null) {
            // Filtro in-memory — evita JPQL complexo; tech debt documentado
            tasks = tasks.stream()
                    .filter(t -> warehouseId.equals(
                            locationRepository.findWarehouseIdByLocationId(
                                    t.getSourceLocation().getId()).orElse(null)))
                    .toList();
        }

        return tasks.stream().map(this::toSummaryResponse).toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sugere localização de armazenagem usando FIFO ou FEFO.
     * Prioriza co-localização com o mesmo produto.
     * Fallback: primeira localização com capacidade disponível.
     */
    private Location suggestLocation(StockItem stockItem, UUID tenantId, PutawayStrategy strategy,
                                     List<Location> storageLocations, Map<UUID, Long> occupancyMap) {
        UUID productId = stockItem.getProduct().getId();

        List<UUID> preferredIds = strategy == PutawayStrategy.FEFO
                ? stockItemRepository.findLocationIdsWithProductByExpiryFEFO(tenantId, productId)
                : stockItemRepository.findLocationIdsWithProduct(tenantId, productId);

        // Mapa id → Location para lookup O(1)
        Map<UUID, Location> locationById = storageLocations.stream()
                .collect(Collectors.toMap(Location::getId, l -> l));

        // Co-localização preferencial
        for (UUID locId : preferredIds) {
            Location loc = locationById.get(locId);
            if (loc != null && hasCapacity(loc, occupancyMap)) {
                return loc;
            }
        }

        // Fallback: qualquer localização com capacidade
        for (Location loc : storageLocations) {
            if (hasCapacity(loc, occupancyMap)) {
                return loc;
            }
        }

        throw new BusinessException(
                "Nenhuma localização de armazenagem disponível para o item " + stockItem.getId()
                        + ". Verifique a capacidade do armazém.");
    }

    private boolean hasCapacity(Location location, Map<UUID, Long> occupancyMap) {
        if (location.getCapacityUnits() == null) return true;
        long current = occupancyMap.getOrDefault(location.getId(), 0L);
        return current < location.getCapacityUnits();
    }

    private Map<UUID, Long> buildOccupancyMap(UUID tenantId) {
        List<Object[]> rows = stockItemRepository.countByTenantIdGroupByLocationId(tenantId);
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }

    private Location resolveConfirmedLocation(PutawayTask task, ConfirmPutawayRequest request,
                                               UUID tenantId) {
        if (request == null || request.confirmedLocationId() == null) {
            return task.getSuggestedLocation();
        }

        Location loc = locationRepository.findById(request.confirmedLocationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Localização não encontrada: " + request.confirmedLocationId()));

        UUID sourceWarehouseId = locationRepository
                .findWarehouseIdByLocationId(task.getSourceLocation().getId())
                .orElseThrow(() -> new BusinessException(
                        "Não foi possível determinar o warehouse da dock de origem"));

        UUID confirmedWarehouseId = locationRepository
                .findWarehouseIdByLocationId(loc.getId())
                .orElseThrow(() -> new BusinessException(
                        "Não foi possível determinar o warehouse da localização confirmada"));

        if (!sourceWarehouseId.equals(confirmedWarehouseId)) {
            throw new BusinessException(
                    "Localização confirmada pertence a warehouse diferente da dock de origem");
        }

        if (loc.getType() != LocationType.STORAGE && loc.getType() != LocationType.PICKING) {
            throw new BusinessException(
                    "Localização confirmada deve ser do tipo STORAGE ou PICKING. Tipo atual: " + loc.getType());
        }

        if (!Boolean.TRUE.equals(loc.getActive())) {
            throw new BusinessException(
                    "Localização confirmada está inativa: " + loc.getCode());
        }

        return loc;
    }

    private void validateCapacity(Location location, UUID tenantId) {
        if (location.getCapacityUnits() == null) return;
        long current = stockItemRepository.countByTenantIdAndLocationId(tenantId, location.getId());
        if (current >= location.getCapacityUnits()) {
            throw new BusinessException(
                    "Localização " + location.getCode() + " está na capacidade máxima ("
                            + location.getCapacityUnits() + " unidades)");
        }
    }

    private UUID extractOperatorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException e) {
                    return SYSTEM_OPERATOR_ID;
                }
            }
        }
        return SYSTEM_OPERATOR_ID;
    }

    private ReceivingNote getReceivingNoteByTenant(UUID noteId, UUID tenantId) {
        return receivingNoteRepository.findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nota de recebimento não encontrada: " + noteId));
    }

    private PutawayTask getByTenant(UUID taskId, UUID tenantId) {
        return putawayTaskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tarefa de putaway não encontrada: " + taskId));
    }

    private PutawayTaskResponse toResponse(PutawayTask task) {
        return new PutawayTaskResponse(
                task.getId(),
                task.getTenantId(),
                task.getReceivingNote().getId(),
                task.getStockItem().getId(),
                task.getSourceLocation().getId(),
                task.getSuggestedLocation().getId(),
                task.getConfirmedLocation() != null ? task.getConfirmedLocation().getId() : null,
                task.getStatus(),
                task.getStrategyUsed(),
                task.getStartedAt(),
                task.getConfirmedAt(),
                task.getCancelledAt(),
                task.getOperatorId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private PutawayTaskSummaryResponse toSummaryResponse(PutawayTask task) {
        return new PutawayTaskSummaryResponse(
                task.getId(),
                task.getReceivingNote().getId(),
                task.getStockItem().getId(),
                task.getSourceLocation().getId(),
                task.getSuggestedLocation().getId(),
                task.getConfirmedLocation() != null ? task.getConfirmedLocation().getId() : null,
                task.getStatus(),
                task.getStrategyUsed(),
                task.getCreatedAt()
        );
    }
}
