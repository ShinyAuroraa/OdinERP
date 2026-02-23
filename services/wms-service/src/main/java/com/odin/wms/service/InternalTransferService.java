package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.InternalTransfer.TransferStatus;
import com.odin.wms.domain.entity.InternalTransfer.TransferType;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.CancelTransferRequest;
import com.odin.wms.dto.request.ConfirmTransferRequest;
import com.odin.wms.dto.request.CreateTransferRequest;
import com.odin.wms.dto.response.InternalTransferResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.StockMovementEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de transferências internas entre posições de armazém.
 * Implementa o ciclo: PENDING → CONFIRMED | CANCELLED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferService {

    static final UUID SYSTEM_OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final InternalTransferRepository internalTransferRepository;
    private final LocationRepository locationRepository;
    private final ProductWmsRepository productWmsRepository;
    private final LotRepository lotRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockBalanceService stockBalanceService;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogIndexer auditLogIndexer;
    private final StockMovementEventPublisher stockMovementEventPublisher;

    // -------------------------------------------------------------------------
    // AC3 — POST /transfers
    // -------------------------------------------------------------------------

    @Transactional
    public InternalTransferResponse createTransfer(CreateTransferRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        if (request.sourceLocationId().equals(request.destinationLocationId())) {
            throw new IllegalArgumentException(
                    "source e destination não podem ser iguais: " + request.sourceLocationId());
        }

        Location sourceLocation = findLocationForTenant(request.sourceLocationId(), tenantId);
        Location destinationLocation = findLocationForTenant(request.destinationLocationId(), tenantId);
        ProductWms product = productWmsRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto não encontrado: " + request.productId()));

        Lot lot = null;
        if (request.lotId() != null) {
            lot = lotRepository.findById(request.lotId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Lote não encontrado: " + request.lotId()));
        }

        UUID requestedBy = extractOperatorId();

        InternalTransfer transfer = internalTransferRepository.save(InternalTransfer.builder()
                .tenantId(tenantId)
                .transferType(TransferType.MANUAL)
                .status(TransferStatus.PENDING)
                .sourceLocation(sourceLocation)
                .destinationLocation(destinationLocation)
                .product(product)
                .lot(lot)
                .quantity(request.quantity())
                .requestedBy(requestedBy)
                .reason(request.reason())
                .build());

        log.debug("Transferência {} criada em PENDING (tenant={})", transfer.getId(), tenantId);
        return InternalTransferResponse.from(transfer);
    }

    // -------------------------------------------------------------------------
    // AC4 + AC5 + AC6 + AC7 — PUT /transfers/{id}/confirm
    // -------------------------------------------------------------------------

    @Transactional
    public InternalTransferResponse confirmTransfer(UUID transferId, ConfirmTransferRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        InternalTransfer transfer = getTransferForTenant(transferId, tenantId);

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new BusinessException(
                    "Transferência não pode ser confirmada. Status atual: " + transfer.getStatus());
        }

        // AC5 — atualizar estoque na origem e destino
        UUID lotId = transfer.getLot() != null ? transfer.getLot().getId() : null;

        StockItem sourceItem = findStockItem(tenantId, transfer.getSourceLocation().getId(),
                transfer.getProduct().getId(), lotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "StockItem não encontrado na origem para o produto/lote informado"));

        if (sourceItem.getQuantityAvailable() < transfer.getQuantity()) {
            throw new BusinessException(String.format(
                    "Estoque insuficiente: disponível=%d, solicitado=%d",
                    sourceItem.getQuantityAvailable(), transfer.getQuantity()));
        }

        sourceItem.setQuantityAvailable(sourceItem.getQuantityAvailable() - transfer.getQuantity());
        stockItemRepository.save(sourceItem);

        StockItem destItem = findStockItem(tenantId, transfer.getDestinationLocation().getId(),
                transfer.getProduct().getId(), lotId)
                .orElseGet(() -> StockItem.builder()
                        .tenantId(tenantId)
                        .location(transfer.getDestinationLocation())
                        .product(transfer.getProduct())
                        .lot(transfer.getLot())
                        .quantityAvailable(0)
                        .receivedAt(sourceItem.getReceivedAt())
                        .build());

        destItem.setQuantityAvailable(destItem.getQuantityAvailable() + transfer.getQuantity());
        stockItemRepository.save(destItem);

        // AC5 — criar StockMovement
        StockMovement movement = stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .type(MovementType.TRANSFER)
                .product(transfer.getProduct())
                .lot(transfer.getLot())
                .sourceLocation(transfer.getSourceLocation())
                .destinationLocation(transfer.getDestinationLocation())
                .quantity(transfer.getQuantity())
                .referenceType(ReferenceType.INTERNAL_TRANSFER)
                .referenceId(transfer.getId())
                .operatorId(request.confirmedBy())
                .reason(transfer.getReason())
                .build());

        // AC5 — invalidar cache Redis
        stockBalanceService.evictAll();

        // AC6 — registrar no audit log + ES (dentro da mesma transação)
        String newValue = String.format(
                "{\"movementType\":\"TRANSFER\",\"quantity\":%d,\"sourceLocationId\":\"%s\",\"destinationLocationId\":\"%s\"}",
                transfer.getQuantity(),
                transfer.getSourceLocation().getId(),
                transfer.getDestinationLocation().getId());

        AuditLog auditLog = auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("INTERNAL_TRANSFER")
                .entityId(transfer.getId())
                .action(AuditAction.MOVEMENT)
                .actorId(request.confirmedBy())
                .newValue(newValue)
                .build());

        auditLogIndexer.indexAuditLogAsync(auditLog);

        // Atualizar transfer para CONFIRMED
        transfer.setStatus(TransferStatus.CONFIRMED);
        transfer.setConfirmedBy(request.confirmedBy());
        InternalTransfer saved = internalTransferRepository.save(transfer);

        log.info("Transferência {} confirmada (tenant={})", transferId, tenantId);

        // AC7 — publicar evento Kafka após transação — degradação graciosa
        stockMovementEventPublisher.publishTransferEvent(saved, movement);

        return InternalTransferResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // AC8 — PUT /transfers/{id}/cancel
    // -------------------------------------------------------------------------

    @Transactional
    public InternalTransferResponse cancelTransfer(UUID transferId, CancelTransferRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        InternalTransfer transfer = getTransferForTenant(transferId, tenantId);

        if (transfer.getStatus() == TransferStatus.CONFIRMED) {
            throw new BusinessException("Transferência já confirmada não pode ser cancelada");
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new BusinessException("Transferência já cancelada");
        }

        UUID cancelledBy = extractOperatorId();

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledBy(cancelledBy);
        if (request != null && request.reason() != null) {
            transfer.setReason(request.reason());
        }
        InternalTransfer saved = internalTransferRepository.save(transfer);

        log.info("Transferência {} cancelada (tenant={})", transferId, tenantId);
        return InternalTransferResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // AC9 — GET /transfers/{id}
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public InternalTransferResponse getTransfer(UUID transferId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        InternalTransfer transfer = getTransferForTenant(transferId, tenantId);
        return InternalTransferResponse.from(transfer);
    }

    // -------------------------------------------------------------------------
    // AC9 — GET /transfers (listagem paginada)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<InternalTransferResponse> listTransfers(TransferStatus status, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Page<InternalTransfer> page = status != null
                ? internalTransferRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : internalTransferRepository.findByTenantId(tenantId, pageable);
        return page.map(InternalTransferResponse::from);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private InternalTransfer getTransferForTenant(UUID transferId, UUID tenantId) {
        return internalTransferRepository.findByIdAndTenantId(transferId, tenantId)
                .orElseThrow(() -> {
                    if (internalTransferRepository.existsById(transferId)) {
                        return new AccessDeniedException("Acesso negado à transferência: " + transferId);
                    }
                    return new ResourceNotFoundException("Transferência não encontrada: " + transferId);
                });
    }

    private Location findLocationForTenant(UUID locationId, UUID tenantId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Localização não encontrada: " + locationId));
        if (!tenantId.equals(location.getTenantId())) {
            throw new IllegalArgumentException(
                    "Localização não pertence ao tenant: " + locationId);
        }
        return location;
    }

    private Optional<StockItem> findStockItem(UUID tenantId, UUID locationId,
                                               UUID productId, UUID lotId) {
        return lotId != null
                ? stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotId(
                        tenantId, locationId, productId, lotId)
                : stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                        tenantId, locationId, productId);
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
}
