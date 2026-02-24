'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { InternalTransfer, CreateTransferRequest, ConfirmTransferRequest, CancelTransferRequest } from '@/types/transfers'
import type { PagedResponse } from '@/types/audit'

export function useTransfers(filters?: { status?: string; page?: number; size?: number }) {
  const params = new URLSearchParams()
  if (filters?.status) params.set('status', filters.status)
  if (filters?.page !== undefined) params.set('page', String(filters.page))
  if (filters?.size !== undefined) params.set('size', String(filters.size))
  const qs = params.toString()

  return useQuery<PagedResponse<InternalTransfer>>({
    queryKey: ['transfers', filters],
    queryFn: () => apiClient<PagedResponse<InternalTransfer>>(`/transfers${qs ? `?${qs}` : ''}`),
    staleTime: 30_000,
  })
}

export function useCreateTransfer() {
  const qc = useQueryClient()
  return useMutation<InternalTransfer, Error, CreateTransferRequest>({
    mutationFn: (body) => apiClient<InternalTransfer>('/transfers', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['transfers'] }),
  })
}

export function useConfirmTransfer(id: string) {
  const qc = useQueryClient()
  return useMutation<InternalTransfer, Error, ConfirmTransferRequest>({
    mutationFn: (body) => apiClient<InternalTransfer>(`/transfers/${id}/confirm`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['transfers'] }),
  })
}

export function useCancelTransfer(id: string) {
  const qc = useQueryClient()
  return useMutation<InternalTransfer, Error, CancelTransferRequest>({
    mutationFn: (body) => apiClient<InternalTransfer>(`/transfers/${id}/cancel`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['transfers'] }),
  })
}
