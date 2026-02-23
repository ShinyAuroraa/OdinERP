package com.odin.wms.transfer;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.InternalTransfer.TransferStatus;
import com.odin.wms.domain.entity.InternalTransfer.TransferType;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.CancelTransferRequest;
import com.odin.wms.dto.request.ConfirmTransferRequest;
import com.odin.wms.dto.request.CreateTransferRequest;
import com.odin.wms.dto.response.InternalTransferResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.StockMovementEventPublisher;
import com.odin.wms.service.InternalTransferService;
import com.odin.wms.service.StockBalanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para InternalTransferService — U1–U10.
 */
@ExtendWith(MockitoExtension.class)
class InternalTransferServiceTest {

    @Mock private InternalTransferRepository internalTransferRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ProductWmsRepository productWmsRepository;
    @Mock private LotRepository lotRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private StockBalanceService stockBalanceService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogIndexer auditLogIndexer;
    @Mock private StockMovementEventPublisher stockMovementEventPublisher;

    @InjectMocks
    private InternalTransferService service;

    private static final UUID TENANT_ID    = UUID.randomUUID();
    private static final UUID SOURCE_LOC   = UUID.randomUUID();
    private static final UUID DEST_LOC     = UUID.randomUUID();
    private static final UUID PRODUCT_ID   = UUID.randomUUID();
    private static final UUID TRANSFER_ID  = UUID.randomUUID();
    private static final UUID OPERATOR_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // U1 — createTransfer_manual_returnsPending
    // -------------------------------------------------------------------------

