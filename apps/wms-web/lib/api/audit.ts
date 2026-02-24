'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { AuditLogEntry, AuditLogFilters, RetentionConfig, UpdateRetentionConfigRequest, PagedResponse } from '@/types/audit'

export function useAuditLog(filters?: AuditLogFilters) {
  const params = new URLSearchParams()
  if (filters?.entityType) params.set('entityType', filters.entityType)
  if (filters?.from) params.set('from', filters.from)
  if (filters?.to) params.set('to', filters.to)
  if (filters?.page !== undefined) params.set('page', String(filters.page))
  if (filters?.size !== undefined) params.set('size', String(filters.size))
  const qs = params.toString()

  return useQuery<PagedResponse<AuditLogEntry>>({
    queryKey: ['audit', 'log', filters],
    queryFn: () => apiClient<PagedResponse<AuditLogEntry>>(`/audit/log${qs ? `?${qs}` : ''}`),
    staleTime: 60_000,
  })
}

export function useRetentionConfig() {
  return useQuery<RetentionConfig>({
    queryKey: ['audit', 'retention'],
    queryFn: () => apiClient<RetentionConfig>('/audit/retention-config'),
    staleTime: 300_000,
  })
}

export function useUpdateRetentionConfig() {
  const qc = useQueryClient()
  return useMutation<RetentionConfig, Error, UpdateRetentionConfigRequest>({
    mutationFn: (body) => apiClient<RetentionConfig>('/audit/retention-config', { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['audit', 'retention'] }),
  })
}
