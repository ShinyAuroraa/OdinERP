package com.odin.wms.messaging.consumer;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.messaging.event.PackingCompletedEvent;
import com.odin.wms.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka para eventos de packing concluído.
 * Cria ShippingOrders automaticamente em resposta a {@code wms.packing.completed}.
 * Idempotente: UNIQUE (tenant_id, packing_order_id) garante sem duplicatas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingEventConsumer {

    private final ShippingService shippingService;

    @KafkaListener(
            topics = "${wms.kafka.topics.packing-completed:wms.packing.completed}",
            groupId = "wms-shipping-consumer",
            containerFactory = "shippingKafkaListenerContainerFactory"
    )
    public void handlePackingCompleted(PackingCompletedEvent event) {
        if (event == null || event.tenantId() == null) {
            log.warn("Evento PackingCompleted nulo ou sem tenantId ignorado");
            return;
        }

        try {
            TenantContextHolder.setTenantId(event.tenantId());
            shippingService.createShippingOrderFromKafka(event);
        } catch (DataIntegrityViolationException e) {
            log.warn("PackingCompleted duplicado (constraint violation) ignorado: packingOrderId={}",
                    event.packingOrderId());
        } catch (Exception e) {
            log.error("Erro ao processar PackingCompleted packingOrderId={}: {}",
                    event.packingOrderId(), e.getMessage(), e);
            // Não relançar — não bloqueia offset Kafka (degradação graciosa)
        } finally {
            TenantContextHolder.clear();
        }
    }
}
