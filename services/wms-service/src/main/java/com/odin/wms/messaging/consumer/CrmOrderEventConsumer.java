package com.odin.wms.messaging.consumer;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.messaging.event.CrmOrderConfirmedEvent;
import com.odin.wms.service.PickingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka para eventos de pedidos confirmados pelo CRM.
 * Cria PickingOrders automaticamente em response a {@code crm.orders.confirmed}.
 * Idempotente: UNIQUE (tenant_id, crm_order_id) garante sem duplicatas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmOrderEventConsumer {

    private final PickingService pickingService;

    @KafkaListener(
            topics = "${wms.kafka.topics.crm-orders-confirmed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "crmKafkaListenerContainerFactory",
            errorHandler = "pickingKafkaErrorHandler"
    )
    public void consumeCrmOrderConfirmed(CrmOrderConfirmedEvent event) {
        if (event == null || event.tenantId() == null) {
            log.warn("Evento CRM nulo ou sem tenantId ignorado");
            return;
        }

        try {
            TenantContextHolder.setTenantId(event.tenantId());
            pickingService.createPickingOrderFromKafka(event);
        } catch (DataIntegrityViolationException e) {
            log.warn("Evento CRM duplicado (constraint violation) ignorado: crmOrderId={}",
                    event.crmOrderId());
        } catch (Exception e) {
            log.error("Erro ao processar CRM order event crmOrderId={}: {}",
                    event.crmOrderId(), e.getMessage(), e);
            throw e; // Propagar para o pickingKafkaErrorHandler
        } finally {
            TenantContextHolder.clear();
        }
    }
}
