package com.odin.wms.messaging;

import com.odin.wms.messaging.event.FinishedGoodsReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica evento de recebimento de produto acabado no WMS.
 * Erros de publicação são logados mas não propagados — degradação graciosa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinishedGoodsReceivedEventPublisher {

    @Value("${wms.kafka.topics.finished-goods-received:wms.finished.goods.received}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishFinishedGoodsReceived(FinishedGoodsReceivedEvent event) {
        kafkaTemplate.send(topic, event.tenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar FinishedGoodsReceivedEvent para OP {}: {}",
                                event.productionOrderId(), ex.getMessage(), ex);
                    } else {
                        log.info("FinishedGoodsReceivedEvent publicado para OP {} (offset {})",
                                event.productionOrderId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
