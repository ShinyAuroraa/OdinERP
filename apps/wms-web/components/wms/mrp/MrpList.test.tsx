import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { MrpList } from './MrpList'
import type { ProductionMaterialRequest } from '@/types/mrp'

vi.mock('@/lib/api/mrp', () => ({
  useMaterialRequests: vi.fn(),
  useMaterialRequest: vi.fn(() => ({ data: undefined, isLoading: false })),
}))

const mockRequests: ProductionMaterialRequest[] = [
  {
    id: 'req-001-uuid', productionOrderId: 'ord-001-uuid', productionOrderNumber: 'OP-001',
    status: 'PENDING', warehouseId: 'w1', items: [
      { id: 'i1', productId: 'p1', productSku: 'SKU-A', productName: 'Produto A', requestedQuantity: 10, reservedQuantity: 0, deliveredQuantity: 0 },
    ],
    tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
  {
    id: 'req-002-uuid', productionOrderId: 'ord-002-uuid', productionOrderNumber: 'OP-002',
    status: 'STOCK_SHORTAGE', warehouseId: 'w1', items: [],
    tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
]

describe('MrpList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de requisições MRP', async () => {
    const { useMaterialRequests } = await import('@/lib/api/mrp')
    vi.mocked(useMaterialRequests).mockReturnValue({ data: { content: mockRequests, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useMaterialRequests>)
    render(<MrpList />)
    expect(screen.getByText('OP-001')).toBeInTheDocument()
    expect(screen.getByText('OP-002')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useMaterialRequests } = await import('@/lib/api/mrp')
    vi.mocked(useMaterialRequests).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useMaterialRequests>)
    const { container } = render(<MrpList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio', async () => {
    const { useMaterialRequests } = await import('@/lib/api/mrp')
    vi.mocked(useMaterialRequests).mockReturnValue({ data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useMaterialRequests>)
    render(<MrpList />)
    expect(screen.getByText('Nenhuma requisição de material encontrada.')).toBeInTheDocument()
  })

  it('exibe header da página', async () => {
    const { useMaterialRequests } = await import('@/lib/api/mrp')
    vi.mocked(useMaterialRequests).mockReturnValue({ data: { content: mockRequests, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useMaterialRequests>)
    render(<MrpList />)
    expect(screen.getByText('MRP')).toBeInTheDocument()
  })

  it('exibe badge Alerta para STOCK_SHORTAGE', async () => {
    const { useMaterialRequests } = await import('@/lib/api/mrp')
    vi.mocked(useMaterialRequests).mockReturnValue({ data: { content: mockRequests, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useMaterialRequests>)
    render(<MrpList />)
    expect(screen.getByText('Alerta')).toBeInTheDocument()
  })

  it('exibe status Sem Estoque', async () => {
    const { useMaterialRequests } = await import('@/lib/api/mrp')
    vi.mocked(useMaterialRequests).mockReturnValue({ data: { content: mockRequests, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useMaterialRequests>)
    render(<MrpList />)
    expect(screen.getAllByText('Sem Estoque').length).toBeGreaterThan(0)
  })
})
