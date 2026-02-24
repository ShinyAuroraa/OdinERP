export type PackingOrderStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type PackingItemStatus = 'PENDING' | 'PACKED' | 'SKIPPED'

export interface PackingOrder {
  id: string
  packingOrderNumber: string
  pickingOrderId: string
  status: PackingOrderStatus
  warehouseId: string
  totalItems: number
  packedItems: number
  totalWeight?: number
  totalVolumes: number
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface PackingItem {
  id: string
  packingOrderId: string
  pickingItemId: string
  productId: string
  productSku: string
  productName: string
  lotNumber?: string
  quantity: number
  weight?: number
  status: PackingItemStatus
}

export interface PackingFilters {
  status?: PackingOrderStatus
  page?: number
  size?: number
}
