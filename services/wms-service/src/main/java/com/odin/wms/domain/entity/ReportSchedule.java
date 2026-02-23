package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.domain.enums.ReportType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "report_schedules")
public class ReportSchedule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_format", nullable = false, length = 10)
    private ExportFormat exportFormat;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters", columnDefinition = "jsonb")
    private Map<String, String> filters;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @Column(name = "next_execution_at")
    private Instant nextExecutionAt;
}
