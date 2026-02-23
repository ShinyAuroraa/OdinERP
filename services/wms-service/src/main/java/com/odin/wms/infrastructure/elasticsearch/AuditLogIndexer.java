package com.odin.wms.infrastructure.elasticsearch;

import com.odin.wms.domain.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Indexador assíncrono de AuditLog no Elasticsearch.
 * O método nunca propaga exceções — falha do ES é não-crítica.
 *
 * AC4 — @Async garante que operações de escrita no PostgreSQL não bloqueiam.
 * Requer @EnableAsync em AsyncConfig (já configurado desde Story 4.2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogIndexer {

    private final AuditLogEsRepository auditLogEsRepository;

    /**
     * Indexa um AuditLog no Elasticsearch de forma assíncrona.
     * Nunca lança exceção — degradação graciosa (fallback para PostgreSQL).
     */
    @Async
    public void indexAuditLogAsync(AuditLog entry) {
        try {
            AuditLogDocument doc = toDocument(entry);
            auditLogEsRepository.save(doc);
            log.debug("AuditLog {} indexado no ES (tenant={})", entry.getId(), entry.getTenantId());
        } catch (Exception e) {
            log.warn("Falha ao indexar AuditLog {} no ES (não-crítico): {}", entry.getId(), e.getMessage());
            // Nunca propaga — ES failure é não-crítica (fallback para PostgreSQL)
        }
    }

    private AuditLogDocument toDocument(AuditLog entry) {
        AuditLogDocument doc = new AuditLogDocument();
        doc.setId(entry.getId().toString());
        doc.setTenantId(entry.getTenantId().toString());
        doc.setEntityType(entry.getEntityType());
        doc.setEntityId(entry.getEntityId().toString());
        doc.setAction(entry.getAction().name());
        doc.setActorId(entry.getActorId().toString());
        doc.setCreatedAt(entry.getCreatedAt());

        if (entry.getActorName() != null) {
            doc.setActorName(entry.getActorName());
        }
        if (entry.getActorRole() != null) {
            doc.setActorRole(entry.getActorRole());
        }
        if (entry.getCorrelationId() != null) {
            doc.setCorrelationId(entry.getCorrelationId());
        }
        return doc;
    }
}
