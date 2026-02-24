'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { Shipment, CreateShipmentRequest, ShipmentFilters } from '@/types/shipping'
import type { PagedResponse } from '@/types/audit'

export function useShipments(filters?: ShipmentFilters) {
  const params = new URLSearchParams()
  if (filters?.status) params.set('status', filters.status)
  if (filters?.page !== undefined) params.set('page', String(filters.page))
  if (filters?.size !== undefined) params.set('size', String(filters.size))
  const qs = params.toString()

  return useQuery<PagedResponse<Shipment>>({
    queryKey: ['shipping', 'shipments', filters],
    queryFn: () => apiClient<PagedResponse<Shipment>>(`/shipping${qs ? `?${qs}` : ''}`),
    staleTime: 30_000,
  })
}

export function useShipment(shipmentId: string) {
  return useQuery<Shipment>({
    queryKey: ['shipping', 'shipment', shipmentId],
    queryFn: () => apiClient<Shipment>(`/shipping/${shipmentId}`),
    staleTime: 15_000,
    enabled: !!shipmentId,
  })
}

export function useCreateShipment() {
  const qc = useQueryClient()
  return useMutation<Shipment, Error, CreateShipmentRequest>({
    mutationFn: (body) => apiClient<Shipment>('/shipping', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shipping', 'shipments'] }),
  })
}

export function useShipOrder(shipmentId: string) {
  const qc = useQueryClient()
  return useMutation<Shipment, Error, void>({
    mutationFn: () => apiClient<Shipment>(`/shipping/${shipmentId}/ship`, { method: 'PUT' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['shipping', 'shipments'] })
      qc.invalidateQueries({ queryKey: ['shipping', 'shipment', shipmentId] })
    },
  })
}

export function useCancelShipment(shipmentId: string) {
  const qc = useQueryClient()
  return useMutation<Shipment, Error, void>({
    mutationFn: () => apiClient<Shipment>(`/shipping/${shipmentId}/cancel`, { method: 'PUT' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['shipping', 'shipments'] })
      qc.invalidateQueries({ queryKey: ['shipping', 'shipment', shipmentId] })
    },
  })
}
