import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { PackingList } from './PackingList'
import type { PackingOrder } from '@/types/packing'

vi.mock('@/lib/api/packing', () => ({
  usePackingOrders: vi.fn(),
  usePackingOrder: vi.fn(() => ({ data: undefined, isLoading: false })),
  usePackingItems: vi.fn(() => ({ data: [], isLoading: false })),
  useStartPacking: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCompletePacking: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

const mockOrders: PackingOrder[] = [
  {
    id: 'p1', packingOrderNumber: 'PACK-001', pickingOrderId: 'o1',
    status: 'PENDING', warehouseId: 'w1', totalItems: 3, packedItems: 0, totalVolumes: 0,
    tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
  {
    id: 'p2', packingOrderNumber: 'PACK-002', pickingOrderId: 'o2',
    status: 'COMPLETED', warehouseId: 'w1', totalItems: 2, packedItems: 2, totalVolumes: 1,
    tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
]

describe('PackingList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de ordens de packing', async () => {
    const { usePackingOrders } = await import('@/lib/api/packing')
    vi.mocked(usePackingOrders).mockReturnValue({ data: { content: mockOrders, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePackingOrders>)
    render(<PackingList />)
    expect(screen.getByText('PACK-001')).toBeInTheDocument()
    expect(screen.getByText('PACK-002')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { usePackingOrders } = await import('@/lib/api/packing')
    vi.mocked(usePackingOrders).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof usePackingOrders>)
    const { container } = render(<PackingList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio', async () => {
    const { usePackingOrders } = await import('@/lib/api/packing')
    vi.mocked(usePackingOrders).mockReturnValue({ data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePackingOrders>)
    render(<PackingList />)
    expect(screen.getByText('Nenhuma ordem de packing encontrada.')).toBeInTheDocument()
  })

  it('exibe header da página', async () => {
    const { usePackingOrders } = await import('@/lib/api/packing')
    vi.mocked(usePackingOrders).mockReturnValue({ data: { content: mockOrders, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePackingOrders>)
    render(<PackingList />)
    expect(screen.getByText('Packing')).toBeInTheDocument()
  })

  it('exibe botão iniciar para ordem pendente', async () => {
    const { usePackingOrders } = await import('@/lib/api/packing')
    vi.mocked(usePackingOrders).mockReturnValue({ data: { content: [mockOrders[0]], totalElements: 1, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePackingOrders>)
    render(<PackingList />)
    expect(screen.getByText('Iniciar')).toBeInTheDocument()
  })

  it('exibe status badge Concluído', async () => {
    const { usePackingOrders } = await import('@/lib/api/packing')
    vi.mocked(usePackingOrders).mockReturnValue({ data: { content: mockOrders, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePackingOrders>)
    render(<PackingList />)
    expect(screen.getAllByText('Concluído').length).toBeGreaterThan(0)
  })
})
