import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { ReceivingDetail } from './ReceivingDetail'
import type { ReceivingNote } from '@/types/receiving'

vi.mock('@/lib/api/receiving', () => ({
  useReceivingNote: vi.fn(),
  useStartConference: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCompleteNote: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useApproveDivergences: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useConfirmItem: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => false) }))

const mockNote: ReceivingNote = {
  id: 'n1', noteNumber: 'NR-001', warehouseId: 'w1', dockLocationId: 'l1',
  supplierId: 'FORN-001', purchaseOrderRef: 'OC-123', status: 'PENDING',
  items: [
    { id: 'i1', productId: 'p1', productSku: 'SKU-001', productName: 'Produto A', expectedQuantity: 10, status: 'PENDING' },
    { id: 'i2', productId: 'p2', productSku: 'SKU-002', productName: 'Produto B', expectedQuantity: 5, status: 'CONFIRMED' },
  ],
  tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: '',
}

describe('ReceivingDetail', () => {
  it('renderiza detalhes da nota', async () => {
    const { useReceivingNote } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNote).mockReturnValue({ data: mockNote, isLoading: false } as ReturnType<typeof useReceivingNote>)

    render(<ReceivingDetail noteId="n1" />)
    expect(screen.getByText(/NR-001/)).toBeInTheDocument()
    expect(screen.getByText(/SKU-001/)).toBeInTheDocument()
    expect(screen.getByText(/SKU-002/)).toBeInTheDocument()
  })

  it('exibe botão iniciar conferência para nota PENDING', async () => {
    const { useReceivingNote } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNote).mockReturnValue({ data: mockNote, isLoading: false } as ReturnType<typeof useReceivingNote>)

    render(<ReceivingDetail noteId="n1" />)
    expect(screen.getByText('Iniciar Conferência')).toBeInTheDocument()
  })

  it('exibe skeleton durante carregamento', async () => {
    const { useReceivingNote } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNote).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useReceivingNote>)

    const { container } = render(<ReceivingDetail noteId="n1" />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe status badges dos itens', async () => {
    const { useReceivingNote } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNote).mockReturnValue({ data: mockNote, isLoading: false } as ReturnType<typeof useReceivingNote>)

    render(<ReceivingDetail noteId="n1" />)
    expect(screen.getAllByText('Pendente').length).toBeGreaterThan(0)
    expect(screen.getByText('Confirmado')).toBeInTheDocument()
  })

  it('não exibe botão de aprovar divergências para não-supervisor', async () => {
    const { useReceivingNote } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNote).mockReturnValue({
      data: { ...mockNote, status: 'FLAGGED' as const },
      isLoading: false,
    } as ReturnType<typeof useReceivingNote>)

    render(<ReceivingDetail noteId="n1" />)
    expect(screen.queryByText('Aprovar Divergências')).not.toBeInTheDocument()
  })
})
