import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { StockList } from './StockList'
import type { StockBalance } from '@/types/stock'

vi.mock('@/lib/api/stock', () => ({
  useStockBalance: vi.fn(),
  useLocationStock: vi.fn(() => ({ data: [], isLoading: false })),
  useWarehouseOccupation: vi.fn(() => ({ data: [], isLoading: false })),
}))

const mockStock: StockBalance[] = [
  {
    id: 's1', productId: 'p1', productSku: 'SKU-001', productName: 'Produto A',
    locationId: 'l1', locationCode: 'A-01-01', warehouseId: 'w1',
    availableQuantity: 100, reservedQuantity: 10, totalQuantity: 110,
    tenantId: 't1', updatedAt: new Date().toISOString(),
  },
  {
    id: 's2', productId: 'p2', productSku: 'SKU-002', productName: 'Produto B',
    locationId: 'l2', locationCode: 'B-02-01', warehouseId: 'w1',
    lotNumber: 'LOT-001', availableQuantity: 50, reservedQuantity: 0, totalQuantity: 50,
    tenantId: 't1', updatedAt: new Date().toISOString(),
  },
]

describe('StockList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de saldo de estoque', async () => {
    const { useStockBalance } = await import('@/lib/api/stock')
    vi.mocked(useStockBalance).mockReturnValue({ data: mockStock, isLoading: false } as ReturnType<typeof useStockBalance>)

    render(<StockList />)
    expect(screen.getByText('SKU-001')).toBeInTheDocument()
    expect(screen.getByText('SKU-002')).toBeInTheDocument()
    expect(screen.getByText('A-01-01')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useStockBalance } = await import('@/lib/api/stock')
    vi.mocked(useStockBalance).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useStockBalance>)

    const { container } = render(<StockList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio quando sem estoque', async () => {
    const { useStockBalance } = await import('@/lib/api/stock')
    vi.mocked(useStockBalance).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useStockBalance>)

    render(<StockList />)
    expect(screen.getByText('Nenhum item em estoque.')).toBeInTheDocument()
  })

  it('exibe coluna de lote quando disponível', async () => {
    const { useStockBalance } = await import('@/lib/api/stock')
    vi.mocked(useStockBalance).mockReturnValue({ data: mockStock, isLoading: false } as ReturnType<typeof useStockBalance>)

    render(<StockList />)
    expect(screen.getByText('LOT-001')).toBeInTheDocument()
  })

  it('exibe header da página', async () => {
    const { useStockBalance } = await import('@/lib/api/stock')
    vi.mocked(useStockBalance).mockReturnValue({ data: mockStock, isLoading: false } as ReturnType<typeof useStockBalance>)

    render(<StockList />)
    expect(screen.getByText('Estoque')).toBeInTheDocument()
  })

  it('exibe quantidades disponível e reservada', async () => {
    const { useStockBalance } = await import('@/lib/api/stock')
    vi.mocked(useStockBalance).mockReturnValue({ data: mockStock, isLoading: false } as ReturnType<typeof useStockBalance>)

    render(<StockList />)
    expect(screen.getAllByText('100').length).toBeGreaterThan(0)
    expect(screen.getAllByText('10').length).toBeGreaterThan(0)
  })
})
