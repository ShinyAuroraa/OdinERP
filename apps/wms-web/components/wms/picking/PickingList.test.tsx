import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { PickingList } from './PickingList'
import type { PickingOrder } from '@/types/picking'

vi.mock('@/lib/api/picking', () => ({
  usePickingOrders: vi.fn(),
  useConfirmPickItem: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCompletePickingOrder: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCancelPickingOrder: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  usePickingItems: vi.fn(() => ({ data: [], isLoading: false })),
  usePickingOrder: vi.fn(() => ({ data: undefined, isLoading: false })),
}))

const mockOrders: PickingOrder[] = [
  {
    id: 'o1', orderNumber: 'PK-001', orderType: 'OUTBOUND', customerName: 'Cliente A',
    priority: 1, status: 'PENDING', warehouseId: 'w1', totalItems: 5, pickedItems: 0,
    createdBy: 'user1', tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
  {
    id: 'o2', orderNumber: 'PK-002', orderType: 'OUTBOUND', customerName: 'Cliente B',
    priority: 2, status: 'IN_PROGRESS', warehouseId: 'w1', totalItems: 3, pickedItems: 1,
    createdBy: 'user1', tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
]

describe('PickingList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de ordens de picking', async () => {
    const { usePickingOrders } = await import('@/lib/api/picking')
    vi.mocked(usePickingOrders).mockReturnValue({ data: { content: mockOrders, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePickingOrders>)
    render(<PickingList />)
    expect(screen.getByText('PK-001')).toBeInTheDocument()
    expect(screen.getByText('PK-002')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { usePickingOrders } = await import('@/lib/api/picking')
    vi.mocked(usePickingOrders).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof usePickingOrders>)
    const { container } = render(<PickingList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio quando sem ordens', async () => {
    const { usePickingOrders } = await import('@/lib/api/picking')
    vi.mocked(usePickingOrders).mockReturnValue({ data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePickingOrders>)
    render(<PickingList />)
    expect(screen.getByText('Nenhuma ordem de picking encontrada.')).toBeInTheDocument()
  })

  it('exibe header da página', async () => {
    const { usePickingOrders } = await import('@/lib/api/picking')
    vi.mocked(usePickingOrders).mockReturnValue({ data: { content: mockOrders, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePickingOrders>)
    render(<PickingList />)
    expect(screen.getByText('Picking')).toBeInTheDocument()
  })

  it('exibe status badge Pendente', async () => {
    const { usePickingOrders } = await import('@/lib/api/picking')
    vi.mocked(usePickingOrders).mockReturnValue({ data: { content: mockOrders, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePickingOrders>)
    render(<PickingList />)
    expect(screen.getAllByText('Pendente').length).toBeGreaterThan(0)
  })

  it('exibe progresso de itens', async () => {
    const { usePickingOrders } = await import('@/lib/api/picking')
    vi.mocked(usePickingOrders).mockReturnValue({ data: { content: mockOrders, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof usePickingOrders>)
    render(<PickingList />)
    expect(screen.getByText('0/5')).toBeInTheDocument()
  })
})
