'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import { useAuth } from '@/hooks/useAuth'
import type {
  Warehouse,
  Zone,
  Aisle,
  Shelf,
  Location,
  CreateWarehouseRequest,
  UpdateWarehouseRequest,
  CreateZoneRequest,
  CreateAisleRequest,
  CreateShelfRequest,
  CreateLocationRequest,
} from '@/types/warehouse'

export const warehouseKeys = {
  all: ['warehouses'] as const,
  lists: () => [...warehouseKeys.all, 'list'] as const,
  detail: (id: string) => [...warehouseKeys.all, 'detail', id] as const,
  zones: (warehouseId: string) => ['zones', warehouseId] as const,
  aisles: (zoneId: string) => ['aisles', zoneId] as const,
  shelves: (aisleId: string) => ['shelves', aisleId] as const,
  locations: (shelfId: string) => ['locations', shelfId] as const,
}

export function useWarehouses() {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: warehouseKeys.lists(),
    queryFn: () => apiClient<Warehouse[]>('/warehouses', { token, tenantId }),
    enabled: !!token,
    staleTime: 5 * 60_000,
  })
}

export function useWarehouse(id: string) {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: warehouseKeys.detail(id),
    queryFn: () => apiClient<Warehouse>(`/warehouses/${id}`, { token, tenantId }),
    enabled: !!token && !!id,
    staleTime: 5 * 60_000,
  })
}

export function useZones(warehouseId: string) {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: warehouseKeys.zones(warehouseId),
    queryFn: () => apiClient<Zone[]>(`/warehouses/${warehouseId}/zones`, { token, tenantId }),
    enabled: !!token && !!warehouseId,
    staleTime: 5 * 60_000,
  })
}

export function useAisles(zoneId: string) {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: warehouseKeys.aisles(zoneId),
    queryFn: () => apiClient<Aisle[]>(`/zones/${zoneId}/aisles`, { token, tenantId }),
    enabled: !!token && !!zoneId,
    staleTime: 5 * 60_000,
  })
}

export function useShelves(aisleId: string) {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: warehouseKeys.shelves(aisleId),
    queryFn: () => apiClient<Shelf[]>(`/aisles/${aisleId}/shelves`, { token, tenantId }),
    enabled: !!token && !!aisleId,
    staleTime: 5 * 60_000,
  })
}

export function useLocations(shelfId: string) {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: warehouseKeys.locations(shelfId),
    queryFn: () => apiClient<Location[]>(`/shelves/${shelfId}/locations`, { token, tenantId }),
    enabled: !!token && !!shelfId,
    staleTime: 5 * 60_000,
  })
}

export function useCreateWarehouse() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateWarehouseRequest) =>
      apiClient<Warehouse>('/warehouses', { method: 'POST', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.lists() })
    },
  })
}

export function useUpdateWarehouse(id: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateWarehouseRequest) =>
      apiClient<Warehouse>(`/warehouses/${id}`, { method: 'PUT', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.lists() })
      queryClient.invalidateQueries({ queryKey: warehouseKeys.detail(id) })
    },
  })
}

export function useDeleteWarehouse() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiClient<void>(`/warehouses/${id}`, { method: 'DELETE', token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.lists() })
    },
  })
}

export function useCreateZone(warehouseId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateZoneRequest) =>
      apiClient<Zone>(`/warehouses/${warehouseId}/zones`, { method: 'POST', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.zones(warehouseId) })
    },
  })
}

export function useDeleteZone(warehouseId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (zoneId: string) =>
      apiClient<void>(`/zones/${zoneId}`, { method: 'DELETE', token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.zones(warehouseId) })
    },
  })
}

export function useCreateAisle(zoneId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateAisleRequest) =>
      apiClient<Aisle>(`/zones/${zoneId}/aisles`, { method: 'POST', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.aisles(zoneId) })
    },
  })
}

export function useCreateShelf(aisleId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateShelfRequest) =>
      apiClient<Shelf>(`/aisles/${aisleId}/shelves`, { method: 'POST', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.shelves(aisleId) })
    },
  })
}

export function useCreateLocation(shelfId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateLocationRequest) =>
      apiClient<Location>(`/shelves/${shelfId}/locations`, { method: 'POST', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: warehouseKeys.locations(shelfId) })
    },
  })
}
