package com.odin.wms.service;

import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.DecideQuarantineRequest;
import com.odin.wms.dto.response.QuarantineTaskResponse;
import com.odin.wms.dto.response.QuarantineTaskSummaryResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.messaging.QuarantineEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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
public class QuarantineService {

    /** UUID de sistema para ações sem contexto de autenticação. */
    static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final QuarantineTaskRepository quarantineTaskRepository;
    private final ReceivingNoteRepository receivingNoteRepository;
    private final ReceivingNoteItemRepository receivingNoteItemRepository;
    private final LocationRepository locationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogRepository auditLogRepository;
    private final QuarantineEventPublisher eventPublisher;

    /**
     * Gera tarefas de quarentena para todos os itens FLAGGED de uma nota COMPLETED_WITH_DIVERGENCE.
     * Idempotente: retorna 409 se já existem tasks para a nota.
     * Move cada StockItem da dock para a quarantineLocation e cria StockMovement(QUARANTINE_IN).
     */
    public List<QuarantineTaskResponse> generateTasks(UUID noteId, UUID tenantId) {
        ReceivingNote note = getReceivingNoteByTenant(noteId, tenantId);

        if (note.getStatus() != ReceivingStatus.COMPLETED_WITH_DIVERGENCE) {
            throw new BusinessException(
                    "Nota deve estar COMPLETED_WITH_DIVERGENCE para gerar tarefas de quarentena. Status atual: "
                            + note.getStatus());
        }

        if (quarantineTaskRepository.existsByTenantIdAndReceivingNoteId(tenantId, noteId)) {
            throw new ConflictException("Tarefas de quarentena já geradas para a nota: " + noteId);
        }

        UUID warehouseId = note.getDockLocation().getShelf().getAisle().getZone().getWarehouse().getId();

        List<Location> quarantineLocations = locationRepository.findQuarantineByWarehouse(warehouseId, tenantId);
        if (quarantineLocations.isEmpty()) {
            throw new BusinessException(
                    "Nenhuma localização do tipo QUARANTINE ativa encontrada no warehouse. "
                            + "Cadastre ao menos uma location QUARANTINE antes de gerar tasks de quarentena.");
        }
        Location quarantineLocation = quarantineLocations.get(0);

        UUID actorId = extractActorId();
        List<QuarantineTask> tasks = new ArrayList<>();

        for (ReceivingNoteItem item : note.getItems()) {
            if (item.getItemStatus() != ReceivingItemStatus.FLAGGED) {
                continue;
            }

            // Cria StockItem para item FLAGGED se ainda não existir (ex: receivedQuantity=0)
            StockItem stockItem = item.getStockItem();
            if (stockItem == null) {
                stockItem = stockItemRepository.save(StockItem.builder()
                        .tenantId(tenantId)
                        .location(note.getDockLocation())
                        .product(item.getProduct())
                        .lot(item.getLot())
                        .quantityAvailable(item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0)
                        .build());
                item.setStockItem(stockItem);
                receivingNoteItemRepository.save(item);
            }

            // Salvar task primeiro para obter o ID para o referenceId do StockMovement
            QuarantineTask task = QuarantineTask.builder()
                    .tenantId(tenantId)
                    .receivingNote(note)
                    .receivingNoteItem(item)
                    .stockItem(stockItem)
                    .sourceLocation(note.getDockLocation())
                    .quarantineLocation(quarantineLocation)
                    .build();
            task = quarantineTaskRepository.save(task);

            // Mover StockItem para quarentena
            stockItem.setLocation(quarantineLocation);
            stockItemRepository.save(stockItem);

            // Registrar movimentação de entrada na quarentena (apenas se qty > 0; constraint db proíbe qty=0)
            int quarantineInQty = stockItem.getQuantityAvailable();
            if (quarantineInQty > 0) {
                stockMovementRepository.save(StockMovement.builder()
                        .tenantId(tenantId)
                        .type(MovementType.QUARANTINE_IN)
                        .product(stockItem.getProduct())
                        .lot(stockItem.getLot())
                        .sourceLocation(note.getDockLocation())
                        .destinationLocation(quarantineLocation)
                        .quantity(quarantineInQty)
                        .referenceType(ReferenceType.QUARANTINE_TASK)
                        .referenceId(task.getId())
                        .operatorId(actorId)
                        .build());
            }

            tasks.add(task);
        }

        if (tasks.isEmpty()) {
            throw new BusinessException(
                    "Nenhum item FLAGGED com StockItem encontrado na nota para gerar tarefas de quarentena");
        }

        log.info("Geradas {} tarefas de quarentena para nota {} (tenant {})", tasks.size(), noteId, tenantId);
        return tasks.stream().map(this::toResponse).toList();
    }

