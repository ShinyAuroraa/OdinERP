package com.odin.wms.service;

import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.ConfirmReceivingItemRequest;
import com.odin.wms.dto.request.CreateReceivingNoteItemRequest;
import com.odin.wms.dto.request.CreateReceivingNoteRequest;
import com.odin.wms.dto.response.ReceivingNoteItemResponse;
import com.odin.wms.dto.response.ReceivingNoteResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReceivingNoteService {

    /** UUID de sistema para movimentos originados por consumers Kafka (sem JWT). */
    static final UUID SYSTEM_OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ReceivingNoteRepository receivingNoteRepository;
    private final ReceivingNoteItemRepository receivingNoteItemRepository;
    private final LocationRepository locationRepository;
    private final ProductWmsRepository productWmsRepository;
    private final LotRepository lotRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogRepository auditLogRepository;
    private final SerialNumberRepository serialNumberRepository;

    /**
     * Cria nota de recebimento manualmente via REST.
     * Valida que a doca pertence ao warehouse e tenant informados.
     */
    public ReceivingNoteResponse create(CreateReceivingNoteRequest request, UUID tenantId) {
        Location dockLocation = locationRepository
                .findReceivingDockByIdAndWarehouse(request.dockLocationId(), request.warehouseId(), tenantId)
                .orElseThrow(() -> new BusinessException(
                        "Dock location não pertence ao warehouse informado"));

        ReceivingNote note = ReceivingNote.builder()
                .tenantId(tenantId)
                .warehouse(dockLocation.getShelf().getAisle().getZone().getWarehouse())
                .dockLocation(dockLocation)
                .purchaseOrderRef(request.purchaseOrderRef())
                .supplierId(request.supplierId())
                .build();

        for (CreateReceivingNoteItemRequest itemRequest : request.items()) {
            ProductWms product = productWmsRepository
                    .findByIdAndTenantId(itemRequest.productId(), tenantId)
                    .orElseThrow(() -> new BusinessException(
                            "Produto não encontrado: " + itemRequest.productId()));

            ReceivingNoteItem item = ReceivingNoteItem.builder()
                    .tenantId(tenantId)
                    .receivingNote(note)
                    .product(product)
                    .expectedQuantity(itemRequest.expectedQuantity())
                    .build();
            note.getItems().add(item);
        }

        return toResponse(receivingNoteRepository.save(note));
    }

    /**
     * Cria nota de recebimento via evento Kafka.
     * Idempotente: ignora se o purchaseOrderRef já existe para o tenant.
     */
    public ReceivingNote createFromKafkaEvent(UUID tenantId, String purchaseOrderRef,
                                               UUID warehouseId, UUID supplierId,
                                               List<CreateReceivingNoteItemRequest> items) {
        if (receivingNoteRepository.existsByTenantIdAndPurchaseOrderRef(tenantId, purchaseOrderRef)) {
            log.warn("PO {} já processado para tenant {} — ignorando evento duplicado",
                    purchaseOrderRef, tenantId);
            return null;
        }

        List<Location> docks = locationRepository.findReceivingDocksByWarehouse(warehouseId, tenantId);
        if (docks.isEmpty()) {
            throw new BusinessException("Nenhuma RECEIVING_DOCK encontrada para warehouse " + warehouseId);
        }
        Location dockLocation = docks.getFirst();

        ReceivingNote note = ReceivingNote.builder()
                .tenantId(tenantId)
                .warehouse(dockLocation.getShelf().getAisle().getZone().getWarehouse())
                .dockLocation(dockLocation)
                .purchaseOrderRef(purchaseOrderRef)
                .supplierId(supplierId)
                .build();

        for (CreateReceivingNoteItemRequest itemRequest : items) {
            productWmsRepository.findByIdAndTenantId(itemRequest.productId(), tenantId)
                    .ifPresentOrElse(product -> {
                        ReceivingNoteItem item = ReceivingNoteItem.builder()
                                .tenantId(tenantId)
                                .receivingNote(note)
                                .product(product)
                                .expectedQuantity(itemRequest.expectedQuantity())
                                .build();
                        note.getItems().add(item);
                    }, () -> log.warn("Produto {} não encontrado para tenant {} — item ignorado",
                            itemRequest.productId(), tenantId));
        }

        return receivingNoteRepository.save(note);
    }

    /**
     * Inicia a conferência da nota: PENDING → IN_PROGRESS.
     */
    public ReceivingNoteResponse start(UUID noteId, UUID tenantId) {
        ReceivingNote note = getByTenant(noteId, tenantId);
        if (note.getStatus() != ReceivingStatus.PENDING) {
            throw new ConflictException("Nota não está com status PENDING. Status atual: " + note.getStatus());
        }
        note.setStatus(ReceivingStatus.IN_PROGRESS);
        return toResponse(receivingNoteRepository.save(note));
    }

    /**
     * Confirma um item da nota com quantidade real, lote e demais atributos.
     */
    public ReceivingNoteResponse confirmItem(UUID noteId, UUID itemId,
                                              ConfirmReceivingItemRequest request,
                                              UUID tenantId) {
        ReceivingNote note = getByTenant(noteId, tenantId);
        if (note.getStatus() != ReceivingStatus.IN_PROGRESS) {
            throw new ConflictException("Nota não está IN_PROGRESS. Status atual: " + note.getStatus());
        }

        ReceivingNoteItem item = receivingNoteItemRepository
                .findByReceivingNoteIdAndId(noteId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item não encontrado: " + itemId));

        ProductWms product = item.getProduct();
        validateProductControls(product, request);

        item.setReceivedQuantity(request.receivedQuantity());
        item.setLotNumber(request.lotNumber());
        item.setManufacturingDate(request.manufacturingDate());
        item.setExpiryDate(request.expiryDate());
        item.setGs1Code(request.gs1Code());

        if (request.receivedQuantity() == 0) {
            item.setItemStatus(ReceivingItemStatus.FLAGGED);
            item.setDivergenceType(DivergenceType.DAMAGED);
        } else {
            item.setItemStatus(ReceivingItemStatus.CONFIRMED);
            if (request.receivedQuantity() < item.getExpectedQuantity()) {
                item.setDivergenceType(DivergenceType.SHORT);
            } else if (request.receivedQuantity() > item.getExpectedQuantity()) {
                item.setDivergenceType(DivergenceType.EXCESS);
            } else {
                item.setDivergenceType(DivergenceType.NONE);
            }
        }

        receivingNoteItemRepository.save(item);
        return toResponse(note);
    }

    /**
     * Conclui a nota após todos os itens confirmados.
     * Cria StockItem, StockMovement e AuditLog para cada item CONFIRMED.
     */
    public ReceivingNoteResponse complete(UUID noteId, UUID tenantId) {
        ReceivingNote note = getByTenant(noteId, tenantId);
        if (note.getStatus() != ReceivingStatus.IN_PROGRESS) {
            throw new ConflictException("Nota não está IN_PROGRESS. Status atual: " + note.getStatus());
        }

        long pendingCount = receivingNoteItemRepository
                .countByReceivingNoteIdAndItemStatus(noteId, ReceivingItemStatus.PENDING);
        if (pendingCount > 0) {
            throw new BusinessException("Ainda há " + pendingCount + " item(ns) pendente(s) de confirmação");
        }

        UUID operatorId = extractOperatorId();
        boolean hasFlagged = false;

        for (ReceivingNoteItem item : note.getItems()) {
            if (item.getItemStatus() == ReceivingItemStatus.FLAGGED) {
                hasFlagged = true;
                continue;
            }
            if (item.getItemStatus() == ReceivingItemStatus.CONFIRMED && item.getReceivedQuantity() > 0) {
                processConfirmedItem(item, note, tenantId, operatorId);
            }
        }

        note.setStatus(hasFlagged ? ReceivingStatus.COMPLETED_WITH_DIVERGENCE : ReceivingStatus.COMPLETED);
        return toResponse(receivingNoteRepository.save(note));
    }

    /**
     * Aprova itens divergentes — apenas SUPERVISOR ou ADMIN.
     * Cria StockItem com quantityDamaged para cada item FLAGGED com qty > 0.
     */
    public ReceivingNoteResponse approveDivergences(UUID noteId, UUID tenantId) {
        ReceivingNote note = getByTenant(noteId, tenantId);
        if (note.getStatus() != ReceivingStatus.COMPLETED_WITH_DIVERGENCE) {
            throw new ConflictException("Nota não está COMPLETED_WITH_DIVERGENCE. Status atual: " + note.getStatus());
        }

        UUID operatorId = extractOperatorId();

        for (ReceivingNoteItem item : note.getItems()) {
            if (item.getItemStatus() == ReceivingItemStatus.FLAGGED && item.getReceivedQuantity() != null
                    && item.getReceivedQuantity() > 0) {
                processDamagedItem(item, note, tenantId, operatorId);
            }
        }

        note.setStatus(ReceivingStatus.COMPLETED);
        return toResponse(receivingNoteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public ReceivingNoteResponse findById(UUID noteId, UUID tenantId) {
        return toResponse(getByTenant(noteId, tenantId));
    }

    @Transactional(readOnly = true)
    public List<ReceivingNoteResponse> findAll(UUID tenantId, ReceivingStatus status, UUID warehouseId) {
        List<ReceivingNote> notes;
        if (status != null && warehouseId != null) {
            notes = receivingNoteRepository.findByTenantIdAndStatus(tenantId, status).stream()
                    .filter(n -> n.getWarehouse().getId().equals(warehouseId))
                    .toList();
        } else if (status != null) {
            notes = receivingNoteRepository.findByTenantIdAndStatus(tenantId, status);
        } else if (warehouseId != null) {
            notes = receivingNoteRepository.findByTenantIdAndWarehouseId(tenantId, warehouseId);
        } else {
            notes = receivingNoteRepository.findByTenantId(tenantId);
        }
        return notes.stream().map(this::toResponse).toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateProductControls(ProductWms product, ConfirmReceivingItemRequest request) {
        if (Boolean.TRUE.equals(product.getControlsLot()) && request.lotNumber() == null) {
            throw new BusinessException("Produto requer lote obrigatório");
        }
        if (Boolean.TRUE.equals(product.getControlsExpiry())) {
            if (request.expiryDate() == null) {
                throw new BusinessException("Produto requer data de validade obrigatória");
            }
            if (!request.expiryDate().isAfter(LocalDate.now())) {
                throw new BusinessException("Produto com validade expirada não pode ser recebido");
            }
        }
        if (Boolean.TRUE.equals(product.getControlsSerial())) {
            int serialCount = request.serialNumbers() == null ? 0 : request.serialNumbers().size();
            if (serialCount != request.receivedQuantity()) {
                throw new BusinessException(
                        "Produto requer número de série: esperados " + request.receivedQuantity()
                                + " seriais, recebidos " + serialCount);
            }
        }
    }

    private void processConfirmedItem(ReceivingNoteItem item, ReceivingNote note,
                                       UUID tenantId, UUID operatorId) {
        ProductWms product = item.getProduct();

        Lot lot = null;
        if (Boolean.TRUE.equals(product.getControlsLot())) {
            lot = lotRepository
                    .findByTenantIdAndProductIdAndLotNumber(tenantId, product.getId(), item.getLotNumber())
                    .orElseGet(() -> lotRepository.save(Lot.builder()
                            .tenantId(tenantId)
                            .product(product)
                            .lotNumber(item.getLotNumber())
                            .manufacturingDate(item.getManufacturingDate())
                            .expiryDate(item.getExpiryDate())
                            .supplierId(note.getSupplierId())
                            .build()));
        }

        StockItem stockItem = stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId)
                .location(note.getDockLocation())
                .product(product)
                .lot(lot)
                .quantityAvailable(item.getReceivedQuantity())
                .build());

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .type(MovementType.INBOUND)
                .product(product)
                .lot(lot)
                .destinationLocation(note.getDockLocation())
                .quantity(item.getReceivedQuantity())
                .referenceType(ReferenceType.RECEIVING_NOTE)
                .referenceId(note.getId())
                .operatorId(operatorId)
                .build());

        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("STOCK_ITEM")
                .entityId(stockItem.getId())
                .action(AuditAction.CREATE)
                .actorId(operatorId)
                .build());

        if (Boolean.TRUE.equals(product.getControlsSerial()) && item.getGs1Code() != null) {
            createSerialNumbers(item, product, lot, note.getDockLocation(), tenantId);
        }

        item.setStockItem(stockItem);
        item.setLot(lot);
        receivingNoteItemRepository.save(item);
    }

    private void processDamagedItem(ReceivingNoteItem item, ReceivingNote note,
                                     UUID tenantId, UUID operatorId) {
        ProductWms product = item.getProduct();

        StockItem stockItem = stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId)
                .location(note.getDockLocation())
                .product(product)
                .quantityAvailable(0)
                .quantityDamaged(item.getReceivedQuantity())
                .build());

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .type(MovementType.INBOUND)
                .product(product)
                .destinationLocation(note.getDockLocation())
                .quantity(item.getReceivedQuantity())
                .referenceType(ReferenceType.RECEIVING_NOTE)
                .referenceId(note.getId())
                .operatorId(operatorId)
                .reason("DAMAGED — aprovado por supervisor")
                .build());

        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("STOCK_ITEM")
                .entityId(stockItem.getId())
                .action(AuditAction.CREATE)
                .actorId(operatorId)
                .build());

        item.setStockItem(stockItem);
        receivingNoteItemRepository.save(item);
    }

    private void createSerialNumbers(ReceivingNoteItem item, ProductWms product, Lot lot,
                                      Location location, UUID tenantId) {
        if (item.getGs1Code() == null) return;
        serialNumberRepository.save(SerialNumber.builder()
                .tenantId(tenantId)
                .product(product)
                .lot(lot)
                .serialNumber(item.getGs1Code())
                .location(location)
                .build());
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

    private ReceivingNote getByTenant(UUID noteId, UUID tenantId) {
        return receivingNoteRepository.findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Nota de recebimento não encontrada: " + noteId));
    }

    private ReceivingNoteResponse toResponse(ReceivingNote note) {
        List<ReceivingNoteItemResponse> items = note.getItems().stream()
                .map(item -> new ReceivingNoteItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getExpectedQuantity(),
                        item.getReceivedQuantity(),
                        item.getDivergenceType(),
                        item.getItemStatus(),
                        item.getLotNumber(),
                        item.getExpiryDate(),
                        item.getGs1Code(),
                        item.getLot() != null ? item.getLot().getId() : null,
                        item.getStockItem() != null ? item.getStockItem().getId() : null
                ))
                .toList();

        return new ReceivingNoteResponse(
                note.getId(),
                note.getTenantId(),
                note.getWarehouse().getId(),
                note.getDockLocation().getId(),
                note.getPurchaseOrderRef(),
                note.getSupplierId(),
                note.getStatus(),
                items,
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
