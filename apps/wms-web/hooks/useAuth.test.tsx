import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import React from 'react'
import { KeycloakProvider, useAuthContext } from '@/lib/auth/AuthContext'

vi.mock('@/lib/auth/keycloak', () => {
  const mockKeycloak = {
    init: vi.fn().mockResolvedValue(true),
    login: vi.fn(),
    logout: vi.fn(),
    updateToken: vi.fn().mockResolvedValue(false),
    onTokenExpired: null as (() => void) | null,
    token: (() => {
      const header = btoa(JSON.stringify({ alg: 'RS256' }))
      const payload = btoa(
        JSON.stringify({
          sub: 'user-001',
          preferred_username: 'john.doe',
          email: 'john@example.com',
          tenant_id: 'tenant-xyz',
          realm_access: { roles: ['WMS_OPERATOR'] },
          exp: Math.floor(Date.now() / 1000) + 3600,
        }),
      )
      return `${header}.${payload}.sig`
    })(),
  }
  return {
    getKeycloak: vi.fn(() => mockKeycloak),
    parseJwtPayload: vi.fn((token: string) => {
      const parts = token.split('.')
      if (!parts[1]) return {}
      return JSON.parse(atob(parts[1]))
    }),
  }
})

function TestConsumer() {
  const auth = useAuthContext()
  return (
    <div>
      <span data-testid="username">{auth.user?.username ?? 'none'}</span>
      <span data-testid="tenantId">{auth.tenantId ?? 'none'}</span>
      <span data-testid="isAuth">{auth.isAuthenticated ? 'true' : 'false'}</span>
      <span data-testid="roles">{auth.roles.join(',')}</span>
    </div>
  )
}

describe('useAuth / KeycloakProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('fornece dados de autenticação após init', async () => {
    await act(async () => {
      render(
        <KeycloakProvider>
          <TestConsumer />
        </KeycloakProvider>,
      )
    })

    expect(screen.getByTestId('username').textContent).toBe('john.doe')
    expect(screen.getByTestId('tenantId').textContent).toBe('tenant-xyz')
    expect(screen.getByTestId('isAuth').textContent).toBe('true')
    expect(screen.getByTestId('roles').textContent).toBe('WMS_OPERATOR')
  })

  it('lança erro se usado fora do provider', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<TestConsumer />)).toThrow()
    consoleSpy.mockRestore()
  })
})
