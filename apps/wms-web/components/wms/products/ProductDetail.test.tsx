import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { ProductDetail } from './ProductDetail'
import type { ProductWms } from '@/types/product'

const mockProduct: ProductWms = {
  id: '1', sku: 'SKU-001', name: 'Produto Teste',
  storageType: 'REFRIGERATED', status: 'ACTIVE', tenantId: 't1',
  controlsLot: true, controlsSerial: false, controlsExpiry: true,
  requiresSanitaryVigilance: true, weightKg: 2.5, unitsPerLocation: 10,
  ean13: '7891234567890', createdAt: '', updatedAt: '',
}

describe('ProductDetail', () => {
  it('renderiza informações do produto', () => {
    render(<ProductDetail product={mockProduct} />)
    expect(screen.getByText('SKU-001')).toBeInTheDocument()
    expect(screen.getByText('2.5 kg')).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()
  })

  it('renderiza badges de controles ativos', () => {
    render(<ProductDetail product={mockProduct} />)
    expect(screen.getByText('Lote')).toBeInTheDocument()
    expect(screen.getByText('Validade')).toBeInTheDocument()
    expect(screen.getByText('ANVISA')).toBeInTheDocument()
    expect(screen.queryByText('Serial')).not.toBeInTheDocument()
  })

  it('renderiza EAN-13 quando presente', () => {
    render(<ProductDetail product={mockProduct} />)
    expect(screen.getByText('7891234567890')).toBeInTheDocument()
  })

  it('exibe mensagem quando sem controles ativos', () => {
    const productNoControls = { ...mockProduct, controlsLot: false, controlsSerial: false, controlsExpiry: false, requiresSanitaryVigilance: false }
    render(<ProductDetail product={productNoControls} />)
    expect(screen.getByText('Nenhum controle de rastreabilidade ativo.')).toBeInTheDocument()
  })

  it('exibe StorageTypeBadge com tipo correto', () => {
    render(<ProductDetail product={mockProduct} />)
    expect(screen.getByText('Refrigerado')).toBeInTheDocument()
  })
})
