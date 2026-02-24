export type ReportType = 'SNGPC' | 'ANVISA' | 'PRESCRIPTION' | 'STOCK_MOVEMENT' | 'INVENTORY'
export type ReportStatus = 'PENDING' | 'GENERATED' | 'ERROR'
export type ScheduleFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY'

export interface Report {
  id: string
  type: ReportType
  status: ReportStatus
  periodFrom: string
  periodTo: string
  fileUrl?: string
  errorMessage?: string
  generatedAt?: string
  tenantId: string
  createdAt: string
}

export interface ReportSchedule {
  id: string
  type: ReportType
  frequency: ScheduleFrequency
  active: boolean
  nextRun?: string
  tenantId: string
  createdAt: string
}

export interface GenerateReportRequest {
  type: ReportType
  periodFrom: string
  periodTo: string
}

export interface CreateScheduleRequest {
  type: ReportType
  frequency: ScheduleFrequency
}

export interface ReportFilters {
  type?: ReportType
  status?: ReportStatus
  from?: string
  to?: string
  page?: number
  size?: number
}
