'use client'

import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import type { ProductionMaterialRequest, MrpFilters } from '@/types/mrp'
import type { PagedResponse } from '@/types/audit'

export function useMaterialRequests(filters?: MrpFilters) {
  const params = new URLSearchParams()
  if (filters?.status) params.set('status', filters.status)
  if (filters?.page !== undefined) params.set('page', String(filters.page))
  if (filters?.size !== undefined) params.set('size', String(filters.size))
  const qs = params.toString()

  return useQuery<PagedResponse<ProductionMaterialRequest>>({
    queryKey: ['mrp', 'requests', filters],
    queryFn: () => apiClient<PagedResponse<ProductionMaterialRequest>>(`/production-material-requests${qs ? `?${qs}` : ''}`),
    staleTime: 60_000,
  })
}

export function useMaterialRequest(id: string) {
  return useQuery<ProductionMaterialRequest>({
    queryKey: ['mrp', 'request', id],
    queryFn: () => apiClient<ProductionMaterialRequest>(`/production-material-requests/${id}`),
    staleTime: 30_000,
    enabled: !!id,
  })
}