    /**
     * Inicia inspeção de QC: PENDING → IN_REVIEW.
     * Requer role WMS_SUPERVISOR ou WMS_ADMIN.
     */
    public QuarantineTaskResponse start(UUID taskId, UUID tenantId) {
        QuarantineTask task = getByTenant(taskId, tenantId);
        if (task.getStatus() != QuarantineStatus.PENDING) {
            throw new ConflictException("Tarefa não está PENDING. Status atual: " + task.getStatus());
        }
        UUID supervisorId = extractActorId();
        task.setStatus(QuarantineStatus.IN_REVIEW);
        task.setStartedAt(Instant.now());
        task.setSupervisorId(supervisorId);
        return toResponse(quarantineTaskRepository.save(task));
    }

    /**
     * Decide o destino do item em quarentena: RELEASE_TO_STOCK, RETURN_TO_SUPPLIER ou SCRAP.
     * Task deve estar IN_REVIEW → 409 caso contrário.
     * Requer role WMS_SUPERVISOR ou WMS_ADMIN.
     * Evicta cache de saldo de estoque (Story 4.1 — AC6).
     */
    @CacheEvict(cacheNames = "stockBalance", allEntries = true)
    public QuarantineTaskResponse decide(UUID taskId, DecideQuarantineRequest request, UUID tenantId) {
        QuarantineTask task = getByTenant(taskId, tenantId);
        if (task.getStatus() != QuarantineStatus.IN_REVIEW) {
            throw new ConflictException("Tarefa não está IN_REVIEW. Status atual: " + task.getStatus());
        }

        UUID supervisorId = extractActorId();

        switch (request.decision()) {
            case RELEASE_TO_STOCK -> handleRelease(task, tenantId, supervisorId);
            case RETURN_TO_SUPPLIER -> handleReturn(task, tenantId, supervisorId);
            case SCRAP -> handleScrap(task, tenantId, supervisorId);
        }

        QuarantineStatus finalStatus = request.decision() == QuarantineDecision.RELEASE_TO_STOCK
                ? QuarantineStatus.APPROVED
                : QuarantineStatus.REJECTED;

        task.setStatus(finalStatus);
        task.setDecision(request.decision());
        task.setQualityNotes(request.qualityNotes());
        task.setDecidedAt(Instant.now());
        task.setSupervisorId(supervisorId);

        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("QUARANTINE_TASK")
                .entityId(task.getId())
                .action(AuditAction.MOVEMENT)
                .actorId(supervisorId)
                .newValue(String.format("{\"decision\":\"%s\",\"quarantineLocationId\":\"%s\"}",
                        request.decision(), task.getQuarantineLocation().getId()))
                .build());

