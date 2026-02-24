import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { TransferList } from './TransferList'
import type { InternalTransfer } from '@/types/transfers'

vi.mock('@/lib/api/transfers', () => ({
  useTransfers: vi.fn(),
  useCreateTransfer: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useConfirmTransfer: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCancelTransfer: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => true) }))

const mockTransfers: InternalTransfer[] = [
  {
    id: 't1', productId: 'p1', productSku: 'SKU-001', productName: 'Produto A',
    quantity: 10, fromLocationId: 'l1', fromLocationCode: 'A-01',
    toLocationId: 'l2', toLocationCode: 'B-01', status: 'PENDING',
    requestedBy: 'user1', tenantId: 't1',
    createdAt: new Date().toISOString(), updatedAt: '',
  },
]

const mockPage = { content: mockTransfers, totalElements: 1, totalPages: 1, number: 0, size: 50 }

describe('TransferList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de transferências', async () => {
    const { useTransfers } = await import('@/lib/api/transfers')
    vi.mocked(useTransfers).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useTransfers>)

    render(<TransferList />)
    expect(screen.getByText('SKU-001')).toBeInTheDocument()
    expect(screen.getByText('A-01')).toBeInTheDocument()
    expect(screen.getByText('B-01')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useTransfers } = await import('@/lib/api/transfers')
    vi.mocked(useTransfers).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useTransfers>)

    const { container } = render(<TransferList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio', async () => {
    const { useTransfers } = await import('@/lib/api/transfers')
    vi.mocked(useTransfers).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 },
      isLoading: false,
    } as ReturnType<typeof useTransfers>)

    render(<TransferList />)
    expect(screen.getByText('Nenhuma transferência interna.')).toBeInTheDocument()
  })

  it('exibe botão Nova Transferência para operator', async () => {
    const { useTransfers } = await import('@/lib/api/transfers')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useTransfers).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useTransfers>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<TransferList />)
    expect(screen.getByText('Nova Transferência')).toBeInTheDocument()
  })

  it('exibe botões de ação para transferência PENDING', async () => {
    const { useTransfers } = await import('@/lib/api/transfers')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useTransfers).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useTransfers>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<TransferList />)
    expect(screen.getByText('Confirmar')).toBeInTheDocument()
    expect(screen.getByText('Cancelar')).toBeInTheDocument()
  })

  it('exibe title da página', async () => {
    const { useTransfers } = await import('@/lib/api/transfers')
    vi.mocked(useTransfers).mockReturnValue({ data: mockPage, isLoading: false } as ReturnType<typeof useTransfers>)

    render(<TransferList />)
    expect(screen.getByText('Transferências Internas')).toBeInTheDocument()
  })
})
