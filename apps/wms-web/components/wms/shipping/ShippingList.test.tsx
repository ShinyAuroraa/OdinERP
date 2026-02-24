import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { ShippingList } from './ShippingList'
import type { Shipment } from '@/types/shipping'

vi.mock('@/lib/api/shipping', () => ({
  useShipments: vi.fn(),
  useShipment: vi.fn(() => ({ data: undefined, isLoading: false })),
  useCreateShipment: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useShipOrder: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useCancelShipment: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

vi.mock('@/hooks/useHasRole', () => ({
  useHasRole: vi.fn(() => false),
}))

const mockShipments: Shipment[] = [
  {
    id: 's1', shippingNumber: 'SHP-001', packingOrderId: 'p1',
    carrier: 'Transportadora A', vehiclePlate: 'ABC-1234',
    status: 'PENDING', tenantId: 't1',
    createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
  {
    id: 's2', shippingNumber: 'SHP-002', packingOrderId: 'p2',
    status: 'SHIPPED', tenantId: 't1',
    createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  },
]

describe('ShippingList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza lista de expedições', async () => {
    const { useShipments } = await import('@/lib/api/shipping')
    vi.mocked(useShipments).mockReturnValue({ data: { content: mockShipments, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useShipments>)
    render(<ShippingList />)
    expect(screen.getByText('SHP-001')).toBeInTheDocument()
    expect(screen.getByText('SHP-002')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useShipments } = await import('@/lib/api/shipping')
    vi.mocked(useShipments).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useShipments>)
    const { container } = render(<ShippingList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe estado vazio', async () => {
    const { useShipments } = await import('@/lib/api/shipping')
    vi.mocked(useShipments).mockReturnValue({ data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useShipments>)
    render(<ShippingList />)
    expect(screen.getByText('Nenhuma expedição encontrada.')).toBeInTheDocument()
  })

  it('exibe header da página', async () => {
    const { useShipments } = await import('@/lib/api/shipping')
    vi.mocked(useShipments).mockReturnValue({ data: { content: mockShipments, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useShipments>)
    render(<ShippingList />)
    expect(screen.getByText('Shipping')).toBeInTheDocument()
  })

  it('exibe botão Nova Expedição', async () => {
    const { useShipments } = await import('@/lib/api/shipping')
    vi.mocked(useShipments).mockReturnValue({ data: { content: mockShipments, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useShipments>)
    render(<ShippingList />)
    expect(screen.getByText('Nova Expedição')).toBeInTheDocument()
  })

  it('exibe status badge Despachado', async () => {
    const { useShipments } = await import('@/lib/api/shipping')
    vi.mocked(useShipments).mockReturnValue({ data: { content: mockShipments, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useShipments>)
    render(<ShippingList />)
    expect(screen.getAllByText('Despachado').length).toBeGreaterThan(0)
  })
})
