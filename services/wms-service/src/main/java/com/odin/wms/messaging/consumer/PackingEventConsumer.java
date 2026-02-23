package com.odin.wms.messaging.consumer;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.messaging.event.PickingCompletedEvent;
import com.odin.wms.service.PackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka para eventos de picking concluído.
 * Cria PackingOrders automaticamente em resposta a {@code wms.picking.completed}.
 * Idempotente: UNIQUE (tenant_id, picking_order_id) garante sem duplicatas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PackingEventConsumer {

    private final PackingService packingService;

    @KafkaListener(
            topics = "${wms.kafka.topics.picking-completed:wms.picking.completed}",
            groupId = "wms-packing-consumer",
            containerFactory = "packingKafkaListenerContainerFactory"
    )
    public void consumePickingCompleted(PickingCompletedEvent event) {
        if (event == null || event.tenantId() == null) {
            log.warn("Evento PickingCompleted nulo ou sem tenantId ignorado");
            return;
        }

        try {
            TenantContextHolder.setTenantId(event.tenantId());
            packingService.createPackingOrderFromKafka(event);
        } catch (DataIntegrityViolationException e) {
            log.warn("PickingCompleted duplicado (constraint violation) ignorado: pickingOrderId={}",
                    event.pickingOrderId());
        } catch (Exception e) {
            log.error("Erro ao processar PickingCompleted pickingOrderId={}: {}",
                    event.pickingOrderId(), e.getMessage(), e);
            // Não relançar — não bloqueia offset Kafka (degradação graciosa)
        } finally {
            TenantContextHolder.clear();
        }
    }
}
