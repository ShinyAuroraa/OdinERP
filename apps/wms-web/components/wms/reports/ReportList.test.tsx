import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { ReportList } from './ReportList'
import type { Report } from '@/types/reports'

vi.mock('@/lib/api/reports', () => ({
  useReports: vi.fn(),
  useGenerateReport: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useSchedules: vi.fn(() => ({ data: [], isLoading: false })),
  useCreateSchedule: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useToggleSchedule: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

const mockReports: Report[] = [
  {
    id: 'r1', type: 'SNGPC', status: 'GENERATED',
    periodFrom: '2026-01-01', periodTo: '2026-01-31',
    generatedAt: '2026-02-01T10:00:00Z',
    tenantId: 't1', createdAt: new Date().toISOString(),
  },
  {
    id: 'r2', type: 'ANVISA', status: 'PENDING',
    periodFrom: '2026-01-01', periodTo: '2026-01-31',
    tenantId: 't1', createdAt: new Date().toISOString(),
  },
]

describe('ReportList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renderiza a página de relatórios', async () => {
    const { useReports } = await import('@/lib/api/reports')
    vi.mocked(useReports).mockReturnValue({ data: { content: mockReports, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useReports>)
    render(<ReportList />)
    expect(screen.getByText('Relatórios')).toBeInTheDocument()
  })

  it('exibe estado de carregamento', async () => {
    const { useReports } = await import('@/lib/api/reports')
    vi.mocked(useReports).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useReports>)
    const { container } = render(<ReportList />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('exibe abas de tipos de relatório', async () => {
    const { useReports } = await import('@/lib/api/reports')
    vi.mocked(useReports).mockReturnValue({ data: { content: mockReports, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useReports>)
    render(<ReportList />)
    expect(screen.getAllByText('SNGPC').length).toBeGreaterThan(0)
    expect(screen.getByText('Anvisa')).toBeInTheDocument()
    expect(screen.getByText('Receituário')).toBeInTheDocument()
  })

  it('exibe botão Gerar Relatório', async () => {
    const { useReports } = await import('@/lib/api/reports')
    vi.mocked(useReports).mockReturnValue({ data: { content: mockReports, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useReports>)
    render(<ReportList />)
    expect(screen.getAllByText('Gerar Relatório').length).toBeGreaterThan(0)
  })

  it('exibe aba de agendamentos', async () => {
    const { useReports } = await import('@/lib/api/reports')
    vi.mocked(useReports).mockReturnValue({ data: { content: mockReports, totalElements: 2, totalPages: 1, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useReports>)
    render(<ReportList />)
    expect(screen.getByText('Agendamentos')).toBeInTheDocument()
  })

  it('exibe estado vazio na aba SNGPC', async () => {
    const { useReports } = await import('@/lib/api/reports')
    vi.mocked(useReports).mockReturnValue({ data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }, isLoading: false } as ReturnType<typeof useReports>)
    render(<ReportList />)
    expect(screen.getAllByText('Nenhum relatório gerado.').length).toBeGreaterThan(0)
  })
})
