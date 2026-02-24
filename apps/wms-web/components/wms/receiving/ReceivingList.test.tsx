import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { ReceivingList } from './ReceivingList'
import type { ReceivingNote } from '@/types/receiving'

vi.mock('@/lib/api/receiving', () => ({
  useReceivingNotes: vi.fn(),
  useCreateReceivingNote: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => true) }))

const mockNotes: ReceivingNote[] = [
  {
    id: '1', noteNumber: 'NR-001', warehouseId: 'w1', warehouseName: 'Armazém A',
    dockLocationId: 'l1', supplierId: 'FORN-001', purchaseOrderRef: 'OC-123',
    status: 'PENDING', items: [], tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: '',
  },
  {
    id: '2', noteNumber: 'NR-002', warehouseId: 'w1', warehouseName: 'Armazém A',
    dockLocationId: 'l1', status: 'COMPLETED', items: [], tenantId: 't1',
    createdAt: new Date().toISOString(), updatedAt: '',
  },
]

describe('ReceivingList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de notas de recebimento', async () => {
    const { useReceivingNotes } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNotes).mockReturnValue({ data: mockNotes, isLoading: false } as ReturnType<typeof useReceivingNotes>)

    render(<ReceivingList />)
    expect(screen.getByText('NR-001')).toBeInTheDocument()
    expect(screen.getByText('NR-002')).toBeInTheDocument()
    expect(screen.getByText('FORN-001')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useReceivingNotes } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNotes).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useReceivingNotes>)

    const { container } = render(<ReceivingList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio quando sem notas', async () => {
    const { useReceivingNotes } = await import('@/lib/api/receiving')
    vi.mocked(useReceivingNotes).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useReceivingNotes>)

    render(<ReceivingList />)
    expect(screen.getByText('Nenhuma nota de recebimento cadastrada.')).toBeInTheDocument()
  })

  it('exibe botão "Nova Nota" para operadores', async () => {
    const { useReceivingNotes } = await import('@/lib/api/receiving')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useReceivingNotes).mockReturnValue({ data: mockNotes, isLoading: false } as ReturnType<typeof useReceivingNotes>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<ReceivingList />)
    expect(screen.getByText('Nova Nota')).toBeInTheDocument()
  })

  it('oculta botão "Nova Nota" para não-operadores', async () => {
    const { useReceivingNotes } = await import('@/lib/api/receiving')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useReceivingNotes).mockReturnValue({ data: mockNotes, isLoading: false } as ReturnType<typeof useReceivingNotes>)
    vi.mocked(useHasRole).mockReturnValue(false)

    render(<ReceivingList />)
    expect(screen.queryByText('Nova Nota')).not.toBeInTheDocument()
  })
})
