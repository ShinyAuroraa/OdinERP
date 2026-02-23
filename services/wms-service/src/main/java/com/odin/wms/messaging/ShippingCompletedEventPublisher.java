package com.odin.wms.messaging;

import com.odin.wms.messaging.event.ShippingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica evento de expedição concluída no tópico wms.shipping.completed.
 * Erros de publicação são logados mas não propagados — degradação graciosa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingCompletedEventPublisher {

    @Value("${wms.kafka.topics.shipping-completed:wms.shipping.completed}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishShippingCompleted(ShippingCompletedEvent event) {
        kafkaTemplate.send(topic, event.tenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar ShippingCompletedEvent para order {}: {}",
                                event.shippingOrderId(), ex.getMessage(), ex);
                    } else {
                        log.info("ShippingCompletedEvent publicado para order {} (offset {})",
                                event.shippingOrderId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
