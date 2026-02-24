import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { ProductForm } from './ProductForm'

vi.mock('@/lib/api/products', () => ({
  useCreateProduct: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateProduct: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}))

describe('ProductForm', () => {
  it('renderiza formulário de criação', () => {
    render(<ProductForm open={true} onOpenChange={vi.fn()} />)
    expect(screen.getByText('Novo Produto')).toBeInTheDocument()
    expect(screen.getByLabelText(/SKU/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Nome/i)).toBeInTheDocument()
  })

  it('exibe título "Editar Produto" quando product é fornecido', () => {
    const product = {
      id: '1', sku: 'SKU-001', name: 'Produto A', storageType: 'DRY' as const,
      controlsLot: false, controlsSerial: false, controlsExpiry: false, requiresSanitaryVigilance: false,
      status: 'ACTIVE' as const, tenantId: 't1', createdAt: '', updatedAt: '',
    }
    render(<ProductForm open={true} onOpenChange={vi.fn()} product={product} />)
    expect(screen.getByText('Editar Produto')).toBeInTheDocument()
  })

  it('exibe erro quando SKU está vazio no submit', async () => {
    render(<ProductForm open={true} onOpenChange={vi.fn()} />)
    const submitButton = screen.getByRole('button', { name: /Criar/i })
    fireEvent.click(submitButton)
    await waitFor(() => {
      expect(screen.getByText('SKU obrigatório')).toBeInTheDocument()
    })
  })

  it('renderiza checkboxes de controles de rastreabilidade', () => {
    render(<ProductForm open={true} onOpenChange={vi.fn()} />)
    expect(screen.getByText('Controla por Lote')).toBeInTheDocument()
    expect(screen.getByText('Controla por Serial')).toBeInTheDocument()
    expect(screen.getByText('Controla Validade')).toBeInTheDocument()
    expect(screen.getByText('Vigilância Sanitária (ANVISA)')).toBeInTheDocument()
  })

  it('não mostra diálogo quando open=false', () => {
    const { container } = render(<ProductForm open={false} onOpenChange={vi.fn()} />)
    expect(container.querySelector('[role="dialog"]')).not.toBeInTheDocument()
  })
})
