export type PickingOrderStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type PickingItemStatus = 'PENDING' | 'PICKED' | 'PARTIAL' | 'SKIPPED'
export type PickingOrderType = 'OUTBOUND' | 'PRODUCTION'

export interface PickingOrder {
  id: string
  orderNumber: string
  orderType: PickingOrderType
  customerId?: string
  customerName?: string
  productionOrderId?: string
  priority: number
  status: PickingOrderStatus
  warehouseId: string
  totalItems: number
  pickedItems: number
  createdBy: string
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface PickingItem {
  id: string
  pickingOrderId: string
  productId: string
  productSku: string
  productName: string
  locationId: string
  locationCode: string
  lotNumber?: string
  serialNumber?: string
  requestedQuantity: number
  pickedQuantity: number
  status: PickingItemStatus
  sortOrder: number
}

export interface ConfirmPickRequest {
  pickedQuantity: number
  actualLocationId?: string
  lotNumber?: string
}

export interface PickingFilters {
  status?: PickingOrderStatus
  priority?: number
  page?: number
  size?: number
}
