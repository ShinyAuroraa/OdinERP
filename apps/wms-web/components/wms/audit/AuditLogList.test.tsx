import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { AuditLogList } from './AuditLogList'
import type { AuditLogEntry, PagedResponse } from '@/types/audit'

vi.mock('@/lib/api/audit', () => ({
  useAuditLog: vi.fn(),
  useRetentionConfig: vi.fn(() => ({ data: undefined, isLoading: false })),
  useUpdateRetentionConfig: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/lib/api/client', () => ({
  apiClient: vi.fn(),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => false) }))

const mockEntries: AuditLogEntry[] = [
  {
    id: 'audit-1', entityType: 'STOCK_MOVEMENT', entityId: 'sm-1',
    actionType: 'CREATE', performedBy: 'user1',
    tenantId: 't1', createdAt: new Date().toISOString(),
  },
]

const mockPage: PagedResponse<AuditLogEntry> = {
  content: mockEntries, totalElements: 1, totalPages: 1, number: 0, size: 50,
}

describe('AuditLogList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de log de auditoria', async () => {
    const { useAuditLog } = await import('@/lib/api/audit')
    vi.mocked(useAuditLog).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useAuditLog>)

    render(<AuditLogList />)
    expect(screen.getByText('STOCK_MOVEMENT')).toBeInTheDocument()
    expect(screen.getByText('CREATE')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useAuditLog } = await import('@/lib/api/audit')
    vi.mocked(useAuditLog).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useAuditLog>)

    const { container } = render(<AuditLogList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio', async () => {
    const { useAuditLog } = await import('@/lib/api/audit')
    vi.mocked(useAuditLog).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 },
      isLoading: false,
    } as ReturnType<typeof useAuditLog>)

    render(<AuditLogList />)
    expect(screen.getByText('Nenhum registro de auditoria.')).toBeInTheDocument()
  })

  it('oculta botão Exportar para não-admin', async () => {
    const { useAuditLog } = await import('@/lib/api/audit')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useAuditLog).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useAuditLog>)
    vi.mocked(useHasRole).mockReturnValue(false)

    render(<AuditLogList />)
    expect(screen.queryByText('Exportar JSON')).not.toBeInTheDocument()
  })

  it('exibe botão Exportar para admin', async () => {
    const { useAuditLog } = await import('@/lib/api/audit')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useAuditLog).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useAuditLog>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<AuditLogList />)
    expect(screen.getByText('Exportar JSON')).toBeInTheDocument()
  })

  it('exibe title da página', async () => {
    const { useAuditLog } = await import('@/lib/api/audit')
    vi.mocked(useAuditLog).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useAuditLog>)

    render(<AuditLogList />)
    expect(screen.getByText('Log de Auditoria')).toBeInTheDocument()
  })
})
