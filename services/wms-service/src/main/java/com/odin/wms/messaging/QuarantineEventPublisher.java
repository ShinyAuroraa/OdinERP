package com.odin.wms.messaging;

import com.odin.wms.domain.entity.QuarantineTask;
import com.odin.wms.infrastructure.kafka.event.QuarantineReturnEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica eventos de quarentena no tópico Kafka do WMS.
 * Erros de publicação são logados mas não propagados — a decisão já foi persistida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuarantineEventPublisher {

    static final String TOPIC_RETURN_TO_SUPPLIER = "wms.quarantine.return-supplier";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publica evento de devolução ao fornecedor após decisão RETURN_TO_SUPPLIER.
     * O key é o tenantId para garantir ordenação por tenant.
     */
    public void publishReturnToSupplier(QuarantineTask task) {
        QuarantineReturnEvent event = new QuarantineReturnEvent(
                task.getTenantId(),
                task.getId(),
                task.getReceivingNote().getId(),
                task.getStockItem().getId(),
                task.getStockItem().getProduct().getId(),
                task.getStockItem().getQuantityAvailable(),
                task.getQualityNotes()
        );

        kafkaTemplate.send(TOPIC_RETURN_TO_SUPPLIER, task.getTenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar QuarantineReturnEvent para task {}: {}",
                                task.getId(), ex.getMessage(), ex);
                    } else {
                        log.info("QuarantineReturnEvent publicado para task {} (offset {})",
                                task.getId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
