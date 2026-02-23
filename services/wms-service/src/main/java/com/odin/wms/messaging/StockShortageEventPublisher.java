package com.odin.wms.messaging;

import com.odin.wms.messaging.event.StockShortageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica evento de falta de estoque de matéria-prima.
 * Erros de publicação são logados mas não propagados — degradação graciosa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockShortageEventPublisher {

    @Value("${wms.kafka.topics.stock-shortage:wms.stock.shortage}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishStockShortage(StockShortageEvent event) {
        kafkaTemplate.send(topic, event.tenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar StockShortageEvent para OP {}: {}",
                                event.productionOrderId(), ex.getMessage(), ex);
                    } else {
                        log.info("StockShortageEvent publicado para OP {} (offset {})",
                                event.productionOrderId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
