import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('renderiza status ACTIVE com verde (success)', () => {
    const { container } = render(<StatusBadge status="ACTIVE" />)
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(container.querySelector('.bg-green-100')).toBeTruthy()
  })

  it('renderiza status PENDING com amarelo (warning)', () => {
    const { container } = render(<StatusBadge status="PENDING" />)
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(container.querySelector('.bg-yellow-100')).toBeTruthy()
  })

  it('renderiza status CANCELLED com vermelho (destructive)', () => {
    const { container } = render(<StatusBadge status="CANCELLED" />)
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
    expect(container.querySelector('.bg-red-100')).toBeTruthy()
  })

  it('renderiza status QUARANTINE com laranja', () => {
    const { container } = render(<StatusBadge status="QUARANTINE" />)
    expect(screen.getByText('Quarantine')).toBeInTheDocument()
    expect(container.querySelector('.bg-orange-100')).toBeTruthy()
  })

  it('renderiza status DRAFT com cinza (secondary)', () => {
    const { container } = render(<StatusBadge status="DRAFT" />)
    expect(screen.getByText('Draft')).toBeInTheDocument()
    expect(container.querySelector('.bg-slate-100')).toBeTruthy()
  })

  it('renderiza status desconhecido como secondary (fallback)', () => {
    const { container } = render(<StatusBadge status="UNKNOWN_STATUS_XYZ" />)
    expect(container.querySelector('.bg-slate-100')).toBeTruthy()
  })

  it('exibe label formatado (snake_case → Title Case)', () => {
    render(<StatusBadge status="PICKING_PENDING" />)
    expect(screen.getByText('Picking Pending')).toBeInTheDocument()
  })
})
