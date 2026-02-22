package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseAppendOnlyEntity;
import com.odin.wms.domain.enums.AuditAction;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Log imutável (append-only) de todas as ações do sistema.
 * NÃO possui FKs de outras tabelas — registra fatos históricos.
 * NÃO possui updatedAt — registros nunca são modificados.
 * ip_address é INET no PostgreSQL — mapeado como String no Java.
 * old_value/new_value são JSONB — mapeados como String (JSON raw).
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseAppendOnlyEntity {

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    /** UUID do usuário Keycloak — sem FK cross-service. */
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    /** Estado anterior em JSON raw (null para CREATE). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "JSONB")
    private String oldValue;

    /** Estado novo em JSON raw (null para DELETE). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "JSONB")
    private String newValue;

    /** IP do cliente — armazenado como TEXT (migração V3 alterou de INET para TEXT). */
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "correlation_id")
    private String correlationId;
}