        log.info("Tarefa de quarentena {} decidida: {} (tenant {})", taskId, request.decision(), tenantId);
        return toResponse(quarantineTaskRepository.save(task));
    }

    /**
     * Cancela tarefa: PENDING ou IN_REVIEW → CANCELLED.
     * StockItem permanece na quarantineLocation; nenhum movimento adicional.
     * Requer role WMS_SUPERVISOR ou WMS_ADMIN.
     * Evicta cache de saldo de estoque (Story 4.1 — AC6).
     */
    @CacheEvict(cacheNames = "stockBalance", allEntries = true)
    public void cancel(UUID taskId, UUID tenantId) {
        QuarantineTask task = getByTenant(taskId, tenantId);
        if (task.getStatus() == QuarantineStatus.APPROVED
                || task.getStatus() == QuarantineStatus.REJECTED) {
            throw new ConflictException("Tarefa não pode ser cancelada. Status atual: " + task.getStatus());
        }
        if (task.getStatus() == QuarantineStatus.CANCELLED) {
            throw new ConflictException("Tarefa já está CANCELLED.");
        }
        task.setStatus(QuarantineStatus.CANCELLED);
        task.setCancelledAt(Instant.now());
        quarantineTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public QuarantineTaskResponse findById(UUID taskId, UUID tenantId) {
        return toResponse(getByTenant(taskId, tenantId));
    }

    @Transactional(readOnly = true)
    public List<QuarantineTaskSummaryResponse> findAll(UUID tenantId, QuarantineStatus status, UUID warehouseId) {
        List<QuarantineTask> tasks;

        if (warehouseId != null && status != null) {
            tasks = quarantineTaskRepository.findByTenantIdAndStatusAndWarehouseId(tenantId, status, warehouseId);
        } else if (warehouseId != null) {
            tasks = quarantineTaskRepository.findByTenantIdAndWarehouseId(tenantId, warehouseId);
        } else if (status != null) {
            tasks = quarantineTaskRepository.findByTenantIdAndStatus(tenantId, status);
        } else {
            tasks = quarantineTaskRepository.findByTenantId(tenantId);
        }

        return tasks.stream().map(this::toSummaryResponse).toList();
    }

    // -------------------------------------------------------------------------
    // Decision handlers
    // -------------------------------------------------------------------------

    /**
     * RELEASE_TO_STOCK: move o item da quarentena para STORAGE/PICKING.
     * Reutiliza lógica de sugestão de localização do PutawayService (tech debt: duplicação a extrair em Story 4.x).
     */
    private void handleRelease(QuarantineTask task, UUID tenantId, UUID supervisorId) {
        UUID warehouseId = locationRepository
                .findWarehouseIdByLocationId(task.getQuarantineLocation().getId())
                .orElseThrow(() -> new BusinessException(
                        "Não foi possível determinar o warehouse da localização de quarentena"));

        Map<UUID, Long> occupancyMap = buildOccupancyMap(tenantId);
        List<Location> storageLocations = locationRepository.findStorageOrPickingByWarehouse(warehouseId, tenantId);

        StockItem stockItem = task.getStockItem();
        ProductWms product = stockItem.getProduct();
        PutawayStrategy strategy = Boolean.TRUE.equals(product.getControlsExpiry())
                ? PutawayStrategy.FEFO
                : PutawayStrategy.FIFO;

        Location storageLocation = suggestStorageLocation(stockItem, tenantId, strategy, storageLocations, occupancyMap);

        stockItem.setLocation(storageLocation);
        stockItemRepository.save(stockItem);

        // Registrar PUTAWAY apenas se qty > 0 (constraint db proíbe quantity=0)
        int putawayQty = stockItem.getQuantityAvailable();
        if (putawayQty > 0) {
            stockMovementRepository.save(StockMovement.builder()
                    .tenantId(tenantId)
                    .type(MovementType.PUTAWAY)
                    .product(stockItem.getProduct())
                    .lot(stockItem.getLot())
                    .sourceLocation(task.getQuarantineLocation())
                    .destinationLocation(storageLocation)
                    .quantity(putawayQty)
                    .referenceType(ReferenceType.QUARANTINE_TASK)
                    .referenceId(task.getId())
                    .operatorId(supervisorId)
                    .build());
        }
    }

    /**
     * RETURN_TO_SUPPLIER: cria QUARANTINE_OUT e publica evento Kafka para SCM.
     * stockItem.quantityAvailable permanece inalterado (SCM processa fisicamente).
     */
    private void handleReturn(QuarantineTask task, UUID tenantId, UUID supervisorId) {
        StockItem stockItem = task.getStockItem();

        // Registrar QUARANTINE_OUT apenas se qty > 0 (constraint db proíbe quantity=0)
        int returnQty = stockItem.getQuantityAvailable();
        if (returnQty > 0) {
            stockMovementRepository.save(StockMovement.builder()
                    .tenantId(tenantId)
                    .type(MovementType.QUARANTINE_OUT)
                    .product(stockItem.getProduct())
                    .lot(stockItem.getLot())
                    .sourceLocation(task.getQuarantineLocation())
                    .quantity(returnQty)
                    .referenceType(ReferenceType.QUARANTINE_TASK)
                    .referenceId(task.getId())
                    .operatorId(supervisorId)
                    .build());
        }

        eventPublisher.publishReturnToSupplier(task);
    }

    /**
     * SCRAP: cria QUARANTINE_OUT com quantidade negativa e zera quantityAvailable.
     */
    private void handleScrap(QuarantineTask task, UUID tenantId, UUID supervisorId) {
        StockItem stockItem = task.getStockItem();
        int qty = stockItem.getQuantityAvailable();

        // Registrar QUARANTINE_OUT com qty negativo apenas se qty > 0 (constraint db proíbe quantity=0)
        if (qty > 0) {
            stockMovementRepository.save(StockMovement.builder()
                    .tenantId(tenantId)
                    .type(MovementType.QUARANTINE_OUT)
                    .product(stockItem.getProduct())
                    .lot(stockItem.getLot())
                    .sourceLocation(task.getQuarantineLocation())
                    .quantity(-qty)
                    .referenceType(ReferenceType.QUARANTINE_TASK)
                    .referenceId(task.getId())
                    .operatorId(supervisorId)
                    .build());
        }

        stockItem.setQuantityAvailable(0);
        stockItemRepository.save(stockItem);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sugere localização STORAGE/PICKING via FIFO ou FEFO.
     * Tech debt: duplicação da lógica em PutawayService — a extrair em LocationSuggestionHelper (Story 4.x).
     */
    private Location suggestStorageLocation(StockItem stockItem, UUID tenantId, PutawayStrategy strategy,
                                             List<Location> storageLocations, Map<UUID, Long> occupancyMap) {
        UUID productId = stockItem.getProduct().getId();

        List<UUID> preferredIds = strategy == PutawayStrategy.FEFO
                ? stockItemRepository.findLocationIdsWithProductByExpiryFEFO(tenantId, productId)
                : stockItemRepository.findLocationIdsWithProduct(tenantId, productId);

        Map<UUID, Location> locationById = storageLocations.stream()
                .collect(Collectors.toMap(Location::getId, l -> l));

        for (UUID locId : preferredIds) {
            Location loc = locationById.get(locId);
            if (loc != null && hasCapacity(loc, occupancyMap)) {
                return loc;
            }
        }

        for (Location loc : storageLocations) {
            if (hasCapacity(loc, occupancyMap)) {
                return loc;
            }
        }

        throw new BusinessException(
                "Nenhuma localização de armazenagem disponível para liberar o item " + stockItem.getId()
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

    private UUID extractActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException e) {
                    return SYSTEM_ACTOR_ID;
                }
            }
        }
        return SYSTEM_ACTOR_ID;
    }

    private ReceivingNote getReceivingNoteByTenant(UUID noteId, UUID tenantId) {
        return receivingNoteRepository.findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nota de recebimento não encontrada: " + noteId));
    }

    private QuarantineTask getByTenant(UUID taskId, UUID tenantId) {
        return quarantineTaskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tarefa de quarentena não encontrada: " + taskId));
    }

    private QuarantineTaskResponse toResponse(QuarantineTask task) {
        return new QuarantineTaskResponse(
                task.getId(),
                task.getTenantId(),
                task.getReceivingNote().getId(),
                task.getReceivingNoteItem().getId(),
                task.getStockItem().getId(),
                task.getSourceLocation().getId(),
                task.getQuarantineLocation().getId(),
                task.getStatus(),
                task.getDecision(),
                task.getQualityNotes(),
                task.getSupervisorId(),
                task.getStartedAt(),
                task.getDecidedAt(),
                task.getCancelledAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private QuarantineTaskSummaryResponse toSummaryResponse(QuarantineTask task) {
        return new QuarantineTaskSummaryResponse(
                task.getId(),
                task.getReceivingNote().getId(),
                task.getStockItem().getId(),
                task.getQuarantineLocation().getId(),
                task.getStatus(),
                task.getDecision(),
                task.getCreatedAt()
        );
    }
}
