package com.odin.wms.infrastructure.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * Documento Elasticsearch para indexação do audit_log.
 * Índice único multi-tenant: 'wms-audit-log' com campo tenantId em todas as queries.
 * NÃO usar índice por tenant — causa explosão de shards em multi-tenant.
 *
 * IMPORTANTE: usa getters/setters explícitos (sem Lombok) para compatibilidade
 * com Spring Data Elasticsearch — mesmo padrão de TraceabilityDocument.
 */
@Document(indexName = "wms-audit-log")
public class AuditLogDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String entityType;

    @Field(type = FieldType.Keyword)
    private String entityId;

    @Field(type = FieldType.Keyword)
    private String action;

    @Field(type = FieldType.Keyword)
    private String actorId;

    @Field(type = FieldType.Text, fielddata = true)
    private String actorName;

    @Field(type = FieldType.Keyword)
    private String actorRole;

    @Field(type = FieldType.Keyword)
    private String correlationId;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    // Getters e setters explícitos (sem Lombok para compatibilidade com Spring Data ES)

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
