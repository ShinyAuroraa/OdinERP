package com.odin.wms.audit;

import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.infrastructure.elasticsearch.AuditLogDocument;
import com.odin.wms.infrastructure.elasticsearch.AuditLogEsRepository;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do AuditLogIndexer — U1, U2.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogIndexerTest {

    @Mock
    private AuditLogEsRepository auditLogEsRepository;

    @InjectMocks
    private AuditLogIndexer auditLogIndexer;

    // -------------------------------------------------------------------------
    // U1 — indexAuditLogAsync_success_savesDocument
    // -------------------------------------------------------------------------

    @Test
    void indexAuditLogAsync_success_savesDocument() throws Exception {
        AuditLog entry = buildAuditLog();

        auditLogIndexer.indexAuditLogAsync(entry);

        verify(auditLogEsRepository).save(any(AuditLogDocument.class));
    }

    // -------------------------------------------------------------------------
    // U2 — indexAuditLogAsync_esFailure_noException
    // -------------------------------------------------------------------------

    @Test
    void indexAuditLogAsync_esFailure_noException() throws Exception {
        AuditLog entry = buildAuditLog();
        doThrow(new RuntimeException("ES indisponível")).when(auditLogEsRepository).save(any());

        // Não deve propagar exceção — degradação graciosa
        auditLogIndexer.indexAuditLogAsync(entry);

        verify(auditLogEsRepository).save(any(AuditLogDocument.class));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private AuditLog buildAuditLog() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLog entry = AuditLog.builder()
                .tenantId(UUID.randomUUID())
                .entityType("STOCK_ITEM")
                .entityId(UUID.randomUUID())
                .action(AuditAction.MOVEMENT)
                .actorId(UUID.randomUUID())
                .actorName("Operador Teste")
                .actorRole("WMS_OPERATOR")
                .correlationId("corr-123")
                .build();

        // Setar id via reflexão (BaseAppendOnlyEntity)
        Field field = Class.forName("com.odin.wms.domain.entity.base.BaseAppendOnlyEntity")
                .getDeclaredField("id");
        field.setAccessible(true);
        field.set(entry, id);

        return entry;
    }
}
