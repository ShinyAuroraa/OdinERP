import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'

// Mock next/navigation
vi.mock('next/navigation', () => ({
  usePathname: vi.fn(() => '/dashboard'),
  useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() })),
}))

// Mock next/link
vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}))

// Mock next/font/google
vi.mock('next/font/google', () => ({
  Inter: vi.fn(() => ({ className: 'inter' })),
}))

vi.mock('@/lib/auth/AuthContext', () => ({
  useAuthContext: vi.fn(() => ({
    user: { id: 'u1', username: 'john.doe', email: 'john@test.com', tenantId: 't1', roles: ['WMS_OPERATOR'] },
    token: 'fake-token',
    roles: ['WMS_OPERATOR'],
    tenantId: 'tenant-test',
    isAuthenticated: true,
    isLoading: false,
    logout: vi.fn(),
  })),
  KeycloakProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { Breadcrumbs } from './Breadcrumbs'

describe('Sidebar', () => {
  it('renderiza logo Odin WMS', () => {
    render(<Sidebar />)
    expect(screen.getByText('Odin WMS')).toBeInTheDocument()
  })

  it('renderiza itens de navegação principais', () => {
    render(<Sidebar />)
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Armazéns')).toBeInTheDocument()
    expect(screen.getByText('Estoque')).toBeInTheDocument()
    expect(screen.getByText('Picking')).toBeInTheDocument()
    expect(screen.getByText('Relatórios')).toBeInTheDocument()
  })

  it('todos os itens de navegação estão implementados (sem badges "Em breve")', () => {
    render(<Sidebar />)
    const badges = screen.queryAllByText('Em breve')
    expect(badges.length).toBe(0)
  })
})

describe('Header', () => {
  it('renderiza nome do usuário', () => {
    render(<Header />)
    expect(screen.getByText('john.doe')).toBeInTheDocument()
  })

  it('renderiza tenant ID', () => {
    render(<Header />)
    expect(screen.getByText('tenant-test')).toBeInTheDocument()
  })

  it('renderiza botão de logout', () => {
    render(<Header />)
    expect(screen.getByLabelText('Sair')).toBeInTheDocument()
  })
})

describe('Breadcrumbs', () => {
  it('renderiza link home', () => {
    render(<Breadcrumbs />)
    expect(screen.getByLabelText('Breadcrumb')).toBeInTheDocument()
  })

  it('renderiza segmento "Dashboard" como current page', () => {
    render(<Breadcrumbs />)
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Dashboard').closest('[aria-current="page"]')).toBeTruthy()
  })
})