    @Test
    void createTransfer_manual_returnsPending() {
        Location src = buildLocation(SOURCE_LOC);
        Location dst = buildLocation(DEST_LOC);
        ProductWms product = buildProduct();

        when(locationRepository.findById(SOURCE_LOC)).thenReturn(Optional.of(src));
        when(locationRepository.findById(DEST_LOC)).thenReturn(Optional.of(dst));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));
        when(internalTransferRepository.save(any())).thenAnswer(inv -> {
            InternalTransfer t = inv.getArgument(0);
            setId(t, TRANSFER_ID);
            return t;
        });

        InternalTransferResponse resp = service.createTransfer(
                new CreateTransferRequest(SOURCE_LOC, DEST_LOC, PRODUCT_ID, null, 5, "Reabastecimento"));

        assertThat(resp.status()).isEqualTo(TransferStatus.PENDING.name());
        assertThat(resp.transferType()).isEqualTo(TransferType.MANUAL.name());
        assertThat(resp.quantity()).isEqualTo(5);
        assertThat(resp.id()).isEqualTo(TRANSFER_ID);
    }

    // -------------------------------------------------------------------------
    // U2 — createTransfer_sameLocation_throwsException
    // -------------------------------------------------------------------------

    @Test
    void createTransfer_sameLocation_throwsException() {
        assertThatThrownBy(() ->
                service.createTransfer(new CreateTransferRequest(
                        SOURCE_LOC, SOURCE_LOC, PRODUCT_ID, null, 5, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source e destination não podem ser iguais");
    }

    // -------------------------------------------------------------------------
    // U3 — createTransfer_insufficientStock_doesNotFail
    // -------------------------------------------------------------------------

    @Test
    void createTransfer_insufficientStock_doesNotFail() {
        // Criação NÃO valida estoque — só verifica na confirmação
        Location src = buildLocation(SOURCE_LOC);
        Location dst = buildLocation(DEST_LOC);
        ProductWms product = buildProduct();

        when(locationRepository.findById(SOURCE_LOC)).thenReturn(Optional.of(src));
        when(locationRepository.findById(DEST_LOC)).thenReturn(Optional.of(dst));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));
        when(internalTransferRepository.save(any())).thenAnswer(inv -> {
            InternalTransfer t = inv.getArgument(0);
            setId(t, TRANSFER_ID);
            return t;
        });

        // Quantidade solicitada maior que zero — mas não há StockItem mockado — não deve falhar aqui
        assertThatCode(() ->
                service.createTransfer(new CreateTransferRequest(
                        SOURCE_LOC, DEST_LOC, PRODUCT_ID, null, 9999, null)))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // U4 — confirmTransfer_success_updatesStock
    // -------------------------------------------------------------------------

    @Test
    void confirmTransfer_success_updatesStock() {
        InternalTransfer transfer = buildPendingTransfer();
        StockItem sourceItem = buildStockItem(10);
        StockItem destItem = buildStockItem(0);
        StockMovement movement = buildMovement();
        AuditLog auditLog = buildAuditLog();

        when(internalTransferRepository.findByIdAndTenantId(TRANSFER_ID, TENANT_ID))
                .thenReturn(Optional.of(transfer));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, SOURCE_LOC, PRODUCT_ID)).thenReturn(Optional.of(sourceItem));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, DEST_LOC, PRODUCT_ID)).thenReturn(Optional.of(destItem));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenReturn(movement);
        when(auditLogRepository.save(any())).thenReturn(auditLog);
        when(internalTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InternalTransferResponse resp = service.confirmTransfer(TRANSFER_ID,
                new ConfirmTransferRequest(OPERATOR_ID));

        assertThat(resp.status()).isEqualTo(TransferStatus.CONFIRMED.name());
        assertThat(sourceItem.getQuantityAvailable()).isEqualTo(5); // 10 - 5
        assertThat(destItem.getQuantityAvailable()).isEqualTo(5);   // 0 + 5
        verify(stockItemRepository, times(2)).save(any());
    }

    // -------------------------------------------------------------------------
    // U5 — confirmTransfer_insufficientStock_throwsException
    // -------------------------------------------------------------------------

    @Test
    void confirmTransfer_insufficientStock_throwsException() {
        InternalTransfer transfer = buildPendingTransfer(); // quantity = 5
        StockItem sourceItem = buildStockItem(2); // apenas 2 disponível

        when(internalTransferRepository.findByIdAndTenantId(TRANSFER_ID, TENANT_ID))
                .thenReturn(Optional.of(transfer));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, SOURCE_LOC, PRODUCT_ID)).thenReturn(Optional.of(sourceItem));

        assertThatThrownBy(() ->
                service.confirmTransfer(TRANSFER_ID, new ConfirmTransferRequest(OPERATOR_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Estoque insuficiente")
                .hasMessageContaining("disponível=2")
                .hasMessageContaining("solicitado=5");
    }

    // -------------------------------------------------------------------------
    // U6 — confirmTransfer_alreadyConfirmed_throwsException
    // -------------------------------------------------------------------------

    @Test
    void confirmTransfer_alreadyConfirmed_throwsException() {
        InternalTransfer transfer = buildPendingTransfer();
        transfer.setStatus(TransferStatus.CONFIRMED);

        when(internalTransferRepository.findByIdAndTenantId(TRANSFER_ID, TENANT_ID))
                .thenReturn(Optional.of(transfer));

        assertThatThrownBy(() ->
                service.confirmTransfer(TRANSFER_ID, new ConfirmTransferRequest(OPERATOR_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode ser confirmada")
                .hasMessageContaining("CONFIRMED");
    }

    // -------------------------------------------------------------------------
    // U7 — confirmTransfer_savesAuditLog
    // -------------------------------------------------------------------------

    @Test
    void confirmTransfer_savesAuditLog() {
        InternalTransfer transfer = buildPendingTransfer();
        StockItem sourceItem = buildStockItem(10);
        StockItem destItem = buildStockItem(0);
        StockMovement movement = buildMovement();

        when(internalTransferRepository.findByIdAndTenantId(TRANSFER_ID, TENANT_ID))
                .thenReturn(Optional.of(transfer));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, SOURCE_LOC, PRODUCT_ID)).thenReturn(Optional.of(sourceItem));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, DEST_LOC, PRODUCT_ID)).thenReturn(Optional.of(destItem));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenReturn(movement);
        when(internalTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(auditCaptor.capture())).thenReturn(buildAuditLog());

        service.confirmTransfer(TRANSFER_ID, new ConfirmTransferRequest(OPERATOR_ID));

        AuditLog saved = auditCaptor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("INTERNAL_TRANSFER");
        assertThat(saved.getEntityId()).isEqualTo(TRANSFER_ID);
        assertThat(saved.getAction()).isEqualTo(AuditAction.MOVEMENT);
        assertThat(saved.getActorId()).isEqualTo(OPERATOR_ID);
        assertThat(saved.getNewValue()).contains("TRANSFER");

        verify(auditLogIndexer).indexAuditLogAsync(any());
    }

    // -------------------------------------------------------------------------
    // U8 — cancelTransfer_pending_returnsCancelled
    // -------------------------------------------------------------------------

    @Test
    void cancelTransfer_pending_returnsCancelled() {
        InternalTransfer transfer = buildPendingTransfer();

        when(internalTransferRepository.findByIdAndTenantId(TRANSFER_ID, TENANT_ID))
                .thenReturn(Optional.of(transfer));
        when(internalTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InternalTransferResponse resp = service.cancelTransfer(TRANSFER_ID,
                new CancelTransferRequest("Motivo cancelamento"));

        assertThat(resp.status()).isEqualTo(TransferStatus.CANCELLED.name());
        // Estoque NÃO foi movido — nenhuma interação com stockItemRepository
        verify(stockItemRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // U9 — cancelTransfer_confirmed_throwsException
    // -------------------------------------------------------------------------

    @Test
    void cancelTransfer_confirmed_throwsException() {
        InternalTransfer transfer = buildPendingTransfer();
        transfer.setStatus(TransferStatus.CONFIRMED);

        when(internalTransferRepository.findByIdAndTenantId(TRANSFER_ID, TENANT_ID))
                .thenReturn(Optional.of(transfer));

        assertThatThrownBy(() ->
                service.cancelTransfer(TRANSFER_ID, new CancelTransferRequest("motivo")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já confirmada não pode ser cancelada");
    }

    // -------------------------------------------------------------------------
    // U10 — confirmTransfer_evictsRedisCache
    // -------------------------------------------------------------------------

    @Test
    void confirmTransfer_evictsRedisCache() {
        InternalTransfer transfer = buildPendingTransfer();
        StockItem sourceItem = buildStockItem(10);
        StockItem destItem = buildStockItem(0);

        when(internalTransferRepository.findByIdAndTenantId(TRANSFER_ID, TENANT_ID))
                .thenReturn(Optional.of(transfer));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, SOURCE_LOC, PRODUCT_ID)).thenReturn(Optional.of(sourceItem));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, DEST_LOC, PRODUCT_ID)).thenReturn(Optional.of(destItem));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenReturn(buildMovement());
        when(auditLogRepository.save(any())).thenReturn(buildAuditLog());
        when(internalTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.confirmTransfer(TRANSFER_ID, new ConfirmTransferRequest(OPERATOR_ID));

        verify(stockBalanceService).evictAll();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InternalTransfer buildPendingTransfer() {
        Location src = buildLocation(SOURCE_LOC);
        Location dst = buildLocation(DEST_LOC);
        ProductWms product = buildProduct();

        InternalTransfer t = InternalTransfer.builder()
                .tenantId(TENANT_ID)
                .transferType(TransferType.MANUAL)
                .status(TransferStatus.PENDING)
                .sourceLocation(src)
                .destinationLocation(dst)
                .product(product)
                .quantity(5)
                .requestedBy(OPERATOR_ID)
                .build();
        setId(t, TRANSFER_ID);
        return t;
    }

    private Location buildLocation(UUID locationId) {
        Location loc = new Location();
        setId(loc, locationId);
        try {
            var f = com.odin.wms.domain.entity.base.BaseEntity.class.getDeclaredField("tenantId");
            f.setAccessible(true);
            f.set(loc, TENANT_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return loc;
    }

    private ProductWms buildProduct() {
        ProductWms p = new ProductWms();
        setId(p, PRODUCT_ID);
        return p;
    }

    private StockItem buildStockItem(int quantity) {
        StockItem si = StockItem.builder()
                .tenantId(TENANT_ID)
                .location(buildLocation(SOURCE_LOC))
                .product(buildProduct())
                .quantityAvailable(quantity)
                .receivedAt(Instant.now())
                .build();
        setId(si, UUID.randomUUID());
        return si;
    }

    private StockMovement buildMovement() {
        StockMovement m = new StockMovement();
        setId(m, UUID.randomUUID());
        return m;
    }

    private AuditLog buildAuditLog() {
        AuditLog log = new AuditLog();
        setId(log, UUID.randomUUID());
        return log;
    }

    private void setId(Object entity, UUID id) {
        try {
            Class<?> base = entity.getClass();
            while (base != null) {
                try {
                    var field = base.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) {
                    base = base.getSuperclass();
                }
            }
            throw new RuntimeException("Campo 'id' não encontrado em: " + entity.getClass());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Falha ao definir UUID via reflexão", e);
        }
    }
}
