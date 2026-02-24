import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { LoadingState } from './LoadingState'

describe('LoadingState', () => {
  it('renderiza spinner por padrão', () => {
    const { container } = render(<LoadingState />)
    // Loader2 renderiza um svg com animate-spin
    expect(container.querySelector('.animate-spin')).toBeTruthy()
  })

  it('renderiza label no modo spinner', () => {
    render(<LoadingState variant="spinner" label="Carregando armazéns..." />)
    expect(screen.getByText('Carregando armazéns...')).toBeInTheDocument()
  })

  it('renderiza skeleton rows na variante skeleton', () => {
    const { container } = render(<LoadingState variant="skeleton" rows={3} />)
    const skeletons = container.querySelectorAll('.animate-pulse')
    expect(skeletons.length).toBeGreaterThanOrEqual(3)
  })

  it('renderiza skeleton de página completa na variante page', () => {
    const { container } = render(<LoadingState variant="page" rows={3} />)
    const skeletons = container.querySelectorAll('.animate-pulse')
    // Page variant tem header skeletons + table skeletons
    expect(skeletons.length).toBeGreaterThan(3)
  })
})
