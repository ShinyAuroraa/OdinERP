package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.ShippingOrder;
import com.odin.wms.domain.entity.ShippingOrder.ShippingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ShippingOrderResponse(
        UUID id,
        UUID tenantId,
        UUID packingOrderId,
        UUID pickingOrderId,
        UUID crmOrderId,
        UUID warehouseId,
        ShippingStatus status,
        String carrierName,
        String vehiclePlate,
        String driverName,
        String trackingNumber,
        LocalDate estimatedDelivery,
        UUID operatorId,
        Instant manifestGeneratedAt,
        Instant dispatchedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        String cancellationReason,
        List<ShippingItemResponse> items,
        Instant createdAt
) {
    public static ShippingOrderResponse from(ShippingOrder order, List<ShippingItemResponse> items) {
        return new ShippingOrderResponse(
                order.getId(),
                order.getTenantId(),
                order.getPackingOrderId(),
                order.getPickingOrderId(),
                order.getCrmOrderId(),
                order.getWarehouseId(),
                order.getStatus(),
                order.getCarrierName(),
                order.getVehiclePlate(),
                order.getDriverName(),
                order.getTrackingNumber(),
                order.getEstimatedDelivery(),
                order.getOperatorId(),
                order.getManifestGeneratedAt(),
                order.getDispatchedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                items,
                order.getCreatedAt()
        );
    }
}
