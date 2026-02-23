package com.odin.wms.messaging;

import com.odin.wms.messaging.event.PackingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica evento de packing concluído no tópico wms.packing.completed.
 * Erros de publicação são logados mas não propagados — degradação graciosa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PackingCompletedEventPublisher {

    @Value("${wms.kafka.topics.packing-completed:wms.packing.completed}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPackingCompleted(PackingCompletedEvent event) {
        kafkaTemplate.send(topic, event.tenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar PackingCompletedEvent para order {}: {}",
                                event.packingOrderId(), ex.getMessage(), ex);
                    } else {
                        log.info("PackingCompletedEvent publicado para order {} (offset {})",
                                event.packingOrderId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
