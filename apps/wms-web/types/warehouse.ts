export type WarehouseStatus = 'ACTIVE' | 'INACTIVE'
export type ZoneStatus = 'ACTIVE' | 'INACTIVE'
export type AisleStatus = 'ACTIVE' | 'INACTIVE'
export type ShelfStatus = 'ACTIVE' | 'INACTIVE'
export type LocationStatus = 'AVAILABLE' | 'OCCUPIED' | 'BLOCKED' | 'QUARANTINE'

export type LocationType =
  | 'PICKING'
  | 'RECEIVING_DOCK'
  | 'QUARANTINE'
  | 'GENERAL_STORAGE'
  | 'CROSS_DOCK'
  | 'STAGING'
  | 'BULK_STORAGE'

export interface Warehouse {
  id: string
  code: string
  name: string
  location?: string
  capacitySqMeters?: number
  description?: string
  status: WarehouseStatus
  tenantId: string
  zonesCount?: number
  createdAt: string
  updatedAt: string
}

export interface Zone {
  id: string
  code: string
  name: string
  description?: string
  status: ZoneStatus
  warehouseId: string
  aislesCount?: number
  createdAt: string
  updatedAt: string
}

export interface Aisle {
  id: string
  code: string
  name: string
  description?: string
  status: AisleStatus
  zoneId: string
  shelvesCount?: number
  createdAt: string
  updatedAt: string
}

export interface Shelf {
  id: string
  code: string
  name: string
  description?: string
  status: ShelfStatus
  aisleId: string
  locationsCount?: number
  createdAt: string
  updatedAt: string
}

export interface Location {
  id: string
  code: string
  fullAddress: string
  locationType: LocationType
  status: LocationStatus
  capacityUnits?: number
  floor?: number
  column?: number
  shelfId: string
  createdAt: string
  updatedAt: string
}

export interface CreateWarehouseRequest {
  code: string
  name: string
  location?: string
  capacitySqMeters?: number
  description?: string
}

export interface UpdateWarehouseRequest {
  name?: string
  location?: string
  capacitySqMeters?: number
  description?: string
  status?: WarehouseStatus
}

export interface CreateZoneRequest {
  code: string
  name: string
  description?: string
}

export interface CreateAisleRequest {
  code: string
  name: string
  description?: string
}

export interface CreateShelfRequest {
  code: string
  name: string
  description?: string
}

export interface CreateLocationRequest {
  code: string
  locationType: LocationType
  capacityUnits?: number
  floor?: number
  column?: number
}
