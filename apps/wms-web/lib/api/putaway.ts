'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import { useAuth } from '@/hooks/useAuth'
import type {
  PutawayTask,
  QuarantineTask,
  ConfirmPutawayRequest,
  DecideQuarantineRequest,
} from '@/types/putaway'

export const putawayKeys = {
  all: ['putaway-tasks'] as const,
  lists: () => [...putawayKeys.all, 'list'] as const,
  list: (filters?: Record<string, string>) => [...putawayKeys.lists(), filters] as const,
}

export const quarantineKeys = {
  all: ['quarantine-tasks'] as const,
  lists: () => [...quarantineKeys.all, 'list'] as const,
  list: (filters?: Record<string, string>) => [...quarantineKeys.lists(), filters] as const,
}

export function usePutawayTasks(filters?: Record<string, string>) {
  const { token, tenantId } = useAuth()
  const params = filters ? new URLSearchParams(filters).toString() : ''
  return useQuery({
    queryKey: putawayKeys.list(filters),
    queryFn: () =>
      apiClient<PutawayTask[]>(`/putaway-tasks${params ? `?${params}` : ''}`, { token, tenantId }),
    enabled: !!token,
    staleTime: 30_000,
  })
}

export function useStartPutaway() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiClient<PutawayTask>(`/putaway-tasks/${id}/start`, {
        method: 'PATCH',
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: putawayKeys.lists() }),
  })
}

export function useConfirmPutaway() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: ConfirmPutawayRequest }) =>
      apiClient<PutawayTask>(`/putaway-tasks/${id}/confirm`, {
        method: 'PATCH',
        body: JSON.stringify(data),
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: putawayKeys.lists() }),
  })
}

export function useCancelPutaway() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiClient<void>(`/putaway-tasks/${id}/cancel`, {
        method: 'PATCH',
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: putawayKeys.lists() }),
  })
}

export function useQuarantineTasks(filters?: Record<string, string>) {
  const { token, tenantId } = useAuth()
  const params = filters ? new URLSearchParams(filters).toString() : ''
  return useQuery({
    queryKey: quarantineKeys.list(filters),
    queryFn: () =>
      apiClient<QuarantineTask[]>(`/quarantine-tasks${params ? `?${params}` : ''}`, {
        token,
        tenantId,
      }),
    enabled: !!token,
    staleTime: 30_000,
  })
}

export function useStartQuarantine() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiClient<QuarantineTask>(`/quarantine-tasks/${id}/start`, {
        method: 'PATCH',
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: quarantineKeys.lists() }),
  })
}

export function useDecideQuarantine() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: DecideQuarantineRequest }) =>
      apiClient<QuarantineTask>(`/quarantine-tasks/${id}/decide`, {
        method: 'PATCH',
        body: JSON.stringify(data),
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: quarantineKeys.lists() }),
  })
}

export function useCancelQuarantine() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiClient<void>(`/quarantine-tasks/${id}/cancel`, {
        method: 'PATCH',
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: quarantineKeys.lists() }),
  })
}
