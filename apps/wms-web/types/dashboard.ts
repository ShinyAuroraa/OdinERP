export interface KpiCard {
  label: string
  value: number | string
  icon: string
  description?: string
}

export interface ProcessStatus {
  process: string
  pending: number
  inProgress: number
  completed: number
}

export interface DashboardStats {
  totalWarehouses: number
  totalProducts: number
  pendingPickings: number
  mrpAlerts: number
}
