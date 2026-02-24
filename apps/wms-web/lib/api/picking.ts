'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { PickingOrder, PickingItem, ConfirmPickRequest, PickingFilters } from '@/types/picking'
import type { PagedResponse } from '@/types/audit'

export function usePickingOrders(filters?: PickingFilters) {
  const params = new URLSearchParams()
  if (filters?.status) params.set('status', filters.status)
  if (filters?.priority !== undefined) params.set('priority', String(filters.priority))
  if (filters?.page !== undefined) params.set('page', String(filters.page))
  if (filters?.size !== undefined) params.set('size', String(filters.size))
  const qs = params.toString()

  return useQuery<PagedResponse<PickingOrder>>({
    queryKey: ['picking', 'orders', filters],
    queryFn: () => apiClient<PagedResponse<PickingOrder>>(`/picking/orders${qs ? `?${qs}` : ''}`),
    staleTime: 30_000,
  })
}

export function usePickingOrder(orderId: string) {
  return useQuery<PickingOrder>({
    queryKey: ['picking', 'order', orderId],
    queryFn: () => apiClient<PickingOrder>(`/picking/orders/${orderId}`),
    staleTime: 15_000,
    enabled: !!orderId,
  })
}

export function usePickingItems(orderId: string) {
  return useQuery<PickingItem[]>({
    queryKey: ['picking', 'order', orderId, 'items'],
    queryFn: () => apiClient<PickingItem[]>(`/picking/orders/${orderId}/items`),
    staleTime: 15_000,
    enabled: !!orderId,
  })
}

export function useConfirmPickItem(orderId: string, itemId: string) {
  const qc = useQueryClient()
  return useMutation<PickingItem, Error, ConfirmPickRequest>({
    mutationFn: (body) => apiClient<PickingItem>(`/picking/orders/${orderId}/items/${itemId}`, { method: 'PATCH', body: JSON.stringify(body) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['picking', 'order', orderId] })
    },
  })
}

export function useCompletePickingOrder(orderId: string) {
  const qc = useQueryClient()
  return useMutation<PickingOrder, Error, void>({
    mutationFn: () => apiClient<PickingOrder>(`/picking/orders/${orderId}/complete`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['picking', 'orders'] })
      qc.invalidateQueries({ queryKey: ['picking', 'order', orderId] })
    },
  })
}

export function useCancelPickingOrder(orderId: string) {
  const qc = useQueryClient()
  return useMutation<PickingOrder, Error, void>({
    mutationFn: () => apiClient<PickingOrder>(`/picking/orders/${orderId}/cancel`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['picking', 'orders'] })
      qc.invalidateQueries({ queryKey: ['picking', 'order', orderId] })
    },
  })
}
