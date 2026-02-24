import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { PageHeader } from './PageHeader'

describe('PageHeader', () => {
  it('renderiza título H1', () => {
    render(<PageHeader title="Gestão de Armazéns" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Gestão de Armazéns' })).toBeInTheDocument()
  })

  it('renderiza descrição quando fornecida', () => {
    render(<PageHeader title="Título" description="Descrição da página WMS" />)
    expect(screen.getByText('Descrição da página WMS')).toBeInTheDocument()
  })

  it('não renderiza descrição quando ausente', () => {
    const { container } = render(<PageHeader title="Apenas título" />)
    expect(container.querySelector('p')).toBeNull()
  })

  it('renderiza slot de ações quando fornecido', () => {
    render(<PageHeader title="Título" actions={<button>Novo Armazém</button>} />)
    expect(screen.getByRole('button', { name: 'Novo Armazém' })).toBeInTheDocument()
  })
})
