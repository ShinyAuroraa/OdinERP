package com.odin.wms.infrastructure.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data Elasticsearch para movimentações de rastreabilidade.
 * Todas as queries sempre filtram por tenantId para isolamento multi-tenant.
 *
 * AC7 — consultas ES com fallback para PostgreSQL via LotTraceabilityService.
 */
@Repository
public interface TraceabilityRepository extends ElasticsearchRepository<TraceabilityDocument, String> {

    /**
     * Histórico de movimentos de um lote, ordenado cronologicamente.
     * Usado como source primária em GET /traceability/lot/{lotNumber}.
     */
    List<TraceabilityDocument> findByTenantIdAndLotNumberOrderByCreatedAtAsc(
            String tenantId, String lotNumber);

    /**
     * Histórico de movimentos de um número de série, ordenado cronologicamente.
     * Usado como source primária em GET /traceability/serial/{serialNumber}.
     */
    List<TraceabilityDocument> findByTenantIdAndSerialNumberOrderByCreatedAtAsc(
            String tenantId, String serialNumber);

    /**
     * Histórico de movimentos de um lote por ID, ordenado cronologicamente.
     * Usado para a árvore de rastreabilidade (GET /traceability/lot/{lotId}/tree).
     */
    List<TraceabilityDocument> findByTenantIdAndLotIdOrderByCreatedAtAsc(
            String tenantId, String lotId);
}
