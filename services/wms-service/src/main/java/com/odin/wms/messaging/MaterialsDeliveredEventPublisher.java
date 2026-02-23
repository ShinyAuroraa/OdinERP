package com.odin.wms.messaging;

import com.odin.wms.messaging.event.MaterialsDeliveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica evento de entrega de matérias-primas na produção.
 * Erros de publicação são logados mas não propagados — degradação graciosa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialsDeliveredEventPublisher {

    @Value("${wms.kafka.topics.materials-delivered:wms.materials.delivered}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishMaterialsDelivered(MaterialsDeliveredEvent event) {
        kafkaTemplate.send(topic, event.tenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar MaterialsDeliveredEvent para OP {}: {}",
                                event.productionOrderId(), ex.getMessage(), ex);
                    } else {
                        log.info("MaterialsDeliveredEvent publicado para OP {} (offset {})",
                                event.productionOrderId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
