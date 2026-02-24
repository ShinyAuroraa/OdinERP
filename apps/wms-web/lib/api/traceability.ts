'use client'

import { useQuery, useMutation } from '@tanstack/react-query'
import { apiClient } from './client'
import type {
  LotTraceability, SerialTraceability, TraceabilityTreeNode,
  ExpiryAlert, GS1ParseRequest, GS1ParsedResponse, GS1GeneratedResponse,
} from '@/types/traceability'

export function useLotTraceability(lotNumber: string) {
  return useQuery<LotTraceability>({
    queryKey: ['traceability', 'lot', lotNumber],
    queryFn: () => apiClient<LotTraceability>(`/traceability/lot/${encodeURIComponent(lotNumber)}`),
    staleTime: 30_000,
    enabled: !!lotNumber,
  })
}

export function useSerialTraceability(serialNumber: string) {
  return useQuery<SerialTraceability>({
    queryKey: ['traceability', 'serial', serialNumber],
    queryFn: () => apiClient<SerialTraceability>(`/traceability/serial/${encodeURIComponent(serialNumber)}`),
    staleTime: 30_000,
    enabled: !!serialNumber,
  })
}

export function useTraceabilityTree(lotId: string) {
  return useQuery<TraceabilityTreeNode>({
    queryKey: ['traceability', 'tree', lotId],
    queryFn: () => apiClient<TraceabilityTreeNode>(`/traceability/lot/${lotId}/tree`),
    staleTime: 60_000,
    enabled: !!lotId,
  })
}

export function useExpiryAlerts(productId: string, filters?: { warehouseId?: string; expiryBefore?: string }) {
  const params = new URLSearchParams()
  if (filters?.warehouseId) params.set('warehouseId', filters.warehouseId)
  if (filters?.expiryBefore) params.set('expiryBefore', filters.expiryBefore)
  const qs = params.toString()

  return useQuery<ExpiryAlert[]>({
    queryKey: ['traceability', 'expiry', productId, filters],
    queryFn: () => apiClient<ExpiryAlert[]>(`/traceability/product/${productId}/expiry${qs ? `?${qs}` : ''}`),
    staleTime: 60_000,
    enabled: !!productId,
  })
}

export function useParseGS1() {
  return useMutation<GS1ParsedResponse, Error, GS1ParseRequest>({
    mutationFn: (body) => apiClient<GS1ParsedResponse>('/gs1/parse', { method: 'POST', body: JSON.stringify(body) }),
  })
}

export function useGenerateGS1(params?: { gtin?: string; lotNumber?: string; serialNumber?: string; expiryDate?: string }) {
  const qs = params ? `?${new URLSearchParams(Object.entries(params).filter(([, v]) => !!v) as [string, string][]).toString()}` : ''
  return useQuery<GS1GeneratedResponse>({
    queryKey: ['gs1', 'generate', params],
    queryFn: () => apiClient<GS1GeneratedResponse>(`/gs1/generate${qs}`),
    staleTime: 0,
    enabled: !!(params?.gtin),
  })
}
