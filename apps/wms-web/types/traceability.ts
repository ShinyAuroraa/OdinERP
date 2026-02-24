export interface TraceabilityMovement {
  id: string
  movementType: string
  fromLocationCode?: string
  toLocationCode?: string
  quantity: number
  operatorId: string
  operatorName?: string
  referenceId?: string
  referenceType?: string
  createdAt: string
}

export interface LotTraceability {
  lotNumber: string
  productSku: string
  productName: string
  expiryDate?: string
  manufacturingDate?: string
  movements: TraceabilityMovement[]
}

export interface SerialTraceability {
  serialNumber: string
  productSku: string
  productName: string
  currentLocationCode?: string
  movements: TraceabilityMovement[]
}

export interface TraceabilityTreeNode {
  lotId: string
  lotNumber: string
  productSku: string
  quantity: number
  children: TraceabilityTreeNode[]
}

export interface ExpiryAlert {
  lotId: string
  lotNumber: string
  productId: string
  productSku: string
  productName: string
  warehouseId: string
  expiryDate: string
  quantity: number
  daysUntilExpiry: number
}

export interface GS1ParseRequest {
  gs1Code: string
}

export interface GS1ParsedResponse {
  gtin?: string
  lotNumber?: string
  serialNumber?: string
  expiryDate?: string
  manufacturingDate?: string
  sscc?: string
  rawAIs: Record<string, string>
}

export interface GS1GeneratedResponse {
  gs1128: string
  qrCode?: string
}
