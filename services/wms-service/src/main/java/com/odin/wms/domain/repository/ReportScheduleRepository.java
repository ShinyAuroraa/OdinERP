package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ReportSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

    Page<ReportSchedule> findByTenantIdAndActive(UUID tenantId, boolean active, Pageable pageable);

    Optional<ReportSchedule> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Busca agendamentos vencidos: ativos e com nextExecutionAt <= now.
     * Usado pelo scheduler para executar relatórios pendentes.
     */
    @Query("""
            SELECT s FROM ReportSchedule s
            WHERE s.active = true
              AND s.nextExecutionAt IS NOT NULL
              AND s.nextExecutionAt <= :now
            ORDER BY s.nextExecutionAt ASC
            """)
    List<ReportSchedule> findDueSchedules(@Param("now") Instant now);
}
