import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import React from 'react'
import { DataTable } from './DataTable'
import type { ColumnDef } from '@tanstack/react-table'

interface TestRow {
  id: number
  name: string
  status: string
}

const columns: ColumnDef<TestRow>[] = [
  { accessorKey: 'id', header: 'ID' },
  { accessorKey: 'name', header: 'Nome' },
  { accessorKey: 'status', header: 'Status' },
]

const data: TestRow[] = [
  { id: 1, name: 'Armazém A', status: 'ACTIVE' },
  { id: 2, name: 'Armazém B', status: 'INACTIVE' },
  { id: 3, name: 'Armazém C', status: 'PENDING' },
]

describe('DataTable', () => {
  it('renderiza cabeçalhos e dados', () => {
    render(<DataTable columns={columns} data={data} />)
    expect(screen.getByText('ID')).toBeInTheDocument()
    expect(screen.getByText('Nome')).toBeInTheDocument()
    expect(screen.getByText('Status')).toBeInTheDocument()
    expect(screen.getByText('Armazém A')).toBeInTheDocument()
    expect(screen.getByText('Armazém B')).toBeInTheDocument()
  })

  it('exibe estado vazio quando data é array vazio', () => {
    render(<DataTable columns={columns} data={[]} emptyMessage="Sem armazéns cadastrados." />)
    expect(screen.getByText('Sem armazéns cadastrados.')).toBeInTheDocument()
  })

  it('exibe skeletons durante loading', () => {
    render(<DataTable columns={columns} data={[]} loading={true} />)
    // Deve renderizar skeletons (divs com classe animate-pulse do skeleton)
    const skeletons = document.querySelectorAll('.animate-pulse')
    expect(skeletons.length).toBeGreaterThan(0)
  })

  it('exibe campo de busca quando searchable=true', () => {
    render(<DataTable columns={columns} data={data} searchable searchPlaceholder="Buscar armazém..." />)
    expect(screen.getByPlaceholderText('Buscar armazém...')).toBeInTheDocument()
  })

  it('filtra dados pelo campo de busca', () => {
    render(<DataTable columns={columns} data={data} searchable />)
    const input = screen.getByPlaceholderText('Buscar...')
    fireEvent.change(input, { target: { value: 'Armazém B' } })
    expect(screen.getByText('Armazém B')).toBeInTheDocument()
    expect(screen.queryByText('Armazém A')).not.toBeInTheDocument()
  })

  it('exibe controles de paginação quando pagination=true', () => {
    render(<DataTable columns={columns} data={data} pagination />)
    expect(screen.getByText('Anterior')).toBeInTheDocument()
    expect(screen.getByText('Próximo')).toBeInTheDocument()
  })

  it('não exibe paginação quando pagination=false', () => {
    render(<DataTable columns={columns} data={data} pagination={false} />)
    expect(screen.queryByText('Anterior')).not.toBeInTheDocument()
  })
})
