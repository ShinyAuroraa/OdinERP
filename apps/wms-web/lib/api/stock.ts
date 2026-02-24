'use client'

import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import type { StockBalance, StockBalanceFilters, WarehouseOccupation } from '@/types/stock'

export function useStockBalance(filters?: StockBalanceFilters) {
  const params = new URLSearchParams()
  if (filters?.productId) params.set('productId', filters.productId)
  if (filters?.locationId) params.set('locationId', filters.locationId)
  if (filters?.lotId) params.set('lotId', filters.lotId)
  if (filters?.warehouseId) params.set('warehouseId', filters.warehouseId)
  const qs = params.toString()

  return useQuery<StockBalance[]>({
    queryKey: ['stock', 'balance', filters],
    queryFn: () => apiClient<StockBalance[]>(`/stock/balance${qs ? `?${qs}` : ''}`),
    staleTime: 30_000,
  })
}

export function useLocationStock(locationId: string) {
  return useQuery<StockBalance[]>({
    queryKey: ['stock', 'balance', 'location', locationId],
    queryFn: () => apiClient<StockBalance[]>(`/stock/balance/location/${locationId}`),
    staleTime: 30_000,
    enabled: !!locationId,
  })
}

export function useWarehouseOccupation(warehouseId?: string) {
  const qs = warehouseId ? `?warehouseId=${warehouseId}` : ''
  return useQuery<WarehouseOccupation[]>({
    queryKey: ['stock', 'occupation', warehouseId],
    queryFn: () => apiClient<WarehouseOccupation[]>(`/stock/occupation${qs}`),
    staleTime: 60_000,
  })
}
