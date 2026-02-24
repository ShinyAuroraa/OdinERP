export type InventoryCountStatus = 'CREATED' | 'IN_PROGRESS' | 'RECONCILED' | 'APPROVED' | 'CLOSED'
export type InventoryItemStatus = 'PENDING' | 'COUNTED' | 'SECOND_COUNT' | 'DIVERGENT' | 'APPROVED'

export interface InventoryCount {
  id: string
  warehouseId: string
  warehouseName?: string
  status: InventoryCountStatus
  totalItems: number
  countedItems: number
  divergentItems: number
  createdBy: string
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface InventoryCountItem {
  id: string
  countId: string
  productId: string
  productSku: string
  productName: string
  locationId: string
  locationCode: string
  lotNumber?: string
  expectedQuantity: number
  countedQuantity?: number
  secondCountQuantity?: number
  divergence?: number
  status: InventoryItemStatus
  notes?: string
}

export interface ReconciliationSummary {
  totalItems: number
  divergentItems: number
  positiveAdjustments: number
  negativeAdjustments: number
  approvedAdjustments?: number
}

export interface CreateInventoryCountRequest {
  warehouseId: string
  locationIds?: string[]
  notes?: string
}

export interface SubmitCountRequest {
  countedQuantity: number
  notes?: string
}
