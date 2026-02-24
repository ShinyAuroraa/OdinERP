'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import { useAuth } from '@/hooks/useAuth'
import type {
  ReceivingNote,
  CreateReceivingNoteRequest,
  ConfirmReceivingItemRequest,
} from '@/types/receiving'

export const receivingKeys = {
  all: ['receiving-notes'] as const,
  lists: () => [...receivingKeys.all, 'list'] as const,
  list: (filters?: Record<string, string>) => [...receivingKeys.lists(), filters] as const,
  detail: (id: string) => [...receivingKeys.all, 'detail', id] as const,
}

export function useReceivingNotes(filters?: Record<string, string>) {
  const { token, tenantId } = useAuth()
  const params = filters ? new URLSearchParams(filters).toString() : ''
  return useQuery({
    queryKey: receivingKeys.list(filters),
    queryFn: () =>
      apiClient<ReceivingNote[]>(`/receiving-notes${params ? `?${params}` : ''}`, {
        token,
        tenantId,
      }),
    enabled: !!token,
    staleTime: 30_000,
  })
}

export function useReceivingNote(id: string) {
  const { token, tenantId } = useAuth()
  return useQuery({
    queryKey: receivingKeys.detail(id),
    queryFn: () => apiClient<ReceivingNote>(`/receiving-notes/${id}`, { token, tenantId }),
    enabled: !!token && !!id,
    staleTime: 15_000,
  })
}

export function useCreateReceivingNote() {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateReceivingNoteRequest) =>
      apiClient<ReceivingNote>('/receiving-notes', {
        method: 'POST',
        body: JSON.stringify(data),
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: receivingKeys.lists() }),
  })
}

export function useStartConference(noteId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient<ReceivingNote>(`/receiving-notes/${noteId}/start`, {
        method: 'POST',
        token,
        tenantId,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: receivingKeys.detail(noteId) })
      queryClient.invalidateQueries({ queryKey: receivingKeys.lists() })
    },
  })
}

export function useConfirmItem(noteId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ itemId, data }: { itemId: string; data: ConfirmReceivingItemRequest }) =>
      apiClient<void>(`/receiving-notes/${noteId}/items/${itemId}/confirm`, {
        method: 'POST',
        body: JSON.stringify(data),
        token,
        tenantId,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: receivingKeys.detail(noteId) }),
  })
}

export function useCompleteNote(noteId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient<ReceivingNote>(`/receiving-notes/${noteId}/complete`, {
        method: 'POST',
        token,
        tenantId,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: receivingKeys.detail(noteId) })
      queryClient.invalidateQueries({ queryKey: receivingKeys.lists() })
    },
  })
}

export function useApproveDivergences(noteId: string) {
  const { token, tenantId } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient<ReceivingNote>(`/receiving-notes/${noteId}/approve-divergences`, {
        method: 'POST',
        token,
        tenantId,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: receivingKeys.detail(noteId) })
      queryClient.invalidateQueries({ queryKey: receivingKeys.lists() })
    },
  })
}
