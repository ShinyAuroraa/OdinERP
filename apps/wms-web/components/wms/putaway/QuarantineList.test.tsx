import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { QuarantineList } from './QuarantineList'
import type { QuarantineTask } from '@/types/putaway'

vi.mock('@/lib/api/putaway', () => ({
  usePutawayTasks: vi.fn(),
  useStartPutaway: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useConfirmPutaway: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCancelPutaway: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useQuarantineTasks: vi.fn(),
  useStartQuarantine: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useDecideQuarantine: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCancelQuarantine: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => true) }))

const mockTasks: QuarantineTask[] = [
  {
    id: 'q1', receivingNoteId: 'n1', productId: 'p1', productSku: 'SKU-QAR-001',
    productName: 'Produto Quarentena A', quantity: 3, status: 'PENDING',
    warehouseId: 'w1', tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: '',
  },
  {
    id: 'q2', receivingNoteId: 'n1', productId: 'p2', productSku: 'SKU-QAR-002',
    productName: 'Produto Quarentena B', quantity: 7, status: 'DECIDED',
    decision: 'RELEASE_TO_STOCK', warehouseId: 'w1', tenantId: 't1',
    createdAt: new Date().toISOString(), updatedAt: '',
  },
]

describe('QuarantineList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de tarefas de quarentena', async () => {
    const { useQuarantineTasks } = await import('@/lib/api/putaway')
    vi.mocked(useQuarantineTasks).mockReturnValue({ data: mockTasks, isLoading: false } as ReturnType<typeof useQuarantineTasks>)

    render(<QuarantineList />)
    expect(screen.getByText('SKU-QAR-001')).toBeInTheDocument()
    expect(screen.getByText('SKU-QAR-002')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useQuarantineTasks } = await import('@/lib/api/putaway')
    vi.mocked(useQuarantineTasks).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useQuarantineTasks>)

    const { container } = render(<QuarantineList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio quando sem tarefas', async () => {
    const { useQuarantineTasks } = await import('@/lib/api/putaway')
    vi.mocked(useQuarantineTasks).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useQuarantineTasks>)

    render(<QuarantineList />)
    expect(screen.getByText('Nenhuma tarefa de quarentena pendente.')).toBeInTheDocument()
  })

  it('exibe botão Iniciar Revisão para supervisor com tarefa PENDING', async () => {
    const { useQuarantineTasks } = await import('@/lib/api/putaway')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useQuarantineTasks).mockReturnValue({ data: mockTasks, isLoading: false } as ReturnType<typeof useQuarantineTasks>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<QuarantineList />)
    expect(screen.getByText('Iniciar Revisão')).toBeInTheDocument()
  })

  it('oculta botão de revisão para não-supervisor', async () => {
    const { useQuarantineTasks } = await import('@/lib/api/putaway')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useQuarantineTasks).mockReturnValue({ data: mockTasks, isLoading: false } as ReturnType<typeof useQuarantineTasks>)
    vi.mocked(useHasRole).mockReturnValue(false)

    render(<QuarantineList />)
    expect(screen.queryByText('Iniciar Revisão')).not.toBeInTheDocument()
    expect(screen.queryByText('Decidir')).not.toBeInTheDocument()
  })
})
