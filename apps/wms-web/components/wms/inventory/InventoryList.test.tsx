import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { InventoryList } from './InventoryList'
import type { InventoryCount } from '@/types/inventory'

vi.mock('@/lib/api/inventory', () => ({
  useInventoryCounts: vi.fn(),
  useInventoryCount: vi.fn(() => ({ data: undefined, isLoading: false })),
  useCountItems: vi.fn(() => ({ data: undefined, isLoading: false })),
  useCreateInventoryCount: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useStartCount: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useSubmitCountItem: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useReconcile: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useApproveCount: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCloseCount: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => true) }))

const mockCounts: InventoryCount[] = [
  {
    id: 'cnt-001', warehouseId: 'w1', status: 'CREATED',
    totalItems: 50, countedItems: 0, divergentItems: 0,
    createdBy: 'user1', tenantId: 't1',
    createdAt: new Date().toISOString(), updatedAt: '',
  },
  {
    id: 'cnt-002', warehouseId: 'w1', status: 'IN_PROGRESS',
    totalItems: 30, countedItems: 20, divergentItems: 2,
    createdBy: 'user1', tenantId: 't1',
    createdAt: new Date().toISOString(), updatedAt: '',
  },
]

describe('InventoryList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de sessões de inventário', async () => {
    const { useInventoryCounts } = await import('@/lib/api/inventory')
    vi.mocked(useInventoryCounts).mockReturnValue({ data: mockCounts, isLoading: false } as ReturnType<typeof useInventoryCounts>)

    render(<InventoryList />)
    expect(screen.getAllByText(/cnt-001/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/cnt-002/).length).toBeGreaterThan(0)
  })

  it('exibe estado de carregamento', async () => {
    const { useInventoryCounts } = await import('@/lib/api/inventory')
    vi.mocked(useInventoryCounts).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useInventoryCounts>)

    const { container } = render(<InventoryList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio', async () => {
    const { useInventoryCounts } = await import('@/lib/api/inventory')
    vi.mocked(useInventoryCounts).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useInventoryCounts>)

    render(<InventoryList />)
    expect(screen.getByText('Nenhuma sessão de inventário.')).toBeInTheDocument()
  })

  it('exibe botão Nova Sessão para supervisor', async () => {
    const { useInventoryCounts } = await import('@/lib/api/inventory')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useInventoryCounts).mockReturnValue({ data: mockCounts, isLoading: false } as ReturnType<typeof useInventoryCounts>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<InventoryList />)
    expect(screen.getByText('Nova Sessão')).toBeInTheDocument()
  })

  it('oculta botão Nova Sessão para não-supervisor', async () => {
    const { useInventoryCounts } = await import('@/lib/api/inventory')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useInventoryCounts).mockReturnValue({ data: mockCounts, isLoading: false } as ReturnType<typeof useInventoryCounts>)
    vi.mocked(useHasRole).mockReturnValue(false)

    render(<InventoryList />)
    expect(screen.queryByText('Nova Sessão')).not.toBeInTheDocument()
  })

  it('exibe title da página', async () => {
    const { useInventoryCounts } = await import('@/lib/api/inventory')
    vi.mocked(useInventoryCounts).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useInventoryCounts>)

    render(<InventoryList />)
    expect(screen.getByText('Inventário Físico')).toBeInTheDocument()
  })
})
