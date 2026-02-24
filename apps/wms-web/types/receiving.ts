export type ReceivingNoteStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FLAGGED'
export type ReceivingItemStatus = 'PENDING' | 'CONFIRMED' | 'FLAGGED'

export interface ReceivingNoteItem {
  id: string
  productId: string
  productSku: string
  productName: string
  expectedQuantity: number
  receivedQuantity?: number
  lotNumber?: string
  manufacturingDate?: string
  expiryDate?: string
  gs1Code?: string
  serialNumbers?: string[]
  status: ReceivingItemStatus
}

export interface ReceivingNote {
  id: string
  noteNumber: string
  warehouseId: string
  warehouseName?: string
  dockLocationId: string
  supplierId?: string
  purchaseOrderRef?: string
  status: ReceivingNoteStatus
  items: ReceivingNoteItem[]
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface CreateReceivingNoteRequest {
  warehouseId: string
  dockLocationId: string
  supplierId?: string
  purchaseOrderRef?: string
  items: Array<{
    productId: string
    expectedQuantity: number
  }>
}

export interface ConfirmReceivingItemRequest {
  receivedQuantity: number
  lotNumber?: string
  manufacturingDate?: string
  expiryDate?: string
  gs1Code?: string
  serialNumbers?: string[]
}
