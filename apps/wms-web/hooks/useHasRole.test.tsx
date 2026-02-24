import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { useHasRole } from './useHasRole'
import type { WmsRole } from '@/types/auth'

vi.mock('@/lib/auth/AuthContext', () => ({
  useAuthContext: vi.fn(() => ({
    user: { id: 'u1', username: 'supervisor', email: '', tenantId: 't1', roles: ['WMS_SUPERVISOR'] },
    token: 'tok',
    roles: ['WMS_SUPERVISOR'] as WmsRole[],
    tenantId: 't1',
    isAuthenticated: true,
    isLoading: false,
    logout: vi.fn(),
  })),
}))

function RoleConsumer({ role }: { role: WmsRole | WmsRole[] }) {
  const has = useHasRole(role)
  return <span data-testid="result">{has ? 'yes' : 'no'}</span>
}

describe('useHasRole', () => {
  it('retorna true para role que o usuário possui', () => {
    render(<RoleConsumer role="WMS_SUPERVISOR" />)
    expect(screen.getByTestId('result').textContent).toBe('yes')
  })

  it('retorna false para role que o usuário não possui', () => {
    render(<RoleConsumer role="WMS_ADMIN" />)
    expect(screen.getByTestId('result').textContent).toBe('no')
  })

  it('retorna true se pelo menos um role array bate', () => {
    render(<RoleConsumer role={['WMS_ADMIN', 'WMS_SUPERVISOR']} />)
    expect(screen.getByTestId('result').textContent).toBe('yes')
  })

  it('retorna false se nenhum role array bate', () => {
    render(<RoleConsumer role={['WMS_ADMIN', 'WMS_OPERATOR']} />)
    expect(screen.getByTestId('result').textContent).toBe('no')
  })
})
