package com.odin.wms.service;

import com.odin.wms.domain.entity.ReportSchedule;
import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.domain.repository.ReportScheduleRepository;
import com.odin.wms.dto.request.ReportScheduleRequest;
import com.odin.wms.dto.response.ReportScheduleResponse;
import com.odin.wms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Gerencia agendamentos de relatórios e executa schedules vencidos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportScheduleService {

    private final ReportScheduleRepository repository;
    private final ReportService reportService;

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Transactional
    public ReportScheduleResponse createSchedule(ReportScheduleRequest request, UUID tenantId) {
        validateCronExpression(request.cronExpression());

        Instant nextExecution = calculateNextExecution(request.cronExpression());

        ReportSchedule schedule = ReportSchedule.builder()
                .tenantId(tenantId)
                .reportType(request.reportType())
                .exportFormat(request.format())
                .cronExpression(request.cronExpression())
                .warehouseId(request.warehouseId())
                .filters(request.filters())
                .active(true)
                .nextExecutionAt(nextExecution)
                .build();

        return toResponse(repository.save(schedule));
    }

    @Transactional(readOnly = true)
    public Page<ReportScheduleResponse> listSchedules(UUID tenantId, Pageable pageable) {
        return repository.findByTenantIdAndActive(tenantId, true, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void deleteSchedule(UUID id, UUID tenantId) {
        ReportSchedule schedule = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado: " + id));

        if (!schedule.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Agendamento pertence a outro tenant");
        }

        repository.delete(schedule);
    }

    // -------------------------------------------------------------------------
    // Scheduler
    // -------------------------------------------------------------------------

    /**
     * Executa a cada 60s: verifica schedules com nextExecutionAt &lt;= now e gera os relatórios.
     * Em múltiplas instâncias (K8s), usar ShedLock ou garantir idempotência via UPDATE...WHERE.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void executeScheduledReports() {
        List<ReportSchedule> dueSchedules = repository.findDueSchedules(Instant.now());

        for (ReportSchedule schedule : dueSchedules) {
            try {
                generateReport(schedule);
                schedule.setLastExecutedAt(Instant.now());
                schedule.setNextExecutionAt(calculateNextExecution(schedule.getCronExpression()));
                repository.save(schedule);

                log.info("Relatório {} gerado para tenant {} — agendamento {}",
                        schedule.getReportType(), schedule.getTenantId(), schedule.getId());
            } catch (Exception e) {
                log.error("Erro ao executar schedule {}: {}", schedule.getId(), e.getMessage(), e);
            }
        }
    }

    private void generateReport(ReportSchedule schedule) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate monthAgo = today.minusMonths(1);
        ExportFormat fmt = schedule.getExportFormat() != null ? schedule.getExportFormat() : ExportFormat.JSON;

        switch (schedule.getReportType()) {
            case FICHA_ESTOQUE -> reportService.generateFichaEstoque(
                    schedule.getWarehouseId(), monthAgo, today, null, fmt, schedule.getTenantId());
            case ANVISA_VIGILANCIA -> reportService.generateAnvisa(
                    schedule.getWarehouseId(), monthAgo, today, null, null, fmt, schedule.getTenantId());
            case MOVIMENTACOES -> reportService.generateMovimentacoes(
                    schedule.getWarehouseId(), monthAgo, today, null, null, null,
                    fmt, schedule.getTenantId(),
                    org.springframework.data.domain.PageRequest.of(0, 1000));
            default -> log.warn("Tipo de relatório não suportado no agendador: {}", schedule.getReportType());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validateCronExpression(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Expressão cron inválida: '" + cron + "'. Use formato Spring (6 campos). " +
                    "Padrões Quartz avançados (L, W, #) não são suportados. Detalhe: " + e.getMessage());
        }
    }

    private Instant calculateNextExecution(String cron) {
        CronExpression cronExpr = CronExpression.parse(cron);
        ZonedDateTime next = cronExpr.next(ZonedDateTime.now(ZoneOffset.UTC));
        return next != null ? next.toInstant() : null;
    }

    private ReportScheduleResponse toResponse(ReportSchedule s) {
        return new ReportScheduleResponse(
                s.getId(),
                s.getTenantId(),
                s.getReportType(),
                s.getExportFormat(),
                s.getCronExpression(),
                s.getWarehouseId(),
                s.isActive(),
                s.getLastExecutedAt(),
                s.getNextExecutionAt(),
                s.getCreatedAt()
        );
    }
}
