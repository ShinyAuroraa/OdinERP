'use client'

import { useWarehouses } from './warehouses'
import { useProducts } from './products'
import { usePickingOrders } from './picking'
import { useMaterialRequests } from './mrp'
import { useAuditLog } from './audit'

export function useDashboardWarehouses() {
  return useWarehouses()
}

export function useDashboardProducts() {
  return useProducts()
}

export function useDashboardPendingPickings() {
  return usePickingOrders({ status: 'PENDING', size: 1 })
}

export function useDashboardMrpAlerts() {
  return useMaterialRequests({ status: 'STOCK_SHORTAGE', size: 1 })
}

export function useDashboardRecentActivity() {
  return useAuditLog({ page: 0, size: 5 })
}
