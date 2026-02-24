'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { Report, ReportSchedule, GenerateReportRequest, CreateScheduleRequest, ReportFilters } from '@/types/reports'
import type { PagedResponse } from '@/types/audit'

export function useReports(filters?: ReportFilters) {
  const params = new URLSearchParams()
  if (filters?.type) params.set('type', filters.type)
  if (filters?.status) params.set('status', filters.status)
  if (filters?.from) params.set('from', filters.from)
  if (filters?.to) params.set('to', filters.to)
  if (filters?.page !== undefined) params.set('page', String(filters.page))
  if (filters?.size !== undefined) params.set('size', String(filters.size))
  const qs = params.toString()

  return useQuery<PagedResponse<Report>>({
    queryKey: ['reports', 'list', filters],
    queryFn: () => apiClient<PagedResponse<Report>>(`/reports${qs ? `?${qs}` : ''}`),
    staleTime: 60_000,
  })
}

export function useGenerateReport() {
  const qc = useQueryClient()
  return useMutation<Report, Error, GenerateReportRequest>({
    mutationFn: (body) => apiClient<Report>('/reports', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reports', 'list'] }),
  })
}

export function useSchedules() {
  return useQuery<ReportSchedule[]>({
    queryKey: ['reports', 'schedules'],
    queryFn: () => apiClient<ReportSchedule[]>('/reports/schedules'),
    staleTime: 300_000,
  })
}

export function useCreateSchedule() {
  const qc = useQueryClient()
  return useMutation<ReportSchedule, Error, CreateScheduleRequest>({
    mutationFn: (body) => apiClient<ReportSchedule>('/reports/schedules', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reports', 'schedules'] }),
  })
}

export function useToggleSchedule(id: string) {
  const qc = useQueryClient()
  return useMutation<ReportSchedule, Error, void>({
    mutationFn: () => apiClient<ReportSchedule>(`/reports/schedules/${id}/toggle`, { method: 'PUT' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reports', 'schedules'] }),
  })
}
