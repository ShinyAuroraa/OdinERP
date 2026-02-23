package com.odin.wms.infrastructure.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * Documento Elasticsearch para indexação de movimentações de estoque.
 * Índice único multi-tenant: 'wms-traceability' com campo tenantId em todas as queries.
 * NÃO usar índice por tenant — causa explosão de shards em multi-tenant.
 *
 * AC7 — indexação assíncrona via TraceabilityIndexer.
 */
@Document(indexName = "wms-traceability")
public class TraceabilityDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String lotId;

    @Field(type = FieldType.Keyword)
    private String lotNumber;

    @Field(type = FieldType.Keyword)
    private String serialNumber;

    @Field(type = FieldType.Keyword)
    private String productId;

    @Field(type = FieldType.Keyword)
    private String movementType;

    @Field(type = FieldType.Integer)
    private int quantity;

    @Field(type = FieldType.Keyword)
    private String sourceLocationCode;

    @Field(type = FieldType.Keyword)
    private String destinationLocationCode;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String referenceId;

    @Field(type = FieldType.Keyword)
    private String referenceType;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    // Getters e setters explícitos (sem Lombok para compatibilidade com Spring Data ES)

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getLotId() { return lotId; }
    public void setLotId(String lotId) { this.lotId = lotId; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getSourceLocationCode() { return sourceLocationCode; }
    public void setSourceLocationCode(String sourceLocationCode) { this.sourceLocationCode = sourceLocationCode; }

    public String getDestinationLocationCode() { return destinationLocationCode; }
    public void setDestinationLocationCode(String destinationLocationCode) { this.destinationLocationCode = destinationLocationCode; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
