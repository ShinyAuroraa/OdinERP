'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import { useAuth } from '@/hooks/useAuth'
import type { ProductWms, CreateProductRequest, UpdateProductRequest } from '@/types/product'

export const productQueryKeys = {
  all: ['products'] as const,
  lists: () => [...productQueryKeys.all, 'list'] as const,
  detail: (id: string) => [...productQueryKeys.all, 'detail', id] as const,
}

export function useProducts(filters?: { storageType?: string; status?: string }) {
  const { token, tenantId } = useAuth()
  const params = new URLSearchParams()
  if (filters?.storageType) params.set('storageType', filters.storageType)
  if (filters?.status) params.set('status', filters.status)
  const query = params.toString() ? `?${params.toString()}` : ''

  return useQuery({
    queryKey: [...productQueryKeys.lists(), filters],
    queryFn: () => apiClient<ProductWms[]>(`/products${query}`, { token, tenantId }),
    enabled: !!token,
    staleTime: 5 * 60_000,
  })
}

export function useProduct(id: string) {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: productQueryKeys.detail(id),
    queryFn: () => apiClient<ProductWms>(`/products/${id}`, { token, tenantId }),
    enabled: !!token && !!id,
    staleTime: 5 * 60_000,
  })
}

export function useCreateProduct() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateProductRequest) =>
      apiClient<ProductWms>('/products', { method: 'POST', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productQueryKeys.lists() })
    },
  })
}

export function useUpdateProduct(id: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateProductRequest) =>
      apiClient<ProductWms>(`/products/${id}`, { method: 'PUT', body: JSON.stringify(data), token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productQueryKeys.lists() })
      queryClient.invalidateQueries({ queryKey: productQueryKeys.detail(id) })
    },
  })
}

export function useDeleteProduct() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiClient<void>(`/products/${id}`, { method: 'DELETE', token, tenantId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productQueryKeys.lists() })
    },
  })
}
