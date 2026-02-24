export type ShipmentStatus = 'PENDING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'

export interface Shipment {
  id: string
  shippingNumber: string
  packingOrderId: string
  carrier?: string
  vehiclePlate?: string
  trackingNumber?: string
  status: ShipmentStatus
  estimatedDelivery?: string
  shippedAt?: string
  deliveredAt?: string
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface CreateShipmentRequest {
  packingOrderId: string
  carrier?: string
  vehiclePlate?: string
  estimatedDelivery?: string
}

export interface ShipmentFilters {
  status?: ShipmentStatus
  page?: number
  size?: number
}
