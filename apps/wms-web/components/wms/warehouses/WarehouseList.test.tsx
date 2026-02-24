import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { WarehouseList } from './WarehouseList'
import type { Warehouse } from '@/types/warehouse'

// Mock hooks
vi.mock('@/lib/api/warehouses', () => ({
  useWarehouses: vi.fn(),
  useDeleteWarehouse: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCreateWarehouse: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateWarehouse: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => true) }))

const mockWarehouses: Warehouse[] = [
  { id: '1', code: 'WH-001', name: 'Armazém Principal', status: 'ACTIVE', tenantId: 't1', zonesCount: 3, createdAt: '', updatedAt: '' },
  { id: '2', code: 'WH-002', name: 'Armazém Secundário', status: 'INACTIVE', tenantId: 't1', zonesCount: 1, createdAt: '', updatedAt: '' },
]

describe('WarehouseList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renderiza lista de armazéns', async () => {
    const { useWarehouses } = await import('@/lib/api/warehouses')
    vi.mocked(useWarehouses).mockReturnValue({ data: mockWarehouses, isLoading: false } as ReturnType<typeof useWarehouses>)

    render(<WarehouseList />)
    expect(screen.getByText('Armazém Principal')).toBeInTheDocument()
    expect(screen.getByText('Armazém Secundário')).toBeInTheDocument()
    expect(screen.getByText('WH-001')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useWarehouses } = await import('@/lib/api/warehouses')
    vi.mocked(useWarehouses).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useWarehouses>)

    const { container } = render(<WarehouseList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio quando sem dados', async () => {
    const { useWarehouses } = await import('@/lib/api/warehouses')
    vi.mocked(useWarehouses).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useWarehouses>)

    render(<WarehouseList />)
    expect(screen.getByText('Nenhum armazém cadastrado.')).toBeInTheDocument()
  })

  it('exibe botão "Novo Armazém" para admins', async () => {
    const { useWarehouses } = await import('@/lib/api/warehouses')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useWarehouses).mockReturnValue({ data: mockWarehouses, isLoading: false } as ReturnType<typeof useWarehouses>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<WarehouseList />)
    expect(screen.getByText('Novo Armazém')).toBeInTheDocument()
  })

  it('oculta botão "Novo Armazém" para não-admins', async () => {
    const { useWarehouses } = await import('@/lib/api/warehouses')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useWarehouses).mockReturnValue({ data: mockWarehouses, isLoading: false } as ReturnType<typeof useWarehouses>)
    vi.mocked(useHasRole).mockReturnValue(false)

    render(<WarehouseList />)
    expect(screen.queryByText('Novo Armazém')).not.toBeInTheDocument()
  })
})
