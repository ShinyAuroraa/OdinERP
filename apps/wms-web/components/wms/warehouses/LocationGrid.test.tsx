import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { LocationGrid } from './LocationGrid'
import type { Location } from '@/types/warehouse'

vi.mock('@/lib/api/warehouses', () => ({
  useLocations: vi.fn(),
}))

const mockLocations: Location[] = [
  { id: '1', code: 'L001', fullAddress: 'WH-001/ZN-01/A01/S01/L001', locationType: 'PICKING', status: 'AVAILABLE', shelfId: 's1', createdAt: '', updatedAt: '' },
  { id: '2', code: 'L002', fullAddress: 'WH-001/ZN-01/A01/S01/L002', locationType: 'GENERAL_STORAGE', status: 'OCCUPIED', shelfId: 's1', createdAt: '', updatedAt: '' },
  { id: '3', code: 'L003', fullAddress: 'WH-001/ZN-01/A01/S01/L003', locationType: 'QUARANTINE', status: 'QUARANTINE', shelfId: 's1', createdAt: '', updatedAt: '' },
]

describe('LocationGrid', () => {
  it('renderiza localizações', async () => {
    const { useLocations } = await import('@/lib/api/warehouses')
    vi.mocked(useLocations).mockReturnValue({ data: mockLocations, isLoading: false } as ReturnType<typeof useLocations>)

    render(<LocationGrid shelfId="s1" />)
    expect(screen.getByText('L001')).toBeInTheDocument()
    expect(screen.getByText('L002')).toBeInTheDocument()
    expect(screen.getByText('L003')).toBeInTheDocument()
  })

  it('exibe estado vazio quando sem localizações', async () => {
    const { useLocations } = await import('@/lib/api/warehouses')
    vi.mocked(useLocations).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useLocations>)

    render(<LocationGrid shelfId="s1" />)
    expect(screen.getByText('Nenhuma localização cadastrada.')).toBeInTheDocument()
  })

  it('exibe botões de filtro por tipo', async () => {
    const { useLocations } = await import('@/lib/api/warehouses')
    vi.mocked(useLocations).mockReturnValue({ data: mockLocations, isLoading: false } as ReturnType<typeof useLocations>)

    render(<LocationGrid shelfId="s1" />)
    expect(screen.getByText('Todos')).toBeInTheDocument()
    // "Picking" pode aparecer no botão de filtro e na card — verificar que ao menos um existe
    expect(screen.getAllByText('Picking').length).toBeGreaterThan(0)
  })

  it('exibe skeleton durante carregamento', async () => {
    const { useLocations } = await import('@/lib/api/warehouses')
    vi.mocked(useLocations).mockReturnValue({ data: undefined, isLoading: true } as ReturnType<typeof useLocations>)

    const { container } = render(<LocationGrid shelfId="s1" />)
    expect(container.querySelector('.animate-pulse')).toBeTruthy()
  })
})
