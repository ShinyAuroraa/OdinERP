import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { WarehouseForm } from './WarehouseForm'

vi.mock('@/lib/api/warehouses', () => ({
  useCreateWarehouse: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateWarehouse: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

describe('WarehouseForm', () => {
  it('renderiza formulário de criação quando open=true', () => {
    render(<WarehouseForm open={true} onOpenChange={vi.fn()} />)
    expect(screen.getByText('Novo Armazém')).toBeInTheDocument()
    expect(screen.getByLabelText(/Código/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Nome/i)).toBeInTheDocument()
  })

  it('exibe "Editar Armazém" quando warehouse é fornecido', () => {
    const warehouse = { id: '1', code: 'WH-001', name: 'Principal', status: 'ACTIVE' as const, tenantId: 't1', createdAt: '', updatedAt: '' }
    render(<WarehouseForm open={true} onOpenChange={vi.fn()} warehouse={warehouse} />)
    expect(screen.getByText('Editar Armazém')).toBeInTheDocument()
  })

  it('exibe erro quando código está vazio no submit', async () => {
    render(<WarehouseForm open={true} onOpenChange={vi.fn()} />)
    const submitButton = screen.getByRole('button', { name: /Criar/i })
    fireEvent.click(submitButton)
    await waitFor(() => {
      expect(screen.getByText('Código obrigatório')).toBeInTheDocument()
    })
  })

  it('não mostra o diálogo quando open=false', () => {
    const { container } = render(<WarehouseForm open={false} onOpenChange={vi.fn()} />)
    expect(container.querySelector('[role="dialog"]')).not.toBeInTheDocument()
  })
})
