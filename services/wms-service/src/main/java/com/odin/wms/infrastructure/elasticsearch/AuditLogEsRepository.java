package com.odin.wms.infrastructure.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório Elasticsearch para AuditLogDocument.
 * Usado pelo AuditLogIndexer para indexação assíncrona de registros de auditoria.
 */
@Repository
public interface AuditLogEsRepository extends ElasticsearchRepository<AuditLogDocument, String> {
}
