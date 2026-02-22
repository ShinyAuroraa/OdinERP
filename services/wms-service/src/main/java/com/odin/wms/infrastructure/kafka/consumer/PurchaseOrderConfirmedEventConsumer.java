package com.odin.wms.infrastructure.kafka.consumer;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.dto.request.CreateReceivingNoteItemRequest;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.infrastructure.kafka.event.PurchaseOrderConfirmedEvent;
import com.odin.wms.service.ReceivingNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumer Kafka para criação automática de notas de recebimento
 * quando o SCM confirma um pedido de compra.
 *
 * IMPORTANTE: sem contexto HTTP — tenantId lido do payload, não do JWT.
 * Erros de negócio são enviados ao DLQ; nunca propagamos exception que
 * reprocessaria o evento infinitamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderConfirmedEventConsumer {

    private static final String DLQ_TOPIC = "wms.receiving.dlq";

    private final ReceivingNoteService receivingNoteService;
    private final KafkaTemplate<String, PurchaseOrderConfirmedEvent> kafkaTemplate;

    @KafkaListener(topics = "scm.purchase-orders.confirmed", groupId = "wms-receiving")
    public void consume(PurchaseOrderConfirmedEvent event) {
        log.info("Recebendo evento PO confirmado: purchaseOrderId={}, tenantId={}",
                event.purchaseOrderId(), event.tenantId());

        try {
            TenantContextHolder.setTenantId(event.tenantId());

            List<CreateReceivingNoteItemRequest> items = event.items().stream()
                    .map(i -> new CreateReceivingNoteItemRequest(i.productId(), i.expectedQuantity()))
                    .toList();

            receivingNoteService.createFromKafkaEvent(
                    event.tenantId(),
                    event.purchaseOrderId(),
                    event.warehouseId(),
                    event.supplierId(),
                    items);

        } catch (BusinessException e) {
            log.error("Erro de negócio processando PO {}: {} — enviando para DLQ",
                    event.purchaseOrderId(), e.getMessage());
            kafkaTemplate.send(DLQ_TOPIC, event.purchaseOrderId(), event);
        } catch (Exception e) {
            log.error("Erro inesperado processando PO {}: {} — enviando para DLQ",
                    event.purchaseOrderId(), e.getMessage(), e);
            kafkaTemplate.send(DLQ_TOPIC, event.purchaseOrderId(), event);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
