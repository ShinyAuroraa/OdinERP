export interface StockBalance {
  id: string
  productId: string
  productSku: string
  productName: string
  locationId: string
  locationCode: string
  warehouseId: string
  lotId?: string
  lotNumber?: string
  availableQuantity: number
  reservedQuantity: number
  totalQuantity: number
  tenantId: string
  updatedAt: string
}

export interface StockBalanceFilters {
  productId?: string
  locationId?: string
  lotId?: string
  warehouseId?: string
}

export interface ZoneOccupation {
  zoneId: string
  zoneName: string
  totalCapacity: number
  usedCapacity: number
  occupancyPercent: number
}

export interface WarehouseOccupation {
  warehouseId: string
  warehouseName: string
  totalCapacity: number
  usedCapacity: number
  occupancyPercent: number
  zones: ZoneOccupation[]
}
