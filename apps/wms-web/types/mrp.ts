export type MaterialRequestStatus =
  | 'PENDING'
  | 'RESERVING'
  | 'PICKING_PENDING'
  | 'DELIVERED'
  | 'STOCK_SHORTAGE'
  | 'COMPENSATED'

export interface ProductionMaterialRequestItem {
  id: string
  productId: string
  productSku: string
  productName: string
  requestedQuantity: number
  reservedQuantity: number
  deliveredQuantity: number
  lotNumber?: string
}

export interface ProductionMaterialRequest {
  id: string
  productionOrderId: string
  productionOrderNumber?: string
  status: MaterialRequestStatus
  warehouseId: string
  items: ProductionMaterialRequestItem[]
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface MrpFilters {
  status?: MaterialRequestStatus
  page?: number
  size?: number
}
