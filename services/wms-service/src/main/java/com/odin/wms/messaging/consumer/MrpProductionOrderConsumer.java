package com.odin.wms.messaging.consumer;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.messaging.event.MrpProductionOrderCancelledEvent;
import com.odin.wms.messaging.event.MrpProductionOrderReleasedEvent;
import com.odin.wms.service.ProductionIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka para eventos MRP de Ordem de Produção.
 * Processa lançamento e cancelamento de OPs via {@link ProductionIntegrationService}.
 * Idempotente: UNIQUE (tenant_id, production_order_id) garante ausência de duplicatas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MrpProductionOrderConsumer {

    private final ProductionIntegrationService productionIntegrationService;

    @KafkaListener(
            topics = "${wms.kafka.topics.mrp-production-order-released:mrp.production.order.released}",
            groupId = "wms-mrp-consumer",
            containerFactory = "mrpReleasedKafkaListenerContainerFactory"
    )
    public void handleProductionOrderReleased(MrpProductionOrderReleasedEvent event) {
        if (event == null || event.tenantId() == null) {
            log.warn("Evento MrpProductionOrderReleased nulo ou sem tenantId ignorado");
            return;
        }
        try {
            TenantContextHolder.setTenantId(event.tenantId());
            productionIntegrationService.processProductionOrderReleased(event);
        } catch (DataIntegrityViolationException e) {
            log.warn("MrpProductionOrderReleased duplicado ignorado: productionOrderId={}",
                    event.productionOrderId());
        } catch (Exception e) {
            log.error("Erro ao processar MrpProductionOrderReleased productionOrderId={}: {}",
                    event.productionOrderId(), e.getMessage(), e);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.mrp-production-order-cancelled:mrp.production.order.cancelled}",
            groupId = "wms-mrp-consumer",
            containerFactory = "mrpCancelledKafkaListenerContainerFactory"
    )
    public void handleProductionOrderCancelled(MrpProductionOrderCancelledEvent event) {
        if (event == null || event.tenantId() == null) {
            log.warn("Evento MrpProductionOrderCancelled nulo ou sem tenantId ignorado");
            return;
        }
        try {
            TenantContextHolder.setTenantId(event.tenantId());
            productionIntegrationService.processProductionOrderCancelled(event);
        } catch (Exception e) {
            log.error("Erro ao processar MrpProductionOrderCancelled productionOrderId={}: {}",
                    event.productionOrderId(), e.getMessage(), e);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
