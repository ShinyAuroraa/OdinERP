import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { PutawayList } from './PutawayList'
import type { PutawayTask } from '@/types/putaway'

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

const mockTasks: PutawayTask[] = [
  {
    id: 't1', receivingNoteId: 'n1', productId: 'p1', productSku: 'SKU-001',
    productName: 'Produto A', quantity: 10, suggestedLocationCode: 'A01-S01',
    suggestedLocationId: 'loc1', status: 'PENDING', warehouseId: 'w1',
    tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: '',
  },
  {
    id: 't2', receivingNoteId: 'n1', productId: 'p2', productSku: 'SKU-002',
    productName: 'Produto B', quantity: 5, status: 'COMPLETED', warehouseId: 'w1',
    tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: '',
  },
]

describe('PutawayList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de tarefas de putaway', async () => {
    const { usePutawayTasks } = await import('@/lib/api/putaway')
    vi.mocked(usePutawayTasks).mockReturnValue({ data: mockTasks, isLoading: false } as ReturnType<typeof usePutawayTasks>)

    render(<PutawayList />)
    expect(screen.getByText('SKU-001')).toBeInTheDocument()
    expect(screen.getByText('SKU-002')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { usePutawayTasks } = await import('@/lib/api/putaway')
    vi.mocked(usePutawayTasks).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof usePutawayTasks>)

    const { container } = render(<PutawayList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio quando sem tarefas', async () => {
    const { usePutawayTasks } = await import('@/lib/api/putaway')
    vi.mocked(usePutawayTasks).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof usePutawayTasks>)

    render(<PutawayList />)
    expect(screen.getByText('Nenhuma tarefa de putaway pendente.')).toBeInTheDocument()
  })

  it('exibe botão Iniciar para tarefas PENDING com role operator', async () => {
    const { usePutawayTasks } = await import('@/lib/api/putaway')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(usePutawayTasks).mockReturnValue({ data: mockTasks, isLoading: false } as ReturnType<typeof usePutawayTasks>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<PutawayList />)
    expect(screen.getByText('Iniciar')).toBeInTheDocument()
  })

  it('oculta botão Iniciar para não-operadores', async () => {
    const { usePutawayTasks } = await import('@/lib/api/putaway')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(usePutawayTasks).mockReturnValue({ data: mockTasks, isLoading: false } as ReturnType<typeof usePutawayTasks>)
    vi.mocked(useHasRole).mockReturnValue(false)

    render(<PutawayList />)
    expect(screen.queryByText('Iniciar')).not.toBeInTheDocument()
  })
})
