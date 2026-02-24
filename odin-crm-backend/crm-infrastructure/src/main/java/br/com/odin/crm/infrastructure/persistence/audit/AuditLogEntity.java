package br.com.odin.crm.infrastructure.persistence.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro imutável de ação administrativa.
 * Gravado ANTES da ação no Keycloak para garantir que o log não se perde
 * em caso de falha na chamada externa.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_email", length = 100)
    private String actorEmail;

    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLogEntity() {}

    public static AuditLogEntity of(String entityType, UUID entityId, String action,
                                     UUID actorId, String actorEmail,
                                     String oldValues, String newValues) {
        AuditLogEntity e = new AuditLogEntity();
        e.entityType = entityType;
        e.entityId = entityId;
        e.action = action;
        e.actorId = actorId;
        e.actorEmail = actorEmail;
        e.oldValues = oldValues;
        e.newValues = newValues;
        e.occurredAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getAction() { return action; }
    public UUID getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getOldValues() { return oldValues; }
    public String getNewValues() { return newValues; }
    public Instant getOccurredAt() { return occurredAt; }
}
