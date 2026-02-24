'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type {
  InventoryCount, InventoryCountItem, ReconciliationSummary,
  CreateInventoryCountRequest, SubmitCountRequest,
} from '@/types/inventory'
import type { PagedResponse } from '@/types/audit'

export function useInventoryCounts() {
  return useQuery<InventoryCount[]>({
    queryKey: ['inventory', 'counts'],
    queryFn: () => apiClient<InventoryCount[]>('/inventory/count'),
    staleTime: 30_000,
  })
}

export function useInventoryCount(countId: string) {
  return useQuery<InventoryCount>({
    queryKey: ['inventory', 'count', countId],
    queryFn: () => apiClient<InventoryCount>(`/inventory/count/${countId}`),
    staleTime: 15_000,
    enabled: !!countId,
  })
}

export function useCountItems(countId: string, page = 0, size = 20) {
  return useQuery<PagedResponse<InventoryCountItem>>({
    queryKey: ['inventory', 'count', countId, 'items', page],
    queryFn: () => apiClient<PagedResponse<InventoryCountItem>>(`/inventory/count/${countId}/items?page=${page}&size=${size}`),
    staleTime: 15_000,
    enabled: !!countId,
  })
}

export function useCreateInventoryCount() {
  const qc = useQueryClient()
  return useMutation<InventoryCount, Error, CreateInventoryCountRequest>({
    mutationFn: (body) => apiClient<InventoryCount>('/inventory/count', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventory', 'counts'] }),
  })
}

export function useStartCount(countId: string) {
  const qc = useQueryClient()
  return useMutation<InventoryCount, Error, void>({
    mutationFn: () => apiClient<InventoryCount>(`/inventory/count/${countId}/start`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventory', 'count', countId] }),
  })
}

export function useSubmitCountItem(countId: string, itemId: string) {
  const qc = useQueryClient()
  return useMutation<InventoryCountItem, Error, SubmitCountRequest>({
    mutationFn: (body) => apiClient<InventoryCountItem>(`/inventory/count/${countId}/items/${itemId}`, { method: 'PATCH', body: JSON.stringify(body) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventory', 'count', countId] })
    },
  })
}

export function useReconcile(countId: string) {
  const qc = useQueryClient()
  return useMutation<ReconciliationSummary, Error, void>({
    mutationFn: () => apiClient<ReconciliationSummary>(`/inventory/count/${countId}/reconcile`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventory', 'count', countId] }),
  })
}

export function useApproveCount(countId: string) {
  const qc = useQueryClient()
  return useMutation<InventoryCount, Error, void>({
    mutationFn: () => apiClient<InventoryCount>(`/inventory/count/${countId}/approve`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['inventory', 'count', countId] }),
  })
}

export function useCloseCount(countId: string) {
  const qc = useQueryClient()
  return useMutation<InventoryCount, Error, void>({
    mutationFn: () => apiClient<InventoryCount>(`/inventory/count/${countId}/close`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['inventory', 'counts'] })
      qc.invalidateQueries({ queryKey: ['inventory', 'count', countId] })
    },
  })
}
