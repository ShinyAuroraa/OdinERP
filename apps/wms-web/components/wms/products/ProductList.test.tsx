import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { ProductList } from './ProductList'
import type { ProductWms } from '@/types/product'

vi.mock('@/lib/api/products', () => ({
  useProducts: vi.fn(),
  useDeleteProduct: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCreateProduct: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateProduct: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))
vi.mock('@/hooks/useHasRole', () => ({ useHasRole: vi.fn(() => true) }))

const mockProducts: ProductWms[] = [
  {
    id: '1', sku: 'SKU-001', name: 'Produto A', storageType: 'DRY',
    controlsLot: true, controlsSerial: false, controlsExpiry: false, requiresSanitaryVigilance: false,
    status: 'ACTIVE', tenantId: 't1', createdAt: '', updatedAt: '',
  },
  {
    id: '2', sku: 'SKU-002', name: 'Produto B', storageType: 'REFRIGERATED',
    controlsLot: false, controlsSerial: true, controlsExpiry: true, requiresSanitaryVigilance: true,
    status: 'ACTIVE', tenantId: 't1', createdAt: '', updatedAt: '',
  },
]

describe('ProductList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de produtos', async () => {
    const { useProducts } = await import('@/lib/api/products')
    vi.mocked(useProducts).mockReturnValue({ data: mockProducts, isLoading: false } as ReturnType<typeof useProducts>)

    render(<ProductList />)
    expect(screen.getByText('SKU-001')).toBeInTheDocument()
    expect(screen.getByText('Produto A')).toBeInTheDocument()
    expect(screen.getByText('SKU-002')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useProducts } = await import('@/lib/api/products')
    vi.mocked(useProducts).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useProducts>)

    const { container } = render(<ProductList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio', async () => {
    const { useProducts } = await import('@/lib/api/products')
    vi.mocked(useProducts).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useProducts>)

    render(<ProductList />)
    expect(screen.getByText('Nenhum produto cadastrado.')).toBeInTheDocument()
  })

  it('exibe botão "Novo Produto" para admin', async () => {
    const { useProducts } = await import('@/lib/api/products')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useProducts).mockReturnValue({ data: mockProducts, isLoading: false } as ReturnType<typeof useProducts>)
    vi.mocked(useHasRole).mockReturnValue(true)

    render(<ProductList />)
    expect(screen.getByText('Novo Produto')).toBeInTheDocument()
  })

  it('oculta botão "Novo Produto" para não-admin', async () => {
    const { useProducts } = await import('@/lib/api/products')
    const { useHasRole } = await import('@/hooks/useHasRole')
    vi.mocked(useProducts).mockReturnValue({ data: mockProducts, isLoading: false } as ReturnType<typeof useProducts>)
    vi.mocked(useHasRole).mockReturnValue(false)

    render(<ProductList />)
    expect(screen.queryByText('Novo Produto')).not.toBeInTheDocument()
  })
})
