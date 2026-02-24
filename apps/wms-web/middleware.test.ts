import { describe, it, expect, vi, beforeEach } from 'vitest'

// JWT payload: { sub, tenant_id, realm_access: { roles }, exp: far future }
const VALID_TOKEN = (() => {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const payload = btoa(
    JSON.stringify({
      sub: 'user-123',
      preferred_username: 'testuser',
      email: 'test@example.com',
      tenant_id: 'tenant-abc',
      realm_access: { roles: ['WMS_OPERATOR'] },
      exp: Math.floor(Date.now() / 1000) + 3600,
    }),
  )
  const sig = btoa('fake-signature')
  return `${header}.${payload}.${sig}`
})()

const EXPIRED_TOKEN = (() => {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const payload = btoa(
    JSON.stringify({
      sub: 'user-123',
      tenant_id: 'tenant-abc',
      realm_access: { roles: ['WMS_OPERATOR'] },
      exp: Math.floor(Date.now() / 1000) - 3600,
    }),
  )
  const sig = btoa('fake-signature')
  return `${header}.${payload}.${sig}`
})()

const ADMIN_TOKEN = (() => {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const payload = btoa(
    JSON.stringify({
      sub: 'admin-456',
      preferred_username: 'adminuser',
      tenant_id: 'tenant-abc',
      realm_access: { roles: ['WMS_ADMIN'] },
      exp: Math.floor(Date.now() / 1000) + 3600,
    }),
  )
  const sig = btoa('fake-signature')
  return `${header}.${payload}.${sig}`
})()

function makeRequest(pathname: string, token?: string): Request {
  const url = `http://localhost:3000${pathname}`
  const headers = new Headers()
  if (token) {
    headers.set('cookie', `wms-auth-token=${token}`)
  }
  return new Request(url, { headers })
}

// Import middleware logic inline to test (isolate from Next.js)
function parseJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return {}
  try {
    const base64 = parts[1]!.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const binary = atob(padded)
    return JSON.parse(binary) as Record<string, unknown>
  } catch {
    return {}
  }
}

function isTokenExpired(payload: Record<string, unknown>): boolean {
  const exp = payload['exp']
  if (typeof exp !== 'number') return true
  return Date.now() / 1000 > exp
}

function getTokenFromCookieHeader(cookieHeader: string | null): string | undefined {
  if (!cookieHeader) return undefined
  const match = cookieHeader.match(/wms-auth-token=([^;]+)/)
  return match?.[1]
}

describe('Middleware logic', () => {
  describe('parseJwtPayload', () => {
    it('extrai payload do token válido', () => {
      const payload = parseJwtPayload(VALID_TOKEN)
      expect(payload['sub']).toBe('user-123')
      expect(payload['tenant_id']).toBe('tenant-abc')
    })

    it('retorna {} para token inválido', () => {
      expect(parseJwtPayload('not.a.token.at.all')).toEqual({})
      expect(parseJwtPayload('only-one-part')).toEqual({})
    })
  })

  describe('isTokenExpired', () => {
    it('retorna false para token com exp no futuro', () => {
      const payload = parseJwtPayload(VALID_TOKEN)
      expect(isTokenExpired(payload)).toBe(false)
    })

    it('retorna true para token expirado', () => {
      const payload = parseJwtPayload(EXPIRED_TOKEN)
      expect(isTokenExpired(payload)).toBe(true)
    })

    it('retorna true quando exp ausente', () => {
      expect(isTokenExpired({})).toBe(true)
    })
  })

  describe('cookie parsing', () => {
    it('extrai token do cookie header', () => {
      const token = getTokenFromCookieHeader(`wms-auth-token=${VALID_TOKEN}; other=val`)
      expect(token).toBe(VALID_TOKEN)
    })

    it('retorna undefined sem cookie', () => {
      expect(getTokenFromCookieHeader(null)).toBeUndefined()
      expect(getTokenFromCookieHeader('other=val')).toBeUndefined()
    })
  })

  describe('role extraction', () => {
    it('extrai roles WMS do token ADMIN', () => {
      const payload = parseJwtPayload(ADMIN_TOKEN)
      const roles = (payload['realm_access'] as { roles?: string[] } | undefined)?.roles ?? []
      expect(roles).toContain('WMS_ADMIN')
    })

    it('extrai roles WMS do token OPERATOR', () => {
      const payload = parseJwtPayload(VALID_TOKEN)
      const roles = (payload['realm_access'] as { roles?: string[] } | undefined)?.roles ?? []
      expect(roles).toContain('WMS_OPERATOR')
      expect(roles).not.toContain('WMS_ADMIN')
    })
  })
})

describe('Middleware route protection (middleware-role)', () => {
  const ROLE_PROTECTED_PATHS: Record<string, string[]> = {
    '/admin': ['WMS_ADMIN'],
  }

  function checkRoleAccess(pathname: string, realmRoles: string[]): boolean {
    for (const [protectedPath, requiredRoles] of Object.entries(ROLE_PROTECTED_PATHS)) {
      if (pathname.startsWith(protectedPath)) {
        return requiredRoles.some((r) => realmRoles.includes(r))
      }
    }
    return true
  }

  it('permite WMS_ADMIN acessar /admin', () => {
    const payload = parseJwtPayload(ADMIN_TOKEN)
    const roles = (payload['realm_access'] as { roles?: string[] } | undefined)?.roles ?? []
    expect(checkRoleAccess('/admin', roles)).toBe(true)
  })

  it('bloqueia WMS_OPERATOR em /admin', () => {
    const payload = parseJwtPayload(VALID_TOKEN)
    const roles = (payload['realm_access'] as { roles?: string[] } | undefined)?.roles ?? []
    expect(checkRoleAccess('/admin', roles)).toBe(false)
  })

  it('permite qualquer role em rota não protegida', () => {
    expect(checkRoleAccess('/dashboard', ['WMS_OPERATOR'])).toBe(true)
    expect(checkRoleAccess('/stock', ['WMS_VIEWER'])).toBe(true)
  })
})
