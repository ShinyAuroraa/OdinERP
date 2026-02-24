export interface AuditLogEntry {
  id: string
  entityType: string
  entityId: string
  actionType: string
  performedBy: string
  performedByName?: string
  details?: Record<string, unknown>
  tenantId: string
  createdAt: string
}

export interface AuditLogFilters {
  entityType?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface RetentionConfig {
  retentionDays: number
  updatedAt: string
  updatedBy: string
}

export interface UpdateRetentionConfigRequest {
  retentionDays: number
}

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
