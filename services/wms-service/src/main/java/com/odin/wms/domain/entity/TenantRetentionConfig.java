package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Configuração de retenção LGPD por tenant.
 * Armazena o período de retenção dos registros de audit_log em meses.
 * Default: 84 meses (7 anos) — mínimo LGPD.
 * Usa BaseEntity (não BaseAppendOnlyEntity) pois pode ser atualizado.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "tenant_retention_config")
public class TenantRetentionConfig extends BaseEntity {

    @Column(name = "retention_months", nullable = false)
    private int retentionMonths;
}
