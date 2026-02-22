package com.odin.wms.infrastructure.kafka.event;

import java.util.List;
import java.util.UUID;

/**
 * Evento Kafka publicado pelo SCM quando um pedido de compra é confirmado.
 * Consumido pelo WMS para criação automática de ReceivingNote.
 *
 * Tópico: scm.purchase-orders.confirmed
 * Grupo: wms-receiving
 */
public record PurchaseOrderConfirmedEvent(
        String purchaseOrderId,
        UUID tenantId,
        UUID warehouseId,
        UUID supplierId,
        List<PurchaseOrderItem> items
) {
}
