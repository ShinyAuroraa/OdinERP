export type StorageType =
  | 'DRY'
  | 'REFRIGERATED'
  | 'FROZEN'
  | 'HAZARDOUS'
  | 'OVERSIZED'
  | 'CONTROLLED'

export type ProductStatus = 'ACTIVE' | 'INACTIVE'

export interface ProductWms {
  id: string
  productId?: string
  sku: string
  name: string
  description?: string
  storageType: StorageType
  lengthM?: number
  widthM?: number
  heightM?: number
  weightKg?: number
  unitsPerLocation?: number
  ean13?: string
  gs1128?: string
  controlsLot: boolean
  controlsSerial: boolean
  controlsExpiry: boolean
  requiresSanitaryVigilance: boolean
  status: ProductStatus
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface CreateProductRequest {
  productId?: string
  sku: string
  name: string
  description?: string
  storageType: StorageType
  lengthM?: number
  widthM?: number
  heightM?: number
  weightKg?: number
  unitsPerLocation?: number
  ean13?: string
  gs1128?: string
  controlsLot?: boolean
  controlsSerial?: boolean
  controlsExpiry?: boolean
  requiresSanitaryVigilance?: boolean
}

export interface UpdateProductRequest {
  name?: string
  description?: string
  storageType?: StorageType
  lengthM?: number
  widthM?: number
  heightM?: number
  weightKg?: number
  unitsPerLocation?: number
  ean13?: string
  gs1128?: string
  controlsLot?: boolean
  controlsSerial?: boolean
  controlsExpiry?: boolean
  requiresSanitaryVigilance?: boolean
  status?: ProductStatus
}
