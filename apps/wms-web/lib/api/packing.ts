'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { PackingOrder, PackingItem, PackingFilters } from '@/types/packing'
import type { PagedResponse } from '@/types/audit'

export function usePackingOrders(filters?: PackingFilters) {
  const params = new URLSearchParams()
  if (filters?.status) params.set('status', filters.status)
  if (filters?.page !== undefined) params.set('page', String(filters.page))
  if (filters?.size !== undefined) params.set('size', String(filters.size))
  const qs = params.toString()

  return useQuery<PagedResponse<PackingOrder>>({
    queryKey: ['packing', 'orders', filters],
    queryFn: () => apiClient<PagedResponse<PackingOrder>>(`/packing${qs ? `?${qs}` : ''}`),
    staleTime: 30_000,
  })
}

export function usePackingOrder(packingId: string) {
  return useQuery<PackingOrder>({
    queryKey: ['packing', 'order', packingId],
    queryFn: () => apiClient<PackingOrder>(`/packing/${packingId}`),
    staleTime: 15_000,
    enabled: !!packingId,
  })
}

export function usePackingItems(packingId: string) {
  return useQuery<PackingItem[]>({
    queryKey: ['packing', 'order', packingId, 'items'],
    queryFn: () => apiClient<PackingItem[]>(`/packing/${packingId}/items`),
    staleTime: 15_000,
    enabled: !!packingId,
  })
}

export function useStartPacking(packingId: string) {
  const qc = useQueryClient()
  return useMutation<PackingOrder, Error, void>({
    mutationFn: () => apiClient<PackingOrder>(`/packing/${packingId}/start`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['packing', 'order', packingId] }),
  })
}

export function useCompletePacking(packingId: string) {
  const qc = useQueryClient()
  return useMutation<PackingOrder, Error, void>({
    mutationFn: () => apiClient<PackingOrder>(`/packing/${packingId}/complete`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['packing', 'orders'] })
      qc.invalidateQueries({ queryKey: ['packing', 'order', packingId] })
    },
  })
}
