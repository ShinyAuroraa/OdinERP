package com.odin.wms.messaging;

import com.odin.wms.domain.entity.PickingItem;
import com.odin.wms.domain.entity.PickingOrder;
import com.odin.wms.messaging.event.PickingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publica evento de picking concluído no tópico wms.picking.completed.
 * Erros de publicação são logados mas não propagados — operação já persistida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PickingCompletedEventPublisher {

    static final String TOPIC_PICKING_COMPLETED = "wms.picking.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPickingCompleted(PickingOrder order, List<PickingItem> items) {
        List<PickingCompletedEvent.PickingCompletedItem> eventItems = items.stream()
                .map(item -> new PickingCompletedEvent.PickingCompletedItem(
                        item.getId(),
                        item.getProductId(),
                        item.getLotId(),
                        item.getLocationId(),
                        item.getQuantityRequested(),
                        item.getQuantityPicked()
                ))
                .toList();

        PickingCompletedEvent event = new PickingCompletedEvent(
                "PICKING_ORDER_COMPLETED",
                order.getTenantId(),
                order.getId(),
                order.getCrmOrderId(),
                order.getStatus().name(),
                order.getWarehouseId(),
                order.getOperatorId(),
                eventItems,
                order.getCompletedAt()
        );

        kafkaTemplate.send(TOPIC_PICKING_COMPLETED, order.getTenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar PickingCompletedEvent para order {}: {}",
                                order.getId(), ex.getMessage(), ex);
                    } else {
                        log.info("PickingCompletedEvent publicado para order {} (offset {})",
                                order.getId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
