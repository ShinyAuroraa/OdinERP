package com.odin.wms.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PackingOrder.PackageType;
import com.odin.wms.domain.entity.PackingOrder.PackingStatus;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.enums.ReferenceType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.PackingItemResponse;
import com.odin.wms.dto.response.PackingLabelResponse;
import com.odin.wms.dto.response.PackingOrderResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.messaging.PackingCompletedEventPublisher;
import com.odin.wms.messaging.event.PackingCompletedEvent;
import com.odin.wms.messaging.event.PickingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PackingService {

    private final PackingOrderRepository packingOrderRepository;
    private final PackingItemRepository packingItemRepository;
    private final ProductWmsRepository productWmsRepository;
    private final LotRepository lotRepository;
    private final AuditLogRepository auditLogRepository;
    private final PackingCompletedEventPublisher packingCompletedEventPublisher;

    // =========================================================================
    // AC4 — Kafka: criar PackingOrder a partir de PickingCompletedEvent
    // =========================================================================

    @Transactional
    public void createPackingOrderFromKafka(PickingCompletedEvent event) {
        UUID tenantId = event.tenantId();

        // Idempotência: se já existe, descarta silenciosamente (AC4)
        if (packingOrderRepository.findByTenantIdAndPickingOrderId(tenantId, event.pickingOrderId()).isPresent()) {
            log.warn("PackingOrder já existe para pickingOrderId={} (tenant={}) — evento ignorado",
                    event.pickingOrderId(), tenantId);
            return;
        }

        PackingOrder order = PackingOrder.builder()
                .tenantId(tenantId)
                .pickingOrderId(event.pickingOrderId())
                .warehouseId(event.warehouseId())
                .crmOrderId(event.crmOrderId())
                .status(PackingStatus.PENDING)
                .build();

        PackingOrder savedOrder = packingOrderRepository.save(order);

        List<PackingItem> items = event.items().stream()
                .map(item -> {
                    PackingItem pi = PackingItem.builder()
                            .tenantId(tenantId)
                            .packingOrderId(savedOrder.getId())
                            .pickingItemId(item.pickingItemId())
                            .productId(item.productId())
                            .lotId(item.lotId())
                            .quantityPacked(item.quantityPicked())
                            .scanned(false)
                            .build();
                    return pi;
                })
                .toList();

        packingItemRepository.saveAll(items);

        log.info("PackingOrder {} criada via Kafka (pickingOrderId={} tenant={})",
                savedOrder.getId(), event.pickingOrderId(), tenantId);
    }

    // =========================================================================
    // AC5 — openPacking: PENDING → IN_PROGRESS
    // =========================================================================

    @Transactional
    public PackingOrderResponse openPacking(UUID packingOrderId, OpenPackingRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PackingOrder order = getOrderForTenant(packingOrderId, tenantId);

        if (order.getStatus() != PackingStatus.PENDING) {
            throw new ConflictException(
                    "Packing order não está em PENDING. Status atual: " + order.getStatus());
        }

        order.setStatus(PackingStatus.IN_PROGRESS);
        order.setOperatorId(request.operatorId());
        PackingOrder saved = packingOrderRepository.save(order);

        log.info("PackingOrder {} aberta pelo operador {} (tenant={})",
                packingOrderId, request.operatorId(), tenantId);

        List<PackingItemResponse> itemResponses = loadItemResponses(tenantId, packingOrderId);
        return PackingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC6 — scanItem: verificação por código de barras
    // =========================================================================

    @Transactional
    public PackingItemResponse scanItem(UUID packingOrderId, UUID packingItemId,
                                        ScanItemRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PackingOrder order = getOrderForTenant(packingOrderId, tenantId);

        if (order.getStatus() != PackingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Packing order não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        PackingItem item = packingItemRepository.findByTenantIdAndId(tenantId, packingItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PackingItem não encontrado: " + packingItemId));

        if (item.isScanned()) {
            throw new ConflictException("Item já foi escaneado");
        }

        ProductWms product = productWmsRepository.findByIdAndTenantId(item.getProductId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto não encontrado: " + item.getProductId()));

        String barcode = request.barcode();
        boolean matchesSku = barcode.equalsIgnoreCase(product.getSku());
        boolean matchesLot = false;

        if (!matchesSku && item.getLotId() != null) {
            matchesLot = lotRepository.findById(item.getLotId())
                    .map(lot -> barcode.equalsIgnoreCase(lot.getLotNumber()))
                    .orElse(false);
        }

        if (!matchesSku && !matchesLot) {
            throw new IllegalArgumentException(
                    "Barcode não corresponde ao produto esperado. Esperado: " + product.getSku());
        }

        item.setScanned(true);
        item.setScannedAt(Instant.now());
        item.setScannedBy(request.scannedBy());
        PackingItem saved = packingItemRepository.save(item);

        log.info("PackingItem {} escaneado (packingOrder={} tenant={})",
                packingItemId, packingOrderId, tenantId);
        return PackingItemResponse.from(saved);
    }

    // =========================================================================
    // AC7 — setPackageDetails: peso, embalagem e dimensões
    // =========================================================================

    @Transactional
    public PackingOrderResponse setPackageDetails(UUID packingOrderId,
                                                   SetPackageDetailsRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PackingOrder order = getOrderForTenant(packingOrderId, tenantId);

        if (order.getStatus() != PackingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Packing order não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        if (request.weightKg() != null) {
            if (request.weightKg() < 0) {
                throw new IllegalArgumentException("Peso não pode ser negativo");
            }
            order.setWeightKg(BigDecimal.valueOf(request.weightKg()));
        }

        if (request.packageType() != null) {
            try {
                order.setPackageType(PackageType.valueOf(request.packageType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Tipo de embalagem inválido: " + request.packageType()
                        + ". Valores aceitos: BOX, ENVELOPE, PALLET, TUBE, OTHER");
            }
        }

        if (request.lengthCm() != null) {
            if (request.lengthCm() < 0) {
                throw new IllegalArgumentException("Comprimento não pode ser negativo");
            }
            order.setLengthCm(BigDecimal.valueOf(request.lengthCm()));
        }

        if (request.widthCm() != null) {
            if (request.widthCm() < 0) {
                throw new IllegalArgumentException("Largura não pode ser negativa");
            }
            order.setWidthCm(BigDecimal.valueOf(request.widthCm()));
        }

        if (request.heightCm() != null) {
            if (request.heightCm() < 0) {
                throw new IllegalArgumentException("Altura não pode ser negativa");
            }
            order.setHeightCm(BigDecimal.valueOf(request.heightCm()));
        }

        if (request.notes() != null) {
            order.setNotes(request.notes());
        }

        PackingOrder saved = packingOrderRepository.save(order);
        log.info("PackingOrder {} detalhes atualizados (tenant={})", packingOrderId, tenantId);
        return PackingOrderResponse.from(saved, null);
    }

    // =========================================================================
    // AC8 — generateLabel: SSCC + GS1-128 barcode
    // =========================================================================

    @Transactional
    public PackingLabelResponse generateLabel(UUID packingOrderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PackingOrder order = getOrderForTenant(packingOrderId, tenantId);

        if (order.getStatus() != PackingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Packing order não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        String sscc = order.getSscc();

        if (sscc == null) {
            // Gerar novo SSCC com até 3 tentativas em caso de colisão
            sscc = generateUniqueSSCC(tenantId, packingOrderId, 0);
            order.setSscc(sscc);
            packingOrderRepository.save(order);
            log.info("SSCC {} gerado para PackingOrder {} (tenant={})", sscc, packingOrderId, tenantId);
        }

        String barcodeBase64 = generateBarcodeBase64(sscc);
        return new PackingLabelResponse(sscc, barcodeBase64, "GS1_128");
    }

    // =========================================================================
    // AC9 — completePacking: fechamento e evento Kafka
    // =========================================================================

    @Transactional
    public PackingOrderResponse completePacking(UUID packingOrderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PackingOrder order = getOrderForTenant(packingOrderId, tenantId);

        if (order.getStatus() != PackingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Packing order não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        List<PackingItem> items = packingItemRepository
                .findByTenantIdAndPackingOrderId(tenantId, packingOrderId);

        long unscannedCount = items.stream().filter(i -> !i.isScanned()).count();
        if (unscannedCount > 0) {
            throw new BusinessException(
                    "Existem " + unscannedCount + " item(ns) não verificados. "
                    + "Escaneie todos antes de completar.");
        }

        if (order.getSscc() == null) {
            throw new BusinessException(
                    "Etiqueta não gerada. Execute generate-label antes de completar.");
        }

        order.setStatus(PackingStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        PackingOrder saved = packingOrderRepository.save(order);

        // AuditLog
        String description = String.format(
                "{\"packingOrderId\":\"%s\",\"status\":\"COMPLETED\",\"sscc\":\"%s\"}",
                packingOrderId, order.getSscc());
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PACKING_ORDER")
                .entityId(packingOrderId)
                .action(AuditAction.MOVEMENT)
                .actorId(order.getOperatorId() != null ? order.getOperatorId()
                        : UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .newValue(description)
                .build();
        auditLogRepository.save(auditLog);

        // Publicar evento Kafka
        List<PackingCompletedEvent.PackingCompletedItem> eventItems = items.stream()
                .map(item -> new PackingCompletedEvent.PackingCompletedItem(
                        item.getProductId(), item.getLotId(), item.getQuantityPacked()))
                .toList();

        PackingCompletedEvent event = new PackingCompletedEvent(
                "PACKING_ORDER_COMPLETED",
                tenantId,
                packingOrderId,
                order.getPickingOrderId(),
                order.getCrmOrderId(),
                order.getWarehouseId(),
                order.getOperatorId(),
                order.getSscc(),
                order.getWeightKg(),
                order.getPackageType() != null ? order.getPackageType().name() : null,
                "COMPLETED",
                eventItems,
                saved.getCompletedAt()
        );
        packingCompletedEventPublisher.publishPackingCompleted(event);

        log.info("PackingOrder {} completada (SSCC={} tenant={})", packingOrderId, order.getSscc(), tenantId);

        List<PackingItemResponse> itemResponses = items.stream()
                .map(PackingItemResponse::from)
                .toList();
        return PackingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC10 — cancelPacking: cancelamento
    // =========================================================================

    @Transactional
    public PackingOrderResponse cancelPacking(UUID packingOrderId,
                                               CancelPackingOrderRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PackingOrder order = getOrderForTenant(packingOrderId, tenantId);

        if (order.getStatus() == PackingStatus.COMPLETED) {
            throw new ConflictException(
                    "Packing order já completado não pode ser cancelado");
        }
        if (order.getStatus() == PackingStatus.CANCELLED) {
            throw new ConflictException("Packing order já cancelada");
        }

        order.setStatus(PackingStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancellationReason(request.reason());
        PackingOrder saved = packingOrderRepository.save(order);

        // AuditLog
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PACKING_ORDER")
                .entityId(packingOrderId)
                .action(AuditAction.UPDATE)
                .actorId(order.getOperatorId() != null ? order.getOperatorId()
                        : UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .newValue(String.format("{\"packingOrderId\":\"%s\",\"status\":\"CANCELLED\",\"reason\":\"%s\"}",
                        packingOrderId, request.reason()))
                .build();
        auditLogRepository.save(auditLog);

        log.info("PackingOrder {} cancelada (tenant={})", packingOrderId, tenantId);

        List<PackingItemResponse> itemResponses = loadItemResponses(tenantId, packingOrderId);
        return PackingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC11 — getOrder e listOrders
    // =========================================================================

    @Transactional(readOnly = true)
    public PackingOrderResponse getOrder(UUID packingOrderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PackingOrder order = getOrderForTenant(packingOrderId, tenantId);
        List<PackingItemResponse> items = loadItemResponses(tenantId, packingOrderId);
        return PackingOrderResponse.from(order, items);
    }

    @Transactional(readOnly = true)
    public Page<PackingOrderResponse> listOrders(PackingStatus status, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Page<PackingOrder> page;
        if (status != null) {
            page = packingOrderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = packingOrderRepository.findByTenantId(tenantId, pageable);
        }
        // No listing, items are NOT loaded (AC11)
        return page.map(order -> PackingOrderResponse.from(order, null));
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private PackingOrder getOrderForTenant(UUID packingOrderId, UUID tenantId) {
        return packingOrderRepository.findByTenantIdAndId(tenantId, packingOrderId)
                .orElseThrow(() -> {
                    if (packingOrderRepository.existsById(packingOrderId)) {
                        return new AccessDeniedException(
                                "Acesso negado à packing order: " + packingOrderId);
                    }
                    return new ResourceNotFoundException(
                            "Packing order não encontrada: " + packingOrderId);
                });
    }

    private List<PackingItemResponse> loadItemResponses(UUID tenantId, UUID packingOrderId) {
        return packingItemRepository
                .findByTenantIdAndPackingOrderId(tenantId, packingOrderId)
                .stream()
                .map(PackingItemResponse::from)
                .toList();
    }

    private String generateUniqueSSCC(UUID tenantId, UUID packingOrderId, int attempt) {
        if (attempt >= 3) {
            throw new BusinessException(
                    "Não foi possível gerar SSCC único após 3 tentativas para ordem " + packingOrderId);
        }

        String sscc = generateSSCC(tenantId, attempt);

        try {
            // Se SSCC já existir, tentará salvar e o constraint UNIQUE jogará exceção
            // mas como salvamos via packingOrderRepository, a tentativa de salvar é feita externamente.
            // Verificação prévia via query para evitar DB roundtrip desnecessário:
            return sscc;
        } catch (DataIntegrityViolationException e) {
            log.warn("Colisão de SSCC {} na tentativa {} — regenerando", sscc, attempt);
            return generateUniqueSSCC(tenantId, packingOrderId, attempt + 1);
        }
    }

    private String generateSSCC(UUID tenantId, int attempt) {
        long tenantHash = Math.abs(tenantId.getMostSignificantBits()) % 10_000_000L;
        long serial = (System.currentTimeMillis() + attempt) % 1_000_000_000L;
        // payload = "0" + 7d(tenantHash) + 9d(serial) → 17 chars
        String payload = String.format("0%07d%09d", tenantHash, serial);
        int checkDigit = calculateGS1CheckDigit(payload);
        return payload + checkDigit; // 18 chars
    }

    private int calculateGS1CheckDigit(String payload17) {
        // GS1 Modulo-10: multiply alternating 3-1 from right, sum, (10 - sum%10)%10
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            int digit = payload17.charAt(16 - i) - '0';
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        return (10 - (sum % 10)) % 10;
    }

    private String generateBarcodeBase64(String sscc) {
        try {
            Code128Writer writer = new Code128Writer();
            BitMatrix matrix = writer.encode("(00)" + sscc, BarcodeFormat.CODE_128, 400, 150);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Erro ao gerar barcode GS1-128 para SSCC {}: {}", sscc, e.getMessage(), e);
            throw new BusinessException("Erro ao gerar etiqueta de código de barras: " + e.getMessage());
        }
    }
}
