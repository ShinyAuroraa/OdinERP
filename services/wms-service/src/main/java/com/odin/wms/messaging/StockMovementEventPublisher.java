package com.odin.wms.messaging;

import com.odin.wms.domain.entity.InternalTransfer;
import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.messaging.event.InternalTransferConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publica eventos de movimentação de estoque no Kafka.
 * Erros de publicação são logados mas não propagados — a operação já foi persistida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockMovementEventPublisher {

    static final String TOPIC_STOCK_MOVEMENTS = "wms.stock.movements";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publica evento de transferência interna confirmada.
     * Key é tenantId para garantir ordenação por tenant.
     * Falhas de Kafka não propagadas — transferência já confirmada no banco.
     */
    public void publishTransferEvent(InternalTransfer transfer, StockMovement movement) {
        InternalTransferConfirmedEvent event = new InternalTransferConfirmedEvent(
                "INTERNAL_TRANSFER_CONFIRMED",
                transfer.getTenantId(),
                transfer.getId(),
                movement.getId(),
                transfer.getProduct().getId(),
                transfer.getLot() != null ? transfer.getLot().getId() : null,
                transfer.getQuantity(),
                transfer.getSourceLocation().getId(),
                transfer.getDestinationLocation().getId(),
                transfer.getConfirmedBy(),
                Instant.now()
        );

        kafkaTemplate.send(TOPIC_STOCK_MOVEMENTS, transfer.getTenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar InternalTransferConfirmedEvent para transfer {}: {}",
                                transfer.getId(), ex.getMessage(), ex);
                    } else {
                        log.info("InternalTransferConfirmedEvent publicado para transfer {} (offset {})",
                                transfer.getId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
