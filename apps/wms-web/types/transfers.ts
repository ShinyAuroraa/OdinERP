export type TransferStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'

export interface InternalTransfer {
  id: string
  productId: string
  productSku: string
  productName: string
  lotId?: string
  lotNumber?: string
  quantity: number
  fromLocationId: string
  fromLocationCode: string
  toLocationId: string
  toLocationCode: string
  status: TransferStatus
  requestedBy: string
  confirmedBy?: string
  cancelledBy?: string
  notes?: string
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface CreateTransferRequest {
  productId: string
  lotId?: string
  quantity: number
  fromLocationId: string
  toLocationId: string
  notes?: string
}

export interface ConfirmTransferRequest {
  confirmedLocationId?: string
  notes?: string
}

export interface CancelTransferRequest {
  reason?: string
}
