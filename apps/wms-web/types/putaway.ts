export type PutawayTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type QuarantineTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'DECIDED' | 'CANCELLED'
export type QuarantineDecision = 'RELEASE_TO_STOCK' | 'RETURN_TO_SUPPLIER' | 'SCRAP'

export interface PutawayTask {
  id: string
  receivingNoteId: string
  productId: string
  productSku: string
  productName: string
  lotNumber?: string
  quantity: number
  suggestedLocationId?: string
  suggestedLocationCode?: string
  confirmedLocationId?: string
  confirmedLocationCode?: string
  status: PutawayTaskStatus
  warehouseId: string
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface QuarantineTask {
  id: string
  receivingNoteId: string
  productId: string
  productSku: string
  productName: string
  lotNumber?: string
  quantity: number
  decision?: QuarantineDecision
  qualityNotes?: string
  status: QuarantineTaskStatus
  warehouseId: string
  tenantId: string
  createdAt: string
  updatedAt: string
}

export interface ConfirmPutawayRequest {
  confirmedLocationId?: string
}

export interface DecideQuarantineRequest {
  decision: QuarantineDecision
  qualityNotes?: string
}

export const QUARANTINE_DECISION_LABELS: Record<QuarantineDecision, string> = {
  RELEASE_TO_STOCK: 'Liberar para Estoque',
  RETURN_TO_SUPPLIER: 'Devolver ao Fornecedor',
  SCRAP: 'Sucatar',
}
