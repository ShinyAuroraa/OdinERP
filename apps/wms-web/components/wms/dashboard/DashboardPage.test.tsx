import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { DashboardPage } from './DashboardPage'
import type { AuditLogEntry } from '@/types/audit'

vi.mock('@/lib/api/dashboard', () => ({
  useDashboardWarehouses: vi.fn(),
  useDashboardProducts: vi.fn(),
  useDashboardPendingPickings: vi.fn(),
  useDashboardMrpAlerts: vi.fn(),
  useDashboardRecentActivity: vi.fn(),
}))

const mockActivity: AuditLogEntry[] = [
  {
    id: 'a1', entityType: 'StockMovement', actionType: 'RECEIVING', performedBy: 'u1',
    performedByName: 'João Silva', tenantId: 't1', createdAt: new Date().toISOString(),
    entityId: 'e1',
  },
  {
    id: 'a2', entityType: 'PickingOrder', actionType: 'COMPLETED', performedBy: 'u2',
    performedByName: 'Maria Souza', tenantId: 't1', createdAt: new Date().toISOString(),
    entityId: 'e2',
  },
]

function mockAll({
  warehouses = [],
  products = [],
  pendingPickings = 0,
  mrpAlerts = 0,
  activity = [],
}: {
  warehouses?: unknown[]
  products?: unknown[]
  pendingPickings?: number
  mrpAlerts?: number
  activity?: AuditLogEntry[]
} = {}) {
  return import('@/lib/api/dashboard').then(({ useDashboardWarehouses, useDashboardProducts, useDashboardPendingPickings, useDashboardMrpAlerts, useDashboardRecentActivity }) => {
    vi.mocked(useDashboardWarehouses).mockReturnValue({ data: warehouses, isLoading: false } as ReturnType<typeof useDashboardWarehouses>)
    vi.mocked(useDashboardProducts).mockReturnValue({ data: products, isLoading: false } as ReturnType<typeof useDashboardProducts>)
    vi.mocked(useDashboardPendingPickings).mockReturnValue({ data: { content: [], totalElements: pendingPickings, totalPages: 1, number: 0, size: 1 }, isLoading: false } as ReturnType<typeof useDashboardPendingPickings>)
    vi.mocked(useDashboardMrpAlerts).mockReturnValue({ data: { content: [], totalElements: mrpAlerts, totalPages: 1, number: 0, size: 1 }, isLoading: false } as ReturnType<typeof useDashboardMrpAlerts>)
    vi.mocked(useDashboardRecentActivity).mockReturnValue({ data: { content: activity, totalElements: activity.length, totalPages: 1, number: 0, size: 5 }, isLoading: false } as ReturnType<typeof useDashboardRecentActivity>)
  })
}

describe('DashboardPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza o header do dashboard', async () => {
    await mockAll({ warehouses: [{}], products: [{}, {}] })
    render(<DashboardPage />)
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useDashboardWarehouses } = await import('@/lib/api/dashboard')
    vi.mocked(useDashboardWarehouses).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useDashboardWarehouses>)
    const { useDashboardProducts } = await import('@/lib/api/dashboard')
    vi.mocked(useDashboardProducts).mockReturnValue({ data: undefined, isLoading: false } as ReturnType<typeof useDashboardProducts>)
    const { useDashboardPendingPickings } = await import('@/lib/api/dashboard')
    vi.mocked(useDashboardPendingPickings).mockReturnValue({ data: undefined, isLoading: false } as ReturnType<typeof useDashboardPendingPickings>)
    const { useDashboardMrpAlerts } = await import('@/lib/api/dashboard')
    vi.mocked(useDashboardMrpAlerts).mockReturnValue({ data: undefined, isLoading: false } as ReturnType<typeof useDashboardMrpAlerts>)
    const { useDashboardRecentActivity } = await import('@/lib/api/dashboard')
    vi.mocked(useDashboardRecentActivity).mockReturnValue({ data: undefined, isLoading: false } as ReturnType<typeof useDashboardRecentActivity>)
    const { container } = render(<DashboardPage />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe KPI cards com valores corretos', async () => {
    await mockAll({ warehouses: [{}, {}], products: [{}, {}, {}], pendingPickings: 7, mrpAlerts: 0 })
    render(<DashboardPage />)
    expect(screen.getByText('2')).toBeInTheDocument() // warehouses
    expect(screen.getByText('3')).toBeInTheDocument() // products
    expect(screen.getByText('7')).toBeInTheDocument() // pending pickings
  })

  it('exibe atividade recente', async () => {
    await mockAll({ activity: mockActivity })
    render(<DashboardPage />)
    expect(screen.getByText('StockMovement')).toBeInTheDocument()
    expect(screen.getByText('João Silva')).toBeInTheDocument()
  })

  it('exibe mensagem de estado vazio na atividade', async () => {
    await mockAll({ activity: [] })
    render(<DashboardPage />)
    expect(screen.getByText('Nenhuma atividade recente.')).toBeInTheDocument()
  })

  it('exibe alerta vermelho quando há alertas MRP', async () => {
    await mockAll({ mrpAlerts: 3 })
    render(<DashboardPage />)
    expect(screen.getByText('Alertas MRP')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })
})
